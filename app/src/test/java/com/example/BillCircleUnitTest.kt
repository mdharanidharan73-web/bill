package com.example

import com.example.util.CurrencyAndSplitUtils
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillCircleUnitTest {

    @Test
    fun equalSplit_exactDivision_fiveMembers() {
        val members = listOf("uid_e", "uid_a", "uid_c", "uid_b", "uid_d")
        val shares = CurrencyAndSplitUtils.splitEqually(3_000_000L, members)
        assertEquals(5, shares.size)
        shares.values.forEach { assertEquals(600_000L, it) }
        assertEquals(3_000_000L, shares.values.sum())
    }

    @Test
    fun equalSplit_deterministicRemainder_threeMembers() {
        val order1 = listOf("u3", "u1", "u2")
        val order2 = listOf("u2", "u3", "u1")
        val shares1 = CurrencyAndSplitUtils.splitEqually(10_000L, order1)
        val shares2 = CurrencyAndSplitUtils.splitEqually(10_000L, order2)

        assertEquals(shares1, shares2)
        assertEquals(3_334L, shares1["u1"])
        assertEquals(3_333L, shares1["u2"])
        assertEquals(3_333L, shares1["u3"])
        assertEquals(10_000L, shares1.values.sum())
    }

    @Test
    fun percentageSplit_exactSumWithRemainder() {
        val bps = mapOf("a" to 3333, "b" to 3333, "c" to 3334)
        val total = 100_001L
        val shares = CurrencyAndSplitUtils.splitByPercentageBps(total, bps)
        assertEquals(3, shares.size)
        assertEquals(total, shares.values.sum())
    }

    @Test
    fun parseRupeesToPaise_validAndInvalidInputs() {
        assertEquals(3_000_000L, CurrencyAndSplitUtils.parseRupeesToPaise("30,000"))
        assertEquals(600_050L, CurrencyAndSplitUtils.parseRupeesToPaise("₹6,000.5"))
        assertEquals(12_345L, CurrencyAndSplitUtils.parseRupeesToPaise("123.45"))
        assertNull(CurrencyAndSplitUtils.parseRupeesToPaise(""))
        assertNull(CurrencyAndSplitUtils.parseRupeesToPaise("0"))
        assertNull(CurrencyAndSplitUtils.parseRupeesToPaise("-50"))
        assertNull(CurrencyAndSplitUtils.parseRupeesToPaise("12.345"))
    }

    @Test
    fun formatInr_indianGrouping() {
        assertEquals("₹30,000", CurrencyAndSplitUtils.formatInr(3_000_000L))
        assertEquals("₹1,25,000", CurrencyAndSplitUtils.formatInr(12_500_000L))
        assertEquals("₹6,000.50", CurrencyAndSplitUtils.formatInr(600_050L))
        assertEquals("-₹500.25", CurrencyAndSplitUtils.formatInr(-50_025L))
        assertEquals("₹1,00,00,000", CurrencyAndSplitUtils.formatInr(1_000_000_000L))
    }

    @Test
    fun qrCodeGenerator_encodesUpiUriToValidSquareMatrix() {
        val uri = "upi://pay?pa=father@okaxis&pn=Rajesh%20Sharma&am=6000.00&cu=INR&tn=BillCircle%20BC-2026-1042&tr=BC-2026-1042"
        val matrix = QrCodeGenerator.encode(uri)
        assertNotNull(matrix)
        val dim = matrix!!.size
        assertTrue("QR dimension should be >= 21", dim >= 21)
        assertTrue("QR dimension should satisfy 17 + 4*v", (dim - 17) % 4 == 0)
        matrix.forEach { row -> assertEquals(dim, row.size) }
        // Verify top-left finder pattern center (3,3) and border (0,0) are dark
        assertTrue(matrix[0][0])
        assertTrue(matrix[3][3])
        // Verify dark module at (dim - 8, 8)
        assertTrue(matrix[dim - 8][8])
    }
}
