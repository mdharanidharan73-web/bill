package com.example.data

import com.example.util.CurrencyAndSplitUtils
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
import java.util.Date

class GroupAndUserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logFirestoreErrors(uid: String, op: OperationType, path: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val ex = firestoreError ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(ex, op, path)
            }
            throw error
        }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (uid.isBlank()) {
            emit(null); return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observeGroups(uid: String): Flow<List<Group>> = flow {
        if (uid.isBlank()) {
            emit(emptyList()); return@flow
        }
        val path = "groups"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeGroupMembers(uid: String): Flow<List<GroupMember>> = flow {
        if (uid.isBlank()) {
            emit(emptyList()); return@flow
        }
        val path = "groupMembers"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeMyInvitations(uid: String): Flow<List<Invitation>> = flow {
        if (uid.isBlank()) {
            emit(emptyList()); return@flow
        }
        val path = "invitations"
        emitAll(
            db.collection(path)
                .whereEqualTo("createdBy", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String?,
        languageCode: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "BillCircle User" }.take(100),
                    "email" to email.ifBlank { "user@billcircle.app" }.take(150),
                    "photoUrl" to photoUrl?.take(600),
                    "preferredLanguage" to languageCode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        phoneNumber: String?,
        preferredLanguage: String,
        defaultUpiId: String?,
        defaultUpiName: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = displayName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Name cannot be empty." }
            val updates = mapOf<String, Any?>(
                "displayName" to cleanName,
                "phoneNumber" to phoneNumber?.trim()?.take(24).orEmpty(),
                "preferredLanguage" to preferredLanguage,
                "defaultUpiId" to defaultUpiId?.trim()?.take(100).orEmpty(),
                "defaultUpiName" to defaultUpiName?.trim()?.take(100).orEmpty(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroup(
        name: String,
        description: String?,
        upiId: String?,
        upiRecipientName: String?,
        ownerName: String,
        ownerEmail: String?,
        ownerPhone: String?
    ): Result<String> {
        var path = "groups"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(100)
            require(cleanName.isNotEmpty()) { "Group name is required." }

            val groupRef = db.collection("groups").document()
            val groupId = groupRef.id
            path = groupRef.path

            val groupPayload = mapOf(
                "name" to cleanName,
                "description" to description?.trim()?.take(400)?.ifBlank { null },
                "ownerId" to uid,
                "ownerName" to ownerName.ifBlank { "Admin" }.take(100),
                "upiId" to upiId?.trim()?.take(100)?.ifBlank { null },
                "upiRecipientName" to upiRecipientName?.trim()?.take(100)?.ifBlank { null },
                "memberUids" to listOf(uid),
                "adminUids" to listOf(uid),
                "viewerUids" to emptyList<String>(),
                "memberCount" to 1L,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            groupRef.set(groupPayload).await()

            val memberDocId = "${groupId}_${uid}"
            val memberPayload = mapOf(
                "groupId" to groupId,
                "userId" to uid,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "adminUids" to listOf(uid),
                "displayName" to ownerName.ifBlank { "Admin" }.take(100),
                "phoneNumber" to ownerPhone?.trim()?.take(24)?.ifBlank { null },
                "email" to ownerEmail?.trim()?.take(150)?.ifBlank { null },
                "role" to MemberRole.ADMIN.name,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("groupMembers").document(memberDocId).set(memberPayload).await()

            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupUpi(group: Group, upiId: String, recipientName: String): Result<Unit> {
        val path = "groups/${group.id}"
        return try {
            requireUserId()
            db.collection("groups").document(group.id).update(
                mapOf(
                    "upiId" to upiId.trim().take(100),
                    "upiRecipientName" to recipientName.trim().take(100),
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

    /**
     * Allows the admin to add a family/group member directly to the roster (even before they sign in),
     * while syncing member count and writing an audit record.
     */
    suspend fun addManagedMember(
        group: Group,
        displayName: String,
        phoneNumber: String?,
        email: String?,
        role: MemberRole,
        actorName: String
    ): Result<Unit> {
        var path = "groupMembers"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Member name is required." }

            val memberDocRef = db.collection("groupMembers").document()
            path = memberDocRef.path
            val syntheticUid = "member_${memberDocRef.id.take(16)}"

            val payload = mapOf(
                "groupId" to group.id,
                "userId" to syntheticUid,
                "ownerId" to group.ownerId,
                "memberUids" to group.memberUids,
                "adminUids" to group.adminUids,
                "displayName" to cleanName,
                "phoneNumber" to phoneNumber?.trim()?.take(24)?.ifBlank { null },
                "email" to email?.trim()?.take(150)?.ifBlank { null },
                "role" to role.name,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            memberDocRef.set(payload).await()
            db.collection("groups").document(group.id).update(
                mapOf(
                    "memberCount" to (group.memberCount + 1L).coerceAtMost(50L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logAudit(
                groupId = group.id,
                billId = "",
                actorUid = uid,
                actorName = actorName,
                memberUids = group.memberUids,
                adminUids = group.adminUids,
                action = "MEMBER_ADDED",
                summary = "Added $cleanName (${role.name}) to ${group.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMemberStatus(
        group: Group,
        member: GroupMember,
        newStatus: String,
        actorName: String
    ): Result<Unit> {
        val path = "groupMembers/${member.id}"
        return try {
            val uid = requireUserId()
            db.collection("groupMembers").document(member.id).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(
                groupId = group.id,
                billId = "",
                actorUid = uid,
                actorName = actorName,
                memberUids = group.memberUids,
                adminUids = group.adminUids,
                action = if (newStatus == "INACTIVE") "MEMBER_REMOVED" else "MEMBER_ADDED",
                summary = "${if (newStatus == "INACTIVE") "Deactivated" else "Reactivated"} ${member.displayName} in ${group.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeMember(group: Group, member: GroupMember, actorName: String): Result<Unit> {
        val path = "groupMembers/${member.id}"
        return try {
            val uid = requireUserId()
            require(member.userId != group.ownerId) { "Group owner cannot be removed." }
            db.collection("groupMembers").document(member.id).delete().await()

            val updatedMembers = group.memberUids.filter { it != member.userId }
            val updatedAdmins = group.adminUids.filter { it != member.userId }
            val updatedViewers = group.viewerUids.filter { it != member.userId }
            db.collection("groups").document(group.id).update(
                mapOf(
                    "memberUids" to updatedMembers.ifEmpty { listOf(group.ownerId) },
                    "adminUids" to updatedAdmins.ifEmpty { listOf(group.ownerId) },
                    "viewerUids" to updatedViewers,
                    "memberCount" to (group.memberCount - 1L).coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logAudit(
                groupId = group.id,
                billId = "",
                actorUid = uid,
                actorName = actorName,
                memberUids = updatedMembers.ifEmpty { listOf(group.ownerId) },
                adminUids = updatedAdmins.ifEmpty { listOf(group.ownerId) },
                action = "MEMBER_REMOVED",
                summary = "Removed ${member.displayName} from ${group.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createInvitation(
        group: Group,
        role: MemberRole,
        expiryHours: Long,
        maxUses: Long,
        creatorName: String
    ): Result<String> {
        var path = "invitations"
        return try {
            val uid = requireUserId()
            val inviteRole = if (role == MemberRole.VIEWER) "VIEWER" else "MEMBER"
            val code = CurrencyAndSplitUtils.generateInviteCode(8)
            val docRef = db.collection("invitations").document(code)
            path = docRef.path

            val expiresDate = Date(System.currentTimeMillis() + expiryHours * 3600_000L)
            val payload = mapOf(
                "groupId" to group.id,
                "groupName" to group.name.take(100),
                "role" to inviteRole,
                "createdBy" to uid,
                "createdByName" to creatorName.ifBlank { "Admin" }.take(100),
                "status" to "ACTIVE",
                "expiresAt" to Timestamp(expiresDate),
                "maxUses" to maxUses.coerceIn(1L, 50L),
                "usedCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeInvitation(inviteCode: String): Result<Unit> {
        val path = "invitations/$inviteCode"
        return try {
            requireUserId()
            db.collection("invitations").document(inviteCode).update(
                mapOf(
                    "status" to "REVOKED",
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

    /**
     * Redeems an expiring invite code, adding the current authenticated user to the group
     * and creating their GroupMember entry (enforced server-side by firestore.rules).
     */
    suspend fun redeemInvitation(
        rawCode: String,
        displayName: String,
        email: String?,
        phoneNumber: String?
    ): Result<String> {
        val code = rawCode.trim().uppercase()
        var path = "invitations/$code"
        return try {
            val uid = requireUserId()
            require(code.length >= 6 && code.matches(Regex("^[A-Z0-9_\\-]+$"))) {
                "Invalid invite code format."
            }

            val invSnap = db.collection("invitations").document(code).get().await()
            if (!invSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invite code not found."))
            }
            val inv = invSnap.toObject(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("Malformed invitation."))

            if (inv.status != "ACTIVE") {
                return Result.failure(IllegalArgumentException("This invite code is no longer active."))
            }
            val expiresMs = inv.expiresAt?.toDate()?.time ?: 0L
            if (expiresMs <= System.currentTimeMillis()) {
                return Result.failure(IllegalArgumentException("This invite code has expired."))
            }
            if (inv.usedCount >= inv.maxUses) {
                return Result.failure(IllegalArgumentException("This invite code has reached its usage limit."))
            }

            val groupRef = db.collection("groups").document(inv.groupId)
            path = groupRef.path

            val groupUpdates = mutableMapOf<String, Any>(
                "memberUids" to FieldValue.arrayUnion(uid),
                "memberCount" to FieldValue.increment(1L),
                "lastInviteCode" to code,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (inv.role == "VIEWER") {
                groupUpdates["viewerUids"] = FieldValue.arrayUnion(uid)
            }
            groupRef.update(groupUpdates).await()

            val groupSnap = groupRef.get().await()
            val updatedGroup = groupSnap.toObject(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val memberUids = updatedGroup?.memberUids ?: listOf(inv.createdBy, uid)
            val adminUids = updatedGroup?.adminUids ?: listOf(inv.createdBy)

            val memberDocId = "${inv.groupId}_${uid}"
            path = "groupMembers/$memberDocId"
            val memberPayload = mapOf(
                "groupId" to inv.groupId,
                "userId" to uid,
                "ownerId" to (updatedGroup?.ownerId ?: inv.createdBy),
                "memberUids" to memberUids,
                "adminUids" to adminUids,
                "displayName" to displayName.ifBlank { "Member" }.take(100),
                "phoneNumber" to phoneNumber?.trim()?.take(24)?.ifBlank { null },
                "email" to email?.trim()?.take(150)?.ifBlank { null },
                "role" to inv.role,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("groupMembers").document(memberDocId).set(memberPayload).await()

            val newUsedCount = inv.usedCount + 1L
            val newStatus = if (newUsedCount >= inv.maxUses) "USED" else "ACTIVE"
            db.collection("invitations").document(code).update(
                mapOf(
                    "usedCount" to newUsedCount,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logAudit(
                groupId = inv.groupId,
                billId = "",
                actorUid = uid,
                actorName = displayName,
                memberUids = memberUids,
                adminUids = adminUids,
                action = "MEMBER_ADDED",
                summary = "$displayName joined ${inv.groupName} as ${inv.role}"
            )
            Result.success(inv.groupName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun logAudit(
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
            val payload = mapOf(
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
            db.collection("auditLogs").document().set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "auditLogs")
        }
    }
}
