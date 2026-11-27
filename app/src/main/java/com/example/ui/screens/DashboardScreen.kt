package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BillStatus
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.FinancialFigureStyle
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.util.CurrencyAndSplitUtils

@Composable
fun DashboardScreen(
    state: BillCircleUiState,
    onCreateNewBill: () -> Unit,
    onOpenAddMember: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onViewAllBills: () -> Unit,
    onOpenReportsAndHistory: (initialTab: Int) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBillDetail: (String) -> Unit,
    onOpenMessages: () -> Unit
) {
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current
    val displayName = state.profile?.displayName?.ifBlank { "Member" } ?: "Member"

    val upcomingBills = remember(state.bills) {
        state.bills
            .filter { it.status != BillStatus.CLEARED.name && it.dueDateMillis > 0L }
            .sortedBy { it.dueDateMillis }
            .take(3)
    }
    val recentMessages = remember(state.messages) { state.messages.takeLast(3).reversed() }
    val recentLogs = remember(state.auditLogs) { state.auditLogs.take(4) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header Row with Notification Bell
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.dashboardWelcome,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Namaste, $displayName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BadgedBox(
                    badge = {
                        if (state.unreadNotificationsCount > 0) {
                            Badge { Text("${state.unreadNotificationsCount}") }
                        }
                    }
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dashboard_notifications_button")
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = strings.notificationsTitle)
                    }
                }
            }
        }

        // Clearly labeled Demo Mode Banner when active
        if (state.demoMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ext.warningContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Science, contentDescription = null, tint = ext.warning)
                        Text(
                            text = strings.demoModeBanner,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Hero Financial Balance Card
        item {
            HeroBalanceCard(
                totalDistributedPaise = state.summary.totalDistributedPaise,
                totalCollectedPaise = state.summary.totalCollectedPaise,
                outstandingPaise = state.summary.outstandingPaise,
                distributedLabel = strings.totalDistributed,
                collectedLabel = strings.totalCollected,
                outstandingLabel = strings.outstandingAmount
            )
        }

        // 3-Column KPI Strip: Total Bills, Pending Payments, Cleared Bills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMiniCard(
                    label = strings.totalBills,
                    value = state.summary.totalBills.toString(),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    label = strings.pendingPayments,
                    value = state.summary.pendingPaymentsCount.toString(),
                    accent = ext.warning,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    label = strings.clearedBills,
                    value = state.summary.clearedBillsCount.toString(),
                    accent = ext.success,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6 Large Touch-Friendly Action Buttons (2x3 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = strings.quickActions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.createNewBill,
                        icon = Icons.Filled.PostAdd,
                        highlighted = true,
                        testTag = "action_create_bill",
                        onClick = onCreateNewBill,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        title = strings.addMember,
                        icon = Icons.Filled.PersonAdd,
                        testTag = "action_add_member",
                        onClick = onOpenAddMember,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.createGroup,
                        icon = Icons.Filled.GroupAdd,
                        testTag = "action_create_group",
                        onClick = onOpenCreateGroup,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        title = strings.viewAllBills,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        testTag = "action_view_all_bills",
                        onClick = onViewAllBills,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.paymentHistory,
                        icon = Icons.Filled.History,
                        testTag = "action_payment_history",
                        onClick = { onOpenReportsAndHistory(0) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        title = strings.reports,
                        icon = Icons.Filled.Assessment,
                        testTag = "action_reports",
                        onClick = { onOpenReportsAndHistory(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Upcoming Payment Deadlines
        item {
            SectionHeader(
                title = strings.upcomingDeadlines,
                actionLabel = strings.viewAllBills,
                onActionClick = onViewAllBills
            )
        }
        if (upcomingBills.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.EventAvailable,
                    title = "No upcoming deadlines",
                    subtitle = "All shared bills are settled or none have been published yet.",
                    actionLabel = strings.createNewBill,
                    onAction = onCreateNewBill
                )
            }
        } else {
            items(upcomingBills, key = { it.id }) { bill ->
                BillSummaryCard(bill = bill, onClick = { onOpenBillDetail(bill.id) })
            }
        }

        // Recent Messages
        if (recentMessages.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.recentMessages,
                    actionLabel = strings.navMessages,
                    onActionClick = onOpenMessages
                )
            }
            items(recentMessages, key = { it.id }) { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (msg.billId.isNotBlank()) onOpenBillDetail(msg.billId) else onOpenMessages()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg.senderName, style = MaterialTheme.typography.titleSmall)
                            StatusPill(
                                label = if (msg.scope == "ANNOUNCEMENT") "Announcement" else "Bill Chat",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Recent Audit Activity
        if (recentLogs.isNotEmpty()) {
            item { SectionHeader(title = strings.recentActivity) }
            items(recentLogs, key = { it.id }) { log ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                            val ts = log.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                            Text(
                                text = "${log.actorName} • $ts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KpiMiniCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalBillCircleColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = FinancialFigureStyle,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
