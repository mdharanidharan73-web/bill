package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin ISO/IEC 18004 Byte-mode QR Code generator (ECC Level M, Versions 1..10)
 * capable of encoding up to 213 UTF-8 bytes — covering all standard UPI payment URIs.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int = 0,
        val dataCodewordsGroup2: Int = 0,
        val alignmentCenters: IntArray = intArrayOf()
    ) {
        val totalDataCodewords: Int
            get() = numBlocksGroup1 * dataCodewordsGroup1 + numBlocksGroup2 * dataCodewordsGroup2
        val size: Int
            get() = 17 + 4 * version
    }

    // ECC Level M specifications for QR Versions 1..10
    private val specsM = listOf(
        VersionSpec(1, 26, 10, 1, 16, alignmentCenters = intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, alignmentCenters = intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, alignmentCenters = intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, alignmentCenters = intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, alignmentCenters = intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, alignmentCenters = intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, alignmentCenters = intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, alignmentCenters = intArrayOf(6, 28, 50))
    )

    fun generateBitmap(content: String, pixelSize: Int = 512): Bitmap? {
        val matrix = encode(content) ?: return null
        val dim = matrix.size
        val quietZone = 4
        val totalModules = dim + quietZone * 2
        val scale = (pixelSize / totalModules).coerceAtLeast(2)
        val outSize = totalModules * scale
        val pixels = IntArray(outSize * outSize) { Color.WHITE }

        for (y in 0 until dim) {
            for (x in 0 until dim) {
                if (matrix[y][x]) {
                    val pxStart = (x + quietZone) * scale
                    val pyStart = (y + quietZone) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (pyStart + dy) * outSize + pxStart
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = Color.rgb(11, 31, 58) // Deep Navy modules
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, outSize, 0, 0, outSize, outSize)
        }
    }

    fun encode(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val spec = specsM.firstOrNull { spec ->
            val charCountBits = if (spec.version < 10) 8 else 16
            val neededBits = 4 + charCountBits + bytes.size * 8
            neededBits <= spec.totalDataCodewords * 8
        } ?: return null

        val dataCodewords = buildDataCodewords(bytes, spec)
        val interleaved = interleaveBlocks(dataCodewords, spec)
        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(spec, modules, isFunction)
        placeDataBits(interleaved, modules, isFunction)

        // Apply mask 0 (i + j) % 2 == 0 and format bits for ECC M (00) + mask 0 (000)
        val mask = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!isFunction[r][c] && ((r + c) % 2 == 0)) {
                    modules[r][c] = !modules[r][c]
                }
            }
        }
        drawFormatBits(modules, size, eccFormatBits = 0, mask = mask)
        if (spec.version >= 7) {
            drawVersionBits(modules, size, spec.version)
        }
        return modules
    }

    private fun buildDataCodewords(bytes: ByteArray, spec: VersionSpec): IntArray {
        val bits = ArrayList<Int>(spec.totalDataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        // Byte mode indicator = 0100
        appendBits(0b0100, 4)
        appendBits(bytes.size, if (spec.version < 10) 8 else 16)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val capBits = spec.totalDataCodewords * 8
        val terminator = minOf(4, capBits - bits.size)
        appendBits(0, terminator)
        while (bits.size % 8 != 0) bits.add(0)

        val out = IntArray(spec.totalDataCodewords)
        var idx = 0
        var i = 0
        while (i < bits.size) {
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[i + b]
            out[idx++] = v
            i += 8
        }
        var pad = 0xEC
        while (idx < out.size) {
            out[idx++] = pad
            pad = if (pad == 0xEC) 0x11 else 0xEC
        }
        return out
    }

    private fun interleaveBlocks(data: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        val gen = rsGeneratorPoly(spec.ecCodewordsPerBlock)
        var offset = 0

        repeat(spec.numBlocksGroup1) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            ecBlocks.add(rsComputeRemainder(block, gen))
        }
        repeat(spec.numBlocksGroup2) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            ecBlocks.add(rsComputeRemainder(block, gen))
        }

        val result = IntArray(spec.totalCodewords)
        var k = 0
        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        for (i in 0 until maxDataLen) {
            for (b in dataBlocks) {
                if (i < b.size) result[k++] = b[i]
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (b in ecBlocks) {
                result[k++] = b[i]
            }
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var a = x
        var b = y
        var z = 0
        repeat(8) {
            if ((b and 1) != 0) z = z xor a
            val hi = (a and 0x80) != 0
            a = (a shl 1) and 0xFF
            if (hi) a = a xor 0x1D
            b = b ushr 1
        }
        return z
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        var root = 1
        repeat(degree) {
            val next = IntArray(poly.size + 1)
            for (i in poly.indices) {
                next[i] = next[i] xor poly[i]
                next[i + 1] = next[i + 1] xor gfMultiply(poly[i], root)
            }
            poly = next
            root = gfMultiply(root, 2)
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, gen: IntArray): IntArray {
        val rem = IntArray(gen.size - 1)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, rem.size - 1)
            rem[rem.size - 1] = 0
            for (i in rem.indices) {
                rem[i] = rem[i] xor gfMultiply(gen[i + 1], factor)
            }
        }
        return rem
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        modules: Array<BooleanArray>,
        isFunc: Array<BooleanArray>
    ) {
        val size = spec.size
        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = dark
                isFunc[r][c] = true
            }
        }

        // Finder patterns + separators
        val finders = listOf(0 to 0, 0 to (size - 7), (size - 7) to 0)
        for ((fr, fc) in finders) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = fr + dr
                    val c = fc + dc
                    val inBox = dr in 0..6 && dc in 0..6
                    val dark = inBox && (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                    setFunc(r, c, dark)
                }
            }
        }

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFunc(6, i, dark)
            setFunc(i, 6, dark)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (rCenter in centers) {
            for (cCenter in centers) {
                val overlapsFinder =
                    (rCenter == 6 && cCenter == 6) ||
                        (rCenter == 6 && cCenter == centers.last()) ||
                        (rCenter == centers.last() && cCenter == 6)
                if (overlapsFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        setFunc(rCenter + dr, cCenter + dc, dark)
                    }
                }
            }
        }

        // Reserve format info strips & dark module
        for (i in 0..8) {
            if (i != 6) {
                isFunc[8][i] = true
                isFunc[i][8] = true
            }
        }
        for (i in size - 8 until size) {
            isFunc[8][i] = true
            isFunc[i][8] = true
        }
        setFunc(size - 8, 8, true)

        // Reserve version info blocks for v >= 7
        if (spec.version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    isFunc[i][size - 11 + j] = true
                    isFunc[size - 11 + j][i] = true
                }
            }
        }
    }

    private fun placeDataBits(
        codewords: IntArray,
        modules: Array<BooleanArray>,
        isFunc: Array<BooleanArray>
    ) {
        val size = modules.size
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                val r = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunc[r][c]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else false
                        modules[r][c] = dark
                        bitIdx++
                    }
                }
            }
            right -= 2
            upward = !upward
        }
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, size: Int, eccFormatBits: Int, mask: Int) {
        val data = (eccFormatBits shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) modules[i][8] = ((bits ushr i) and 1) != 0
        modules[7][8] = ((bits ushr 6) and 1) != 0
        modules[8][8] = ((bits ushr 7) and 1) != 0
        modules[8][7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8][14 - i] = ((bits ushr i) and 1) != 0

        for (i in 0..7) modules[8][size - 1 - i] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[size - 15 + i][8] = ((bits ushr i) and 1) != 0
    }

    private fun drawVersionBits(modules: Array<BooleanArray>, size: Int, version: Int) {
        var rem = version
        repeat(12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[a][b] = dark
            modules[b][a] = dark
        }
    }
}
