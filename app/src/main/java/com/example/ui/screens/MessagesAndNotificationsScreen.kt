package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.ui.viewmodel.BillCircleViewModel
import com.example.util.CurrencyAndSplitUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesAndNotificationsScreen(
    state: BillCircleUiState,
    viewModel: BillCircleViewModel,
    initialTab: Int = 0,
    onOpenBill: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showComposeAnnouncement by remember { mutableStateOf(false) }

    val adminGroups = remember(state.groups, state.currentUserId) {
        state.groups.filter { state.currentUserId in it.adminUids }
    }
    val sortedMessages = remember(state.messages) { state.messages.reversed() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.announcementsTitle, style = MaterialTheme.typography.headlineMedium)
            if (selectedTab == 0 && state.unreadNotificationsCount > 0) {
                TextButton(onClick = { viewModel.markAllNotificationsRead() }) {
                    Text(strings.markAllRead)
                }
            } else if (selectedTab == 1 && adminGroups.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { showComposeAnnouncement = true },
                    modifier = Modifier.testTag("compose_announcement_button")
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.sendAnnouncement)
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("${strings.notificationsTitle} (${state.unreadNotificationsCount})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Messages (${state.messages.size})") }
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.notifications.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.NotificationsNone,
                                title = "All caught up!",
                                subtitle = "Bill publications, approvals, disputes, document uploads, payment verifications, and reminders appear here."
                            )
                        }
                    } else {
                        items(state.notifications, key = { it.id }) { notif ->
                            val ts = notif.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.read) viewModel.markNotificationRead(notif.id)
                                        if (!notif.billId.isNullOrBlank()) onOpenBill(notif.billId)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!notif.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (!notif.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else ext.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = when (notif.type) {
                                            "BILL_CLEARED", "PAYMENT_VERIFIED" -> Icons.Filled.CheckCircle
                                            "BILL_DISPUTED" -> Icons.Filled.ReportProblem
                                            "PAYMENT_REMINDER" -> Icons.Filled.Alarm
                                            "DOC_UPLOADED" -> Icons.Filled.AttachFile
                                            else -> Icons.Filled.Notifications
                                        },
                                        contentDescription = null,
                                        tint = if (!notif.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                notif.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (!notif.read) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (!notif.read) {
                                                StatusPill("New", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                        Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${notif.senderName} • $ts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sortedMessages.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.ChatBubbleOutline,
                                title = "No discussions or announcements yet",
                                subtitle = "Open any bill to chat with members, or send an administrator announcement to your group."
                            )
                        }
                    } else {
                        items(sortedMessages, key = { it.id }) { msg ->
                            val unread = state.currentUserId !in msg.readByUids
                            val ts = msg.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                            val billTitle = state.bills.firstOrNull { it.id == msg.billId }?.title
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (unread) viewModel.markMessageRead(msg)
                                        if (msg.billId.isNotBlank()) onOpenBill(msg.billId)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.scope == "ANNOUNCEMENT") ext.infoContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                ),
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
                                            label = if (msg.scope == "ANNOUNCEMENT") "Announcement" else billTitle?.take(18) ?: "Bill Chat",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(ts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showComposeAnnouncement && adminGroups.isNotEmpty()) {
        var targetGroupId by remember { mutableStateOf(adminGroups.first().id) }
        var targetBillId by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        val group = adminGroups.firstOrNull { it.id == targetGroupId } ?: adminGroups.first()
        val groupBills = state.bills.filter { it.groupId == group.id }

        AlertDialog(
            onDismissRequest = { showComposeAnnouncement = false },
            title = { Text("Send Group Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Group: ${group.name}", style = MaterialTheme.typography.labelLarge)
                    if (groupBills.isNotEmpty()) {
                        Text("Scope (Optional Bill):", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = targetBillId.isEmpty(), onClick = { targetBillId = "" }, label = { Text("Whole Group") })
                            groupBills.take(2).forEach { b ->
                                FilterChip(
                                    selected = targetBillId == b.id,
                                    onClick = { targetBillId = b.id },
                                    label = { Text(b.billCode) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Message *") },
                        placeholder = { Text("e.g., New bill uploaded. Please review and pay before due date.") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            val targetUids = if (targetBillId.isNotBlank()) {
                                groupBills.firstOrNull { it.id == targetBillId }?.memberUids ?: group.memberUids
                            } else group.memberUids
                            viewModel.sendGroupAnnouncement(group, targetUids, targetBillId, text)
                            showComposeAnnouncement = false
                        }
                    }
                ) { Text("Send Announcement") }
            },
            dismissButton = { TextButton(onClick = { showComposeAnnouncement = false }) { Text("Cancel") } }
        )
    }
}
