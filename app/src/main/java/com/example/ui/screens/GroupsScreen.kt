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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Group
import com.example.data.MemberRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.ui.viewmodel.BillCircleViewModel
import com.example.util.CurrencyAndSplitUtils
import com.example.util.ExportManager

@Composable
fun GroupsScreen(
    state: BillCircleUiState,
    viewModel: BillCircleViewModel,
    openCreateDialogImmediately: Boolean = false,
    openAddMemberImmediately: Boolean = false,
    onConsumeInitialTrigger: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var selectedGroupId by remember(state.groups) { mutableStateOf(state.groups.firstOrNull()?.id) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showUpiConfigDialog by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var generatedInviteCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openCreateDialogImmediately, openAddMemberImmediately) {
        if (openCreateDialogImmediately) {
            showCreateGroupDialog = true
            onConsumeInitialTrigger()
        } else if (openAddMemberImmediately) {
            if (state.groups.isNotEmpty()) showAddMemberDialog = true else showCreateGroupDialog = true
            onConsumeInitialTrigger()
        }
    }

    val selectedGroup: Group? = state.groups.firstOrNull { it.id == selectedGroupId } ?: state.groups.firstOrNull()
    val isAdminOfSelected = selectedGroup != null && state.currentUserId in selectedGroup.adminUids
    val selectedMembers = remember(state.members, selectedGroup?.id) {
        state.members.filter { it.groupId == selectedGroup?.id }
    }
    val selectedInvites = remember(state.invitations, selectedGroup?.id) {
        state.invitations.filter { it.groupId == selectedGroup?.id && it.status == "ACTIVE" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.groupsTitle, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showJoinGroupDialog = true },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).testTag("join_group_button")
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Join")
                    }
                    Button(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).testTag("create_group_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
        }

        if (state.groups.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Groups,
                    title = "No groups yet",
                    subtitle = "Create a family or shared expense group, or join an existing group with an expiring invite code.",
                    actionLabel = strings.createGroup,
                    onAction = { showCreateGroupDialog = true }
                )
            }
        } else {
            // Group Switcher Cards
            items(state.groups, key = { it.id }) { grp ->
                val isSelected = grp.id == selectedGroup?.id
                val roleLabel = when {
                    state.currentUserId in grp.adminUids -> "ADMIN"
                    state.currentUserId in grp.viewerUids -> "VIEWER"
                    else -> "MEMBER"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedGroupId = grp.id },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(grp.name, style = MaterialTheme.typography.titleLarge)
                            RoleBadge(roleLabel)
                        }
                        if (!grp.description.isNullOrBlank()) {
                            Text(grp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${grp.memberCount} members • UPI: ${grp.upiId?.ifBlank { "Not configured" } ?: "Not configured"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selected Group Management Panel
            if (selectedGroup != null) {
                if (isAdminOfSelected) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Manage ${selectedGroup.name}", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showAddMemberDialog = true },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("group_add_member_button")
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                    FilledTonalButton(
                                        onClick = { showInviteDialog = true },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("group_invite_button")
                                    ) {
                                        Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Invite")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showUpiConfigDialog = true },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("UPI Setup")
                                    }
                                    OutlinedButton(
                                        onClick = { showAnnouncementDialog = true },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Announce")
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Expiring Invite Codes
                if (isAdminOfSelected && selectedInvites.isNotEmpty()) {
                    item {
                        Text("Active Invite Codes", style = MaterialTheme.typography.titleMedium)
                    }
                    items(selectedInvites, key = { it.id }) { inv ->
                        val expMs = inv.expiresAt?.toDate()?.time ?: 0L
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(inv.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Role: ${inv.role} • Uses: ${inv.usedCount}/${inv.maxUses} • Expires ${CurrencyAndSplitUtils.formatDate(expMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            ExportManager.sharePlainText(
                                                context,
                                                "You're invited to join *${inv.groupName}* on BillCircle as a ${inv.role}.\n\nInvite Code: *${inv.id}*\nExpires: ${CurrencyAndSplitUtils.formatDateTime(expMs)}\n\nOpen BillCircle → Groups → Join and enter this code.",
                                                "Share Group Invite Code"
                                            )
                                        }
                                    ) { Icon(Icons.Filled.Share, contentDescription = "Share code") }
                                    IconButton(onClick = { viewModel.revokeInvite(inv.id) }) {
                                        Icon(Icons.Filled.Block, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // Member Directory
                item {
                    Text("${strings.membersLabel} (${selectedMembers.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(selectedMembers, key = { it.id }) { member ->
                    val isOwner = member.userId == selectedGroup.ownerId
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                                    RoleBadge(member.role)
                                }
                                val contact = listOfNotNull(member.phoneNumber?.ifBlank { null }, member.email?.ifBlank { null }).joinToString(" • ")
                                if (contact.isNotBlank()) {
                                    Text(contact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(
                                    label = member.status,
                                    containerColor = if (member.status == "ACTIVE") ext.successContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (member.status == "ACTIVE") ext.success else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdminOfSelected && !isOwner) {
                                Row {
                                    IconButton(onClick = { viewModel.toggleMemberActive(selectedGroup, member) }) {
                                        Icon(
                                            if (member.status == "ACTIVE") Icons.Filled.PauseCircleOutline else Icons.Filled.PlayCircleOutline,
                                            contentDescription = "Toggle status"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeMemberFromGroup(selectedGroup, member) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var upiId by remember { mutableStateOf(state.profile?.defaultUpiId.orEmpty()) }
        var upiName by remember { mutableStateOf(state.profile?.defaultUpiName ?: state.profile?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text(strings.createGroup) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name *") }, placeholder = { Text("e.g., Family Home Bills") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true)
                    OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("Admin UPI ID (for payments)") }, placeholder = { Text("name@upi") }, singleLine = true)
                    OutlinedTextField(value = upiName, onValueChange = { upiName = it }, label = { Text("UPI Recipient Name") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createGroup(name, desc, upiId, upiName) { showCreateGroupDialog = false }
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_group_button")
                ) { Text("Create Group") }
            },
            dismissButton = { TextButton(onClick = { showCreateGroupDialog = false }) { Text("Cancel") } }
        )
    }

    // Join Group via Invite Code Dialog
    if (showJoinGroupDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = { Text(strings.joinWithCode) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the 8-character invite code shared by your group administrator.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Invite Code") },
                        placeholder = { Text("e.g., BC7K9M2P") },
                        singleLine = true,
                        modifier = Modifier.testTag("join_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (code.trim().length >= 6) {
                            viewModel.joinGroupByInviteCode(code) { showJoinGroupDialog = false }
                        }
                    }
                ) { Text("Join Group") }
            },
            dismissButton = { TextButton(onClick = { showJoinGroupDialog = false }) { Text("Cancel") } }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog && selectedGroup != null) {
        var mName by remember { mutableStateOf("") }
        var mPhone by remember { mutableStateOf("") }
        var mEmail by remember { mutableStateOf("") }
        var mRole by remember { mutableStateOf(MemberRole.MEMBER) }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Member to ${selectedGroup.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = mName, onValueChange = { mName = it }, label = { Text("Full Name *") }, singleLine = true)
                    OutlinedTextField(value = mPhone, onValueChange = { mPhone = it }, label = { Text("Mobile Number") }, singleLine = true)
                    OutlinedTextField(value = mEmail, onValueChange = { mEmail = it }, label = { Text("Email (optional)") }, singleLine = true)
                    Text("Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(MemberRole.MEMBER, MemberRole.VIEWER, MemberRole.ADMIN).forEach { r ->
                            FilterChip(selected = mRole == r, onClick = { mRole = r }, label = { Text(r.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mName.isNotBlank()) {
                            viewModel.addMemberToGroup(selectedGroup, mName, mPhone, mEmail, mRole)
                            showAddMemberDialog = false
                        }
                    }
                ) { Text("Add Member") }
            },
            dismissButton = { TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") } }
        )
    }

    // Generate Expiring Invite Code Dialog
    if (showInviteDialog && selectedGroup != null) {
        var invRole by remember { mutableStateOf(MemberRole.MEMBER) }
        var hours by remember { mutableLongStateOf(72L) }
        AlertDialog(
            onDismissRequest = { showInviteDialog = false; generatedInviteCode = null },
            title = { Text("Generate Expiring Invite Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (generatedInviteCode != null) {
                        Text("Share this private invite code:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            generatedInviteCode!!,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                ExportManager.sharePlainText(
                                    context,
                                    "Join *${selectedGroup.name}* on BillCircle as ${invRole.name}.\nInvite Code: *${generatedInviteCode}*\nValid for ${hours} hours.",
                                    "Share Invite Code"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share via WhatsApp / Apps")
                        }
                    } else {
                        Text("Select Role for Invited Members:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = invRole == MemberRole.MEMBER, onClick = { invRole = MemberRole.MEMBER }, label = { Text("Member") })
                            FilterChip(selected = invRole == MemberRole.VIEWER, onClick = { invRole = MemberRole.VIEWER }, label = { Text("Viewer") })
                        }
                        Text("Expires After:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(24L to "24h", 72L to "3 Days", 168L to "7 Days").forEach { (h, lbl) ->
                                FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(lbl) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (generatedInviteCode == null) {
                    Button(
                        onClick = {
                            viewModel.generateInviteCode(selectedGroup, invRole, hours) { code ->
                                generatedInviteCode = code
                            }
                        }
                    ) { Text("Generate Code") }
                } else {
                    Button(onClick = { showInviteDialog = false; generatedInviteCode = null }) { Text("Done") }
                }
            },
            dismissButton = {
                if (generatedInviteCode == null) {
                    TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    // UPI Config Dialog
    if (showUpiConfigDialog && selectedGroup != null) {
        var upi by remember(selectedGroup.id) { mutableStateOf(selectedGroup.upiId.orEmpty()) }
        var recName by remember(selectedGroup.id) { mutableStateOf(selectedGroup.upiRecipientName ?: selectedGroup.ownerName) }
        AlertDialog(
            onDismissRequest = { showUpiConfigDialog = false },
            title = { Text("Group UPI Payment Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = upi, onValueChange = { upi = it }, label = { Text("Recipient UPI ID") }, placeholder = { Text("father@okicici") }, singleLine = true)
                    OutlinedTextField(value = recName, onValueChange = { recName = it }, label = { Text("Recipient Display Name") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGroupUpi(selectedGroup, upi, recName)
                        showUpiConfigDialog = false
                    }
                ) { Text("Save UPI") }
            },
            dismissButton = { TextButton(onClick = { showUpiConfigDialog = false }) { Text("Cancel") } }
        )
    }

    // Group Announcement Dialog
    if (showAnnouncementDialog && selectedGroup != null) {
        var annText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Send Announcement — ${selectedGroup.name}") },
            text = {
                OutlinedTextField(
                    value = annText,
                    onValueChange = { annText = it },
                    label = { Text("Announcement Message") },
                    placeholder = { Text("e.g., New bill uploaded. Total ₹30,000. Each share is ₹6,000.") },
                    minLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (annText.isNotBlank()) {
                            viewModel.sendGroupAnnouncement(selectedGroup, selectedGroup.memberUids, "", annText)
                            showAnnouncementDialog = false
                        }
                    }
                ) { Text("Send to Group") }
            },
            dismissButton = { TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") } }
        )
    }
}
