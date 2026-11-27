package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.AuditLog
import com.example.data.Bill
import com.example.data.BillParticipant
import java.io.File
import java.io.FileOutputStream

object ExportManager {

    fun sharePlainText(context: Context, text: String, chooserTitle: String = "Share Bill via WhatsApp or Apps") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun launchUpiPaymentIntent(
        context: Context,
        upiId: String,
        recipientName: String,
        amountPaise: Long,
        billCode: String,
        billTitle: String
    ): Result<Unit> {
        return try {
            val uri = CurrencyAndSplitUtils.buildUpiPaymentUri(
                upiId = upiId,
                recipientName = recipientName,
                amountPaise = amountPaise,
                billCode = billCode,
                billTitle = billTitle
            )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay ${CurrencyAndSplitUtils.formatInr(amountPaise)} via UPI")
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates an official Bill Clearance Receipt PDF using Android's native PdfDocument API
     * and opens the Android Share Sheet so it can be saved or sent via WhatsApp/Email.
     */
    fun exportBillClearanceReceiptPdf(
        context: Context,
        bill: Bill,
        participants: List<BillParticipant>,
        auditLogs: List<AuditLog>
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val navyPaint = Paint().apply { color = Color.rgb(11, 31, 58); style = Paint.Style.FILL }
            val tealPaint = Paint().apply { color = Color.rgb(13, 148, 136); style = Paint.Style.FILL }
            val whiteTitle = Paint().apply {
                color = Color.WHITE; textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            }
            val whiteSub = Paint().apply { color = Color.rgb(204, 251, 241); textSize = 11f; isAntiAlias = true }
            val headingPaint = Paint().apply {
                color = Color.rgb(11, 31, 58); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            }
            val bodyPaint = Paint().apply { color = Color.rgb(30, 41, 59); textSize = 11f; isAntiAlias = true }
            val mutedPaint = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 10f; isAntiAlias = true }
            val linePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }

            // Header Banner
            canvas.drawRect(0f, 0f, 595f, 102f, navyPaint)
            canvas.drawRect(0f, 96f, 595f, 102f, tealPaint)
            canvas.drawText("BILLCIRCLE", 36f, 44f, whiteTitle)
            canvas.drawText("OFFICIAL BILL CLEARANCE & PAYMENT RECEIPT", 36f, 66f, whiteSub)
            canvas.drawText("Bill ID: ${bill.billCode}   |   Status: ${bill.status}", 36f, 84f, whiteSub)

            var y = 132f
            canvas.drawText(bill.title, 36f, y, headingPaint)
            y += 18f
            canvas.drawText(
                "Group: ${bill.groupName}   •   Category: ${bill.category}   •   Merchant: ${bill.merchantName ?: "—"}",
                36f, y, mutedPaint
            )
            y += 16f
            canvas.drawText(
                "Bill Date: ${CurrencyAndSplitUtils.formatDate(bill.billDateMillis)}   •   Due Date: ${CurrencyAndSplitUtils.formatDate(bill.dueDateMillis)}",
                36f, y, mutedPaint
            )
            if (bill.clearedAtMillis != null && bill.clearedAtMillis > 0L) {
                y += 16f
                canvas.drawText(
                    "Cleared On: ${CurrencyAndSplitUtils.formatDateTime(bill.clearedAtMillis)}",
                    36f, y, Paint(bodyPaint).apply { color = Color.rgb(5, 150, 105); typeface = Typeface.DEFAULT_BOLD }
                )
            }
            if (!bill.clearanceOverrideReason.isNullOrBlank()) {
                y += 16f
                canvas.drawText("Admin Override Note: ${bill.clearanceOverrideReason}", 36f, y, mutedPaint)
            }

            // Financial Summary Box
            y += 20f
            val boxBg = Paint().apply { color = Color.rgb(246, 248, 251); style = Paint.Style.FILL }
            canvas.drawRect(36f, y, 559f, y + 66f, boxBg)
            val outstanding = (bill.totalPaise - bill.collectedPaise).coerceAtLeast(0L)
            canvas.drawText("Total Bill Amount", 52f, y + 22f, mutedPaint)
            canvas.drawText(CurrencyAndSplitUtils.formatInr(bill.totalPaise), 52f, y + 46f, headingPaint)

            canvas.drawText("Total Collected", 225f, y + 22f, mutedPaint)
            canvas.drawText(CurrencyAndSplitUtils.formatInr(bill.collectedPaise), 225f, y + 46f, headingPaint)

            canvas.drawText("Outstanding", 400f, y + 22f, mutedPaint)
            canvas.drawText(CurrencyAndSplitUtils.formatInr(outstanding), 400f, y + 46f, headingPaint)

            y += 92f
            canvas.drawText("Member Share & Payment Breakdown", 36f, y, headingPaint)
            y += 14f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 18f
            canvas.drawText("Member", 36f, y, mutedPaint)
            canvas.drawText("Share", 220f, y, mutedPaint)
            canvas.drawText("Verified Paid", 315f, y, mutedPaint)
            canvas.drawText("Status", 435f, y, mutedPaint)
            y += 8f
            canvas.drawLine(36f, y, 559f, y, linePaint)

            for (p in participants.take(16)) {
                y += 20f
                canvas.drawText(p.participantName.take(26), 36f, y, bodyPaint)
                canvas.drawText(CurrencyAndSplitUtils.formatInr(p.sharePaise), 220f, y, bodyPaint)
                canvas.drawText(CurrencyAndSplitUtils.formatInr(p.paidPaise), 315f, y, bodyPaint)
                canvas.drawText(p.paymentStatus.replace("_", " "), 435f, y, mutedPaint)
            }

            if (auditLogs.isNotEmpty() && y < 680f) {
                y += 30f
                canvas.drawText("Recent Audit Trail", 36f, y, headingPaint)
                y += 10f
                canvas.drawLine(36f, y, 559f, y, linePaint)
                for (log in auditLogs.take(6)) {
                    if (y > 780f) break
                    y += 16f
                    val ts = log.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDate(it) } ?: ""
                    canvas.drawText("[$ts] ${log.actorName}: ${log.summary.take(72)}", 36f, y, mutedPaint)
                }
            }

