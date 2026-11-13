package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AdminBroadcast
import com.example.model.AlertChangeEvent
import com.example.model.AlertEventLog
import com.example.model.AlertLevel
import com.example.model.DiscussionComment
import com.example.model.RegionItem
import com.example.model.SoundSettings
import com.example.model.UserRole
import com.example.network.OfficialOblastStatus
import com.example.network.OnlineAlertSyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AlertRepository(
    context: Context,
    private val enableBackgroundSync: Boolean = true
) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("omastro_trivoga_prefs", Context.MODE_PRIVATE)
    private val syncClient = OnlineAlertSyncClient(appContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val KRYVE_OZERO_ID = "kryve_ozero"
        const val PREF_PIN = "admin_pin"
        const val PREF_ROLE = "user_role"
        const val PREF_MONITORED = "monitored_region_ids"
        const val PREF_REGIONS_STATE = "regions_full_state_json"
        const val PREF_EVENTS_LOG = "events_log_json"
        const val PREF_LATEST_BROADCAST = "latest_broadcast_json"
        const val PREF_VOLUME = "sound_volume"
        const val PREF_VIBRATION = "sound_vibration"
        const val PREF_AUTO_VOICE = "sound_auto_voice"
        const val PREF_COMMENTS = "community_comments"
        const val PREF_AUTHOR_NAME = "discussion_author_name"
        const val PREF_SELECTED_TAB = "selected_tab_index"
        const val PREF_SELECTED_REGION_ID = "selected_region_id"
        const val PREF_ONLINE_CHANNEL = "online_sync_channel_id"
        const val PREF_DSNS_AUTO_SYNC = "dsns_auto_sync_enabled"
        const val PREF_LAST_SYNC_TS = "last_sync_timestamp"
        const val PREF_DEVICE_ID = "device_unique_id"
        const val PREF_PROCESSED_IDS = "processed_remote_ids"
        const val DEFAULT_PIN = "1234"
        const val DEFAULT_ONLINE_CHANNEL = "omastrotryvoga-kryveozero-live-v1"
        const val ADMIN_OWNER_EMAIL = "denispekar12@gmail.com"
        const val ADMIN_OWNER_NAME = "Денис"
        private const val ADMIN_OVERRIDE_GRACE_MS = 30 * 60 * 1000L
    }

    private val deviceId: String by lazy {
        val existing = prefs.getString(PREF_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, generated).apply()
            generated
        }
    }

    // Tracks recent admin manual overrides so DSNS background sync doesn't immediately revert them
    private val manualOverrideTimestamps = ConcurrentHashMap<String, Long>()
    private val processedRemoteIds = mutableSetOf<String>()

    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    private val _events = MutableStateFlow<List<AlertEventLog>>(emptyList())
    val events: StateFlow<List<AlertEventLog>> = _events.asStateFlow()

    private val _comments = MutableStateFlow<List<DiscussionComment>>(emptyList())
    val comments: StateFlow<List<DiscussionComment>> = _comments.asStateFlow()

    // Sole Administrator is Denis (denispekar12@gmail.com). Others are restricted users.
    private val _userRole = MutableStateFlow(UserRole.ADMIN)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _soundSettings = MutableStateFlow(SoundSettings())
    val soundSettings: StateFlow<SoundSettings> = _soundSettings.asStateFlow()

    private val _isApiConnected = MutableStateFlow(syncClient.isNetworkAvailable())
    val isApiConnected: StateFlow<Boolean> = _isApiConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _onlineStatusMessage = MutableStateFlow(
        if (syncClient.isNetworkAvailable()) "Онлайн • Підключення до ефіру..." else "Офлайн • Збережено локально"
    )
    val onlineStatusMessage: StateFlow<String> = _onlineStatusMessage.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong(PREF_LAST_SYNC_TS, 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _onlineChannelId = MutableStateFlow(DEFAULT_ONLINE_CHANNEL)
    val onlineChannelId: StateFlow<String> = _onlineChannelId.asStateFlow()

    private val _dsnsAutoSyncEnabled = MutableStateFlow(true)
    val dsnsAutoSyncEnabled: StateFlow<Boolean> = _dsnsAutoSyncEnabled.asStateFlow()

    private val _authorName = MutableStateFlow("")
    val authorName: StateFlow<String> = _authorName.asStateFlow()

    private val _savedSelectedTab = MutableStateFlow(0)
    val savedSelectedTab: StateFlow<Int> = _savedSelectedTab.asStateFlow()

    private val _savedSelectedRegionId = MutableStateFlow(KRYVE_OZERO_ID)
    val savedSelectedRegionId: StateFlow<String> = _savedSelectedRegionId.asStateFlow()

    // Real-time broadcast pushed when administrator taps an action
    private val _latestAdminBroadcast = MutableStateFlow<AdminBroadcast?>(null)
    val latestAdminBroadcast: StateFlow<AdminBroadcast?> = _latestAdminBroadcast.asStateFlow()

    // Triggered whenever an alert changes for active audio/notification handling
    private val _alertStateChanges = MutableSharedFlow<AlertChangeEvent>(extraBufferCapacity = 10)
    val alertStateChanges: SharedFlow<AlertChangeEvent> = _alertStateChanges.asSharedFlow()

    init {
        loadSettings()
        initInitialRegions()
        loadPersistedEvents()
        loadPersistedBroadcast()
        loadComments()
        if (enableBackgroundSync) {
            startPeriodicSync()
        }
    }

    private fun loadSettings() {
        val roleStr = prefs.getString(PREF_ROLE, UserRole.ADMIN.name) ?: UserRole.ADMIN.name
        _userRole.value = try {
            UserRole.valueOf(roleStr)
        } catch (_: Exception) {
            UserRole.ADMIN
        }

        val vol = prefs.getFloat(PREF_VOLUME, 1.0f)
        val vib = prefs.getBoolean(PREF_VIBRATION, true)
        val autoVoice = prefs.getBoolean(PREF_AUTO_VOICE, true)

        _soundSettings.value = SoundSettings(
            volume = vol,
            vibrationEnabled = vib,
            playOnceOnly = true, // Play once cleanly without looping or repeating
            autoPlayVoice = autoVoice
        )

        _onlineChannelId.value = prefs.getString(PREF_ONLINE_CHANNEL, DEFAULT_ONLINE_CHANNEL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_ONLINE_CHANNEL
        _dsnsAutoSyncEnabled.value = prefs.getBoolean(PREF_DSNS_AUTO_SYNC, true)
        _authorName.value = prefs.getString(PREF_AUTHOR_NAME, "") ?: ""
        _savedSelectedTab.value = prefs.getInt(PREF_SELECTED_TAB, 0).coerceIn(0, 4)
        _savedSelectedRegionId.value = prefs.getString(PREF_SELECTED_REGION_ID, KRYVE_OZERO_ID) ?: KRYVE_OZERO_ID
        _lastSyncTimestamp.value = prefs.getLong(PREF_LAST_SYNC_TS, 0L)

        prefs.getStringSet(PREF_PROCESSED_IDS, emptySet())?.let {
            processedRemoteIds.addAll(it)
        }
    }

    private fun initInitialRegions() {
        val monitoredIds = prefs.getStringSet(
            PREF_MONITORED,
            setOf(KRYVE_OZERO_ID, "mykolaiv", "kyiv_city")
        ) ?: setOf(KRYVE_OZERO_ID, "mykolaiv")

        val initial = listOf(
            RegionItem(
                id = KRYVE_OZERO_ID,
                name = "Кривоозерська громада (Криве Озеро)",
                oblast = "Миколаївська область",
                isHromada = true,
                isMonitored = monitoredIds.contains(KRYVE_OZERO_ID),
                currentLevel = AlertLevel.SAFE,
                threatDetails = "Рідна громада під постійним моніторингом",
                mapNormX = 0.47f,
                mapNormY = 0.65f
            ),
            RegionItem(
                id = "mykolaiv",
                name = "Миколаївська область",
                oblast = "Миколаївська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("mykolaiv"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.52f,
                mapNormY = 0.70f
            ),
            RegionItem(
                id = "kyiv_city",
                name = "м. Київ",
                oblast = "м. Київ",
                isHromada = false,
                isMonitored = monitoredIds.contains("kyiv_city"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.48f,
                mapNormY = 0.35f
            ),
            RegionItem(
                id = "kyiv_obl",
                name = "Київська область",
                oblast = "Київська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("kyiv_obl"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.48f,
                mapNormY = 0.31f
            ),
            RegionItem(
                id = "odesa",
                name = "Одеська область",
                oblast = "Одеська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("odesa"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.40f,
                mapNormY = 0.75f
            ),
            RegionItem(
                id = "kharkiv",
                name = "Харківська область",
                oblast = "Харківська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("kharkiv"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 15 * 60 * 1000,
                threatDetails = "Загроза ударних БПЛА та КАБ",
                mapNormX = 0.75f,
                mapNormY = 0.38f
            ),
            RegionItem(
                id = "dnipro",
                name = "Дніпропетровська область",
                oblast = "Дніпропетровська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("dnipro"),
                currentLevel = AlertLevel.YELLOW,
                alertStartTime = System.currentTimeMillis() - 8 * 60 * 1000,
                threatDetails = "Активність тактичної авіації",
                mapNormX = 0.64f,
                mapNormY = 0.55f
            ),
            RegionItem(
                id = "zaporizhzhia",
                name = "Запорізька область",
                oblast = "Запорізька область",
                isHromada = false,
                isMonitored = monitoredIds.contains("zaporizhzhia"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 22 * 60 * 1000,
                threatDetails = "Артилерійський обстріл та балістика",
                mapNormX = 0.68f,
                mapNormY = 0.68f
            ),
            RegionItem(
                id = "kherson",
                name = "Херсонська область",
                oblast = "Херсонська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("kherson"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 35 * 60 * 1000,
                threatDetails = "Загроза обстрілів прибережних зон",
                mapNormX = 0.58f,
                mapNormY = 0.78f
            ),
            RegionItem(
                id = "vinnytsia",
                name = "Вінницька область",
                oblast = "Вінницька область",
                isHromada = false,
                isMonitored = monitoredIds.contains("vinnytsia"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.38f,
                mapNormY = 0.50f
            ),
            RegionItem(
                id = "cherkasy",
                name = "Черкаська область",
                oblast = "Черкаська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("cherkasy"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.50f,
                mapNormY = 0.46f
            ),
            RegionItem(
                id = "poltava",
                name = "Полтавська область",
                oblast = "Полтавська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("poltava"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.62f,
                mapNormY = 0.42f
            ),
            RegionItem(
                id = "sumy",
                name = "Сумська область",
                oblast = "Сумська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("sumy"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 40 * 60 * 1000,
                threatDetails = "КАБ по прикордонних громадах",
                mapNormX = 0.66f,
                mapNormY = 0.26f
            ),
            RegionItem(
                id = "chernihiv",
                name = "Чернігівська область",
                oblast = "Чернігівська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("chernihiv"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.52f,
                mapNormY = 0.22f
            ),
            RegionItem(
                id = "zhytomyr",
                name = "Житомирська область",
                oblast = "Житомирська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("zhytomyr"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.38f,
                mapNormY = 0.34f
            ),
            RegionItem(
                id = "rivne",
                name = "Рівненська область",
                oblast = "Рівненська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("rivne"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.28f,
                mapNormY = 0.30f
            ),
            RegionItem(
                id = "volyn",
                name = "Волинська область",
                oblast = "Волинська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("volyn"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.18f,
                mapNormY = 0.28f
            ),
            RegionItem(
                id = "lviv",
                name = "Львівська область",
                oblast = "Львівська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("lviv"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.16f,
                mapNormY = 0.42f
            ),
            RegionItem(
                id = "khmelnytskyi",
                name = "Хмельницька область",
                oblast = "Хмельницька область",
                isHromada = false,
                isMonitored = monitoredIds.contains("khmelnytskyi"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.30f,
                mapNormY = 0.46f
            ),
            RegionItem(
                id = "ternopil",
                name = "Тернопільська область",
                oblast = "Тернопільська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("ternopil"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.24f,
                mapNormY = 0.48f
            ),
            RegionItem(
                id = "ivano_frankivsk",
                name = "Івано-Франківська область",
                oblast = "Івано-Франківська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("ivano_frankivsk"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.19f,
                mapNormY = 0.56f
            ),
            RegionItem(
                id = "zakarpattia",
                name = "Закарпатська область",
                oblast = "Закарпатська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("zakarpattia"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.12f,
                mapNormY = 0.58f
            ),
            RegionItem(
                id = "chernivtsi",
                name = "Чернівецька область",
                oblast = "Чернівецька область",
                isHromada = false,
                isMonitored = monitoredIds.contains("chernivtsi"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.28f,
                mapNormY = 0.58f
            ),
            RegionItem(
                id = "kirovohrad",
                name = "Кіровоградська область",
                oblast = "Кіровоградська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("kirovohrad"),
                currentLevel = AlertLevel.SAFE,
                mapNormX = 0.54f,
                mapNormY = 0.54f
            ),
            RegionItem(
                id = "donetsk",
                name = "Донецька область",
                oblast = "Донецька область",
                isHromada = false,
                isMonitored = monitoredIds.contains("donetsk"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 120 * 60 * 1000,
                threatDetails = "Постійна загроза бойових дій та ракет",
                mapNormX = 0.82f,
                mapNormY = 0.58f
            ),
            RegionItem(
                id = "luhansk",
                name = "Луганська область",
                oblast = "Луганська область",
                isHromada = false,
                isMonitored = monitoredIds.contains("luhansk"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 300 * 60 * 1000,
                threatDetails = "Тривала повітряна тривога",
                mapNormX = 0.90f,
                mapNormY = 0.44f
            ),
            RegionItem(
                id = "crimea",
                name = "АР Крим",
                oblast = "АР Крим",
                isHromada = false,
                isMonitored = monitoredIds.contains("crimea"),
                currentLevel = AlertLevel.RED,
                alertStartTime = System.currentTimeMillis() - 300 * 60 * 1000,
                threatDetails = "Тривога діє постійно",
                mapNormX = 0.62f,
                mapNormY = 0.92f
            )
        )

        val savedStates = loadPersistedRegionsState()
        _regions.value = initial.map { base ->
            val saved = savedStates[base.id]
            if (saved != null) {
                if (saved.manualOverrideAt > 0L) {
                    manualOverrideTimestamps[base.id] = saved.manualOverrideAt
                }
                val restoredSet = saved.activeAlerts.filter { it.isDanger }.toSet().ifEmpty {
                    if (saved.level.isDanger) setOf(saved.level) else emptySet()
                }
                val primary = AlertLevel.primaryOf(restoredSet)
                base.copy(
                    isMonitored = saved.isMonitored,
                    currentLevel = primary,
                    activeAlerts = restoredSet,
                    alertStartTime = saved.alertStartTime,
                    threatDetails = saved.threatDetails.ifBlank { base.threatDetails }
                )
            } else {
                base
            }
        }
    }

    private data class SavedRegionState(
        val isMonitored: Boolean,
        val level: AlertLevel,
        val activeAlerts: Set<AlertLevel>,
        val alertStartTime: Long?,
        val threatDetails: String,
        val manualOverrideAt: Long
    )

    private fun loadPersistedRegionsState(): Map<String, SavedRegionState> {
        val raw = prefs.getString(PREF_REGIONS_STATE, null) ?: return emptyMap()
        return try {
            val array = JSONArray(raw)
            val map = mutableMapOf<String, SavedRegionState>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                val level = try {
                    AlertLevel.valueOf(obj.optString("level", AlertLevel.SAFE.name))
                } catch (_: Exception) {
                    AlertLevel.SAFE
                }
                val parsedActive = parseAlertLevelsJsonArray(obj.optJSONArray("activeLevels"))
                    .filter { it.isDanger }
                    .toSet()
                val startTime = if (obj.has("alertStartTime") && !obj.isNull("alertStartTime")) {
                    obj.optLong("alertStartTime")
                } else {
                    null
                }
                map[id] = SavedRegionState(
                    isMonitored = obj.optBoolean("isMonitored", false),
                    level = level,
                    activeAlerts = parsedActive.ifEmpty {
                        if (level.isDanger) setOf(level) else emptySet()
                    },
                    alertStartTime = startTime,
                    threatDetails = obj.optString("threatDetails", ""),
                    manualOverrideAt = obj.optLong("manualOverrideAt", 0L)
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseAlertLevelsJsonArray(array: JSONArray?): List<AlertLevel> {
        if (array == null) return emptyList()
        val list = mutableListOf<AlertLevel>()
        for (i in 0 until array.length()) {
            val name = array.optString(i, "")
            if (name.isNotBlank()) {
                try {
                    list.add(AlertLevel.valueOf(name))
                } catch (_: Exception) {}
            }
        }
        return list
    }

    private fun saveRegionsState() {
        try {
            val array = JSONArray()
            val monitoredIds = mutableSetOf<String>()
            _regions.value.forEach { region ->
                if (region.isMonitored) monitoredIds.add(region.id)
                val activeArray = JSONArray().apply {
                    region.effectiveActiveAlerts.forEach { put(it.name) }
                }
                val obj = JSONObject().apply {
                    put("id", region.id)
                    put("isMonitored", region.isMonitored)
                    put("level", region.currentLevel.name)
                    put("activeLevels", activeArray)
                    if (region.alertStartTime != null) {
                        put("alertStartTime", region.alertStartTime)
                    } else {
                        put("alertStartTime", JSONObject.NULL)
                    }
                    put("threatDetails", region.threatDetails)
                    put("manualOverrideAt", manualOverrideTimestamps[region.id] ?: 0L)
                }
                array.put(obj)
            }
            prefs.edit()
                .putStringSet(PREF_MONITORED, monitoredIds)
                .putString(PREF_REGIONS_STATE, array.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadPersistedEvents() {
        val raw = prefs.getString(PREF_EVENTS_LOG, null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<AlertEventLog>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val level = try {
                        AlertLevel.valueOf(obj.optString("level", AlertLevel.SAFE.name))
                    } catch (_: Exception) {
                        AlertLevel.SAFE
                    }
                    val parsedActive = parseAlertLevelsJsonArray(obj.optJSONArray("activeLevels"))
                        .ifEmpty { listOf(level) }
                    list.add(
                        AlertEventLog(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            regionName = obj.optString("regionName", ""),
                            level = level,
                            activeLevels = parsedActive,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            threatDetails = obj.optString("threatDetails", ""),
                            source = obj.optString("source", "ДСНС / Адміністратор")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _events.value = list
                    return
                }
            } catch (_: Exception) {}
        }

        val initialEvents = listOf(
            AlertEventLog(
                id = "initial_ko_event",
                regionName = "Кривоозерська громада",
                level = AlertLevel.SAFE,
                activeLevels = listOf(AlertLevel.SAFE),
                timestamp = System.currentTimeMillis() - 10 * 60 * 1000,
                threatDetails = "Офіційне сповіщення: ситуація спокійна",
                source = "Адміністратор Денис"
            )
        )
        _events.value = initialEvents
        saveEvents(initialEvents)
    }

    private fun saveEvents(list: List<AlertEventLog>) {
        try {
            val array = JSONArray()
            list.take(50).forEach { event ->
                val activeArray = JSONArray().apply {
                    event.activeLevels.forEach { put(it.name) }
                }
                val obj = JSONObject().apply {
                    put("id", event.id)
                    put("regionName", event.regionName)
                    put("level", event.level.name)
                    put("activeLevels", activeArray)
                    put("timestamp", event.timestamp)
                    put("threatDetails", event.threatDetails)
                    put("source", event.source)
                }
                array.put(obj)
            }
            prefs.edit().putString(PREF_EVENTS_LOG, array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadPersistedBroadcast() {
        val raw = prefs.getString(PREF_LATEST_BROADCAST, null)
        if (raw.isNullOrBlank()) {
            _latestAdminBroadcast.value = null
            return
        }
        try {
            val obj = JSONObject(raw)
            val level = try {
                AlertLevel.valueOf(obj.optString("level", AlertLevel.SAFE.name))
            } catch (_: Exception) {
                AlertLevel.SAFE
            }
            val parsedActive = parseAlertLevelsJsonArray(obj.optJSONArray("activeLevels"))
                .ifEmpty { listOf(level) }
            _latestAdminBroadcast.value = AdminBroadcast(
                id = obj.optString("id", UUID.randomUUID().toString()),
                regionName = obj.optString("regionName", ""),
                level = level,
                activeLevels = parsedActive,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                note = obj.optString("note", ""),
                adminName = obj.optString("adminName", ADMIN_OWNER_NAME)
            )
        } catch (_: Exception) {
            _latestAdminBroadcast.value = null
        }
    }

    private fun saveLatestBroadcast(broadcast: AdminBroadcast?) {
        if (broadcast == null) {
            prefs.edit().remove(PREF_LATEST_BROADCAST).apply()
            return
        }
        try {
            val activeArray = JSONArray().apply {
                broadcast.activeLevels.forEach { put(it.name) }
            }
            val obj = JSONObject().apply {
                put("id", broadcast.id)
                put("regionName", broadcast.regionName)
                put("level", broadcast.level.name)
                put("activeLevels", activeArray)
                put("timestamp", broadcast.timestamp)
                put("note", broadcast.note)
                put("adminName", broadcast.adminName)
            }
            prefs.edit().putString(PREF_LATEST_BROADCAST, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addOrPrioritizeKryveOzero() {
        _regions.update { current ->
            current.map {
                if (it.id == KRYVE_OZERO_ID) it.copy(isMonitored = true) else it
            }
        }
        saveRegionsState()
    }

    fun toggleRegionMonitored(regionId: String) {
        _regions.update { current ->
            current.map {
                if (it.id == regionId) it.copy(isMonitored = !it.isMonitored) else it
            }
        }
        saveRegionsState()
    }

    // Admin mutation: single alert level (replaces active set or clears to SAFE)
    fun setRegionAlertLevel(regionId: String, newLevel: AlertLevel, threatNote: String = "") {
        val targetLevels = if (newLevel.isDanger) setOf(newLevel) else emptySet()
        setRegionActiveAlerts(regionId, targetLevels, threatNote)
    }

    // Admin mutation: set MULTIPLE simultaneous alerts on one region (e.g. Street Fights + Civil Defense)
    fun setRegionActiveAlerts(
        regionId: String,
        levels: Set<AlertLevel>,
        threatNote: String = "",
        audioOverride: List<AlertLevel>? = null
    ) {
        val now = System.currentTimeMillis()
        manualOverrideTimestamps[regionId] = now
        val dangerSet = levels.filter { it.isDanger }.toSet()
        val updatedItem = applyRegionMultiAlertMutation(
            regionId = regionId,
            activeDangerLevels = dangerSet,
            threatNote = threatNote,
            source = "Адміністратор $ADMIN_OWNER_NAME",
            timestamp = now,
            emitBroadcastBanner = true,
            emitAudioAndNotification = true,
            audioOverride = audioOverride
        )

        updatedItem?.let { reg ->
            val eventId = UUID.randomUUID().toString()
            markRemoteIdProcessed(eventId)
            val activeNames = JSONArray().apply {
                reg.effectiveActiveAlerts.forEach { put(it.name) }
            }
            val envelope = JSONObject().apply {
                put("type", "REGION_ALERT")
                put("eventId", eventId)
                put("senderDeviceId", deviceId)
                put("regionId", reg.id)
                put("regionName", reg.name)
                put("level", reg.currentLevel.name)
                put("activeLevels", activeNames)
                put("note", reg.threatDetails)
                put("adminName", ADMIN_OWNER_NAME)
                put("timestamp", now)
            }
            val titleText = if (reg.hasMultipleAlerts) {
                "🚨 Кілька тривог (${reg.effectiveActiveAlerts.size}) — ${reg.name}"
            } else {
                "${reg.currentLevel.title} — ${reg.name}"
            }
            publishEnvelopeOnline(
                title = titleText,
                envelope = envelope,
                priority = if (reg.currentLevel.isDanger) 5 else 3
            )
        }
    }

    // Toggle a single alert type ON or OFF in a region while preserving all other active alerts
    fun toggleAlertTypeForRegion(regionId: String, level: AlertLevel, threatNote: String = "") {
        if (!level.isDanger) {
            setRegionActiveAlerts(regionId, emptySet(), threatNote)
            return
        }
        val currentRegion = _regions.value.find { it.id == regionId } ?: return
        val existing = currentRegion.effectiveActiveAlerts.toMutableSet()
        val wasAdded: Boolean
        if (existing.contains(level)) {
            existing.remove(level)
            wasAdded = false
        } else {
            existing.add(level)
            wasAdded = true
        }
        val audioList = when {
            wasAdded -> listOf(level)
            existing.isEmpty() -> listOf(AlertLevel.SAFE)
            else -> emptyList()
        }
        setRegionActiveAlerts(regionId, existing, threatNote, audioOverride = audioList)
    }

    // Apply multiple alert levels across multiple regions at once
    fun setMultipleRegionsActiveAlerts(
        regionIds: List<String>,
        levels: Set<AlertLevel>,
        threatNote: String = ""
    ) {
        val uniqueIds = regionIds.distinct().filter { id -> _regions.value.any { it.id == id } }
        if (uniqueIds.isEmpty()) return
        uniqueIds.forEachIndexed { index, id ->
            val now = System.currentTimeMillis()
            manualOverrideTimestamps[id] = now
            val dangerSet = levels.filter { it.isDanger }.toSet()
            val updatedItem = applyRegionMultiAlertMutation(
                regionId = id,
                activeDangerLevels = dangerSet,
                threatNote = threatNote,
                source = "Адміністратор $ADMIN_OWNER_NAME",
                timestamp = now,
                emitBroadcastBanner = (index == uniqueIds.lastIndex),
                // Play audio only once for the batch instead of repeating per region
                emitAudioAndNotification = (index == 0)
            )
            updatedItem?.let { reg ->
                val eventId = UUID.randomUUID().toString()
                markRemoteIdProcessed(eventId)
                val activeNames = JSONArray().apply {
                    reg.effectiveActiveAlerts.forEach { put(it.name) }
                }
                val envelope = JSONObject().apply {
                    put("type", "REGION_ALERT")
                    put("eventId", eventId)
                    put("senderDeviceId", deviceId)
                    put("regionId", reg.id)
                    put("regionName", reg.name)
                    put("level", reg.currentLevel.name)
                    put("activeLevels", activeNames)
                    put("note", reg.threatDetails)
                    put("adminName", ADMIN_OWNER_NAME)
                    put("timestamp", now)
                }
                publishEnvelopeOnline(
                    title = "${reg.currentLevel.title} — ${reg.name}",
                    envelope = envelope,
                    priority = if (reg.currentLevel.isDanger) 5 else 3
                )
            }
        }
    }

    private fun applyRegionMultiAlertMutation(
        regionId: String,
        activeDangerLevels: Set<AlertLevel>,
        threatNote: String,
        source: String,
        timestamp: Long,
        emitBroadcastBanner: Boolean,
        emitAudioAndNotification: Boolean,
        audioOverride: List<AlertLevel>? = null
    ): RegionItem? {
        val cleanDangerSet = activeDangerLevels.filter { it.isDanger }.toSet()
        val orderedActive = cleanDangerSet.sortedByDescending { it.priorityOrder }
        val newPrimaryLevel = AlertLevel.primaryOf(cleanDangerSet)

        var updatedItem: RegionItem? = null
        _regions.update { current ->
            current.map { item ->
                if (item.id == regionId) {
                    val wasDanger = item.currentLevel.isDanger || item.effectiveActiveAlerts.isNotEmpty()
                    val isDanger = cleanDangerSet.isNotEmpty()
                    val startTime = if (isDanger && !wasDanger) {
                        timestamp
                    } else if (!isDanger) {
                        null
                    } else {
                        item.alertStartTime ?: timestamp
                    }
                    val resolvedNote = threatNote.ifBlank {
                        when {
                            orderedActive.size > 1 ->
                                "Одночасно: " + orderedActive.joinToString(" + ") { it.shortName }
                            newPrimaryLevel == AlertLevel.RED ->
                                "Повітряна тривога! Перейдіть в укриття!"
                            newPrimaryLevel == AlertLevel.YELLOW ->
                                "Жовтий рівень небезпеки. Підвищена увага."
                            newPrimaryLevel == AlertLevel.STREET_FIGHTS ->
                                "Вуличні бої! Залишайтесь в укритті!"
                            newPrimaryLevel == AlertLevel.CIVIL_DEFENSE ->
                                "Важливе сповіщення цивільного захисту! Ознайомтеся з додатком Омастротривога."
                            else -> "Відбій загрози. Спокійно."
                        }
                    }
                    val updated = item.copy(
                        currentLevel = newPrimaryLevel,
                        activeAlerts = cleanDangerSet,
                        alertStartTime = startTime,
                        threatDetails = resolvedNote
                    )
                    updatedItem = updated
                    updated
                } else {
                    item
                }
            }
        }

        updatedItem?.let { reg ->
            saveRegionsState()

            val levelsForDisplay = orderedActive.ifEmpty { listOf(AlertLevel.SAFE) }

            if (emitBroadcastBanner) {
                val broadcast = AdminBroadcast(
                    id = UUID.randomUUID().toString(),
                    regionName = reg.name,
                    level = newPrimaryLevel,
                    activeLevels = levelsForDisplay,
                    timestamp = timestamp,
                    note = reg.threatDetails,
                    adminName = ADMIN_OWNER_NAME
                )
                _latestAdminBroadcast.value = broadcast
                saveLatestBroadcast(broadcast)
            }

            val event = AlertEventLog(
                id = UUID.randomUUID().toString(),
                regionName = reg.name,
                level = newPrimaryLevel,
                activeLevels = levelsForDisplay,
                timestamp = timestamp,
                threatDetails = reg.threatDetails,
                source = source
            )
            _events.update { current ->
                val next = listOf(event) + current.take(49)
                saveEvents(next)
                next
            }

            if (emitAudioAndNotification) {
                val tracksToPlay = audioOverride ?: levelsForDisplay
                if (tracksToPlay.isNotEmpty()) {
                    scope.launch {
                        _alertStateChanges.emit(
                            AlertChangeEvent(
                                region = reg,
                                levelsToPlay = tracksToPlay,
                                allActiveLevels = levelsForDisplay
                            )
                        )
                    }
                }
            }
        }
        return updatedItem
    }

    fun triggerKryveOzeroAlert(level: AlertLevel, threatNote: String = "") {
        setRegionAlertLevel(KRYVE_OZERO_ID, level, threatNote)
    }

    fun triggerKryveOzeroMultipleAlerts(levels: Set<AlertLevel>, threatNote: String = "") {
        setRegionActiveAlerts(KRYVE_OZERO_ID, levels, threatNote)
    }

    // Master Total All-Clear: sets all regions and hromadas across Ukraine to SAFE, saves & broadcasts online
    fun triggerTotalAllClear() {
        val now = System.currentTimeMillis()
        val totalNote = "Тотальний відбій для всієї території України! Небезпека минула."
        _regions.value.forEach { manualOverrideTimestamps[it.id] = now }
        applyTotalAllClearLocally(totalNote, "Адміністратор $ADMIN_OWNER_NAME", now, emitAudio = true)

        val eventId = UUID.randomUUID().toString()
        markRemoteIdProcessed(eventId)
        val envelope = JSONObject().apply {
            put("type", "TOTAL_CLEAR")
            put("eventId", eventId)
            put("senderDeviceId", deviceId)
            put("note", totalNote)
            put("adminName", ADMIN_OWNER_NAME)
            put("timestamp", now)
        }
        publishEnvelopeOnline(
            title = "🟢 ВЕСЬ ВІДБІЙ — Вся Україна",
            envelope = envelope,
            priority = 4
        )
    }

    private fun applyTotalAllClearLocally(
        totalNote: String,
        source: String,
        timestamp: Long,
        emitAudio: Boolean
    ) {
        _regions.update { current ->
            current.map {
                it.copy(
                    currentLevel = AlertLevel.SAFE,
                    activeAlerts = emptySet(),
                    alertStartTime = null,
                    threatDetails = totalNote
                )
            }
        }
        saveRegionsState()

        val kryveOzero = _regions.value.find { it.id == KRYVE_OZERO_ID }
            ?: _regions.value.firstOrNull()
            ?: RegionItem(
                id = KRYVE_OZERO_ID,
                name = "Вся Україна",
                oblast = "Україна",
                isHromada = false,
                isMonitored = true,
                currentLevel = AlertLevel.SAFE,
                activeAlerts = emptySet()
            )

        val broadcast = AdminBroadcast(
            id = UUID.randomUUID().toString(),
            regionName = "Вся територія України (ВЕСЬ ВІДБІЙ)",
            level = AlertLevel.SAFE,
            activeLevels = listOf(AlertLevel.SAFE),
            timestamp = timestamp,
            note = totalNote,
            adminName = ADMIN_OWNER_NAME
        )
        _latestAdminBroadcast.value = broadcast
        saveLatestBroadcast(broadcast)

        val event = AlertEventLog(
            id = UUID.randomUUID().toString(),
            regionName = "Вся Україна (Тотальний відбій)",
            level = AlertLevel.SAFE,
            activeLevels = listOf(AlertLevel.SAFE),
            timestamp = timestamp,
            threatDetails = totalNote,
            source = source
        )
        _events.update { current ->
            val next = listOf(event) + current.take(49)
            saveEvents(next)
            next
        }

        if (emitAudio) {
            scope.launch {
                _alertStateChanges.emit(
                    AlertChangeEvent(
                        region = kryveOzero,
                        levelsToPlay = listOf(AlertLevel.SAFE),
                        allActiveLevels = listOf(AlertLevel.SAFE)
                    )
                )
            }
        }
    }

    // Dedicated Street Fights All-Clear: clears STREET_FIGHTS while keeping any other active threats
    fun triggerStreetFightsAllClear(regionId: String = KRYVE_OZERO_ID) {
        val current = _regions.value.find { it.id == regionId }
        val remaining = current?.effectiveActiveAlerts?.filter { it != AlertLevel.STREET_FIGHTS }?.toSet()
            ?: emptySet()
        val sfNote = if (remaining.isEmpty()) {
            "Відбій загрози вуличних боїв! Ситуація в громаді стабілізована та під повним контролем."
        } else {
            "Відбій вуличних боїв. Залишаються активними: " + remaining.joinToString(", ") { it.shortName }
        }
        setRegionActiveAlerts(
            regionId = regionId,
            levels = remaining,
            threatNote = sfNote,
            audioOverride = listOf(AlertLevel.SAFE)
        )
    }

    fun dismissAdminBroadcast() {
        _latestAdminBroadcast.value = null
        saveLatestBroadcast(null)
    }

    // Community Discussion Methods (persisted in SharedPreferences)
    private fun loadComments() {
        val rawJson = prefs.getString(PREF_COMMENTS, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val array = JSONArray(rawJson)
                val list = mutableListOf<DiscussionComment>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        DiscussionComment(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            authorName = obj.optString("authorName", "Громадянин"),
                            authorRole = try {
                                UserRole.valueOf(obj.optString("authorRole", UserRole.USER.name))
                            } catch (_: Exception) {
                                UserRole.USER
                            },
                            text = obj.optString("text", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            regionTag = obj.optString("regionTag", "Кривоозерська громада"),
                            isAdminVerified = obj.optBoolean("isAdminVerified", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _comments.value = list
                    return
                }
            } catch (_: Exception) {}
        }

        // Default initial community discussion
        val defaultComments = listOf(
            DiscussionComment(
                id = "admin_welcome",
                authorName = "Денис (Адміністратор)",
                authorRole = UserRole.ADMIN,
                text = "Вітаю у прямому часі громади «Омастротривога»! Тут публікуються термінові звіти та сповіщення. Звуковий сигнал звучить рівно один раз без зациклень.",
                timestamp = System.currentTimeMillis() - 25 * 60 * 1000,
                regionTag = "Кривоозерська громада",
                isAdminVerified = true
            ),
            DiscussionComment(
                id = "c1",
                authorName = "Олександр П.",
                authorRole = UserRole.USER,
                text = "У Кривому Озері зараз тихо, сирена не лунає. Дякую за оперативне сповіщення!",
                timestamp = System.currentTimeMillis() - 15 * 60 * 1000,
                regionTag = "Криве Озеро",
                isAdminVerified = false
            ),
            DiscussionComment(
                id = "c2",
                authorName = "Наталія М.",
                authorRole = UserRole.USER,
                text = "Сповіщення приходить миттєво, відбій чути чітко.",
                timestamp = System.currentTimeMillis() - 5 * 60 * 1000,
                regionTag = "Кривоозерська громада",
                isAdminVerified = false
            )
        )
        _comments.value = defaultComments
        saveComments(defaultComments)
    }

    private fun saveComments(list: List<DiscussionComment>) {
        try {
            val array = JSONArray()
            list.take(60).forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("authorName", item.authorName)
                    put("authorRole", item.authorRole.name)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("regionTag", item.regionTag)
                    put("isAdminVerified", item.isAdminVerified)
                }
                array.put(obj)
            }
            prefs.edit().putString(PREF_COMMENTS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun postComment(
        text: String,
        authorName: String,
        role: UserRole = _userRole.value,
        regionTag: String = "Кривоозерська громада"
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val isAdmin = role == UserRole.ADMIN
        val finalAuthor = if (isAdmin) {
            if (authorName.isNotBlank() && !authorName.contains("Денис")) "$authorName (Денис)" else "Денис (Адміністратор)"
        } else {
            authorName.ifBlank { "Житель громади" }
        }

        val commentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        if (role != UserRole.ADMIN && authorName.isNotBlank()) {
            saveAuthorName(authorName.trim())
        }

        val comment = DiscussionComment(
            id = commentId,
            authorName = finalAuthor,
            authorRole = role,
            text = trimmed,
            timestamp = now,
            regionTag = regionTag,
            isAdminVerified = isAdmin
        )

        _comments.update { current ->
            val updated = listOf(comment) + current
            saveComments(updated)
            updated
        }

        // Publish comment to live online channel so other users see it
        markRemoteIdProcessed(commentId)
        val envelope = JSONObject().apply {
            put("type", "COMMENT")
            put("eventId", commentId)
            put("senderDeviceId", deviceId)
            put("authorName", finalAuthor)
            put("authorRole", role.name)
            put("text", trimmed)
            put("regionTag", regionTag)
            put("isAdminVerified", isAdmin)
            put("timestamp", now)
        }
        publishEnvelopeOnline(
            title = "💬 $finalAuthor",
            envelope = envelope,
            priority = 3
        )
    }

    fun saveAuthorName(name: String) {
        _authorName.value = name
        prefs.edit().putString(PREF_AUTHOR_NAME, name).apply()
    }

    fun saveSelectedTab(index: Int) {
        val safeIndex = index.coerceIn(0, 4)
        _savedSelectedTab.value = safeIndex
        prefs.edit().putInt(PREF_SELECTED_TAB, safeIndex).apply()
    }

    fun saveSelectedRegionId(regionId: String) {
        _savedSelectedRegionId.value = regionId
        prefs.edit().putString(PREF_SELECTED_REGION_ID, regionId).apply()
    }

    fun updateOnlineChannelId(newChannel: String) {
        val sanitized = syncClient.sanitizeChannelId(newChannel).ifBlank { DEFAULT_ONLINE_CHANNEL }
        _onlineChannelId.value = sanitized
        prefs.edit().putString(PREF_ONLINE_CHANNEL, sanitized).apply()
        syncNow()
    }

    fun setDsnsAutoSyncEnabled(enabled: Boolean) {
        _dsnsAutoSyncEnabled.value = enabled
        prefs.edit().putBoolean(PREF_DSNS_AUTO_SYNC, enabled).apply()
        if (enabled) syncNow()
    }

    // Role management
    fun setUserRole(role: UserRole) {
        _userRole.value = role
        prefs.edit().putString(PREF_ROLE, role.name).apply()
    }

    fun verifyAdminPin(pin: String): Boolean {
        val storedPin = prefs.getString(PREF_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        return pin == storedPin
    }

    fun updateAdminPin(newPin: String) {
        prefs.edit().putString(PREF_PIN, newPin).apply()
    }

    fun updateSoundSettings(newSettings: SoundSettings) {
        _soundSettings.value = newSettings.copy(playOnceOnly = true)
        prefs.edit()
            .putFloat(PREF_VOLUME, newSettings.volume)
            .putBoolean(PREF_VIBRATION, newSettings.vibrationEnabled)
            .putBoolean(PREF_AUTO_VOICE, newSettings.autoPlayVoice)
            .apply()
    }

    fun syncNow() {
        scope.launch {
            performOnlineSync()
        }
    }

    private fun startPeriodicSync() {
        scope.launch {
            // Immediate online sync right at startup
            performOnlineSync()
            while (true) {
                delay(10000)
                performOnlineSync()
            }
        }
    }

    private fun publishEnvelopeOnline(
        title: String,
        envelope: JSONObject,
        priority: Int
    ) {
        scope.launch {
            if (!syncClient.isNetworkAvailable()) {
                _isApiConnected.value = false
                _onlineStatusMessage.value = "Офлайн • Збережено локально"
                return@launch
            }
            val result = syncClient.publishToChannel(
                channelId = _onlineChannelId.value,
                title = title,
                envelope = envelope,
                priority = priority
            )
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                _lastSyncTimestamp.value = now
                prefs.edit().putLong(PREF_LAST_SYNC_TS, now).apply()
                _isApiConnected.value = true
                _onlineStatusMessage.value = "Онлайн • Надіслано в ефір"
            }
        }
    }

    @Synchronized
    private fun markRemoteIdProcessed(id: String): Boolean {
        if (processedRemoteIds.contains(id)) return false
        processedRemoteIds.add(id)
        if (processedRemoteIds.size > 250) {
            val trimmed = processedRemoteIds.toList().takeLast(150).toSet()
            processedRemoteIds.clear()
            processedRemoteIds.addAll(trimmed)
        }
        prefs.edit().putStringSet(PREF_PROCESSED_IDS, processedRemoteIds.toSet()).apply()
        return true
    }

    private fun performOnlineSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        try {
            if (!syncClient.isNetworkAvailable()) {
                _isApiConnected.value = false
                _onlineStatusMessage.value = "Офлайн • Збережено локально"
                return
            }

            var anySuccess = false

            // 1. Poll live online channel (Admin broadcasts + Community comments)
            val channelRes = syncClient.pollChannelMessages(_onlineChannelId.value, since = "12h")
            if (channelRes.isSuccess) {
                anySuccess = true
                val messages = channelRes.getOrNull().orEmpty()
                processRemoteChannelMessages(messages)
            }

            // 2. Fetch live Ukraine official air raid alerts from ubilling.net.ua/aerialalerts/
            if (_dsnsAutoSyncEnabled.value) {
                val alertsRes = syncClient.fetchUkraineOfficialAlerts()
                if (alertsRes.isSuccess) {
                    anySuccess = true
                    val states = alertsRes.getOrNull().orEmpty()
                    applyOfficialOnlineAlerts(states)
                }
            }

            if (anySuccess) {
                val now = System.currentTimeMillis()
                _isApiConnected.value = true
                _lastSyncTimestamp.value = now
                prefs.edit().putLong(PREF_LAST_SYNC_TS, now).apply()
                _onlineStatusMessage.value = if (_dsnsAutoSyncEnabled.value) {
                    "Онлайн • ДСНС + Прямий ефір"
                } else {
                    "Онлайн • Прямий ефір"
                }
            } else {
                _isApiConnected.value = false
                _onlineStatusMessage.value = "Офлайн • Помилка з'єднання"
            }
        } catch (_: Exception) {
            _isApiConnected.value = false
            _onlineStatusMessage.value = "Офлайн • Збережено локально"
        } finally {
            _isSyncing.value = false
        }
    }

    private fun processRemoteChannelMessages(messages: List<com.example.network.RemoteSyncMessage>) {
        messages.sortedBy { it.timeSeconds }.forEach { msg ->
            val env = msg.envelope
            val senderDevice = env.optString("senderDeviceId", "")
            val eventId = env.optString("eventId", msg.remoteId).ifBlank { msg.remoteId }
            if (senderDevice == deviceId) {
                markRemoteIdProcessed(eventId)
                return@forEach
            }
            if (!markRemoteIdProcessed(eventId)) return@forEach

            val type = env.optString("type", "")
            val timestamp = env.optLong("timestamp", msg.timeSeconds * 1000L)
            val isRecent = (System.currentTimeMillis() - timestamp) < 20 * 60 * 1000L

            when (type) {
                "REGION_ALERT" -> {
                    val regionId = env.optString("regionId", KRYVE_OZERO_ID)
                    val level = try {
                        AlertLevel.valueOf(env.optString("level", AlertLevel.SAFE.name))
                    } catch (_: Exception) {
                        AlertLevel.SAFE
                    }
                    val parsedActive = parseAlertLevelsJsonArray(env.optJSONArray("activeLevels"))
                        .filter { it.isDanger }
                        .toSet()
                        .ifEmpty { if (level.isDanger) setOf(level) else emptySet() }
                    val note = env.optString("note", "")
                    val adminName = env.optString("adminName", ADMIN_OWNER_NAME)
                    manualOverrideTimestamps[regionId] = timestamp
                    applyRegionMultiAlertMutation(
                        regionId = regionId,
                        activeDangerLevels = parsedActive,
                        threatNote = note,
                        source = "Адміністратор $adminName (Онлайн)",
                        timestamp = timestamp,
                        emitBroadcastBanner = true,
                        emitAudioAndNotification = isRecent
                    )
                }

                "TOTAL_CLEAR" -> {
                    val note = env.optString("note", "Тотальний відбій для всієї території України!")
                    val adminName = env.optString("adminName", ADMIN_OWNER_NAME)
                    _regions.value.forEach { manualOverrideTimestamps[it.id] = timestamp }
                    applyTotalAllClearLocally(
                        totalNote = note,
                        source = "Адміністратор $adminName (Онлайн)",
                        timestamp = timestamp,
                        emitAudio = isRecent
                    )
                }

                "COMMENT" -> {
                    val authorName = env.optString("authorName", "Житель громади")
                    val role = try {
                        UserRole.valueOf(env.optString("authorRole", UserRole.USER.name))
                    } catch (_: Exception) {
                        UserRole.USER
                    }
                    val text = env.optString("text", "").trim()
                    val regionTag = env.optString("regionTag", "Кривоозерська громада")
                    val isAdmin = env.optBoolean("isAdminVerified", false)
                    if (text.isNotEmpty()) {
                        val incomingComment = DiscussionComment(
                            id = eventId,
                            authorName = authorName,
                            authorRole = role,
                            text = text,
                            timestamp = timestamp,
                            regionTag = regionTag,
                            isAdminVerified = isAdmin
                        )
                        _comments.update { current ->
                            if (current.any { it.id == eventId }) {
                                current
                            } else {
                                val updated = (listOf(incomingComment) + current)
                                    .sortedByDescending { it.timestamp }
                                saveComments(updated)
                                updated
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyOfficialOnlineAlerts(states: Map<String, OfficialOblastStatus>) {
        if (states.isEmpty()) return
        val now = System.currentTimeMillis()
        val hasSavedStatesBefore = prefs.contains(PREF_REGIONS_STATE)
        val newlyChangedMonitored = mutableListOf<Pair<RegionItem, AlertLevel>>()
        var anyRegionChanged = false

        val updatedList = _regions.value.map { region ->
            val official = findOfficialStatusForRegion(region, states) ?: return@map region

            // Do not overwrite recent manual Admin Denis overrides or special threats
            val overrideTs = manualOverrideTimestamps[region.id] ?: 0L
            val isManualProtected = overrideTs > 0L &&
                (now - overrideTs) < ADMIN_OVERRIDE_GRACE_MS &&
                official.changedAtMillis <= overrideTs
            val isSpecialMode = region.effectiveActiveAlerts.any {
                it == AlertLevel.STREET_FIGHTS || it == AlertLevel.CIVIL_DEFENSE
            }
            if (isManualProtected || (isSpecialMode && (now - overrideTs) < ADMIN_OVERRIDE_GRACE_MS)) {
                return@map region
            }

            val targetLevel = if (official.isAlertActive) AlertLevel.RED else AlertLevel.SAFE
            val targetSet = if (official.isAlertActive) setOf(AlertLevel.RED) else emptySet()
            if (region.currentLevel != targetLevel || region.activeAlerts != targetSet) {
                anyRegionChanged = true
                val newDetails = if (targetLevel == AlertLevel.RED) {
                    "Офіційна повітряна тривога (Онлайн API ДСНС)"
                } else {
                    "Відбій повітряної тривоги (Онлайн API ДСНС)"
                }
                val nextItem = region.copy(
                    currentLevel = targetLevel,
                    activeAlerts = targetSet,
                    alertStartTime = if (targetLevel.isDanger) official.changedAtMillis else null,
                    threatDetails = newDetails
                )
                if (hasSavedStatesBefore && region.isMonitored) {
                    newlyChangedMonitored.add(Pair(nextItem, targetLevel))
                }
                nextItem
            } else {
                region
            }
        }

        if (anyRegionChanged) {
            _regions.value = updatedList
            saveRegionsState()
        }

        if (newlyChangedMonitored.isNotEmpty()) {
            val newLogs = newlyChangedMonitored.map { (reg, lvl) ->
                AlertEventLog(
                    id = UUID.randomUUID().toString(),
                    regionName = reg.name,
                    level = lvl,
                    activeLevels = listOf(lvl),
                    timestamp = now,
                    threatDetails = reg.threatDetails,
                    source = "ДСНС / Онлайн API"
                )
            }
            _events.update { current ->
                val next = (newLogs + current).take(50)
                saveEvents(next)
                next
            }
            val primaryChange = newlyChangedMonitored.firstOrNull { it.first.id == KRYVE_OZERO_ID }
                ?: newlyChangedMonitored.first()
            scope.launch {
                _alertStateChanges.emit(
                    AlertChangeEvent(
                        region = primaryChange.first,
                        levelsToPlay = listOf(primaryChange.second),
                        allActiveLevels = listOf(primaryChange.second)
                    )
                )
            }
        }
    }

    private fun findOfficialStatusForRegion(
        region: RegionItem,
        states: Map<String, OfficialOblastStatus>
    ): OfficialOblastStatus? {
        states[region.oblast]?.let { return it }
        states[region.name]?.let { return it }
        if (region.id == "crimea") {
            states["Автономна Республіка Крим"]?.let { return it }
        }
        return null
    }
}
