package com.example.util

import android.net.Uri
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CurrencyAndSplitUtils {

    private val random = SecureRandom()
    private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * Parses a user-entered INR amount string (e.g. "30000", "30,000.50") into integer paise.
     * Returns null if invalid or <= 0.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val rupees = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        if (rupees < 0) return null
        val paisePart = if (parts.size == 2) {
            val frac = parts[1]
            if (frac.length > 2 || frac.any { !it.isDigit() }) return null
            frac.padEnd(2, '0').toLongOrNull() ?: return null
        } else {
            0L
        }
        val total = rupees * 100L + paisePart
        return if (total > 0L) total else null
    }

    /**
     * Formats integer paise in Indian Rupee convention (e.g. ₹30,000 or ₹1,25,000.50).
     */
    fun formatInr(paise: Long, showZeroPaise: Boolean = false): String {
        val negative = paise < 0
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remPaise = absPaise % 100L
        val formattedRupees = formatIndianGrouping(rupees)
        val prefix = if (negative) "-₹" else "₹"
        return if (remPaise == 0L && !showZeroPaise) {
            "$prefix$formattedRupees"
        } else {
            "$prefix$formattedRupees.${remPaise.toString().padStart(2, '0')}"
        }
    }

    private fun formatIndianGrouping(amount: Long): String {
        val str = amount.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)
        val grouped = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$grouped,$lastThree"
    }

    /**
     * Integer paise equal split with deterministic remainder distribution.
     * Sorted member IDs ensure identical results across devices and exact sum == totalPaise.
     */
    fun splitEqually(totalPaise: Long, memberIds: List<String>): Map<String, Long> {
        require(totalPaise >= 0L) { "Total paise cannot be negative" }
        val uniqueSorted = memberIds.distinct().sorted()
        if (uniqueSorted.isEmpty()) return emptyMap()
        val count = uniqueSorted.size.toLong()
        val base = totalPaise / count
        val remainder = (totalPaise % count).toInt()
        return uniqueSorted.mapIndexed { index, uid ->
            val extra = if (index < remainder) 1L else 0L
            uid to (base + extra)
        }.toMap()
    }

    /**
     * Integer paise percentage split using basis points (10000 bps = 100.00%).
     * Uses Hamilton / largest-remainder method with deterministic UID tie-breaking
     * so all shares always sum to exactly totalPaise.
     */
    fun splitByPercentageBps(totalPaise: Long, bpsByMember: Map<String, Int>): Map<String, Long> {
        if (bpsByMember.isEmpty()) return emptyMap()
        val totalBps = bpsByMember.values.sum()
        if (totalBps <= 0) return bpsByMember.keys.associateWith { 0L }

        val entries = bpsByMember.entries.sortedBy { it.key }
        val rawProducts = entries.map { (uid, bps) ->
            val numerator = totalPaise * bps.toLong()
            val floorVal = numerator / totalBps.toLong()
            val rem = numerator % totalBps.toLong()
            Triple(uid, floorVal, rem)
        }
        val assignedSum = rawProducts.sumOf { it.second }
        val leftover = (totalPaise - assignedSum).toInt()

        val sortedByRemainder = rawProducts.sortedWith(
            compareByDescending<Triple<String, Long, Long>> { it.third }.thenBy { it.first }
        )
        val extraSet = sortedByRemainder.take(leftover.coerceAtLeast(0)).map { it.first }.toSet()

        return rawProducts.associate { (uid, floorVal, _) ->
            uid to (floorVal + if (uid in extraSet) 1L else 0L)
        }
    }

    fun generateBillCode(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val num = 1000 + random.nextInt(9000)
        return "BC-$year-$num"
    }

    fun generateInviteCode(length: Int = 8): String {
        return buildString(length) {
            repeat(length) {
                append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)])
            }
        }
    }

    fun buildUpiPaymentUri(
        upiId: String,
        recipientName: String,
        amountPaise: Long,
        billCode: String,
        billTitle: String
    ): Uri {
        val amountDecimal = String.format(Locale.US, "%.2f", amountPaise / 100.0)
        val note = "BillCircle $billCode: ${billTitle.take(40)}"
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId.trim())
            .appendQueryParameter("pn", recipientName.trim())
            .appendQueryParameter("am", amountDecimal)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", note)
            .appendQueryParameter("tr", billCode)
            .build()
    }

    fun buildWhatsAppShareMessage(
        billTitle: String,
        billCode: String,
        groupName: String,
        totalPaise: Long,
        sharePaise: Long?,
        dueDateMillis: Long,
        upiId: String?
    ): String {
        val dueFormatted = formatDate(dueDateMillis)
        return buildString {
            appendLine("*BillCircle — Shared Bill Notice*")
            appendLine("Group: $groupName")
            appendLine("Bill: $billTitle")
            appendLine("Bill ID: $billCode")
            appendLine("Total Amount: ${formatInr(totalPaise)}")
            if (sharePaise != null) {
                appendLine("Your Share: ${formatInr(sharePaise)}")
            }
            appendLine("Due Date: $dueFormatted")
            if (!upiId.isNullOrBlank()) {
                appendLine("UPI ID: $upiId")
            }
            appendLine()
            append("Please open the BillCircle app to review, approve, upload receipts, and submit your payment proof.")
        }
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "—"
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "—"
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
    }
}