            canvas.drawText(
                "Generated by BillCircle • Verified digital financial record",
                36f, 816f, mutedPaint
            )

            pdf.finishPage(page)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "BillCircle_Receipt_${bill.billCode}.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            shareFile(context, file, "application/pdf", "Share Bill Clearance Receipt")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a formatted PDF Report for any selected report scope.
     */
    fun exportFinancialReportPdf(
        context: Context,
        reportTitle: String,
        subtitle: String,
        bills: List<Bill>
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val navyPaint = Paint().apply { color = Color.rgb(11, 31, 58); style = Paint.Style.FILL }
            val whiteTitle = Paint().apply {
                color = Color.WHITE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            val whiteSub = Paint().apply { color = Color.rgb(204, 251, 241); textSize = 11f; isAntiAlias = true }
            val headingPaint = Paint().apply {
                color = Color.rgb(11, 31, 58); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            val bodyPaint = Paint().apply { color = Color.rgb(30, 41, 59); textSize = 10.5f; isAntiAlias = true }
            val mutedPaint = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 9.5f; isAntiAlias = true }
            val linePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }

            canvas.drawRect(0f, 0f, 595f, 88f, navyPaint)
            canvas.drawText("BILLCIRCLE — $reportTitle", 36f, 40f, whiteTitle)
            canvas.drawText(subtitle, 36f, 64f, whiteSub)

            val totalDist = bills.sumOf { it.totalPaise }
            val totalColl = bills.sumOf { it.collectedPaise }
            val totalOut = (totalDist - totalColl).coerceAtLeast(0L)

            var y = 118f
            canvas.drawText(
                "Bills: ${bills.size}   |   Distributed: ${CurrencyAndSplitUtils.formatInr(totalDist)}   |   Collected: ${CurrencyAndSplitUtils.formatInr(totalColl)}   |   Outstanding: ${CurrencyAndSplitUtils.formatInr(totalOut)}",
                36f, y, headingPaint
            )
            y += 16f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 18f
            canvas.drawText("Bill ID", 36f, y, mutedPaint)
            canvas.drawText("Title / Group", 118f, y, mutedPaint)
            canvas.drawText("Total", 310f, y, mutedPaint)
            canvas.drawText("Collected", 395f, y, mutedPaint)
            canvas.drawText("Status", 482f, y, mutedPaint)
            y += 8f
            canvas.drawLine(36f, y, 559f, y, linePaint)

            for (b in bills.take(28)) {
                y += 20f
                if (y > 800f) break
                canvas.drawText(b.billCode, 36f, y, mutedPaint)
                canvas.drawText("${b.title.take(20)} (${b.groupName.take(10)})", 118f, y, bodyPaint)
                canvas.drawText(CurrencyAndSplitUtils.formatInr(b.totalPaise), 310f, y, bodyPaint)
                canvas.drawText(CurrencyAndSplitUtils.formatInr(b.collectedPaise), 395f, y, bodyPaint)
                canvas.drawText(b.status.replace("_", " ").take(12), 482f, y, mutedPaint)
            }

            pdf.finishPage(page)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = reportTitle.replace(Regex("[^a-zA-Z0-9]+"), "_")
            val file = File(dir, "BillCircle_${safeTitle}.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            shareFile(context, file, "application/pdf", "Share $reportTitle PDF")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports CSV or Excel-compatible UTF-8 BOM spreadsheet (.csv) and launches Android Share Sheet.
     */
    fun exportBillsSpreadsheet(
        context: Context,
        reportTitle: String,
        bills: List<Bill>,
        excelCompatible: Boolean
    ): Result<File> {
        return try {
            val sb = StringBuilder()
            if (excelCompatible) sb.append('\uFEFF') // UTF-8 BOM so Microsoft Excel preserves ₹ symbol
            sb.appendLine("Bill ID,Title,Group,Category,Merchant,Bill Date,Due Date,Total (INR),Collected (INR),Outstanding (INR),Status,Split Type,Participants,Paid Count")
            for (b in bills) {
                val out = (b.totalPaise - b.collectedPaise).coerceAtLeast(0L)
                sb.appendLine(
                    listOf(
                        csvEscape(b.billCode),
                        csvEscape(b.title),
                        csvEscape(b.groupName),
                        csvEscape(b.category),
                        csvEscape(b.merchantName.orEmpty()),
                        csvEscape(CurrencyAndSplitUtils.formatDate(b.billDateMillis)),
                        csvEscape(CurrencyAndSplitUtils.formatDate(b.dueDateMillis)),
                        String.format(java.util.Locale.US, "%.2f", b.totalPaise / 100.0),
                        String.format(java.util.Locale.US, "%.2f", b.collectedPaise / 100.0),
                        String.format(java.util.Locale.US, "%.2f", out / 100.0),
                        csvEscape(b.status),
                        csvEscape(b.splitType),
                        b.participantCount.toString(),
                        b.paidCount.toString()
                    ).joinToString(",")
                )
            }
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = reportTitle.replace(Regex("[^a-zA-Z0-9]+"), "_")
            val suffix = if (excelCompatible) "_Excel.csv" else ".csv"
            val file = File(dir, "BillCircle_${safeTitle}$suffix")
            file.writeText(sb.toString(), Charsets.UTF_8)
            shareFile(context, file, "text/csv", "Export $reportTitle")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
