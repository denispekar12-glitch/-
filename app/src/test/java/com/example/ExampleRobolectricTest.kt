package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlertRepository
import com.example.model.AlertLevel
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Омастротривога", appName)
  }

  @Test
  fun `verify Kryve Ozero initial state and priority`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    val regions = repository.regions.value
    val kryveOzero = regions.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertNotNull(kryveOzero)
    assertEquals("Кривоозерська громада (Криве Озеро)", kryveOzero?.name)

    // Verify admin can trigger alert for Kryve Ozero and broadcast updates
    repository.triggerKryveOzeroAlert(AlertLevel.RED, "Повітряна тривога!")
    val updated = repository.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertEquals(AlertLevel.RED, updated?.currentLevel)

    // Verify broadcast is generated in real time
    val broadcast = repository.latestAdminBroadcast.value
    assertNotNull(broadcast)
    assertEquals(AlertLevel.RED, broadcast?.level)
  }

  @Test
  fun `verify civil defense alert level and sound resource`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    repository.triggerKryveOzeroAlert(AlertLevel.CIVIL_DEFENSE, "Важливе сповіщення цивільного захисту!")
    val updated = repository.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertEquals(AlertLevel.CIVIL_DEFENSE, updated?.currentLevel)
    assertEquals(R.raw.alert_civil_defense, AlertLevel.CIVIL_DEFENSE.soundResId)
  }

  @Test
  fun `verify single playback setting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    assertTrue(repository.soundSettings.value.playOnceOnly)
  }

  @Test
  fun `verify admin PIN verification and role switching`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    assertTrue(repository.verifyAdminPin("1234"))
    repository.setUserRole(UserRole.USER)
    assertEquals(UserRole.USER, repository.userRole.value)
  }

  @Test
  fun `verify total all clear resets all regions to safe`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    // Put regions into danger first
    repository.triggerKryveOzeroAlert(AlertLevel.RED)
    repository.setRegionAlertLevel("kyiv_city", AlertLevel.RED)

    // Trigger total all-clear
    repository.triggerTotalAllClear()

    val regions = repository.regions.value
    assertTrue(regions.all { it.currentLevel == AlertLevel.SAFE })
    val broadcast = repository.latestAdminBroadcast.value
    assertNotNull(broadcast)
    assertEquals(AlertLevel.SAFE, broadcast?.level)
    assertTrue(broadcast?.regionName?.contains("ВЕСЬ ВІДБІЙ") == true)
  }

  @Test
  fun `verify street fights all clear`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    repository.triggerKryveOzeroAlert(AlertLevel.STREET_FIGHTS, "Вуличні бої!")
    assertEquals(AlertLevel.STREET_FIGHTS, repository.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }?.currentLevel)

    repository.triggerStreetFightsAllClear()
    val cleared = repository.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertEquals(AlertLevel.SAFE, cleared?.currentLevel)
    assertTrue(cleared?.threatDetails?.contains("Відбій загрози вуличних боїв") == true)
  }

  @Test
  fun `verify community discussion posting and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AlertRepository(context, enableBackgroundSync = false)

    val initialCount = repository.comments.value.size
    repository.postComment(
      text = "Тестове повідомлення громади",
      authorName = "Денис",
      role = UserRole.ADMIN,
      regionTag = "Криве Озеро"
    )

    val updatedComments = repository.comments.value
    assertEquals(initialCount + 1, updatedComments.size)
    val latest = updatedComments.first()
    assertEquals("Тестове повідомлення громади", latest.text)
    assertTrue(latest.isAdminVerified)
    assertTrue(latest.authorName.contains("Денис"))
  }

  @Test
  fun `verify full state persistence across repository restarts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo1 = AlertRepository(context, enableBackgroundSync = false)

    repo1.setRegionAlertLevel("kyiv_city", AlertLevel.RED, "Загроза БПЛА")
    repo1.triggerKryveOzeroAlert(AlertLevel.YELLOW, "Жовтий рівень безпеки")
    repo1.toggleRegionMonitored("odesa")
    repo1.postComment(
      text = "Повідомлення збережено після перезапуску",
      authorName = "Олена",
      role = UserRole.USER,
      regionTag = "Кривоозерська громада"
    )
    repo1.saveAuthorName("Олена")
    repo1.saveSelectedTab(2)
    repo1.saveSelectedRegionId("kyiv_city")
    repo1.updateOnlineChannelId("omastro-test-channel")
    repo1.setDsnsAutoSyncEnabled(false)

    val repo2 = AlertRepository(context, enableBackgroundSync = false)

    val restoredKyiv = repo2.regions.value.find { it.id == "kyiv_city" }
    val restoredKryveOzero = repo2.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    val restoredOdesa = repo2.regions.value.find { it.id == "odesa" }

    assertEquals(AlertLevel.RED, restoredKyiv?.currentLevel)
    assertEquals("Загроза БПЛА", restoredKyiv?.threatDetails)
    assertEquals(AlertLevel.YELLOW, restoredKryveOzero?.currentLevel)
    assertEquals(true, restoredOdesa?.isMonitored)

    assertEquals("Олена", repo2.authorName.value)
    assertEquals(2, repo2.savedSelectedTab.value)
    assertEquals("kyiv_city", repo2.savedSelectedRegionId.value)
    assertEquals("omastro-test-channel", repo2.onlineChannelId.value)
    assertEquals(false, repo2.dsnsAutoSyncEnabled.value)

    assertNotNull(repo2.latestAdminBroadcast.value)
    assertTrue(repo2.comments.value.any { it.text == "Повідомлення збережено після перезапуску" })
    assertTrue(repo2.events.value.any { it.threatDetails == "Загроза БПЛА" })
  }

  @Test
  fun `verify multiple simultaneous alerts such as street fights and civil defense persist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo1 = AlertRepository(context, enableBackgroundSync = false)

    val combined = setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
    repo1.triggerKryveOzeroMultipleAlerts(combined, "Вуличні бої та цивільний захист одночасно")

    val ko1 = repo1.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertNotNull(ko1)
    assertTrue(ko1!!.hasMultipleAlerts)
    assertEquals(2, ko1.effectiveActiveAlerts.size)
    assertTrue(ko1.effectiveActiveAlerts.contains(AlertLevel.STREET_FIGHTS))
    assertTrue(ko1.effectiveActiveAlerts.contains(AlertLevel.CIVIL_DEFENSE))
    assertEquals(2, repo1.latestAdminBroadcast.value?.activeLevels?.size)

    // Verify persistence across repository restart
    val repo2 = AlertRepository(context, enableBackgroundSync = false)
    val koRestored = repo2.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertNotNull(koRestored)
    assertTrue(koRestored!!.effectiveActiveAlerts.contains(AlertLevel.STREET_FIGHTS))
    assertTrue(koRestored.effectiveActiveAlerts.contains(AlertLevel.CIVIL_DEFENSE))

    // Clearing street fights keeps civil defense active
    repo2.triggerStreetFightsAllClear()
    val koAfterStreetClear = repo2.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertEquals(listOf(AlertLevel.CIVIL_DEFENSE), koAfterStreetClear?.effectiveActiveAlerts)
    assertEquals(AlertLevel.CIVIL_DEFENSE, koAfterStreetClear?.currentLevel)
  }

  @Test
  fun `verify multiple alerts across multiple regions simultaneously`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo = AlertRepository(context, enableBackgroundSync = false)

    val multiRegions = listOf(AlertRepository.KRYVE_OZERO_ID, "mykolaiv", "odesa")
    val multiLevels = setOf(AlertLevel.RED, AlertLevel.CIVIL_DEFENSE, AlertLevel.STREET_FIGHTS)
    repo.setMultipleRegionsActiveAlerts(multiRegions, multiLevels)

    multiRegions.forEach { id ->
      val reg = repo.regions.value.find { it.id == id }
      assertNotNull(reg)
      assertEquals(3, reg!!.effectiveActiveAlerts.size)
      assertTrue(reg.effectiveActiveAlerts.containsAll(multiLevels))
    }
  }

  @Test
  fun `verify all regions start SAFE with no automatic alerts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo = AlertRepository(context, enableBackgroundSync = false)

    assertEquals(false, repo.dsnsAutoSyncEnabled.value)
    assertTrue(repo.regions.value.isNotEmpty())
    assertTrue(repo.regions.value.all { it.currentLevel == AlertLevel.SAFE && it.activeAlerts.isEmpty() })
  }

  @Test
  fun `verify max 3 pinned districts limit and setPinnedRegions clamping`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo = AlertRepository(context, enableBackgroundSync = false)

    // Default fresh install has 2 pinned districts: kryve_ozero and mykolaiv
    assertEquals(2, repo.regions.value.count { it.isMonitored })

    // Adding a 3rd succeeds
    assertTrue(repo.toggleRegionMonitored("pervomaisk_raion"))
    assertEquals(3, repo.regions.value.count { it.isMonitored })
    assertEquals(null, repo.pinnedLimitMessage.value)

    // Adding a 4th is rejected and sets warning message
    val fourthAdded = repo.toggleRegionMonitored("odesa")
    assertEquals(false, fourthAdded)
    assertEquals(3, repo.regions.value.count { it.isMonitored })
    assertTrue(repo.pinnedLimitMessage.value?.contains("Максимум 3") == true)

    // setPinnedRegions also enforces max 3
    repo.setPinnedRegions(setOf("kryve_ozero", "pervomaisk_raion", "voznesensk_raion", "bashtanka_raion"))
    assertEquals(3, repo.regions.value.count { it.isMonitored })
  }

  @Test
  fun `verify new alert types and withoutAllClear vs withAllClear persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo1 = AlertRepository(context, enableBackgroundSync = false)

    // Trigger new threat types across districts with withoutAllClear = true
    repo1.setRegionActiveAlerts(
      regionId = "pervomaisk_raion",
      levels = setOf(AlertLevel.MISSILE, AlertLevel.DRONE),
      threatNote = "Ракети та БПЛА на Первомайський район",
      withoutAllClear = true
    )
    repo1.setRegionAlertLevel("voznesensk_raion", AlertLevel.ARTILLERY, "Артобстріл", withoutAllClear = false)
    repo1.setRegionAlertLevel("bashtanka_raion", AlertLevel.CHEMICAL, "Хімічна загроза", withoutAllClear = false)
    repo1.setRegionAlertLevel("kryve_ozero", AlertLevel.CUSTOM_ALERT, "Спецсигнал громади", withoutAllClear = false)
    val customAudioFile = java.io.File(context.filesDir, "custom_alert_audio/missile.audio").apply {
      parentFile?.mkdirs()
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    repo1.setCustomAudioPath(AlertLevel.MISSILE, customAudioFile.absolutePath)

    val pervomaisk1 = repo1.regions.value.find { it.id == "pervomaisk_raion" }
    assertNotNull(pervomaisk1)
    assertEquals(AlertLevel.MISSILE, pervomaisk1?.currentLevel)
    assertTrue(pervomaisk1?.withoutAllClear == true)
    assertTrue(pervomaisk1?.effectiveActiveAlerts?.containsAll(listOf(AlertLevel.MISSILE, AlertLevel.DRONE)) == true)

    // Restart repository and verify everything persists cleanly (simulating next-day resume)
    val repo2 = AlertRepository(context, enableBackgroundSync = false)
    val pervomaisk2 = repo2.regions.value.find { it.id == "pervomaisk_raion" }
    assertEquals(AlertLevel.MISSILE, pervomaisk2?.currentLevel)
    assertTrue(pervomaisk2?.withoutAllClear == true)
    assertTrue(pervomaisk2?.effectiveActiveAlerts?.containsAll(listOf(AlertLevel.MISSILE, AlertLevel.DRONE)) == true)
    assertEquals(
      customAudioFile.absolutePath,
      repo2.customAudioPaths.value[AlertLevel.MISSILE]
    )

    // Clear WITHOUT all-clear sound -> emits NO SAFE audio event and keeps withoutAllClear = true
    runBlocking {
      val deferredAudio = async(Dispatchers.Unconfined) {
        repo2.alertStateChanges.first()
      }
      repo2.clearRegionAlerts("pervomaisk_raion", withAllClearSound = false)
      delay(100)
      assertEquals(false, deferredAudio.isCompleted)
      val pervomaiskSilent = repo2.regions.value.find { it.id == "pervomaisk_raion" }
      assertEquals(AlertLevel.SAFE, pervomaiskSilent?.currentLevel)
      assertEquals(true, pervomaiskSilent?.withoutAllClear)
      assertEquals(true, repo2.latestAdminBroadcast.value?.withoutAllClear)

      // Clear WITH all-clear sound -> emits SAFE audio and clears withoutAllClear
      repo2.clearRegionAlerts("voznesensk_raion", withAllClearSound = true)
      val soundEvent = withTimeout(2000) { deferredAudio.await() }
      val voznesenskCleared = repo2.regions.value.find { it.id == "voznesensk_raion" }
      assertEquals(AlertLevel.SAFE, voznesenskCleared?.currentLevel)
      assertEquals(false, voznesenskCleared?.withoutAllClear)
      assertEquals("voznesensk_raion", soundEvent.region.id)
      assertEquals(false, soundEvent.withoutAllClear)
      assertEquals(listOf(AlertLevel.SAFE), soundEvent.levelsToPlay)
    }
  }

  @Test
  fun `verify artillery chemical radiation voice resources and dedicated per-type clear playback`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo = AlertRepository(context, enableBackgroundSync = false)

    assertEquals(R.raw.alert_artillery, AlertLevel.ARTILLERY.soundResId)
    assertEquals(R.raw.alert_artillery_clear, AlertLevel.ARTILLERY.clearSoundResId)
    assertEquals(R.raw.alert_chemical, AlertLevel.CHEMICAL.soundResId)
    assertEquals(R.raw.alert_chemical_clear, AlertLevel.CHEMICAL.clearSoundResId)
    assertEquals(R.raw.alert_radiation, AlertLevel.RADIATION.soundResId)
    assertEquals(R.raw.alert_radiation_clear, AlertLevel.RADIATION.clearSoundResId)

    repo.setRegionActiveAlerts(
      regionId = AlertRepository.KRYVE_OZERO_ID,
      levels = setOf(AlertLevel.ARTILLERY, AlertLevel.CHEMICAL, AlertLevel.RADIATION)
    )
    val initial = repo.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
    assertEquals(3, initial?.effectiveActiveAlerts?.size)

    runBlocking {
      val artClearDeferred = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.triggerSpecificAllClear(AlertRepository.KRYVE_OZERO_ID, AlertLevel.ARTILLERY, withoutAllClear = false)
      val artEvent = withTimeout(2000) { artClearDeferred.await() }
      assertEquals(listOf(R.raw.alert_artillery_clear), artEvent.explicitRawResIds)
      assertTrue(artEvent.allActiveLevels.containsAll(listOf(AlertLevel.CHEMICAL, AlertLevel.RADIATION)))
      assertEquals(false, artEvent.allActiveLevels.contains(AlertLevel.ARTILLERY))

      val chemClearDeferred = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.triggerSpecificAllClear(AlertRepository.KRYVE_OZERO_ID, AlertLevel.CHEMICAL, withoutAllClear = false)
      val chemEvent = withTimeout(2000) { chemClearDeferred.await() }
      assertEquals(listOf(R.raw.alert_chemical_clear), chemEvent.explicitRawResIds)
      assertEquals(listOf(AlertLevel.RADIATION), chemEvent.allActiveLevels)

      val radClearDeferred = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.triggerSpecificAllClear(AlertRepository.KRYVE_OZERO_ID, AlertLevel.RADIATION, withoutAllClear = false)
      val radEvent = withTimeout(2000) { radClearDeferred.await() }
      assertEquals(listOf(R.raw.alert_radiation_clear), radEvent.explicitRawResIds)
      assertEquals(AlertLevel.SAFE, repo.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }?.currentLevel)
    }
  }

  @Test
  fun `verify clicking cross on civil defense plays civil defense all clear voice`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo = AlertRepository(context, enableBackgroundSync = false)

    assertEquals(R.raw.alert_civil_defense_clear, AlertLevel.CIVIL_DEFENSE.clearSoundResId)

    // Case 1: Civil defense active alongside RED alert, then user clicks cross (×) on Civil Defense
    repo.setRegionActiveAlerts(
      regionId = AlertRepository.KRYVE_OZERO_ID,
      levels = setOf(AlertLevel.RED, AlertLevel.CIVIL_DEFENSE)
    )

    runBlocking {
      val clearWhileRedActive = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.toggleAlertTypeForRegion(
        regionId = AlertRepository.KRYVE_OZERO_ID,
        level = AlertLevel.CIVIL_DEFENSE,
        withoutAllClear = false
      )
      val ev1 = withTimeout(2000) { clearWhileRedActive.await() }
      assertEquals(listOf(R.raw.alert_civil_defense_clear), ev1.explicitRawResIds)
      assertEquals(listOf(AlertLevel.RED), ev1.allActiveLevels)
      assertTrue(ev1.region.threatDetails.contains("Відбій цивільного захисту"))

      // Case 2: Only Civil Defense active, then user clicks cross (×) on Civil Defense
      val addBackDeferred = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.setRegionAlertLevel(AlertRepository.KRYVE_OZERO_ID, AlertLevel.CIVIL_DEFENSE)
      withTimeout(2000) { addBackDeferred.await() }

      val soloClearDeferred = async(Dispatchers.Unconfined) { repo.alertStateChanges.first() }
      repo.toggleAlertTypeForRegion(
        regionId = AlertRepository.KRYVE_OZERO_ID,
        level = AlertLevel.CIVIL_DEFENSE,
        withoutAllClear = false
      )
      val ev2 = withTimeout(2000) { soloClearDeferred.await() }
      assertEquals(listOf(R.raw.alert_civil_defense_clear), ev2.explicitRawResIds)
      assertEquals(AlertLevel.SAFE, repo.regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }?.currentLevel)
      assertTrue(ev2.region.threatDetails.contains("Відбій цивільного захисту"))
    }
  }

  @Test
  fun `verify all Ukraine alert activates all districts persists and emits audio once`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val repo1 = AlertRepository(context, enableBackgroundSync = false)

    runBlocking {
      var audioEventsCount = 0
      val firstAudio = async(Dispatchers.Unconfined) {
        val ev = repo1.alertStateChanges.first()
        audioEventsCount++
        ev
      }

      repo1.triggerAllUkraineAlert(
        level = AlertLevel.DRONE,
        threatNote = "Масована атака БПЛА по всій території України!"
      )

      val ev = withTimeout(2000) { firstAudio.await() }
      delay(80)
      assertEquals(1, audioEventsCount)
      assertEquals(listOf(AlertLevel.DRONE), ev.levelsToPlay)
      assertTrue(repo1.regions.value.all { it.currentLevel == AlertLevel.DRONE })
      assertEquals("Вся територія України (ВСЯ УКРАЇНА)", repo1.latestAdminBroadcast.value?.regionName)

      // Restart repository and verify persistence across next-day resume
      val repo2 = AlertRepository(context, enableBackgroundSync = false)
      assertTrue(repo2.regions.value.all { it.currentLevel == AlertLevel.DRONE })
      assertEquals("Вся територія України (ВСЯ УКРАЇНА)", repo2.latestAdminBroadcast.value?.regionName)
    }
  }

  @Test
  fun `verify both primary and second admin emails are recognized`() {
    assertTrue(AlertRepository.isAdminEmail("denispekar12@gmail.com"))
    assertTrue(AlertRepository.isAdminEmail("semenenkom342@gmail.com"))
    assertTrue(AlertRepository.isAdminEmail("  SEMENENKOM342@GMAIL.COM "))
    assertEquals(false, AlertRepository.isAdminEmail("regular.user@gmail.com"))
    assertEquals(false, AlertRepository.isAdminEmail(null))
  }
}
