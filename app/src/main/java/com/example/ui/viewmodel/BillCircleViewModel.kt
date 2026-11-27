package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.i18n.AppLanguage
import com.example.util.PreparedAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val VM_TAG = "BillCircleVM"

sealed interface AiScanUiState {
    data object Idle : AiScanUiState
    data object Scanning : AiScanUiState
    data class ReadyForReview(val result: ScannedBillResult) : AiScanUiState
    data class Error(val message: String) : AiScanUiState
}

data class DashboardSummary(
    val totalBills: Int = 0,
    val totalDistributedPaise: Long = 0L,
    val totalCollectedPaise: Long = 0L,
    val outstandingPaise: Long = 0L,
    val pendingPaymentsCount: Int = 0,
    val clearedBillsCount: Int = 0
)

data class BillCircleUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentUserId: String = "",
    val profile: UserProfile? = null,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val demoMode: Boolean = false,
    val groups: List<Group> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val participants: List<BillParticipant> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val summary: DashboardSummary = DashboardSummary(),
    val unreadNotificationsCount: Int = 0,
    val unreadMessagesCount: Int = 0,
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val aiScanState: AiScanUiState = AiScanUiState.Idle,
    val firebaseDiagnostic: FirebaseDiagnosticState = FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED
)

class BillCircleViewModel(
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    private val initialPhotoUrl: String?,
    private val groupRepo: GroupAndUserRepository,
    private val billRepo: BillOperationsRepository,
    private val aiRepo: AiBillScannerRepository,
    private val prefsStore: PreferencesStore
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<String?>(null)
    private val _streamError = MutableStateFlow<String?>(null)
    private val _firebaseDiagnostic = MutableStateFlow(FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED)
    private val _aiScanState = MutableStateFlow<AiScanUiState>(AiScanUiState.Idle)

    private fun <T> Flow<T>.safeState(initial: T, label: String): StateFlow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(VM_TAG, "Error observing $label", root)
            when (firestoreError?.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    _firebaseDiagnostic.value = FirebaseDiagnosticState.FIRESTORE_PERMISSION_DENIED
                    _streamError.value = "Firestore permission denied while reading $label. Verify your account belongs to this group and Firestore rules are deployed."
                }
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                    _firebaseDiagnostic.value = FirebaseDiagnosticState.NETWORK_UNAVAILABLE
                    _streamError.value = "Network unavailable: Cloud Firestore could not reach the server while syncing $label."
                }
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    _firebaseDiagnostic.value = FirebaseDiagnosticState.AUTHENTICATION_FAILED
                    _streamError.value = "Authentication required to access $label. Please sign in with Google again."
                }
                else -> {
                    _streamError.value = root.localizedMessage ?: "Failed to sync $label"
                }
            }
            emit(initial)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = initial
        )

    private val profileFlow = groupRepo.observeUserProfile(currentUserId).safeState(null, "profile")
    private val groupsFlow = groupRepo.observeGroups(currentUserId).safeState(emptyList(), "groups")
    private val membersFlow = groupRepo.observeGroupMembers(currentUserId).safeState(emptyList(), "groupMembers")
    private val invitesFlow = groupRepo.observeMyInvitations(currentUserId).safeState(emptyList(), "invitations")
    private val billsFlow = billRepo.observeBills(currentUserId).safeState(emptyList(), "bills")
    private val participantsFlow = billRepo.observeBillParticipants(currentUserId).safeState(emptyList(), "billParticipants")
    private val attachmentsFlow = billRepo.observeAttachments(currentUserId).safeState(emptyList(), "attachments")
    private val paymentsFlow = billRepo.observePayments(currentUserId).safeState(emptyList(), "payments")
    private val messagesFlow = billRepo.observeMessages(currentUserId).safeState(emptyList(), "messages")
    private val notificationsFlow = billRepo.observeNotifications(currentUserId).safeState(emptyList(), "notifications")
    private val auditLogsFlow = billRepo.observeAuditLogs(currentUserId).safeState(emptyList(), "auditLogs")

    private val languageFlow = prefsStore.languageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.ENGLISH)
    private val demoModeFlow = prefsStore.demoModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private data class CoreBundle(
        val profile: UserProfile?,
        val groups: List<Group>,
        val members: List<GroupMember>,
        val invites: List<Invitation>,
        val bills: List<Bill>
    )

    private data class DetailBundle(
        val participants: List<BillParticipant>,
        val attachments: List<Attachment>,
        val payments: List<PaymentRecord>,
        val messages: List<ChatMessage>,
        val notifications: List<AppNotification>
    )

    private data class MetaBundle(
        val auditLogs: List<AuditLog>,
        val language: AppLanguage,
        val demoMode: Boolean,
        val busy: Boolean,
        val feedback: String?
    )

    private val coreBundleFlow = combine(profileFlow, groupsFlow, membersFlow, invitesFlow, billsFlow, ::CoreBundle)
    private val detailBundleFlow = combine(participantsFlow, attachmentsFlow, paymentsFlow, messagesFlow, notificationsFlow, ::DetailBundle)
    private val metaBundleFlow = combine(auditLogsFlow, languageFlow, demoModeFlow, _busy, _feedback, ::MetaBundle)

    val uiState: StateFlow<BillCircleUiState> = combine(
        coreBundleFlow,
        detailBundleFlow,
        metaBundleFlow,
        combine(_streamError, _firebaseDiagnostic, _aiScanState) { err, diag, ai -> Triple(err, diag, ai) }
    ) { core, detail, meta, statusTriple ->
        val (err, diag, aiState) = statusTriple
        val effectiveName = core.profile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        val groups = if (meta.demoMode) DemoModeData.sampleGroups(currentUserId, effectiveName) else core.groups
        val members = if (meta.demoMode) DemoModeData.sampleMembers(currentUserId, effectiveName) else core.members
        val bills = if (meta.demoMode) DemoModeData.sampleBills(currentUserId, effectiveName) else core.bills
        val participants = if (meta.demoMode) DemoModeData.sampleParticipants(currentUserId, effectiveName) else detail.participants
        val messages = if (meta.demoMode) DemoModeData.sampleMessages(currentUserId, effectiveName) else detail.messages
        val auditLogs = if (meta.demoMode) DemoModeData.sampleAuditLogs(currentUserId, effectiveName) else meta.auditLogs

        val totalDistributed = bills.sumOf { it.totalPaise }
        val totalCollected = bills.sumOf { it.collectedPaise }
        val outstanding = (totalDistributed - totalCollected).coerceAtLeast(0L)
        val pendingCount = participants.count {
            it.paymentStatus != PaymentStatus.PAYMENT_VERIFIED.name
        }
        val clearedCount = bills.count { it.status == BillStatus.CLEARED.name }

        val unreadNotifs = detail.notifications.count { !it.read }
        val unreadMsgs = messages.count { currentUserId !in it.readByUids }

        BillCircleUiState(
            isLoading = false,
            errorMessage = err,
            currentUserId = currentUserId,
            profile = core.profile,
            language = meta.language,
            demoMode = meta.demoMode,
            groups = groups,
            members = members,
            invitations = core.invites,
            bills = bills,
            participants = participants,
            attachments = detail.attachments,
            payments = detail.payments,
            messages = messages,
            notifications = detail.notifications,
            auditLogs = auditLogs,
            summary = DashboardSummary(
                totalBills = bills.size,
                totalDistributedPaise = totalDistributed,
                totalCollectedPaise = totalCollected,
                outstandingPaise = outstanding,
                pendingPaymentsCount = pendingCount,
                clearedBillsCount = clearedCount
            ),
            unreadNotificationsCount = unreadNotifs,
            unreadMessagesCount = unreadMsgs,
            isBusy = meta.busy,
            feedbackMessage = meta.feedback,
            aiScanState = aiState,
            firebaseDiagnostic = diag
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BillCircleUiState(currentUserId = currentUserId)
    )

    init {
        viewModelScope.launch {
            groupRepo.ensureUserProfile(
                displayName = initialDisplayName,
                email = initialEmail,
                photoUrl = initialPhotoUrl,
                languageCode = AppLanguage.ENGLISH.code
            )
        }
    }

    private fun currentActorName(): String =
        uiState.value.profile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName.ifBlank { "User" }

    fun clearFeedback() {
        _feedback.value = null
        _streamError.value = null
    }

    fun showFeedback(message: String) {
        _feedback.value = message
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefsStore.setLanguage(language)
            val p = uiState.value.profile
            if (p != null) {
                groupRepo.updateUserProfile(
                    displayName = p.displayName.ifBlank { currentActorName() },
                    phoneNumber = p.phoneNumber,
                    preferredLanguage = language.code,
                    defaultUpiId = p.defaultUpiId,
                    defaultUpiName = p.defaultUpiName
                )
            }
        }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setDemoMode(enabled)
            _feedback.value = if (enabled) "Sample Preview Mode enabled (read-only showcase)." else "Returned to live Firebase workspace."
        }
    }

    private fun runAction(successMessage: String? = null, block: suspend () -> Result<*>) {
        if (uiState.value.demoMode) {
            _feedback.value = "Disable Sample Preview Mode in Profile to save live changes to Firebase."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = block()
                result.onSuccess {
                    if (successMessage != null) _feedback.value = successMessage
                }.onFailure { err ->
                    _feedback.value = err.localizedMessage ?: "Operation failed."
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveProfile(name: String, phone: String, upiId: String, upiName: String) {
        runAction("Profile updated.") {
            groupRepo.updateUserProfile(
                displayName = name,
                phoneNumber = phone,
                preferredLanguage = uiState.value.language.code,
                defaultUpiId = upiId,
                defaultUpiName = upiName
            )
        }
    }

    fun createGroup(name: String, description: String, upiId: String, upiRecipientName: String, onCreated: () -> Unit = {}) {
        runAction("Group created.") {
            groupRepo.createGroup(
                name = name,
                description = description,
                upiId = upiId.ifBlank { uiState.value.profile?.defaultUpiId },
                upiRecipientName = upiRecipientName.ifBlank { uiState.value.profile?.defaultUpiName ?: currentActorName() },
                ownerName = currentActorName(),
                ownerEmail = uiState.value.profile?.email ?: initialEmail,
                ownerPhone = uiState.value.profile?.phoneNumber
            ).also { if (it.isSuccess) onCreated() }
        }
    }

    fun updateGroupUpi(group: Group, upiId: String, recipientName: String) {
        runAction("Group UPI settings saved.") {
            groupRepo.updateGroupUpi(group, upiId, recipientName)
        }
    }

    fun addMemberToGroup(group: Group, name: String, phone: String, email: String, role: MemberRole) {
        runAction("$name added to ${group.name}.") {
            groupRepo.addManagedMember(group, name, phone, email, role, currentActorName())
        }
    }

    fun toggleMemberActive(group: Group, member: GroupMember) {
        val next = if (member.status == "ACTIVE") "INACTIVE" else "ACTIVE"
        runAction("${member.displayName} marked $next.") {
            groupRepo.toggleMemberStatus(group, member, next, currentActorName())
        }
    }

    fun removeMemberFromGroup(group: Group, member: GroupMember) {
        runAction("${member.displayName} removed from group.") {
            groupRepo.removeMember(group, member, currentActorName())
        }
    }

    fun generateInviteCode(group: Group, role: MemberRole, expiryHours: Long = 72L, onCodeCreated: (String) -> Unit) {
        runAction {
            groupRepo.createInvitation(group, role, expiryHours, 10L, currentActorName()).onSuccess { code ->
                _feedback.value = "Invite code $code created (valid ${expiryHours}h)."
                onCodeCreated(code)
            }
        }
    }

    fun revokeInvite(code: String) {
        runAction("Invite code revoked.") { groupRepo.revokeInvitation(code) }
    }

    fun joinGroupByInviteCode(code: String, onJoined: () -> Unit = {}) {
        runAction {
            groupRepo.redeemInvitation(
                rawCode = code,
                displayName = currentActorName(),
                email = uiState.value.profile?.email ?: initialEmail,
                phoneNumber = uiState.value.profile?.phoneNumber
            ).onSuccess { groupName ->
                _feedback.value = "Joined $groupName!"
                onJoined()
            }
        }
    }

    fun createBill(input: NewBillDraftInput, onCreated: (String) -> Unit) {
        runAction {
            billRepo.createBill(input, currentActorName()).onSuccess { billId ->
                _feedback.value = if (input.saveAsDraft) "Draft bill saved." else "Bill published and members notified!"
                onCreated(billId)
            }
        }
    }

    fun publishDraft(bill: Bill) {
        runAction("Bill published and members notified.") {
            billRepo.publishDraftBill(bill, currentActorName())
        }
    }

    fun approveOrDispute(bill: Bill, participant: BillParticipant, approve: Boolean, reason: String? = null) {
        val billParticipants = uiState.value.participants.filter { it.billId == bill.id }
        runAction(if (approve) "Bill approved." else "Dispute recorded and admin notified.") {
            billRepo.approveOrDisputeBill(bill, participant, billParticipants, approve, reason, currentActorName())
        }
    }

    fun resolveDispute(bill: Bill, newTitle: String, newTotalPaise: Long, newNotes: String?) {
        val billParticipants = uiState.value.participants.filter { it.billId == bill.id }
        runAction("Dispute resolved and bill updated.") {
            billRepo.resolveDisputeAndEditBill(bill, newTitle, newTotalPaise, newNotes, billParticipants, currentActorName())
        }
    }

    fun uploadBillAttachment(bill: Bill, prepared: PreparedAttachment, category: String) {
        runAction("Uploaded ${prepared.fileName}.") {
            billRepo.uploadAttachment(bill, prepared, category, currentActorName())
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        runAction("Document deleted.") { billRepo.deleteAttachment(attachment) }
    }

    fun submitMemberPayment(
        bill: Bill,
        participant: BillParticipant,
        amountPaise: Long,
        method: String,
        upiRefId: String,
        notes: String,
        proof: PreparedAttachment?
    ) {
        runAction("Payment submitted for admin verification.") {
            billRepo.submitPayment(bill, participant, amountPaise, method, upiRefId, notes, proof, currentActorName())
        }
    }

    fun verifyOrRejectPayment(
        bill: Bill,
        payment: PaymentRecord,
        participant: BillParticipant,
        verify: Boolean,
        verifiedAmountPaise: Long,
        rejectionReason: String? = null
    ) {
        val billParticipants = uiState.value.participants.filter { it.billId == bill.id }
        runAction(if (verify) "Payment verified." else "Payment rejected.") {
            billRepo.verifyOrRejectPayment(bill, payment, participant, billParticipants, verify, verifiedAmountPaise, rejectionReason, currentActorName())
        }
    }

    fun recordAdminDirectPayment(
        bill: Bill,
        participant: BillParticipant,
        amountPaise: Long,
        method: String,
        note: String
    ) {
        val billParticipants = uiState.value.participants.filter { it.billId == bill.id }
        runAction("Verified payment recorded.") {
            billRepo.recordAdminVerifiedPayment(bill, participant, billParticipants, amountPaise, method, note, currentActorName())
        }
    }

    fun clearBill(bill: Bill, overrideReason: String? = null) {
        runAction("Bill officially marked CLEARED!") {
            billRepo.clearBill(bill, overrideReason, currentActorName())
        }
    }

    fun sendBillMessage(bill: Bill, text: String, replyTo: ChatMessage? = null, attachment: PreparedAttachment? = null) {
        val role = when {
            currentUserId in bill.adminUids -> MemberRole.ADMIN
            currentUserId in bill.viewerUids -> MemberRole.VIEWER
            else -> MemberRole.MEMBER
        }
        runAction {
            billRepo.sendBillMessage(bill, text, currentActorName(), role, replyTo, attachment)
        }
    }

    fun sendGroupAnnouncement(group: Group, targetUids: List<String>, billId: String = "", text: String) {
        runAction("Announcement sent to members.") {
            billRepo.sendGroupAnnouncement(group, targetUids, billId, text, currentActorName())
        }
    }

    fun sendPaymentReminder(bill: Bill) {
        val pending = uiState.value.participants.filter {
            it.billId == bill.id && it.paymentStatus != PaymentStatus.PAYMENT_VERIFIED.name
        }
        runAction("Payment reminders sent.") {
            billRepo.sendPaymentReminder(bill, pending, currentActorName())
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { billRepo.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { billRepo.markAllNotificationsRead(uiState.value.notifications) }
    }

    fun markMessageRead(msg: ChatMessage) {
        if (uiState.value.demoMode) return
        viewModelScope.launch { billRepo.markMessageRead(msg) }
    }

    // Gemini AI Bill Scanner
    fun scanBillWithAi(bitmap: Bitmap) {
        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Scanning
            val res = aiRepo.scanBillImage(bitmap)
            res.onSuccess { extracted ->
                _aiScanState.value = AiScanUiState.ReadyForReview(extracted)
            }.onFailure { err ->
                _aiScanState.value = AiScanUiState.Error(err.localizedMessage ?: "Failed to scan bill.")
            }
        }
    }

    fun resetAiScanState() {
        _aiScanState.value = AiScanUiState.Idle
    }
}
