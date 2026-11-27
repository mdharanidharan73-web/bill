package com.example.data

import com.example.util.CurrencyAndSplitUtils
import com.example.util.PreparedAttachment
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class NewBillDraftInput(
    val group: Group,
    val title: String,
    val billCode: String,
    val category: String,
    val merchantName: String?,
    val description: String?,
    val notes: String?,
    val totalPaise: Long,
    val splitType: SplitType,
    val billDateMillis: Long,
    val dueDateMillis: Long,
    val selectedMembers: List<GroupMember>,
    val sharePaiseByMemberUid: Map<String, Long>,
    val percentageBpsByMemberUid: Map<String, Int>,
    val saveAsDraft: Boolean,
    val aiScanned: Boolean,
    val initialAttachment: PreparedAttachment? = null
)

class BillOperationsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logErrors(uid: String, path: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val ex = firestoreError ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(ex, OperationType.LIST, path)
            }
            throw error
        }

    fun observeBills(uid: String): Flow<List<Bill>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("bills")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Bill::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "bills")
        )
    }

    fun observeBillParticipants(uid: String): Flow<List<BillParticipant>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("billParticipants")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(BillParticipant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.participantName.lowercase() }
                }
                .logErrors(uid, "billParticipants")
        )
    }

    fun observeAttachments(uid: String): Flow<List<Attachment>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("attachments")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Attachment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "attachments")
        )
    }

    fun observePayments(uid: String): Flow<List<PaymentRecord>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("payments")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "payments")
        )
    }

    fun observeMessages(uid: String): Flow<List<ChatMessage>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "messages")
        )
    }

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("notifications")
                .whereEqualTo("recipientUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "notifications")
        )
    }

    fun observeAuditLogs(uid: String): Flow<List<AuditLog>> = flow {
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        emitAll(
            db.collection("auditLogs")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(uid, "auditLogs")
        )
    }

    suspend fun createBill(input: NewBillDraftInput, actorName: String): Result<String> {
        var path = "bills"
        return try {
            val uid = requireUserId()
            require(input.title.isNotBlank()) { "Bill title is required." }
            require(input.totalPaise > 0L) { "Total amount must be greater than ₹0." }
            require(input.selectedMembers.isNotEmpty()) { "Select at least one member." }

            val sumShares = input.selectedMembers.sumOf { input.sharePaiseByMemberUid[it.userId] ?: 0L }
            require(sumShares == input.totalPaise) {
                "Member shares (${CurrencyAndSplitUtils.formatInr(sumShares)}) must add up exactly to ${CurrencyAndSplitUtils.formatInr(input.totalPaise)}."
            }

            val billRef = db.collection("bills").document()
            val billId = billRef.id
            path = billRef.path

            val billStatus = if (input.saveAsDraft) BillStatus.DRAFT.name else BillStatus.AWAITING_APPROVAL.name
            val memberUids = (input.group.memberUids + uid).distinct().take(50)
            val adminUids = (input.group.adminUids + uid).distinct().take(10)
            val viewerUids = input.group.viewerUids.distinct().take(50)

            val billPayload = mapOf(
                "billCode" to input.billCode.take(32),
                "groupId" to input.group.id,
                "groupName" to input.group.name.take(100),
                "ownerId" to uid,
                "ownerName" to actorName.ifBlank { "Admin" }.take(100),
                "memberUids" to memberUids,
                "adminUids" to adminUids,
                "viewerUids" to viewerUids,
                "title" to input.title.trim().take(120),
                "category" to input.category.take(50),
                "merchantName" to input.merchantName?.trim()?.take(120)?.ifBlank { null },
                "description" to input.description?.trim()?.take(600)?.ifBlank { null },
                "notes" to input.notes?.trim()?.take(600)?.ifBlank { null },
                "totalPaise" to input.totalPaise,
                "collectedPaise" to 0L,
                "splitType" to input.splitType.name,
                "billDateMillis" to input.billDateMillis,
                "dueDateMillis" to input.dueDateMillis,
                "status" to billStatus,
                "upiId" to input.group.upiId?.trim()?.take(100)?.ifBlank { null },
                "upiRecipientName" to input.group.upiRecipientName?.trim()?.take(100)?.ifBlank { null },
                "participantCount" to input.selectedMembers.size.toLong(),
                "paidCount" to 0L,
                "approvedCount" to 0L,
                "disputedCount" to 0L,
                "aiScanned" to input.aiScanned,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            billRef.set(billPayload).await()

            for (member in input.selectedMembers) {
                val share = input.sharePaiseByMemberUid[member.userId] ?: 0L
                val bps = input.percentageBpsByMemberUid[member.userId] ?: 0
                val pDocId = "${billId}_${member.userId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}".take(120)
                val pPayload = mapOf(
                    "billId" to billId,
                    "groupId" to input.group.id,
                    "ownerId" to uid,
                    "participantUid" to member.userId,
                    "participantName" to member.displayName.take(100),
                    "participantRole" to member.role,
                    "memberUids" to memberUids,
                    "adminUids" to adminUids,
                    "viewerUids" to viewerUids,
                    "sharePaise" to share,
                    "paidPaise" to 0L,
                    "percentageBps" to bps.toLong().coerceIn(0L, 10000L),
                    "approvalStatus" to ApprovalStatus.PENDING.name,
                    "paymentStatus" to PaymentStatus.NOT_PAID.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("billParticipants").document(pDocId).set(pPayload).await()
            }

            if (input.initialAttachment != null) {
                uploadAttachment(
                    bill = Bill(
                        id = billId,
                        groupId = input.group.id,
                        ownerId = uid,
                        memberUids = memberUids,
                        adminUids = adminUids,
                        viewerUids = viewerUids,
                        title = input.title
                    ),
                    prepared = input.initialAttachment,
                    category = "ORIGINAL_BILL",
                    uploaderName = actorName
                )
            }

            writeAuditLog(
                groupId = input.group.id,
                billId = billId,
                actorUid = uid,
                actorName = actorName,
                memberUids = memberUids,
                adminUids = adminUids,
                action = "BILL_CREATED",
                summary = "${if (input.saveAsDraft) "Saved draft" else "Published"} ${input.title} (${input.billCode}) for ${CurrencyAndSplitUtils.formatInr(input.totalPaise)} split across ${input.selectedMembers.size} members"
            )

            if (!input.saveAsDraft) {
                notifyMembers(
                    recipientUids = memberUids.filter { it != uid },
                    senderUid = uid,
                    senderName = actorName,
                    groupId = input.group.id,
                    billId = billId,
                    type = "BILL_PUBLISHED",
                    title = "New Bill: ${input.title}",
                    body = "Total ${CurrencyAndSplitUtils.formatInr(input.totalPaise)} in ${input.group.name}. Please review and approve."
                )
            }

            Result.success(billId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishDraftBill(bill: Bill, actorName: String): Result<Unit> {
        val path = "bills/${bill.id}"
        return try {
            val uid = requireUserId()
            db.collection("bills").document(bill.id).update(
                mapOf("status" to BillStatus.AWAITING_APPROVAL.name, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            writeAuditLog(bill.groupId, bill.id, uid, actorName, bill.memberUids, bill.adminUids, "BILL_EDITED", "Published draft bill ${bill.billCode}")
            notifyMembers(
                bill.memberUids.filter { it != uid }, uid, actorName, bill.groupId, bill.id,
                "BILL_PUBLISHED", "Bill Published: ${bill.title}",
                "Total ${CurrencyAndSplitUtils.formatInr(bill.totalPaise)}. Please review & pay before ${CurrencyAndSplitUtils.formatDate(bill.dueDateMillis)}."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun approveOrDisputeBill(
        bill: Bill,
        participant: BillParticipant,
        allParticipants: List<BillParticipant>,
        approve: Boolean,
        disputeReason: String?,
        actorName: String
    ): Result<Unit> {
        var path = "billParticipants/${participant.id}"
        return try {
            val uid = requireUserId()
            val newApproval = if (approve) ApprovalStatus.APPROVED.name else ApprovalStatus.DISPUTED.name
            val pUpdates = mutableMapOf<String, Any>(
                "approvalStatus" to newApproval,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!approve && !disputeReason.isNullOrBlank()) {
                pUpdates["disputeReason"] = disputeReason.trim().take(400)
            }
            db.collection("billParticipants").document(participant.id).update(pUpdates).await()

            val updatedList = allParticipants.map {
                if (it.id == participant.id) it.copy(approvalStatus = newApproval) else it
            }
            val approvedCnt = updatedList.count { it.approvalStatus == ApprovalStatus.APPROVED.name }.toLong()
            val disputedCnt = updatedList.count { it.approvalStatus == ApprovalStatus.DISPUTED.name }.toLong()
            val nextBillStatus = when {
                disputedCnt > 0L -> BillStatus.DISPUTED.name
                approvedCnt >= updatedList.size.toLong() && bill.status != BillStatus.PAYMENT_IN_PROGRESS.name -> BillStatus.APPROVED.name
                else -> if (bill.status == BillStatus.PAYMENT_IN_PROGRESS.name) BillStatus.PAYMENT_IN_PROGRESS.name else BillStatus.AWAITING_APPROVAL.name
            }

            path = "bills/${bill.id}"
            db.collection("bills").document(bill.id).update(
                mapOf(
                    "approvedCount" to approvedCnt,
                    "disputedCount" to disputedCnt,
                    "status" to nextBillStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, actorName, bill.memberUids, bill.adminUids,
                action = if (approve) "BILL_APPROVED" else "BILL_DISPUTED",
                summary = if (approve) "$actorName approved ${bill.billCode}" else "$actorName disputed ${bill.billCode}: ${disputeReason.orEmpty()}"
            )
            notifyMembers(
                bill.adminUids.filter { it != uid }, uid, actorName, bill.groupId, bill.id,
                type = if (approve) "BILL_APPROVED" else "BILL_DISPUTED",
                title = if (approve) "Bill Approved" else "Bill Disputed",
                body = if (approve) "$actorName approved ${bill.title}." else "$actorName raised a dispute on ${bill.title}: ${disputeReason.orEmpty()}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveDisputeAndEditBill(
        bill: Bill,
        newTitle: String,
        newTotalPaise: Long,
        newNotes: String?,
        participants: List<BillParticipant>,
        actorName: String
    ): Result<Unit> {
        var path = "bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(newTitle.isNotBlank() && newTotalPaise > 0L) { "Provide valid title and positive amount." }

            // Recompute equal shares deterministically if total changed
            val equalShares = CurrencyAndSplitUtils.splitEqually(newTotalPaise, participants.map { it.participantUid })
            for (p in participants) {
                val share = if (newTotalPaise != bill.totalPaise) (equalShares[p.participantUid] ?: p.sharePaise) else p.sharePaise
                db.collection("billParticipants").document(p.id).update(
                    mapOf(
                        "sharePaise" to share,
                        "approvalStatus" to ApprovalStatus.PENDING.name,
                        "disputeReason" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("bills").document(bill.id).update(
                mapOf(
                    "title" to newTitle.trim().take(120),
                    "totalPaise" to newTotalPaise,
                    "notes" to newNotes?.trim()?.take(600).orEmpty(),
                    "disputedCount" to 0L,
                    "approvedCount" to 0L,
                    "status" to BillStatus.AWAITING_APPROVAL.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, actorName, bill.memberUids, bill.adminUids,
                "DISPUTE_RESOLVED",
                "Resolved dispute and updated ${bill.billCode} to ${CurrencyAndSplitUtils.formatInr(newTotalPaise)}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun uploadAttachment(
        bill: Bill,
        prepared: PreparedAttachment,
        category: String,
        uploaderName: String
    ): Result<String> {
        var path = "attachments"
        return try {
            val uid = requireUserId()
            val ref = db.collection("attachments").document()
            path = ref.path
            val payload = mapOf(
                "billId" to bill.id,
                "groupId" to bill.groupId,
                "ownerId" to bill.ownerId,
                "memberUids" to bill.memberUids,
                "adminUids" to bill.adminUids,
                "viewerUids" to bill.viewerUids,
                "uploadedByUid" to uid,
                "uploadedByName" to uploaderName.ifBlank { "Member" }.take(100),
                "fileName" to prepared.fileName.take(180),
                "mimeType" to prepared.mimeType,
                "fileSizeBytes" to prepared.fileSizeBytes.toLong(),
                "category" to category,
                "contentBase64" to prepared.contentBase64,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, uploaderName, bill.memberUids, bill.adminUids,
                "ATTACHMENT_UPLOADED",
                "$uploaderName uploaded ${prepared.fileName} ($category)"
            )
            notifyMembers(
                bill.memberUids.filter { it != uid }, uid, uploaderName, bill.groupId, bill.id,
                "DOC_UPLOADED", "New Document Uploaded",
                "$uploaderName added ${prepared.fileName} to ${bill.title}."
            )
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAttachment(attachment: Attachment): Result<Unit> {
        val path = "attachments/${attachment.id}"
        return try {
            requireUserId()
            db.collection("attachments").document(attachment.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Submits a payment proof for admin verification.
     * IMPORTANT: Never marks payment as PAYMENT_VERIFIED automatically!
     */
    suspend fun submitPayment(
        bill: Bill,
        participant: BillParticipant,
        amountPaise: Long,
        method: String,
        upiRefId: String?,
        notes: String?,
        proofAttachment: PreparedAttachment?,
        payerName: String
    ): Result<Unit> {
        var path = "payments"
        return try {
            val uid = requireUserId()
            require(amountPaise > 0L) { "Payment amount must be greater than ₹0." }

            var attachmentId: String? = null
            if (proofAttachment != null) {
                attachmentId = uploadAttachment(bill, proofAttachment, "PAYMENT_PROOF", payerName).getOrNull()
            }

            val payRef = db.collection("payments").document()
            path = payRef.path
            val payPayload = mapOf(
                "billId" to bill.id,
                "groupId" to bill.groupId,
                "participantDocId" to participant.id,
                "ownerId" to bill.ownerId,
                "payerUid" to participant.participantUid,
                "payerName" to participant.participantName.take(100),
                "memberUids" to bill.memberUids,
                "adminUids" to bill.adminUids,
                "viewerUids" to bill.viewerUids,
                "amountPaise" to amountPaise,
                "method" to method,
                "upiRefId" to upiRefId?.trim()?.take(120)?.ifBlank { null },
                "notes" to notes?.trim()?.take(400)?.ifBlank { null },
                "proofAttachmentId" to attachmentId,
                "status" to PaymentStatus.PAYMENT_SUBMITTED.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            payRef.set(payPayload).await()

            path = "billParticipants/${participant.id}"
            db.collection("billParticipants").document(participant.id).update(
                mapOf(
                    "paymentStatus" to PaymentStatus.PAYMENT_SUBMITTED.name,
                    "paymentRef" to upiRefId?.trim()?.take(120).orEmpty(),
                    "paymentNote" to notes?.trim()?.take(300).orEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (bill.status != BillStatus.PAYMENT_IN_PROGRESS.name && bill.status != BillStatus.CLEARED.name) {
                db.collection("bills").document(bill.id).update(
                    mapOf(
                        "status" to BillStatus.PAYMENT_IN_PROGRESS.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            writeAuditLog(
                bill.groupId, bill.id, uid, payerName, bill.memberUids, bill.adminUids,
                "PAYMENT_SUBMITTED",
                "${participant.participantName} submitted ${CurrencyAndSplitUtils.formatInr(amountPaise)} via $method (pending admin verification)"
            )
            notifyMembers(
                bill.adminUids.filter { it != uid }, uid, payerName, bill.groupId, bill.id,
                "PAYMENT_SUBMITTED", "Payment Submitted for Verification",
                "${participant.participantName} submitted ${CurrencyAndSplitUtils.formatInr(amountPaise)} for ${bill.title}."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Admin manually verifies (full or partial) or rejects a payment record and recalculates bill totals.
     */
    suspend fun verifyOrRejectPayment(
        bill: Bill,
        payment: PaymentRecord,
        participant: BillParticipant,
        allParticipants: List<BillParticipant>,
        verify: Boolean,
        verifiedAmountPaise: Long,
        rejectionReason: String?,
        adminName: String
    ): Result<Unit> {
        var path = "payments/${payment.id}"
        return try {
            val uid = requireUserId()
            val newPaidTotal = if (verify) {
                (participant.paidPaise + verifiedAmountPaise).coerceAtMost(participant.sharePaise * 2)
            } else {
                participant.paidPaise
            }
            val newParticipantPaymentStatus = when {
                !verify -> PaymentStatus.PAYMENT_REJECTED.name
                newPaidTotal >= participant.sharePaise -> PaymentStatus.PAYMENT_VERIFIED.name
                newPaidTotal > 0L -> PaymentStatus.PARTIALLY_PAID.name
                else -> PaymentStatus.NOT_PAID.name
            }
            val newPaymentRecordStatus = when {
                !verify -> PaymentStatus.PAYMENT_REJECTED.name
                newPaidTotal >= participant.sharePaise -> PaymentStatus.PAYMENT_VERIFIED.name
                else -> PaymentStatus.PARTIALLY_PAID.name
            }

            val paymentUpdates = mutableMapOf<String, Any>(
                "status" to newPaymentRecordStatus,
                "verifiedByUid" to uid,
                "verifiedByName" to adminName.take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (verify) {
                paymentUpdates["amountPaise"] = verifiedAmountPaise
            } else if (!rejectionReason.isNullOrBlank()) {
                paymentUpdates["rejectionReason"] = rejectionReason.trim().take(300)
            }
            db.collection("payments").document(payment.id).update(paymentUpdates).await()

            path = "billParticipants/${participant.id}"
            db.collection("billParticipants").document(participant.id).update(
                mapOf(
                    "paidPaise" to newPaidTotal,
                    "paymentStatus" to newParticipantPaymentStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val updatedParticipants = allParticipants.map {
                if (it.id == participant.id) it.copy(paidPaise = newPaidTotal, paymentStatus = newParticipantPaymentStatus) else it
            }
            val totalCollected = updatedParticipants.sumOf { it.paidPaise }
            val paidCount = updatedParticipants.count { it.paymentStatus == PaymentStatus.PAYMENT_VERIFIED.name }.toLong()

            path = "bills/${bill.id}"
            db.collection("bills").document(bill.id).update(
                mapOf(
                    "collectedPaise" to totalCollected,
                    "paidCount" to paidCount,
                    "status" to BillStatus.PAYMENT_IN_PROGRESS.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, adminName, bill.memberUids, bill.adminUids,
                action = if (verify) "PAYMENT_VERIFIED" else "PAYMENT_REJECTED",
                summary = if (verify) {
                    "Admin verified ${CurrencyAndSplitUtils.formatInr(verifiedAmountPaise)} from ${participant.participantName}"
                } else {
                    "Admin rejected payment from ${participant.participantName}: ${rejectionReason.orEmpty()}"
                }
            )
            if (participant.participantUid != uid && !participant.participantUid.startsWith("member_")) {
                notifyMembers(
                    listOf(participant.participantUid), uid, adminName, bill.groupId, bill.id,
                    "PAYMENT_VERIFIED",
                    if (verify) "Payment Verified" else "Payment Needs Attention",
                    if (verify) "Your payment of ${CurrencyAndSplitUtils.formatInr(verifiedAmountPaise)} for ${bill.title} was verified."
                    else "Your payment for ${bill.title} was rejected: ${rejectionReason.orEmpty()}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Direct admin verification for offline/cash/managed members (records payment + updates rollups).
     */
    suspend fun recordAdminVerifiedPayment(
        bill: Bill,
        participant: BillParticipant,
        allParticipants: List<BillParticipant>,
        amountPaise: Long,
        method: String,
        note: String?,
        adminName: String
    ): Result<Unit> {
        var path = "payments"
        return try {
            val uid = requireUserId()
            require(amountPaise > 0L) { "Amount must be positive." }
            val newPaid = participant.paidPaise + amountPaise
            val pStatus = if (newPaid >= participant.sharePaise) PaymentStatus.PAYMENT_VERIFIED.name else PaymentStatus.PARTIALLY_PAID.name

            val payRef = db.collection("payments").document()
            path = payRef.path
            payRef.set(
                mapOf(
                    "billId" to bill.id,
                    "groupId" to bill.groupId,
                    "participantDocId" to participant.id,
                    "ownerId" to bill.ownerId,
                    "payerUid" to participant.participantUid,
                    "payerName" to participant.participantName.take(100),
                    "memberUids" to bill.memberUids,
                    "adminUids" to bill.adminUids,
                    "viewerUids" to bill.viewerUids,
                    "amountPaise" to amountPaise,
                    "method" to method,
                    "notes" to note?.trim()?.take(400)?.ifBlank { null },
                    "status" to pStatus,
                    "verifiedByUid" to uid,
                    "verifiedByName" to adminName.take(100),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
            ).await()

            db.collection("billParticipants").document(participant.id).update(
                mapOf("paidPaise" to newPaid, "paymentStatus" to pStatus, "updatedAt" to FieldValue.serverTimestamp())
            ).await()

            val updatedList = allParticipants.map {
                if (it.id == participant.id) it.copy(paidPaise = newPaid, paymentStatus = pStatus) else it
            }
            val collected = updatedList.sumOf { it.paidPaise }
            val paidCount = updatedList.count { it.paymentStatus == PaymentStatus.PAYMENT_VERIFIED.name }.toLong()

            db.collection("bills").document(bill.id).update(
                mapOf(
                    "collectedPaise" to collected,
                    "paidCount" to paidCount,
                    "status" to BillStatus.PAYMENT_IN_PROGRESS.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, adminName, bill.memberUids, bill.adminUids,
                "PAYMENT_VERIFIED",
                "Admin recorded verified $method payment of ${CurrencyAndSplitUtils.formatInr(amountPaise)} for ${participant.participantName}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Marks a bill CLEARED. Server rules strictly reject CLEARED if collectedPaise < totalPaise
     * unless an explicit authorized override reason (>= 5 chars) is provided and audited.
     */
    suspend fun clearBill(bill: Bill, overrideReason: String?, adminName: String): Result<Unit> {
        val path = "bills/${bill.id}"
        return try {
            val uid = requireUserId()
            val hasOutstanding = bill.collectedPaise < bill.totalPaise
            if (hasOutstanding && (overrideReason.isNullOrBlank() || overrideReason.trim().length < 5)) {
                return Result.failure(
                    IllegalArgumentException("Outstanding balance remains. Verify all payments or provide an authorized override reason (min 5 chars).")
                )
            }
            val updates = mutableMapOf<String, Any>(
                "status" to BillStatus.CLEARED.name,
                "clearedAtMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!overrideReason.isNullOrBlank()) {
                updates["clearanceOverrideReason"] = overrideReason.trim().take(300)
            }
            db.collection("bills").document(bill.id).update(updates).await()

            writeAuditLog(
                bill.groupId, bill.id, uid, adminName, bill.memberUids, bill.adminUids,
                "BILL_CLEARED",
                if (hasOutstanding) "Cleared ${bill.billCode} with admin override: ${overrideReason?.trim()}"
                else "All payments verified. Officially cleared ${bill.billCode} (${CurrencyAndSplitUtils.formatInr(bill.totalPaise)})"
            )
            notifyMembers(
                bill.memberUids.filter { it != uid }, uid, adminName, bill.groupId, bill.id,
                "BILL_CLEARED", "Bill Cleared: ${bill.title}",
                "${bill.title} (${bill.billCode}) is officially CLEARED. Download your clearance receipt anytime."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendBillMessage(
        bill: Bill,
        text: String,
        senderName: String,
        senderRole: MemberRole,
        replyTo: ChatMessage? = null,
        attachment: PreparedAttachment? = null
    ): Result<Unit> {
        var path = "messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }

            var attachmentId: String? = null
            var attachmentName: String? = null
            if (attachment != null) {
                attachmentId = uploadAttachment(bill, attachment, "SUPPORTING_DOC", senderName).getOrNull()
                attachmentName = attachment.fileName
            }

            val ref = db.collection("messages").document()
            path = ref.path
            val payload = mapOf(
                "groupId" to bill.groupId,
                "billId" to bill.id,
                "scope" to "BILL",
                "senderUid" to uid,
                "senderName" to senderName.ifBlank { "Member" }.take(100),
                "senderRole" to senderRole.name,
                "memberUids" to bill.memberUids,
                "adminUids" to bill.adminUids,
                "viewerUids" to bill.viewerUids,
                "text" to cleanText,
                "replyToMessageId" to replyTo?.id,
                "replyToSnippet" to replyTo?.text?.take(180),
                "attachmentId" to attachmentId,
                "attachmentName" to attachmentName,
                "readByUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            ref.set(payload).await()

            writeAuditLog(bill.groupId, bill.id, uid, senderName, bill.memberUids, bill.adminUids, "MESSAGE_SENT", "$senderName posted in ${bill.billCode}")
            notifyMembers(
                bill.memberUids.filter { it != uid }, uid, senderName, bill.groupId, bill.id,
                "NEW_MESSAGE", "New Message in ${bill.title}",
                "$senderName: ${cleanText.take(120)}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupAnnouncement(
        group: Group,
        targetMemberUids: List<String>,
        billId: String,
        text: String,
        adminName: String
    ): Result<Unit> {
        var path = "messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotEmpty()) { "Announcement message cannot be empty." }
            val recipients = (targetMemberUids + uid).distinct().take(50)

            val ref = db.collection("messages").document()
            path = ref.path
            ref.set(
                mapOf(
                    "groupId" to group.id,
                    "billId" to billId,
                    "scope" to "ANNOUNCEMENT",
                    "senderUid" to uid,
                    "senderName" to adminName.ifBlank { "Admin" }.take(100),
                    "senderRole" to MemberRole.ADMIN.name,
                    "memberUids" to recipients,
                    "adminUids" to group.adminUids,
                    "viewerUids" to group.viewerUids,
                    "text" to cleanText,
                    "readByUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(group.id, billId, uid, adminName, recipients, group.adminUids, "ANNOUNCEMENT_SENT", "Announcement sent to ${recipients.size} members")
            notifyMembers(
                recipients.filter { it != uid }, uid, adminName, group.id, billId.ifBlank { null },
                "ANNOUNCEMENT", "Announcement — ${group.name}", cleanText.take(400)
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendPaymentReminder(bill: Bill, pendingParticipants: List<BillParticipant>, adminName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val targets = pendingParticipants
                .map { it.participantUid }
                .filter { it != uid && !it.startsWith("member_") }
                .distinct()
            notifyMembers(
                recipientUids = targets.ifEmpty { bill.memberUids.filter { it != uid } },
                senderUid = uid,
                senderName = adminName,
                groupId = bill.groupId,
                billId = bill.id,
                type = "PAYMENT_REMINDER",
                title = "Payment Reminder: ${bill.title}",
                body = "Reminder for ${bill.billCode} due on ${CurrencyAndSplitUtils.formatDate(bill.dueDateMillis)}. Please submit your share."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "notifications")
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId).update(
                mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(notifications: List<AppNotification>): Result<Unit> {
        return try {
            requireUserId()
            for (n in notifications.filter { !it.read }) {
                db.collection("notifications").document(n.id).update(
                    mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications")
            Result.failure(e)
        }
    }

    suspend fun markMessageRead(message: ChatMessage): Result<Unit> {
        val path = "messages/${message.id}"
        return try {
            val uid = requireUserId()
            if (uid in message.readByUids) return Result.success(Unit)
            db.collection("messages").document(message.id).update(
                mapOf(
                    "readByUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun notifyMembers(
        recipientUids: List<String>,
        senderUid: String,
        senderName: String,
        groupId: String,
        billId: String?,
        type: String,
        title: String,
        body: String
    ) {
        for (targetUid in recipientUids.distinct().take(40)) {
            if (targetUid.isBlank() || targetUid.startsWith("member_")) continue
            try {
                val payload = mapOf(
                    "recipientUid" to targetUid,
                    "senderUid" to senderUid,
                    "senderName" to senderName.ifBlank { "BillCircle" }.take(100),
                    "groupId" to groupId,
                    "billId" to billId?.ifBlank { null },
                    "type" to type,
                    "title" to title.take(140),
                    "body" to body.take(500),
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                db.collection("notifications").document().set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "notifications")
            }
        }
    }

    private suspend fun writeAuditLog(
        groupId: String,
        billId: String,
        actorUid: String,
        actorName: String,
        memberUids: List<String>,
        adminUids: List<String>,
        action: String,
        summary: String
    ) {
        try {
            db.collection("auditLogs").document().set(
                mapOf(
                    "groupId" to groupId,
                    "billId" to billId,
                    "actorUid" to actorUid,
                    "actorName" to actorName.ifBlank { "User" }.take(100),
                    "memberUids" to memberUids,
                    "adminUids" to adminUids,
                    "action" to action,
                    "summary" to summary.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "auditLogs")
        }
    }
}
