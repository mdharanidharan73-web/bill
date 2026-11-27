package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.FinancialFigureStyle
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.ui.viewmodel.BillCircleViewModel
import com.example.util.AttachmentHelper
import com.example.util.CurrencyAndSplitUtils
import com.example.util.ExportManager
import com.example.util.PreparedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: String,
    state: BillCircleUiState,
    viewModel: BillCircleViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current

    val bill = state.bills.firstOrNull { it.id == billId }
    if (bill == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bill Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("Bill not found or access revoked.")
            }
        }
        return
    }

    val isAdmin = state.currentUserId in bill.adminUids
    val isViewer = state.currentUserId in bill.viewerUids
    val canContribute = !isViewer
    val isCleared = bill.status == BillStatus.CLEARED.name

    val participants = remember(state.participants, billId) { state.participants.filter { it.billId == billId } }
    val myParticipant = participants.firstOrNull { it.participantUid == state.currentUserId }
    val attachments = remember(state.attachments, billId) { state.attachments.filter { it.billId == billId } }
    val payments = remember(state.payments, billId) { state.payments.filter { it.billId == billId } }
    val messages = remember(state.messages, billId) { state.messages.filter { it.billId == billId && it.scope == "BILL" } }
    val logs = remember(state.auditLogs, billId) { state.auditLogs.filter { it.billId == billId } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDisputeDialog by remember { mutableStateOf(false) }
    var showResolveDialog by remember { mutableStateOf(false) }
    var showSubmitPayDialog by remember { mutableStateOf<BillParticipant?>(null) }
    var showAdminDirectPayDialog by remember { mutableStateOf<BillParticipant?>(null) }
    var showQrDialogFor by remember { mutableStateOf<Long?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Pair<Attachment, Bitmap>?>(null) }
    var uploadingAttachment by remember { mutableStateOf(false) }
    var rejectPaymentTarget by remember { mutableStateOf<Pair<PaymentRecord, BillParticipant>?>(null) }

    // Chat state
    var chatInput by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var chatAttachment by remember { mutableStateOf<PreparedAttachment?>(null) }

    // Upload document picker (JPG/PNG/PDF via SAF — zero storage permissions!)
    var uploadTargetCategory by remember { mutableStateOf("SUPPORTING_DOC") }
    var paymentDialogProof by remember { mutableStateOf<PreparedAttachment?>(null) }
    var pickingForPaymentDialog by remember { mutableStateOf(false) }
    var pickingForChat by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                uploadingAttachment = true
                val preparedRes = withContext(Dispatchers.IO) { AttachmentHelper.prepareFromUri(context, uri) }
                uploadingAttachment = false
                preparedRes.onSuccess { prepared ->
                    when {
                        pickingForPaymentDialog -> {
                            paymentDialogProof = prepared
                            pickingForPaymentDialog = false
                        }
                        pickingForChat -> {
                            chatAttachment = prepared
                            pickingForChat = false
                        }
                        else -> viewModel.uploadBillAttachment(bill, prepared, uploadTargetCategory)
                    }
                }.onFailure { err ->
                    pickingForPaymentDialog = false
                    pickingForChat = false
                    viewModel.showFeedback(err.localizedMessage ?: "Failed to prepare file.")
                }
            }
        } else {
            pickingForPaymentDialog = false
            pickingForChat = false
        }
    }

    // Mark unread bill messages read when opening Discussion tab
    LaunchedEffect(selectedTab, messages.size) {
        if (selectedTab == 2) {
            messages.filter { state.currentUserId !in it.readByUids }.forEach { viewModel.markMessageRead(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(bill.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${bill.billCode} • ${bill.groupName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("bill_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareText = CurrencyAndSplitUtils.buildWhatsAppShareMessage(
                                billTitle = bill.title,
                                billCode = bill.billCode,
                                groupName = bill.groupName,
                                totalPaise = bill.totalPaise,
                                sharePaise = myParticipant?.sharePaise,
                                dueDateMillis = bill.dueDateMillis,
                                upiId = bill.upiId
                            )
                            ExportManager.sharePlainText(context, shareText)
                        },
                        modifier = Modifier.testTag("share_whatsapp_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = strings.shareWhatsApp)
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Files (${attachments.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Chat (${messages.size})") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit") })
            }

            if (uploadingAttachment) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hero Summary & Lifecycle Card
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BillStatusBadge(bill.status)
                                        Text(
                                            text = "Due ${CurrencyAndSplitUtils.formatDate(bill.dueDateMillis)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Total Bill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(CurrencyAndSplitUtils.formatInr(bill.totalPaise), style = FinancialFigureStyle)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Collected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(CurrencyAndSplitUtils.formatInr(bill.collectedPaise), style = FinancialFigureStyle, color = ext.success)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(CurrencyAndSplitUtils.formatInr(bill.outstandingPaise), style = FinancialFigureStyle, color = ext.warning)
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { bill.completionPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                        color = if (isCleared) ext.success else MaterialTheme.colorScheme.primary
                                    )

                                    Text(
                                        text = "${bill.completionPercent}% collected • ${bill.paidCount}/${bill.participantCount} members verified • ${bill.approvedCount}/${bill.participantCount} approved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!bill.merchantName.isNullOrBlank()) {
                                        Text("Merchant: ${bill.merchantName} • Category: ${bill.category}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (!bill.description.isNullOrBlank()) {
                                        Text(bill.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!bill.notes.isNullOrBlank()) {
                                        Text("Note: ${bill.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (isCleared) {
                                        Surface(
                                            color = ext.successContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    "Bill Officially Cleared",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = ext.success
                                                )
                                                bill.clearedAtMillis?.let {
                                                    Text("Cleared on ${CurrencyAndSplitUtils.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (!bill.clearanceOverrideReason.isNullOrBlank()) {
                                                    Text("Admin Override: ${bill.clearanceOverrideReason}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    // Action Strip: Share, Reminder, PDF Receipt
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val msg = CurrencyAndSplitUtils.buildWhatsAppShareMessage(
                                                    bill.title, bill.billCode, bill.groupName,
                                                    bill.totalPaise, myParticipant?.sharePaise, bill.dueDateMillis, bill.upiId
                                                )
                                                ExportManager.sharePlainText(context, msg)
                                            },
                                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (isAdmin && !isCleared) {
                                            OutlinedButton(
                                                onClick = { viewModel.sendPaymentReminder(bill) },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("send_reminder_button")
                                            ) {
                                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Remind", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                ExportManager.exportBillClearanceReceiptPdf(context, bill, participants, logs)
                                                    .onFailure { viewModel.showFeedback(it.localizedMessage ?: "PDF export failed.") }
                                            },
                                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("download_pdf_button")
                                        ) {
                                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }

                        // My Approval & UPI Payment Card (for assigned non-viewer members)
                        if (myParticipant != null && !isCleared) {
                            item {
                                val remainingMine = (myParticipant.sharePaise - myParticipant.paidPaise).coerceAtLeast(0L)
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Your Share", style = MaterialTheme.typography.labelMedium)
                                                Text(CurrencyAndSplitUtils.formatInr(myParticipant.sharePaise), style = FinancialFigureStyle, color = MaterialTheme.colorScheme.primary)
                                            }
                                            PaymentStatusBadge(myParticipant.paymentStatus)
                                        }

                                        if (canContribute && myParticipant.approvalStatus != ApprovalStatus.APPROVED.name) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { viewModel.approveOrDispute(bill, myParticipant, approve = true) },
                                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("approve_bill_button")
                                                ) {
                                                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(strings.approveBill)
                                                }
                                                OutlinedButton(
                                                    onClick = { showDisputeDialog = true },
                                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("dispute_bill_button")
                                                ) {
                                                    Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(strings.disputeBill)
                                                }
                                            }
                                        }

                                        if (canContribute && remainingMine > 0L) {
                                            if (!bill.upiId.isNullOrBlank()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            ExportManager.launchUpiPaymentIntent(
                                                                context = context,
                                                                upiId = bill.upiId,
                                                                recipientName = bill.upiRecipientName ?: bill.ownerName,
                                                                amountPaise = remainingMine,
                                                                billCode = bill.billCode,
                                                                billTitle = bill.title
                                                            ).onFailure {
                                                                viewModel.showFeedback("No UPI app detected. Use the UPI QR Code button instead.")
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("pay_upi_button")
                                                    ) {
                                                        Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(strings.payUsingUpi)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { showQrDialogFor = remainingMine },
                                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("show_qr_button")
                                                    ) {
                                                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(strings.showQrCode)
                                                    }
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    paymentDialogProof = null
                                                    showSubmitPayDialog = myParticipant
                                                },
                                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("submit_payment_button")
                                            ) {
                                                Icon(Icons.Filled.UploadFile, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("${strings.submitPaymentProof} (${CurrencyAndSplitUtils.formatInr(remainingMine)})")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Admin Controls (Publish Draft, Resolve Dispute, Mark Bill Cleared)
                        if (isAdmin && !isCleared) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Administrator Controls", style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (bill.status == BillStatus.DRAFT.name) {
                                                Button(
                                                    onClick = { viewModel.publishDraft(bill) },
                                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                                ) { Text("Publish Draft") }
                                            }
                                            OutlinedButton(
                                                onClick = { showResolveDialog = true },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("edit_resolve_bill_button")
                                            ) {
                                                Text(if (bill.disputedCount > 0L) "Resolve Dispute" else "Edit Bill")
                                            }
                                            Button(
                                                onClick = { showClearDialog = true },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("mark_cleared_button")
                                            ) {
                                                Text(strings.markBillCleared)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Pending Payment Verifications (Admin Queue)
                        val pendingVerifications = payments.filter { it.status == PaymentStatus.PAYMENT_SUBMITTED.name }
                        if (isAdmin && pendingVerifications.isNotEmpty() && !isCleared) {
                            item { SectionHeader(title = "Payments Awaiting Verification (${pendingVerifications.size})") }
                            items(pendingVerifications, key = { it.id }) { pay ->
                                val participant = participants.firstOrNull { it.id == pay.participantDocId }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = ext.infoContainer.copy(alpha = 0.45f)),
                                    border = BorderStroke(1.dp, ext.info.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(pay.payerName, style = MaterialTheme.typography.titleMedium)
                                            Text(CurrencyAndSplitUtils.formatInr(pay.amountPaise), style = FinancialFigureStyle, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(
                                            "Method: ${pay.method} ${pay.upiRefId?.let { "• Ref: $it" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (!pay.notes.isNullOrBlank()) {
                                            Text("Note: ${pay.notes}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (participant != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        viewModel.verifyOrRejectPayment(bill, pay, participant, verify = true, verifiedAmountPaise = pay.amountPaise)
                                                    },
                                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("verify_payment_${pay.id}")
                                                ) {
                                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(strings.verifyPayment)
                                                }
                                                OutlinedButton(
                                                    onClick = { rejectPaymentTarget = pay to participant },
                                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                                ) {
                                                    Text(strings.rejectPayment, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Member Share Breakdown List
                        item { SectionHeader(title = "Member Shares (${participants.size})") }
                        items(participants, key = { it.id }) { p ->
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
                                            Text(p.participantName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Approval: ${ApprovalStatus.fromString(p.approvalStatus).label} • Paid: ${CurrencyAndSplitUtils.formatInr(p.paidPaise)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(CurrencyAndSplitUtils.formatInr(p.sharePaise), style = FinancialFigureStyle)
                                            PaymentStatusBadge(p.paymentStatus)
                                        }
                                    }
                                    if (!p.disputeReason.isNullOrBlank()) {
                                        Text(
                                            "Dispute: ${p.disputeReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (isAdmin && !isCleared && p.paidPaise < p.sharePaise) {
                                        TextButton(
                                            onClick = { showAdminDirectPayDialog = p },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Record Verified Payment")
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }

                // TAB 1: SHARED DOCUMENTS & RECEIPTS
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (canContribute) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(strings.sharedDocuments, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Upload JPG, PNG, or PDF receipts, payment proofs, or supporting documents (up to 500 KB).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    uploadTargetCategory = "PAYMENT_PROOF"
                                                    pickingForPaymentDialog = false
                                                    pickingForChat = false
                                                    filePicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                                                },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("upload_receipt_button")
                                            ) {
                                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Payment Proof")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    uploadTargetCategory = "SUPPORTING_DOC"
                                                    pickingForPaymentDialog = false
                                                    pickingForChat = false
                                                    filePicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                                                },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Receipt / PDF")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (attachments.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.FolderOpen,
                                    title = "No shared documents yet",
                                    subtitle = "Original bills, payment screenshots, and scanned receipts uploaded by members appear here."
                                )
                            }
                        } else {
                            items(attachments, key = { it.id }) { att ->
                                val canDelete = isAdmin || att.uploadedByUid == state.currentUserId
                                val ts = att.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, ext.cardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (att.mimeType == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(att.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${att.category.replace("_", " ")} • ${att.fileSizeBytes / 1024} KB • ${att.uploadedByName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(ts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (att.mimeType.startsWith("image/")) {
                                            IconButton(
                                                onClick = {
                                                    val bmp = AttachmentHelper.decodeImageBitmap(att.contentBase64)
                                                    if (bmp != null) previewImage = att to bmp
                                                    else viewModel.showFeedback("Unable to preview image.")
                                                }
                                            ) {
                                                Icon(Icons.Filled.Visibility, contentDescription = "Preview")
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                AttachmentHelper.shareOrOpenAttachment(context, att.fileName, att.mimeType, att.contentBase64)
                                                    .onFailure { viewModel.showFeedback(it.localizedMessage ?: "Download failed.") }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = "Download")
                                        }
                                        if (canDelete) {
                                            IconButton(onClick = { viewModel.deleteAttachment(att) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: REAL-TIME BILL DISCUSSION
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (messages.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        icon = Icons.Filled.Forum,
                                        title = "No messages yet",
                                        subtitle = "Ask questions, discuss bill items, or share receipts in real time."
                                    )
                                }
                            } else {
                                items(messages, key = { it.id }) { msg ->
                                    val isMine = msg.senderUid == state.currentUserId
                                    val ts = msg.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                                    ) {
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(1.dp, ext.cardBorder),
                                            modifier = Modifier.widthIn(max = 310.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(msg.senderName, style = MaterialTheme.typography.labelLarge)
                                                    RoleBadge(msg.senderRole)
                                                }
                                                if (!msg.replyToSnippet.isNullOrBlank()) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            "Replying to: ${msg.replyToSnippet}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(6.dp)
                                                        )
                                                    }
                                                }
                                                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                                if (!msg.attachmentName.isNullOrBlank()) {
                                                    Text(
                                                        "📎 ${msg.attachmentName}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(ts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (canContribute) {
                                                        IconButton(
                                                            onClick = { replyTarget = msg },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (canContribute) {
                            HorizontalDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (replyTarget != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Replying to ${replyTarget?.senderName}: ${replyTarget?.text?.take(40)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                if (chatAttachment != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📎 ${chatAttachment?.fileName}", style = MaterialTheme.typography.bodySmall)
                                        IconButton(onClick = { chatAttachment = null }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            pickingForChat = true
                                            pickingForPaymentDialog = false
                                            filePicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                                        }
                                    ) {
                                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
                                    }
                                    OutlinedTextField(
                                        value = chatInput,
                                        onValueChange = { chatInput = it },
                                        placeholder = { Text("Write a message...") },
                                        modifier = Modifier.weight(1f).testTag("bill_chat_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            if (chatInput.isNotBlank()) {
                                                viewModel.sendBillMessage(bill, chatInput, replyTarget, chatAttachment)
                                                chatInput = ""
                                                replyTarget = null
                                                chatAttachment = null
                                            }
                                        },
                                        modifier = Modifier.size(48.dp).testTag("bill_chat_send_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: IMMUTABLE AUDIT TRAIL
                3 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.HistoryEdu,
                                    title = "No audit events recorded yet",
                                    subtitle = "All bill creations, approvals, disputes, uploads, payments, and clearances are permanently recorded here."
                                )
                            }
                        } else {
                            items(logs, key = { it.id }) { log ->
                                val ts = log.createdAt?.toDate()?.time?.let { CurrencyAndSplitUtils.formatDateTime(it) } ?: "Just now"
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, ext.cardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(log.action.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                            Text("${log.actorName} • $ts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dispute Dialog
    if (showDisputeDialog && myParticipant != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisputeDialog = false },
            title = { Text("Dispute Bill or Report Error") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Explain the issue or incorrect amount *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.approveOrDispute(bill, myParticipant, approve = false, reason = reason)
                            showDisputeDialog = false
                        }
                    }
                ) { Text("Submit Dispute") }
            },
            dismissButton = { TextButton(onClick = { showDisputeDialog = false }) { Text("Cancel") } }
        )
    }

    // Admin Edit / Resolve Dispute Dialog
    if (showResolveDialog) {
        var editTitle by remember { mutableStateOf(bill.title) }
        var editAmount by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", bill.totalPaise / 100.0)) }
        var editNotes by remember { mutableStateOf(bill.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title = { Text("Edit Bill & Resolve Disputes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Bill Title") }, singleLine = true)
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Total Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = editNotes, onValueChange = { editNotes = it }, label = { Text("Resolution Notes") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = CurrencyAndSplitUtils.parseRupeesToPaise(editAmount)
                        if (paise != null && editTitle.isNotBlank()) {
                            viewModel.resolveDispute(bill, editTitle, paise, editNotes)
                            showResolveDialog = false
                        } else {
                            viewModel.showFeedback("Enter a valid title and positive INR amount.")
                        }
                    }
                ) { Text("Save & Audit") }
            },
            dismissButton = { TextButton(onClick = { showResolveDialog = false }) { Text("Cancel") } }
        )
    }

    // Submit Payment Proof Dialog (Member)
    val submitTarget = showSubmitPayDialog
    if (submitTarget != null) {
        val defaultRem = (submitTarget.sharePaise - submitTarget.paidPaise).coerceAtLeast(0L)
        var amountStr by remember(submitTarget.id) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", defaultRem / 100.0)) }
        var method by remember { mutableStateOf("UPI") }
        var upiRef by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubmitPayDialog = null },
            title = { Text("Submit Payment Proof") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "The administrator will verify your payment proof before marking your share as verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount Paid (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("UPI", "BANK_TRANSFER", "CASH").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.replace("_", " ")) })
                        }
                    }
                    OutlinedTextField(value = upiRef, onValueChange = { upiRef = it }, label = { Text("UPI UTR / Transaction Ref") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                    OutlinedButton(
                        onClick = {
                            pickingForPaymentDialog = true
                            pickingForChat = false
                            filePicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(paymentDialogProof?.fileName ?: "Attach Payment Screenshot / Receipt")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = CurrencyAndSplitUtils.parseRupeesToPaise(amountStr)
                        if (paise != null) {
                            viewModel.submitMemberPayment(bill, submitTarget, paise, method, upiRef, note, paymentDialogProof)
                            showSubmitPayDialog = null
                            paymentDialogProof = null
                        } else {
                            viewModel.showFeedback("Enter a valid positive amount.")
                        }
                    }
                ) { Text("Submit for Verification") }
            },
            dismissButton = { TextButton(onClick = { showSubmitPayDialog = null }) { Text("Cancel") } }
        )
    }

    // Admin Direct Verified Payment Dialog (for managed/cash/offline members)
    val directTarget = showAdminDirectPayDialog
    if (directTarget != null) {
        val rem = (directTarget.sharePaise - directTarget.paidPaise).coerceAtLeast(0L)
        var amtStr by remember(directTarget.id) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", rem / 100.0)) }
        var method by remember { mutableStateOf("UPI") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdminDirectPayDialog = null },
            title = { Text("Record Verified Payment — ${directTarget.participantName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it },
                        label = { Text("Verified Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("UPI", "CASH", "BANK_TRANSFER").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.replace("_", " ")) })
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Verification Note") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = CurrencyAndSplitUtils.parseRupeesToPaise(amtStr)
                        if (paise != null) {
                            viewModel.recordAdminDirectPayment(bill, directTarget, paise, method, note)
                            showAdminDirectPayDialog = null
                        }
                    }
                ) { Text("Confirm Verified") }
            },
            dismissButton = { TextButton(onClick = { showAdminDirectPayDialog = null }) { Text("Cancel") } }
        )
    }

    // Reject Payment Reason Dialog
    val rejectPair = rejectPaymentTarget
    if (rejectPair != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectPaymentTarget = null },
            title = { Text("Reject Payment Submission") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for rejection *") },
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyOrRejectPayment(bill, rejectPair.first, rejectPair.second, verify = false, verifiedAmountPaise = 0L, rejectionReason = reason.ifBlank { "Payment proof could not be verified" })
                        rejectPaymentTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectPaymentTarget = null }) { Text("Cancel") } }
        )
    }

    // Clear Bill Dialog with Outstanding Balance Guard & Authorized Override
    if (showClearDialog) {
        val hasOutstanding = bill.collectedPaise < bill.totalPaise
        var overrideReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Mark Bill Officially Cleared") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total: ${CurrencyAndSplitUtils.formatInr(bill.totalPaise)} • Verified Collected: ${CurrencyAndSplitUtils.formatInr(bill.collectedPaise)}")
                    if (hasOutstanding) {
                        Text(
                            "Outstanding amount of ${CurrencyAndSplitUtils.formatInr(bill.outstandingPaise)} remains. Standard clearance requires 100% verified payments. To proceed anyway, enter an authorized override reason (permanently audited).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedTextField(
                            value = overrideReason,
                            onValueChange = { overrideReason = it },
                            label = { Text("Authorized Override Reason (min 5 chars) *") },
                            minLines = 2
                        )
                    } else {
                        Text("All member shares are verified! Once cleared, this bill is locked and a clearance receipt PDF is available for all participants.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearBill(bill, if (hasOutstanding) overrideReason else null)
                        showClearDialog = false
                    },
                    enabled = !hasOutstanding || overrideReason.trim().length >= 5
                ) { Text("Confirm Clearance") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    // UPI QR Dialog
    val qrAmt = showQrDialogFor
    if (qrAmt != null && !bill.upiId.isNullOrBlank()) {
        UpiQrDialog(
            upiId = bill.upiId,
            recipientName = bill.upiRecipientName ?: bill.ownerName,
            amountPaise = qrAmt,
            billCode = bill.billCode,
            billTitle = bill.title,
            onDismiss = { showQrDialogFor = null },
            onLaunchUpi = {
                ExportManager.launchUpiPaymentIntent(
                    context, bill.upiId, bill.upiRecipientName ?: bill.ownerName, qrAmt, bill.billCode, bill.title
                )
                showQrDialogFor = null
            }
        )
    }

    // Image Preview Dialog
    val preview = previewImage
    if (preview != null) {
        ImagePreviewDialog(
            title = preview.first.fileName,
            bitmap = preview.second,
            onShare = {
                AttachmentHelper.shareOrOpenAttachment(context, preview.first.fileName, preview.first.mimeType, preview.first.contentBase64)
            },
            onDismiss = { previewImage = null }
        )
    }
}
