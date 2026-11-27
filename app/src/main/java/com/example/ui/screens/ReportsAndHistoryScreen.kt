package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BillStatus
import com.example.data.PaymentStatus
import com.example.ui.components.BillSummaryCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.FinancialFigureStyle
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.util.CurrencyAndSplitUtils
import com.example.util.ExportManager

private enum class ReportScope(val label: String) {
    TOTAL("Total Collection"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    GROUP("Group-wise"),
    MEMBER("Member-wise"),
    PENDING("Pending"),
    CLEARED("Cleared")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsAndHistoryScreen(
    state: BillCircleUiState,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    onFeedback: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var reportScope by remember { mutableStateOf(ReportScope.TOTAL) }

    val now = System.currentTimeMillis()
    val filteredBills = remember(state.bills, reportScope) {
        when (reportScope) {
            ReportScope.TOTAL, ReportScope.GROUP, ReportScope.MEMBER -> state.bills
            ReportScope.DAILY -> state.bills.filter { now - it.billDateMillis <= 86_400_000L * 2 }
            ReportScope.WEEKLY -> state.bills.filter { now - it.billDateMillis <= 86_400_000L * 7 }
            ReportScope.MONTHLY -> state.bills.filter { now - it.billDateMillis <= 86_400_000L * 31 }
            ReportScope.PENDING -> state.bills.filter { it.status != BillStatus.CLEARED.name }
            ReportScope.CLEARED -> state.bills.filter { it.status == BillStatus.CLEARED.name }
        }
    }

    val scopeTotal = filteredBills.sumOf { it.totalPaise }
    val scopeCollected = filteredBills.sumOf { it.collectedPaise }
    val scopeOutstanding = (scopeTotal - scopeCollected).coerceAtLeast(0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) strings.paymentHistory else strings.reportsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.paymentHistory) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.reports) })
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.payments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Payments,
                                title = "No payment records yet",
                                subtitle = "Submitted UPI, bank transfer, and verified cash payments across all bills will appear here."
                            )
                        }
                    } else {
                        items(state.payments, key = { it.id }) { pay ->
                            val billTitle = state.bills.firstOrNull { it.id == pay.billId }?.title ?: "Bill"
                            val ts = pay.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pay.payerName, style = MaterialTheme.typography.titleMedium)
                                            Text(billTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(CurrencyAndSplitUtils.formatInr(pay.amountPaise), style = FinancialFigureStyle)
                                            PaymentStatusBadge(pay.status)
                                        }
                                    }
                                    Text(
                                        "${pay.method} ${pay.upiRefId?.let { "• Ref: $it" }.orEmpty()} • $ts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!pay.verifiedByName.isNullOrBlank()) {
                                        Text(
                                            "Verified by ${pay.verifiedByName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.success
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Report Type Selector
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ReportScope.entries) { scope ->
                                FilterChip(
                                    selected = reportScope == scope,
                                    onClick = { reportScope = scope },
                                    label = { Text(scope.label) }
                                )
                            }
                        }
                    }

                    // Visual Analytics Bar Chart (Jetpack Compose Canvas)
                    item {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val successColor = ext.success
                        val warningColor = ext.warning
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${reportScope.label} Analytics", style = MaterialTheme.typography.titleLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(CurrencyAndSplitUtils.formatInr(scopeTotal), style = FinancialFigureStyle)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Collected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(CurrencyAndSplitUtils.formatInr(scopeCollected), style = FinancialFigureStyle, color = successColor)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(CurrencyAndSplitUtils.formatInr(scopeOutstanding), style = FinancialFigureStyle, color = warningColor)
                                    }
                                }

                                // Canvas Horizontal Comparison Bars
                                val maxVal = scopeTotal.coerceAtLeast(1L).toFloat()
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(86.dp)
                                ) {
                                    val barHeight = 18.dp.toPx()
                                    val gap = 14.dp.toPx()
                                    val radius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                                    val bars = listOf(
                                        Triple(scopeTotal.toFloat() / maxVal, primaryColor, 0f),
                                        Triple(scopeCollected.toFloat() / maxVal, successColor, barHeight + gap),
                                        Triple(scopeOutstanding.toFloat() / maxVal, warningColor, (barHeight + gap) * 2f)
                                    )
                                    bars.forEach { (ratio, color, y) ->
                                        drawRoundRect(trackColor, Offset(0f, y), Size(size.width, barHeight), radius)
                                        val w = (size.width * ratio.coerceIn(0f, 1f)).coerceAtLeast(if (ratio > 0f) 10f else 0f)
                                        if (w > 0f) {
                                            drawRoundRect(color, Offset(0f, y), Size(w, barHeight), radius)
                                        }
                                    }
                                }

                                // Export Actions: PDF, CSV, Excel
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            ExportManager.exportFinancialReportPdf(
                                                context,
                                                "${reportScope.label} Report",
                                                "Generated ${CurrencyAndSplitUtils.formatDateTime(System.currentTimeMillis())} • ${filteredBills.size} bills",
                                                filteredBills
                                            ).onFailure { onFeedback(it.localizedMessage ?: "PDF export failed.") }
                                        },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("export_pdf_button")
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.exportPdf)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            ExportManager.exportBillsSpreadsheet(context, reportScope.label, filteredBills, excelCompatible = false)
                                                .onFailure { onFeedback(it.localizedMessage ?: "CSV export failed.") }
                                        },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("export_csv_button")
                                    ) {
                                        Text(strings.exportCsv)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            ExportManager.exportBillsSpreadsheet(context, reportScope.label, filteredBills, excelCompatible = true)
                                                .onFailure { onFeedback(it.localizedMessage ?: "Excel export failed.") }
                                        },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("export_excel_button")
                                    ) {
                                        Text(strings.exportExcel)
                                    }
                                }
                            }
                        }
                    }

                    // Group-wise Breakdown
                    if (reportScope == ReportScope.GROUP) {
                        val byGroup = filteredBills.groupBy { it.groupName }
                        items(byGroup.entries.toList()) { (grpName, grpBills) ->
                            val tot = grpBills.sumOf { it.totalPaise }
                            val col = grpBills.sumOf { it.collectedPaise }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(grpName, style = MaterialTheme.typography.titleMedium)
                                        Text("${grpBills.size} bills", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(CurrencyAndSplitUtils.formatInr(tot), style = FinancialFigureStyle)
                                        Text("Collected ${CurrencyAndSplitUtils.formatInr(col)}", style = MaterialTheme.typography.bodySmall, color = ext.success)
                                    }
                                }
                            }
                        }
                    } else if (reportScope == ReportScope.MEMBER) {
                        val byMember = state.participants.groupBy { it.participantName }
                        items(byMember.entries.toList()) { (memberName, shares) ->
                            val totShare = shares.sumOf { it.sharePaise }
                            val totPaid = shares.sumOf { it.paidPaise }
                            val pendingCnt = shares.count { it.paymentStatus != PaymentStatus.PAYMENT_VERIFIED.name }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(memberName, style = MaterialTheme.typography.titleMedium)
                                        Text("${shares.size} bills • $pendingCnt pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(CurrencyAndSplitUtils.formatInr(totPaid), style = FinancialFigureStyle, color = ext.success)
                                        Text("of ${CurrencyAndSplitUtils.formatInr(totShare)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredBills, key = { it.id }) { bill ->
                            BillSummaryCard(bill = bill, onClick = { onOpenBill(bill.id) })
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
