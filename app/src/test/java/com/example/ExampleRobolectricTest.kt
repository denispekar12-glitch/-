package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlertRepository
import com.example.model.AlertLevel
import com.example.model.UserRole
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
}
