package com.example.data

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Strictly separated Demo Mode sample data — ONLY displayed when the user
 * explicitly enables "Sample Preview Mode" in Profile settings.
 * Live Firebase Firestore is always used by default.
 */
object DemoModeData {
    private const val DAY = 86_400_000L

    fun sampleGroups(uid: String, ownerName: String): List<Group> {
        val now = Timestamp(Date())
        return listOf(
            Group(
                id = "demo_grp_1",
                name = "Sharma Family Home",
                description = "Monthly utilities, society maintenance & property expenses",
                ownerId = uid,
                ownerName = ownerName,
                upiId = "family.treasury@okicici",
                upiRecipientName = ownerName,
                memberUids = listOf(uid, "demo_m2", "demo_m3", "demo_m4", "demo_m5"),
                adminUids = listOf(uid),
                viewerUids = listOf("demo_m5"),
                memberCount = 5,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun sampleMembers(uid: String, ownerName: String): List<GroupMember> {
        val now = Timestamp(Date())
        return listOf(
            GroupMember("demo_gm_1", "demo_grp_1", uid, uid, listOf(uid), listOf(uid), ownerName, "+91 98450 11223", "admin@example.com", "ADMIN", "ACTIVE", now, now),
            GroupMember("demo_gm_2", "demo_grp_1", "demo_m2", uid, listOf(uid), listOf(uid), "Arjun Sharma", "+91 98450 22334", "arjun@example.com", "MEMBER", "ACTIVE", now, now),
            GroupMember("demo_gm_3", "demo_grp_1", "demo_m3", uid, listOf(uid), listOf(uid), "Priya Nair", "+91 98450 33445", "priya@example.com", "MEMBER", "ACTIVE", now, now),
            GroupMember("demo_gm_4", "demo_grp_1", "demo_m4", uid, listOf(uid), listOf(uid), "Karthik S", "+91 98450 44556", "karthik@example.com", "MEMBER", "ACTIVE", now, now),
            GroupMember("demo_gm_5", "demo_grp_1", "demo_m5", uid, listOf(uid), listOf(uid), "Ramesh Uncle", "+91 98450 55667", "ramesh@example.com", "VIEWER", "ACTIVE", now, now)
        )
    }

    fun sampleBills(uid: String, ownerName: String): List<Bill> {
        val nowMs = System.currentTimeMillis()
        val now = Timestamp(Date(nowMs))
        return listOf(
            Bill(
                id = "demo_bill_1",
                billCode = "BC-2026-4102",
                groupId = "demo_grp_1",
                groupName = "Sharma Family Home",
                ownerId = uid,
                ownerName = ownerName,
                memberUids = listOf(uid, "demo_m2", "demo_m3", "demo_m4", "demo_m5"),
                adminUids = listOf(uid),
                viewerUids = listOf("demo_m5"),
                title = "Quarterly Apartment Maintenance",
                category = "Maintenance",
                merchantName = "Prestige Lakeside RWA",
                description = "Q4 building maintenance, generator backup, and water charges",
                notes = "Split equally across 5 family members (₹6,000 each)",
                totalPaise = 3_000_000L, // ₹30,000
                collectedPaise = 1_800_000L, // ₹18,000
                splitType = "EQUAL",
                billDateMillis = nowMs - 2 * DAY,
                dueDateMillis = nowMs + 4 * DAY,
                status = BillStatus.PAYMENT_IN_PROGRESS.name,
                upiId = "family.treasury@okicici",
                upiRecipientName = ownerName,
                participantCount = 5,
                paidCount = 3,
                approvedCount = 4,
                disputedCount = 0,
                aiScanned = true,
                createdAt = now,
                updatedAt = now
            ),
            Bill(
                id = "demo_bill_2",
                billCode = "BC-2026-4088",
                groupId = "demo_grp_1",
                groupName = "Sharma Family Home",
                ownerId = uid,
                ownerName = ownerName,
                memberUids = listOf(uid, "demo_m2", "demo_m3", "demo_m4"),
                adminUids = listOf(uid),
                viewerUids = emptyList(),
                title = "BESCOM Electricity & Fiber",
                category = "Utilities",
                merchantName = "BESCOM + ACT Fibernet",
                totalPaise = 840_000L, // ₹8,400
                collectedPaise = 840_000L,
                splitType = "EQUAL",
                billDateMillis = nowMs - 14 * DAY,
                dueDateMillis = nowMs - 7 * DAY,
                status = BillStatus.CLEARED.name,
                upiId = "family.treasury@okicici",
                upiRecipientName = ownerName,
                participantCount = 4,
                paidCount = 4,
                approvedCount = 4,
                disputedCount = 0,
                clearedAtMillis = nowMs - 5 * DAY,
                createdAt = Timestamp(Date(nowMs - 14 * DAY)),
                updatedAt = Timestamp(Date(nowMs - 5 * DAY))
            )
        )
    }

    fun sampleParticipants(uid: String, ownerName: String): List<BillParticipant> {
        val now = Timestamp(Date())
        return listOf(
            BillParticipant("demo_bp_1", "demo_bill_1", "demo_grp_1", uid, uid, ownerName, "ADMIN", listOf(uid), listOf(uid), emptyList(), 600_000L, 600_000L, 2000, "APPROVED", null, "PAYMENT_VERIFIED", "Settled", "UTR4321001", now, now),
            BillParticipant("demo_bp_2", "demo_bill_1", "demo_grp_1", uid, "demo_m2", "Arjun Sharma", "MEMBER", listOf(uid), listOf(uid), emptyList(), 600_000L, 600_000L, 2000, "APPROVED", null, "PAYMENT_VERIFIED", "GPay", "UTR4321002", now, now),
            BillParticipant("demo_bp_3", "demo_bill_1", "demo_grp_1", uid, "demo_m3", "Priya Nair", "MEMBER", listOf(uid), listOf(uid), emptyList(), 600_000L, 600_000L, 2000, "APPROVED", null, "PAYMENT_VERIFIED", "PhonePe", "UTR4321003", now, now),
            BillParticipant("demo_bp_4", "demo_bill_1", "demo_grp_1", uid, "demo_m4", "Karthik S", "MEMBER", listOf(uid), listOf(uid), emptyList(), 600_000L, 0L, 2000, "APPROVED", null, "PAYMENT_SUBMITTED", "Sent via UPI", "UTR4321999", now, now),
            BillParticipant("demo_bp_5", "demo_bill_1", "demo_grp_1", uid, "demo_m5", "Ramesh Uncle", "VIEWER", listOf(uid), listOf(uid), emptyList(), 600_000L, 0L, 2000, "PENDING", null, "NOT_PAID", null, null, now, now)
        )
    }

    fun sampleMessages(uid: String, ownerName: String): List<ChatMessage> {
        val now = Timestamp(Date())
        return listOf(
            ChatMessage(
                id = "demo_msg_1",
                groupId = "demo_grp_1",
                billId = "",
                scope = "ANNOUNCEMENT",
                senderUid = uid,
                senderName = ownerName,
                senderRole = "ADMIN",
                memberUids = listOf(uid),
                adminUids = listOf(uid),
                text = "New bill uploaded. Total amount ₹30,000. Each person's share is ₹6,000. Please review the bill and complete payment before the due date.",
                readByUids = listOf(uid),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun sampleAuditLogs(uid: String, ownerName: String): List<AuditLog> {
        val now = Timestamp(Date())
        return listOf(
            AuditLog("demo_log_1", "demo_grp_1", "demo_bill_1", uid, ownerName, listOf(uid), listOf(uid), "BILL_CREATED", "Published Quarterly Apartment Maintenance (₹30,000) split equally across 5 members", now),
            AuditLog("demo_log_2", "demo_grp_1", "demo_bill_1", uid, ownerName, listOf(uid), listOf(uid), "PAYMENT_VERIFIED", "Verified ₹6,000 from Arjun Sharma & Priya Nair", now)
        )
    }
}
