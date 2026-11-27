package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MemberRole {
    ADMIN, MEMBER, VIEWER;

    companion object {
        fun fromString(raw: String?): MemberRole =
            entries.firstOrNull { it.name == raw } ?: MEMBER
    }
}

enum class SplitType {
    EQUAL, CUSTOM, PERCENTAGE;

    companion object {
        fun fromString(raw: String?): SplitType =
            entries.firstOrNull { it.name == raw } ?: EQUAL
    }
}

enum class BillStatus(val label: String) {
    DRAFT("Draft"),
    PUBLISHED("Published"),
    AWAITING_APPROVAL("Awaiting Approval"),
    APPROVED("Approved"),
    DISPUTED("Disputed"),
    PAYMENT_IN_PROGRESS("Payment in Progress"),
    CLEARED("Cleared");

    companion object {
        fun fromString(raw: String?): BillStatus =
            entries.firstOrNull { it.name == raw } ?: PUBLISHED
    }
}

enum class ApprovalStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    DISPUTED("Disputed");

    companion object {
        fun fromString(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class PaymentStatus(val label: String) {
    NOT_PAID("Not Paid"),
    PAYMENT_SUBMITTED("Payment Submitted"),
    PARTIALLY_PAID("Partially Paid"),
    PAYMENT_VERIFIED("Payment Verified"),
    PAYMENT_REJECTED("Payment Rejected");

    companion object {
        fun fromString(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_PAID
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = "en",
    val defaultUpiId: String? = null,
    val defaultUpiName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Group(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String? = null,
    val ownerId: String = "",
    val ownerName: String = "",
    val upiId: String? = null,
    val upiRecipientName: String? = null,
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val memberCount: Long = 1L,
    val archived: Boolean = false,
    val lastInviteCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMember(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val displayName: String = "",
    val phoneNumber: String? = null,
    val email: String? = null,
    val role: String = "MEMBER",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Invitation(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val role: String = "MEMBER",
    val createdBy: String = "",
    val createdByName: String = "",
    val status: String = "ACTIVE",
    val expiresAt: Timestamp? = null,
    val maxUses: Long = 10L,
    val usedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Bill(
    @DocumentId val id: String = "",
    val billCode: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val title: String = "",
    val category: String = "Utilities",
    val merchantName: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val totalPaise: Long = 0L,
    val collectedPaise: Long = 0L,
    val splitType: String = "EQUAL",
    val billDateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val status: String = "AWAITING_APPROVAL",
    val upiId: String? = null,
    val upiRecipientName: String? = null,
    val participantCount: Long = 1L,
    val paidCount: Long = 0L,
    val approvedCount: Long = 0L,
    val disputedCount: Long = 0L,
    val clearanceOverrideReason: String? = null,
    val clearedAtMillis: Long? = null,
    val aiScanned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val outstandingPaise: Long
        get() = (totalPaise - collectedPaise).coerceAtLeast(0L)

    val completionPercent: Int
        get() = if (totalPaise <= 0L) 0 else ((collectedPaise * 100L) / totalPaise).toInt().coerceIn(0, 100)
}

@Keep
@IgnoreExtraProperties
data class BillParticipant(
    @DocumentId val id: String = "",
    val billId: String = "",
    val groupId: String = "",
    val ownerId: String = "",
    val participantUid: String = "",
    val participantName: String = "",
    val participantRole: String = "MEMBER",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val sharePaise: Long = 0L,
    val paidPaise: Long = 0L,
    val percentageBps: Long = 0L,
    val approvalStatus: String = "PENDING",
    val disputeReason: String? = null,
    val paymentStatus: String = "NOT_PAID",
    val paymentNote: String? = null,
    val paymentRef: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Attachment(
    @DocumentId val id: String = "",
    val billId: String = "",
    val groupId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val uploadedByUid: String = "",
    val uploadedByName: String = "",
    val fileName: String = "",
    val mimeType: String = "image/jpeg",
    val fileSizeBytes: Long = 0L,
    val category: String = "SUPPORTING_DOC",
    val contentBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val billId: String = "",
    val groupId: String = "",
    val participantDocId: String = "",
    val ownerId: String = "",
    val payerUid: String = "",
    val payerName: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val amountPaise: Long = 0L,
    val method: String = "UPI",
    val upiRefId: String? = null,
    val notes: String? = null,
    val proofAttachmentId: String? = null,
    val status: String = "PAYMENT_SUBMITTED",
    val verifiedByUid: String? = null,
    val verifiedByName: String? = null,
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val billId: String = "",
    val scope: String = "BILL",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "MEMBER",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val text: String = "",
    val replyToMessageId: String? = null,
    val replyToSnippet: String? = null,
    val attachmentId: String? = null,
    val attachmentName: String? = null,
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val groupId: String = "",
    val billId: String? = null,
    val type: String = "BILL_PUBLISHED",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val billId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val action: String = "BILL_CREATED",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ScannedBillResult(
    val merchantName: String = "",
    val billDate: String = "",
    val totalAmountInr: String = "",
    val taxAmountInr: String = "",
    val category: String = "Utilities",
    val lineItems: List<String> = emptyList(),
    val confidenceNote: String = ""
)

val BILL_CATEGORIES = listOf(
    "Utilities",
    "Maintenance",
    "Groceries",
    "Property Tax",
    "Rent",
    "Medical",
    "Travel",
    "Events",
    "Internet & Cable",
    "Other"
)
