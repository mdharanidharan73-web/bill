package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class PreparedAttachment(
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Int,
    val contentBase64: String,
    val previewBitmap: Bitmap? = null
)

object AttachmentHelper {

    const val MAX_UPLOAD_BYTES = 520_000 // Firestore safe bound matching firestore.rules (<= 550,000)
    private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "application/pdf")

    /**
     * Reads a picked Photo Picker or SAF Document URI without needing broad storage permissions.
     * Images are smartly resized & compressed to stay fast and safely under Firestore's payload cap.
     */
    fun prepareFromUri(context: Context, uri: Uri): Result<PreparedAttachment> {
        return try {
            val resolver = context.contentResolver
            val rawMime = resolver.getType(uri)?.lowercase() ?: guessMimeFromName(queryFileName(context, uri))
            val fileName = queryFileName(context, uri).ifBlank {
                when (rawMime) {
                    "application/pdf" -> "document_${System.currentTimeMillis()}.pdf"
                    "image/png" -> "receipt_${System.currentTimeMillis()}.png"
                    else -> "receipt_${System.currentTimeMillis()}.jpg"
                }
            }

            if (rawMime == "application/pdf") {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.failure(IllegalArgumentException("Unable to read selected PDF file."))
                if (bytes.isEmpty()) {
                    return Result.failure(IllegalArgumentException("Selected PDF file is empty."))
                }
                if (bytes.size > MAX_UPLOAD_BYTES) {
                    return Result.failure(
                        IllegalArgumentException(
                            "PDF size (${bytes.size / 1024} KB) exceeds the 500 KB inline cloud limit. Please upload a smaller PDF or photo."
                        )
                    )
                }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Result.success(
                    PreparedAttachment(
                        fileName = fileName.take(180),
                        mimeType = "application/pdf",
                        fileSizeBytes = bytes.size,
                        contentBase64 = base64
                    )
                )
            } else if (rawMime == "image/jpeg" || rawMime == "image/jpg" || rawMime == "image/png" || rawMime.startsWith("image/")) {
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.failure(IllegalArgumentException("Unable to read selected image."))
                val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return Result.failure(IllegalArgumentException("Unsupported or corrupted image format."))

                val scaled = scaleBitmapDown(original, maxDim = 1080)
                val out = ByteArrayOutputStream()
                var quality = 82
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                while (out.size() > MAX_UPLOAD_BYTES && quality > 35) {
                    out.reset()
                    quality -= 12
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                val compressedBytes = out.toByteArray()
                if (compressedBytes.size > MAX_UPLOAD_BYTES) {
                    return Result.failure(IllegalArgumentException("Image could not be compressed under 500 KB."))
                }

                val normalizedName = if (fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true)) {
                    fileName
                } else {
                    "${fileName.substringBeforeLast('.')}.jpg"
                }

                Result.success(
                    PreparedAttachment(
                        fileName = normalizedName.take(180),
                        mimeType = "image/jpeg",
                        fileSizeBytes = compressedBytes.size,
                        contentBase64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP),
                        previewBitmap = scaled
                    )
                )
            } else {
                Result.failure(IllegalArgumentException("Unsupported file type ($rawMime). Allowed: JPG, JPEG, PNG, PDF."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decodeImageBitmap(contentBase64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(contentBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBitmapForAi(context: Context, uri: Uri): Result<Bitmap> {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalArgumentException("Could not open selected bill image."))
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalArgumentException("Could not decode bill image. Please select a clear JPG or PNG photo."))
            Result.success(scaleBitmapDown(decoded, maxDim = 1400))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareOrOpenAttachment(
        context: Context,
        fileName: String,
        mimeType: String,
        contentBase64: String
    ): Result<Unit> {
        return try {
            val bytes = Base64.decode(contentBase64, Base64.DEFAULT)
            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val file = File(dir, safeName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (mimeType in ALLOWED_MIME_TYPES) mimeType else "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open or Save $fileName"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun queryFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun guessMimeFromName(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        else -> "image/jpeg"
    }
}
