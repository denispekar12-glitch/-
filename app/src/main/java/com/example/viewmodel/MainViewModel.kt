package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlertSoundPlayer
import com.example.data.AlertRepository
import com.example.model.AdminBroadcast
import com.example.model.AlertEventLog
import com.example.model.AlertLevel
import com.example.model.DiscussionComment
import com.example.model.RegionItem
import com.example.model.SoundSettings
import com.example.model.UserRole
import com.example.notifications.AlertNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlertRepository(application)
    private val soundPlayer = AlertSoundPlayer(application)
    private val notificationHelper = AlertNotificationHelper(application)

    val regions: StateFlow<List<RegionItem>> = repository.regions
    val events: StateFlow<List<AlertEventLog>> = repository.events
    val comments: StateFlow<List<DiscussionComment>> = repository.comments
    val userRole: StateFlow<UserRole> = repository.userRole
    val soundSettings: StateFlow<SoundSettings> = repository.soundSettings
    val isApiConnected: StateFlow<Boolean> = repository.isApiConnected
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val onlineStatusMessage: StateFlow<String> = repository.onlineStatusMessage
    val lastSyncTimestamp: StateFlow<Long> = repository.lastSyncTimestamp
    val onlineChannelId: StateFlow<String> = repository.onlineChannelId
    val dsnsAutoSyncEnabled: StateFlow<Boolean> = repository.dsnsAutoSyncEnabled
    val authorName: StateFlow<String> = repository.authorName
    val latestAdminBroadcast: StateFlow<AdminBroadcast?> = repository.latestAdminBroadcast

    val isAudioPlaying: StateFlow<Boolean> = soundPlayer.isPlaying
    val currentlyPlayingTitle: StateFlow<String> = soundPlayer.currentlyPlayingTitle

    private val _selectedRegion = MutableStateFlow<RegionItem?>(null)
    val selectedRegion: StateFlow<RegionItem?> = _selectedRegion.asStateFlow()

    private val _selectedTab = MutableStateFlow(repository.savedSelectedTab.value)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    init {
        // Keep selected region restored and always synchronized with live updates
        viewModelScope.launch {
            repository.regions.collect { list ->
                val targetId = _selectedRegion.value?.id ?: repository.savedSelectedRegionId.value
                _selectedRegion.value = list.find { it.id == targetId }
                    ?: list.find { it.id == AlertRepository.KRYVE_OZERO_ID }
                    ?: list.firstOrNull()
            }
        }

        // Listen for alert state changes to play real-time audio (EXACTLY ONCE per track) & trigger instant push notification
        viewModelScope.launch {
            repository.alertStateChanges.collect { change ->
                val settings = repository.soundSettings.value
                // Plays each triggered speech recording sequentially, once each, and stops cleanly
                soundPlayer.playAlertsSequence(change.levelsToPlay, settings)

                // Dispatch instant real-time notification
                notificationHelper.showEmergencyNotification(
                    regionName = change.region.name,
                    levels = change.allActiveLevels,
                    details = change.region.threatDetails,
                    isAdminBroadcast = true
                )
            }
        }
    }

    fun setTab(index: Int) {
        _selectedTab.value = index
        repository.saveSelectedTab(index)
    }

    fun selectRegion(region: RegionItem) {
        _selectedRegion.value = region
        repository.saveSelectedRegionId(region.id)
    }

    fun focusKryveOzero() {
        val ko = regions.value.find { it.id == AlertRepository.KRYVE_OZERO_ID }
        if (ko != null) {
            selectRegion(ko)
        }
    }

    fun addOrPrioritizeKryveOzero() {
        repository.addOrPrioritizeKryveOzero()
        focusKryveOzero()
    }

    fun toggleMonitored(regionId: String) {
        repository.toggleRegionMonitored(regionId)
    }

    fun setAlertLevel(regionId: String, level: AlertLevel, note: String = "") {
        repository.setRegionAlertLevel(regionId, level, note)
    }

    fun setRegionActiveAlerts(regionId: String, levels: Set<AlertLevel>, note: String = "") {
        repository.setRegionActiveAlerts(regionId, levels, note)
    }

    fun toggleAlertTypeForRegion(regionId: String, level: AlertLevel, note: String = "") {
        repository.toggleAlertTypeForRegion(regionId, level, note)
    }

    fun setMultipleRegionsActiveAlerts(regionIds: List<String>, levels: Set<AlertLevel>, note: String = "") {
        repository.setMultipleRegionsActiveAlerts(regionIds, levels, note)
    }

    fun triggerKryveOzeroAlert(level: AlertLevel, note: String = "") {
        repository.triggerKryveOzeroAlert(level, note)
        focusKryveOzero()
    }

    fun triggerKryveOzeroMultipleAlerts(levels: Set<AlertLevel>, note: String = "") {
        repository.triggerKryveOzeroMultipleAlerts(levels, note)
        focusKryveOzero()
    }

    fun triggerAllClearKryveOzero() {
        repository.triggerKryveOzeroAlert(AlertLevel.SAFE, "Відбій повітряної тривоги. Небезпека минула.")
        focusKryveOzero()
    }

    fun triggerTotalAllClear() {
        repository.triggerTotalAllClear()
        focusKryveOzero()
    }

    fun triggerStreetFightsAllClear(regionId: String = AlertRepository.KRYVE_OZERO_ID) {
        repository.triggerStreetFightsAllClear(regionId)
        focusKryveOzero()
    }

    fun postComment(
        text: String,
        authorName: String,
        regionTag: String = "Кривоозерська громада"
    ) {
        repository.postComment(text = text, authorName = authorName, regionTag = regionTag)
    }

    fun saveAuthorName(name: String) {
        repository.saveAuthorName(name)
    }

    fun syncNow() {
        repository.syncNow()
    }

    fun updateOnlineChannelId(channel: String) {
        repository.updateOnlineChannelId(channel)
    }

    fun setDsnsAutoSyncEnabled(enabled: Boolean) {
        repository.setDsnsAutoSyncEnabled(enabled)
    }

    fun dismissAdminBroadcast() {
        repository.dismissAdminBroadcast()
    }

    fun stopAudio() {
        soundPlayer.stopAll()
    }

    fun previewSound(resId: Int, title: String) {
        soundPlayer.previewResource(resId, title, soundSettings.value)
    }

    fun updateSoundSettings(newSettings: SoundSettings) {
        repository.updateSoundSettings(newSettings)
    }

    fun openPinDialog() {
        _showPinDialog.value = true
    }

    fun closePinDialog() {
        _showPinDialog.value = false
    }

    fun verifyAdminPin(pin: String): Boolean {
        val valid = repository.verifyAdminPin(pin)
        if (valid) {
            repository.setUserRole(UserRole.ADMIN)
        }
        return valid
    }

    fun lockToUserRole() {
        repository.setUserRole(UserRole.USER)
    }

    fun updateAdminPin(newPin: String) {
        repository.updateAdminPin(newPin)
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.stopAll()
    }
}
