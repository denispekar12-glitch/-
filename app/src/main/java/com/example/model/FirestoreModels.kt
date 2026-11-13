package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val monitoredRegionIds: List<String> = emptyList(),
    val soundVolume: Double = 1.0,
    val vibrationEnabled: Boolean = true,
    val autoPlayVoice: Boolean = true,
    val onlineChannelId: String = "omastrotryvoga-kryveozero-live-v1",
    val dsnsAutoSyncEnabled: Boolean = false,
    val selectedTab: Long = 0L,
    val selectedRegionId: String = "kryve_ozero",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAlertRegion(
    @DocumentId val id: String = "",
    val regionName: String = "",
    val currentLevel: String = "SAFE",
    val activeLevels: List<String> = emptyList(),
    val threatDetails: String = "",
    val adminName: String = "Денис",
    val updatedByUid: String = "",
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val alertStartTime: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAlertEvent(
    @DocumentId val id: String = "",
    val regionId: String = "",
    val regionName: String = "",
    val level: String = "SAFE",
    val activeLevels: List<String> = emptyList(),
    val threatDetails: String = "",
    val source: String = "",
    val adminName: String = "Денис",
    val authorUid: String = "",
    @get:PropertyName("isBroadcastBanner") @set:PropertyName("isBroadcastBanner")
    var isBroadcastBanner: Boolean = true,
    @get:PropertyName("isTotalAllClear") @set:PropertyName("isTotalAllClear")
    var isTotalAllClear: Boolean = false,
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val clientTimestampMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCommunityMessage(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = "USER",
    val text: String = "",
    val regionTag: String = "Кривоозерська громада",
    @get:PropertyName("isAdminVerified") @set:PropertyName("isAdminVerified")
    var isAdminVerified: Boolean = false,
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val clientTimestampMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
