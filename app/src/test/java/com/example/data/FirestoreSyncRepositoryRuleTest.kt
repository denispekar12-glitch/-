package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertUserProfile_authenticatedOwner_succeedsAndEmitsUpdate() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore, auth)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                displayName = "Аліса",
                email = ALICE_EMAIL,
                monitoredRegionIds = listOf("kryve_ozero", "mykolaiv"),
                soundVolume = 0.85f,
                vibrationEnabled = true,
                autoPlayVoice = true,
                onlineChannelId = AlertRepository.DEFAULT_ONLINE_CHANNEL,
                dsnsAutoSyncEnabled = true,
                selectedTab = 1,
                selectedRegionId = "kryve_ozero"
            )
        }
        assertTrue(saveRes.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("Аліса", profile?.displayName)
        assertEquals(uid, profile?.userId)
    }

    @Test
    fun observeUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreSyncRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertUserProfile(
                displayName = "Аліса",
                email = ALICE_EMAIL,
                monitoredRegionIds = listOf("kryve_ozero"),
                soundVolume = 1.0f,
                vibrationEnabled = true,
                autoPlayVoice = true,
                onlineChannelId = AlertRepository.DEFAULT_ONLINE_CHANNEL,
                dsnsAutoSyncEnabled = true,
                selectedTab = 0,
                selectedRegionId = "kryve_ozero"
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreSyncRepository(firestore, auth)
        var thrown: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeUserProfile(aliceUid).first()
            }
        } catch (t: Throwable) {
            thrown = t
        }
        val firestoreEx = checkNotNull(thrown?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user profile read, got $thrown"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun publishRegionAlert_regularUser_failsWithPermissionDenied() = runBlocking {
        signInTestUser(BOB_EMAIL)
        val repo = FirestoreSyncRepository(firestore, auth)

        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishRegionAlert(
                regionId = "kryve_ozero",
                regionName = "Кривоозерська громада",
                currentLevel = "RED",
                activeLevels = listOf("RED"),
                threatDetails = "Спроба неадмінського запуску",
                adminName = "Bob",
                alertStartTime = System.currentTimeMillis()
            )
        }
        assertTrue(res.isFailure)
        val firestoreEx = checkNotNull(res.exceptionOrNull()?.unwrapFirestoreException())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun postCommunityMessage_regularUser_succeeds_andAdminSpoofFails() = runBlocking {
        signInTestUser(BOB_EMAIL)
        val repo = FirestoreSyncRepository(firestore, auth)
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "_")}"

        val validRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.postCommunityMessage(
                messageId = msgId,
                authorName = "Боб",
                authorRole = "USER",
                text = "У громаді спокійно",
                regionTag = "Кривоозерська громада",
                isAdminVerified = false
            )
        }
        assertTrue(validRes.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCommunityMessages().first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.id == msgId && it.text == "У громаді спокійно" })

        // Attempt to spoof admin badge as regular user must fail
        val spoofRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.postCommunityMessage(
                messageId = "spoof_${UUID.randomUUID().toString().replace("-", "_")}",
                authorName = "Fake Admin",
                authorRole = "ADMIN",
                text = "Підроблене адмін-повідомлення",
                regionTag = "Криве Озеро",
                isAdminVerified = true
            )
        }
        assertTrue(spoofRes.isFailure)
        val firestoreEx = checkNotNull(spoofRes.exceptionOrNull()?.unwrapFirestoreException())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeAlertRegions_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreSyncRepository(firestore, auth)

        var thrown: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAlertRegions().first()
            }
        } catch (t: Throwable) {
            thrown = t
        }
        val firestoreEx = checkNotNull(thrown?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated read, got $thrown"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun publishRegionAlert_verifiedAdmin_createsAndUpdatesSuccessfully() = runBlocking {
        signInVerifiedTestUser(ADMIN_EMAIL)
        val repo = FirestoreSyncRepository(firestore, auth)
        val testRegionId = "test_region_${UUID.randomUUID().toString().replace("-", "_")}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishRegionAlert(
                regionId = testRegionId,
                regionName = "Кривоозерська громада (Тест)",
                currentLevel = "RED",
                activeLevels = listOf("RED"),
                threatDetails = "Повітряна тривога!",
                adminName = "Денис",
                alertStartTime = System.currentTimeMillis()
            )
        }
        assertTrue("Expected admin create to succeed, got ${createRes.exceptionOrNull()}", createRes.isSuccess)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishRegionAlert(
                regionId = testRegionId,
                regionName = "Кривоозерська громада (Тест)",
                currentLevel = "SAFE",
                activeLevels = emptyList(),
                threatDetails = "Відбій загрози!",
                adminName = "Денис",
                alertStartTime = null
            )
        }
        assertTrue("Expected admin update to succeed, got ${updateRes.exceptionOrNull()}", updateRes.isSuccess)
    }

    private companion object {
        const val ADMIN_EMAIL = "denispekar12@gmail.com"
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
