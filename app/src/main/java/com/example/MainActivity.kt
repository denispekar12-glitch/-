package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.UserRole
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.AlertHeaderBar
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AlertsDashboardScreen
import com.example.ui.screens.AudioSettingsScreen
import com.example.ui.screens.DiscussionScreen
import com.example.ui.screens.MapScreen
import com.example.ui.theme.AlertRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.UkrYellow
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val regions by viewModel.regions.collectAsState()
    val events by viewModel.events.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val soundSettings by viewModel.soundSettings.collectAsState()
    val isApiConnected by viewModel.isApiConnected.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val onlineStatusMessage by viewModel.onlineStatusMessage.collectAsState()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    val onlineChannelId by viewModel.onlineChannelId.collectAsState()
    val dsnsAutoSyncEnabled by viewModel.dsnsAutoSyncEnabled.collectAsState()
    val authorName by viewModel.authorName.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val playingAudioTitle by viewModel.currentlyPlayingTitle.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()

    // Request notification permissions gracefully on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Handle back button: return to dashboard if on subscreen
    BackHandler(enabled = selectedTab != 0) {
        viewModel.setTab(0)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AlertHeaderBar(
                userRole = userRole,
                isApiConnected = isApiConnected,
                isSyncing = isSyncing,
                onlineStatusMessage = onlineStatusMessage,
                lastSyncTimestamp = lastSyncTimestamp,
                isAudioPlaying = isAudioPlaying,
                playingAudioTitle = playingAudioTitle,
                onStopAudio = { viewModel.stopAudio() },
                onSyncNow = { viewModel.syncNow() },
                onRoleClick = { viewModel.openPinDialog() }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                containerColor = Color(0xFF0D101A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsActive,
                            contentDescription = "Тривога"
                        )
                    },
                    label = { Text("Тривога", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AlertRed,
                        selectedTextColor = AlertRed,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = AlertRed.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("nav_item_dashboard")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Map else Icons.Outlined.Map,
                            contentDescription = "Мапа"
                        )
                    },
                    label = { Text("Мапа", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("nav_item_map")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Forum else Icons.Outlined.Forum,
                            contentDescription = "Обговорення"
                        )
                    },
                    label = { Text("Чат", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UkrYellow,
                        selectedTextColor = UkrYellow,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = UkrYellow.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("nav_item_discussion")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.VolumeUp else Icons.Outlined.VolumeUp,
                            contentDescription = "Звук"
                        )
                    },
                    label = { Text("Звук", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UkrYellow,
                        selectedTextColor = UkrYellow,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = UkrYellow.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("nav_item_audio")
                )

                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.setTab(4) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 4) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                            contentDescription = "Адмін"
                        )
                    },
                    label = { Text("Адмін", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (userRole == UserRole.ADMIN) UkrYellow else Color.White,
                        selectedTextColor = if (userRole == UserRole.ADMIN) UkrYellow else Color.White,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = UkrYellow.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("nav_item_admin")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> AlertsDashboardScreen(
                    regions = regions,
                    events = events,
                    userRole = userRole,
                    latestAdminBroadcast = viewModel.latestAdminBroadcast.collectAsState().value,
                    onDismissBroadcast = { viewModel.dismissAdminBroadcast() },
                    onAddKryveOzero = { viewModel.addOrPrioritizeKryveOzero() },
                    onSelectRegion = { reg ->
                        viewModel.selectRegion(reg)
                        viewModel.setTab(1) // Jump to map view of region
                    },
                    onTriggerKryveOzero = { level ->
                        viewModel.triggerKryveOzeroAlert(level)
                    },
                    onTriggerKryveOzeroMultiple = { levels ->
                        viewModel.triggerKryveOzeroMultipleAlerts(levels)
                    },
                    onToggleKryveOzeroAlert = { level ->
                        viewModel.toggleAlertTypeForRegion("kryve_ozero", level)
                    },
                    onTriggerTotalAllClear = {
                        viewModel.triggerTotalAllClear()
                    },
                    onTriggerStreetFightsAllClear = {
                        viewModel.triggerStreetFightsAllClear()
                    },
                    onOpenAudioSettings = { viewModel.setTab(3) },
                    onOpenMap = { viewModel.setTab(1) }
                )

                1 -> MapScreen(
                    regions = regions,
                    selectedRegion = selectedRegion,
                    userRole = userRole,
                    onSelectRegion = { viewModel.selectRegion(it) },
                    onFocusKryveOzero = { viewModel.focusKryveOzero() },
                    onToggleMonitored = { viewModel.toggleMonitored(it) },
                    onSetAlertLevel = { id, level -> viewModel.setAlertLevel(id, level) },
                    onToggleAlertType = { id, level -> viewModel.toggleAlertTypeForRegion(id, level) }
                )

                2 -> DiscussionScreen(
                    comments = comments,
                    userRole = userRole,
                    savedAuthorName = authorName,
                    onSaveAuthorName = { viewModel.saveAuthorName(it) },
                    onPostComment = { text, name, tag ->
                        viewModel.postComment(text, name, tag)
                    }
                )

                3 -> AudioSettingsScreen(
                    soundSettings = soundSettings,
                    isAudioPlaying = isAudioPlaying,
                    playingAudioTitle = playingAudioTitle,
                    userRole = userRole,
                    onUpdateSettings = { viewModel.updateSoundSettings(it) },
                    onPreviewSound = { resId, title -> viewModel.previewSound(resId, title) },
                    onStopAudio = { viewModel.stopAudio() },
                    onTestKryveOzeroAlert = { level -> viewModel.triggerKryveOzeroAlert(level) }
                )

                4 -> AdminScreen(
                    userRole = userRole,
                    regions = regions,
                    isApiConnected = isApiConnected,
                    isSyncing = isSyncing,
                    onlineStatusMessage = onlineStatusMessage,
                    onlineChannelId = onlineChannelId,
                    dsnsAutoSyncEnabled = dsnsAutoSyncEnabled,
                    onOpenPinDialog = { viewModel.openPinDialog() },
                    onLockToUserRole = { viewModel.lockToUserRole() },
                    onSetAlertLevel = { id, level, note -> viewModel.setAlertLevel(id, level, note) },
                    onSetRegionActiveAlerts = { id, levels, note -> viewModel.setRegionActiveAlerts(id, levels, note) },
                    onToggleRegionAlertType = { id, level -> viewModel.toggleAlertTypeForRegion(id, level) },
                    onSetMultipleRegionsActiveAlerts = { ids, levels, note ->
                        viewModel.setMultipleRegionsActiveAlerts(ids, levels, note)
                    },
                    onTriggerTotalAllClear = { viewModel.triggerTotalAllClear() },
                    onTriggerStreetFightsAllClear = { viewModel.triggerStreetFightsAllClear() },
                    onSyncNow = { viewModel.syncNow() },
                    onUpdateOnlineChannel = { viewModel.updateOnlineChannelId(it) },
                    onToggleDsnsAutoSync = { viewModel.setDsnsAutoSyncEnabled(it) },
                    onUpdatePin = { viewModel.updateAdminPin(it) }
                )
            }

            if (showPinDialog) {
                AdminPinDialog(
                    currentRole = userRole,
                    onDismiss = { viewModel.closePinDialog() },
                    onVerifyPin = { pin -> viewModel.verifyAdminPin(pin) },
                    onLockToUser = { viewModel.lockToUserRole() }
                )
            }
        }
    }
}
