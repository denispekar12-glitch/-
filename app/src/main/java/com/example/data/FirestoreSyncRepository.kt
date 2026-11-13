package com.example.data

import android.content.Context
import com.example.R
import com.example.model.FirestoreAlertEvent
import com.example.model.FirestoreAlertRegion
import com.example.model.FirestoreCommunityMessage
import com.example.model.FirestoreUserProfile
import com.google.firebase.Firebase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Користувач має увійти через Google перед доступом до Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<FirestoreUserProfile?> {
        val path = "$COLLECTION_USERS/$userId"
        return db.collection(COLLECTION_USERS)
            .document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    FirestoreUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.GET, path, userId)
                throw error
            }
    }

    fun observeAlertRegions(): Flow<List<FirestoreAlertRegion>> {
        return db.collection(COLLECTION_ALERT_REGIONS)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreAlertRegion::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, COLLECTION_ALERT_REGIONS, auth.currentUser?.uid)
                throw error
            }
    }

    fun observeAlertEvents(): Flow<List<FirestoreAlertEvent>> {
        return db.collection(COLLECTION_ALERT_EVENTS)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreAlertEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.clientTimestampMs }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, COLLECTION_ALERT_EVENTS, auth.currentUser?.uid)
                throw error
            }
    }

    fun observeCommunityMessages(): Flow<List<FirestoreCommunityMessage>> {
        return db.collection(COLLECTION_COMMUNITY_MESSAGES)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreCommunityMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.clientTimestampMs }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, COLLECTION_COMMUNITY_MESSAGES, auth.currentUser?.uid)
                throw error
            }
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String,
        monitoredRegionIds: List<String>,
        soundVolume: Float,
        vibrationEnabled: Boolean,
        autoPlayVoice: Boolean,
        onlineChannelId: String,
        dsnsAutoSyncEnabled: Boolean,
        selectedTab: Int,
        selectedRegionId: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection(COLLECTION_USERS).document(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updatePayload = mapOf(
                    "displayName" to displayName.take(80),
                    "email" to email.take(160),
                    "monitoredRegionIds" to monitoredRegionIds.take(35),
                    "soundVolume" to soundVolume.toDouble().coerceIn(0.0, 1.0),
                    "vibrationEnabled" to vibrationEnabled,
                    "autoPlayVoice" to autoPlayVoice,
                    "onlineChannelId" to onlineChannelId.ifBlank { AlertRepository.DEFAULT_ONLINE_CHANNEL }.take(64),
                    "dsnsAutoSyncEnabled" to dsnsAutoSyncEnabled,
                    "selectedTab" to selectedTab.coerceIn(0, 4).toLong(),
                    "selectedRegionId" to selectedRegionId.ifBlank { AlertRepository.KRYVE_OZERO_ID }.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(80),
                    "email" to email.take(160),
                    "monitoredRegionIds" to monitoredRegionIds.take(35),
                    "soundVolume" to soundVolume.toDouble().coerceIn(0.0, 1.0),
                    "vibrationEnabled" to vibrationEnabled,
                    "autoPlayVoice" to autoPlayVoice,
                    "onlineChannelId" to onlineChannelId.ifBlank { AlertRepository.DEFAULT_ONLINE_CHANNEL }.take(64),
                    "dsnsAutoSyncEnabled" to dsnsAutoSyncEnabled,
                    "selectedTab" to selectedTab.coerceIn(0, 4).toLong(),
                    "selectedRegionId" to selectedRegionId.ifBlank { AlertRepository.KRYVE_OZERO_ID }.take(64),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishRegionAlert(
        regionId: String,
        regionName: String,
        currentLevel: String,
        activeLevels: List<String>,
        threatDetails: String,
        adminName: String,
        alertStartTime: Long?
    ): Result<Unit> {
        val uid = requireUserId()
        val safeId = regionId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection(COLLECTION_ALERT_REGIONS).document(safeId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mutableMapOf<String, Any>(
                    "regionName" to regionName.take(120),
                    "currentLevel" to currentLevel,
                    "activeLevels" to activeLevels.take(5),
                    "threatDetails" to threatDetails.take(500),
                    "adminName" to adminName.ifBlank { AlertRepository.ADMIN_OWNER_NAME }.take(80),
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (alertStartTime != null && alertStartTime >= 0L) {
                    updateMap["alertStartTime"] = alertStartTime
                } else {
                    updateMap["alertStartTime"] = FieldValue.delete()
                }
                docRef.update(updateMap).await()
            } else {
                val createMap = mutableMapOf<String, Any>(
                    "regionName" to regionName.take(120),
                    "currentLevel" to currentLevel,
                    "activeLevels" to activeLevels.take(5),
                    "threatDetails" to threatDetails.take(500),
                    "adminName" to adminName.ifBlank { AlertRepository.ADMIN_OWNER_NAME }.take(80),
                    "updatedByUid" to uid,
                    "isPublic" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (alertStartTime != null && alertStartTime >= 0L) {
                    createMap["alertStartTime"] = alertStartTime
                }
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishAlertEvent(
        eventId: String = UUID.randomUUID().toString(),
        regionId: String,
        regionName: String,
        level: String,
        activeLevels: List<String>,
        threatDetails: String,
        source: String,
        adminName: String,
        isBroadcastBanner: Boolean,
        isTotalAllClear: Boolean,
        clientTimestampMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = requireUserId()
        val safeId = eventId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection(COLLECTION_ALERT_EVENTS).document(safeId)
        val payload = mapOf(
            "regionId" to regionId.take(64),
            "regionName" to regionName.take(120),
            "level" to level,
            "activeLevels" to activeLevels.ifEmpty { listOf(level) }.take(5),
            "threatDetails" to threatDetails.take(500),
            "source" to source.ifBlank { "Адміністратор Денис" }.take(120),
            "adminName" to adminName.ifBlank { AlertRepository.ADMIN_OWNER_NAME }.take(80),
            "authorUid" to uid,
            "isBroadcastBanner" to isBroadcastBanner,
            "isTotalAllClear" to isTotalAllClear,
            "isPublic" to true,
            "clientTimestampMs" to clientTimestampMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun postCommunityMessage(
        messageId: String = UUID.randomUUID().toString(),
        authorName: String,
        authorRole: String,
        text: String,
        regionTag: String,
        isAdminVerified: Boolean,
        clientTimestampMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = requireUserId()
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection(COLLECTION_COMMUNITY_MESSAGES).document(safeId)
        val payload = mapOf(
            "authorUid" to uid,
            "authorName" to authorName.ifBlank { "Житель громади" }.take(80),
            "authorRole" to authorRole,
            "text" to text.trim().take(1000),
            "regionTag" to regionTag.ifBlank { "Кривоозерська громада" }.take(100),
            "isAdminVerified" to isAdminVerified,
            "isPublic" to true,
            "clientTimestampMs" to clientTimestampMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCommunityMessage(messageId: String): Result<Unit> {
        requireUserId()
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection(COLLECTION_COMMUNITY_MESSAGES).document(safeId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String?
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && expectedUid != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_ALERT_REGIONS = "alert_regions"
        const val COLLECTION_ALERT_EVENTS = "alert_events"
        const val COLLECTION_COMMUNITY_MESSAGES = "community_messages"
    }
}
