package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BroadcastOnPersonal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAdvisorUiState
import com.example.data.AlertRepository
import com.example.model.AdminBroadcast
import com.example.model.AlertEventLog
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.model.UserRole
import com.example.ui.components.AdminAlertTabsPanel
import com.example.ui.components.AiSafetyCard
import com.example.ui.components.ShahedDroneIcon
import com.example.ui.components.ThreatLevelIcon
import com.example.ui.components.levelMapColor
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertOrangeStreetFight
import com.example.ui.theme.AlertRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrBlue
import com.example.ui.theme.UkrYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsDashboardScreen(
    regions: List<RegionItem>,
    events: List<AlertEventLog>,
    userRole: UserRole,
    latestAdminBroadcast: AdminBroadcast?,
    pinnedLimitMessage: String? = null,
    onDismissPinnedLimitMessage: () -> Unit = {},
    onDismissBroadcast: () -> Unit,
    onToggleMonitored: (String) -> Boolean = { true },
    onSelectRegion: (RegionItem) -> Unit,
    onTriggerRegionAlert: (String, AlertLevel, Boolean) -> Unit,
    onTriggerRegionMultiple: (String, Set<AlertLevel>, Boolean) -> Unit,
    onToggleRegionAlert: (String, AlertLevel, Boolean) -> Unit,
    onClearRegion: (String, Boolean) -> Unit,
    onTriggerTotalAllClear: (Boolean) -> Unit,
    onTriggerStreetFightsAllClear: (String, Boolean) -> Unit,
    onTriggerSpecificAllClear: (String, AlertLevel, Boolean) -> Unit = { _, _, _ -> },
    onTriggerAllUkraineAlert: (AlertLevel, Set<AlertLevel>, Boolean) -> Unit = { _, _, _ -> },
    onOpenAudioSettings: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    aiState: AiAdvisorUiState = AiAdvisorUiState.Idle,
    onAnalyzeWithAi: () -> Unit = {},
    onAskAiQuestion: (String) -> Unit = {},
    onResetAiState: () -> Unit = {}
) {
    val monitored = regions.filter { it.isMonitored }
    val activeDangers = regions.filter { it.currentLevel.isDanger }
    var focusedDistrictId by remember { mutableStateOf(AlertRepository.KRYVE_OZERO_ID) }
    val focusedRegion = monitored.find { it.id == focusedDistrictId }
        ?: regions.find { it.id == focusedDistrictId }
        ?: monitored.firstOrNull()
        ?: regions.firstOrNull()

    var showDistrictPickerDialog by remember { mutableStateOf(false) }
    var noAllClearMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time Admin Broadcast Banner
        if (latestAdminBroadcast != null) {
            item {
                val broadcastColor = levelMapColor(latestAdminBroadcast.level)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1726)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(broadcastColor, UkrYellow))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_live_broadcast_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(broadcastColor.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (latestAdminBroadcast.activeLevels.contains(AlertLevel.DRONE)) {
                                    ShahedDroneIcon(size = 24.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.BroadcastOnPersonal,
                                        contentDescription = null,
                                        tint = broadcastColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "📡 НАЖИВО ВІД АДМІНІСТРАТОРА (ДЕНИС)",
                                        color = UkrYellow,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.4.sp
                                    )
                                    if (latestAdminBroadcast.withoutAllClear) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = UkrYellow.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = "БЕЗ ВІДБОЮ",
                                                color = UkrYellow,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                val activeBroadcastLevels = latestAdminBroadcast.activeLevels
                                val headline = if (activeBroadcastLevels.size > 1) {
                                    "Декілька загроз (${activeBroadcastLevels.size}) — ${latestAdminBroadcast.regionName}"
                                } else {
                                    "${latestAdminBroadcast.level.title} — ${latestAdminBroadcast.regionName}"
                                }
                                Text(
                                    text = headline,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (activeBroadcastLevels.size > 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        activeBroadcastLevels.forEach { lvl ->
                                            val pillColor = alertAccentColor(lvl)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = pillColor.copy(alpha = 0.22f)
                                            ) {
                                                Text(
                                                    text = lvl.shortName,
                                                    color = pillColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = latestAdminBroadcast.note.ifBlank {
                                        "Сигнал збережено та активно"
                                    },
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismissBroadcast) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрити",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // First Card / First Button: Add or Manage up to 3 Districts / Oblasts
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_kryve_ozero_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2B)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(UkrYellow.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocationAlt,
                                    contentDescription = "Обрати райони",
                                    tint = UkrYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "ОБРАНІ РАЙОНИ (${monitored.size}/${AlertRepository.MAX_PINNED_REGIONS})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = UkrBlue
                                    ) {
                                        Text(
                                            text = "МАКС ${AlertRepository.MAX_PINNED_REGIONS}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Додайте до ${AlertRepository.MAX_PINNED_REGIONS} районів — Кривоозерська або інші",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        ElevatedButton(
                            onClick = { showDistrictPickerDialog = true },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = UkrYellow,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_add_kryve_ozero")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Додати (${monitored.size}/3)",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (monitored.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(monitored, key = { it.id }) { reg ->
                                val isCurrent = focusedRegion?.id == reg.id
                                val dotColor = levelMapColor(reg.currentLevel)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) Color(0xFF1F2A44) else Color(0xFF0E1422),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(
                                            if (isCurrent) UkrYellow else SurfaceBorderDark
                                        )
                                    ),
                                    modifier = Modifier
                                        .clickable { focusedDistrictId = reg.id }
                                        .testTag("pinned_district_chip_${reg.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = reg.name,
                                            color = if (isCurrent) UkrYellow else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Alert Status Card for currently focused district
        if (focusedRegion != null) {
            item {
                val statusLevel = focusedRegion.currentLevel
                val isAlertActive = statusLevel.isDanger
                val accentColor = levelMapColor(statusLevel)
                val cardBg = Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = if (isAlertActive) 0.25f else 0.16f),
                        Color(0xFF0B101B)
                    )
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_status_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.4f)))
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardBg)
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                val activeAlerts = focusedRegion.effectiveActiveAlerts
                                if (activeAlerts.isEmpty()) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = accentColor.copy(alpha = 0.2f),
                                            border = CardDefaults.outlinedCardBorder().copy(
                                                brush = androidx.compose.ui.graphics.SolidColor(accentColor)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = statusLevel.title.uppercase(),
                                                    color = accentColor,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                        if (focusedRegion.withoutAllClear) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UkrYellow.copy(alpha = 0.22f)
                                            ) {
                                                Text(
                                                    text = "БЕЗ ВІДБОЮ",
                                                    color = UkrYellow,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    FlowRow(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        activeAlerts.forEach { lvl ->
                                            val lvlColor = alertAccentColor(lvl)
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = lvlColor.copy(alpha = 0.22f),
                                                border = CardDefaults.outlinedCardBorder().copy(
                                                    brush = androidx.compose.ui.graphics.SolidColor(lvlColor)
                                                ),
                                                modifier = if (userRole == UserRole.ADMIN) {
                                                    Modifier.clickable {
                                                        onToggleRegionAlert(focusedRegion.id, lvl, noAllClearMode)
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = lvlColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = lvl.shortName.uppercase(),
                                                        color = lvlColor,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp
                                                    )
                                                    if (userRole == UserRole.ADMIN) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Вимкнути ${lvl.shortName}",
                                                            tint = lvlColor,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (focusedRegion.withoutAllClear) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UkrYellow.copy(alpha = 0.22f)
                                            ) {
                                                Text(
                                                    text = "БЕЗ ВІДБОЮ",
                                                    color = UkrYellow,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = onOpenMap,
                                    shape = RoundedCornerShape(10.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Мапа районів", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = focusedRegion.name,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp
                            )
                            Text(
                                text = focusedRegion.oblast,
                                color = UkrYellow,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )

                            Text(
                                text = if (isAlertActive) {
                                    focusedRegion.threatDetails.ifBlank { "Небезпека в районі! Перебувайте в укритті!" }
                                } else if (focusedRegion.withoutAllClear && focusedRegion.threatDetails.isNotBlank()) {
                                    focusedRegion.threatDetails
                                } else {
                                    "Тривога відсутня. Обстановка спокійна."
                                },
                                color = if (isAlertActive) Color.White.copy(alpha = 0.95f) else TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            if (focusedRegion.alertStartTime != null && isAlertActive) {
                                val elapsedMinutes = (System.currentTimeMillis() - focusedRegion.alertStartTime) / (60 * 1000)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Триває вже: ${elapsedMinutes.coerceAtLeast(1)} хв",
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Fast real-time tab-based broadcast controls for Admin Denis (no map required)
                            if (userRole == UserRole.ADMIN) {
                                Spacer(modifier = Modifier.height(16.dp))
                                AdminAlertTabsPanel(
                                    regions = regions,
                                    focusedRegion = focusedRegion,
                                    noAllClearMode = noAllClearMode,
                                    onNoAllClearChange = { noAllClearMode = it },
                                    onFocusDistrict = { id -> focusedDistrictId = id },
                                    onOpenDistrictPicker = { showDistrictPickerDialog = true },
                                    onTriggerRegionAlert = onTriggerRegionAlert,
                                    onTriggerRegionMultiple = onTriggerRegionMultiple,
                                    onClearRegion = onClearRegion,
                                    onTriggerTotalAllClear = onTriggerTotalAllClear,
                                    onTriggerStreetFightsAllClear = onTriggerStreetFightsAllClear,
                                    onTriggerSpecificAllClear = onTriggerSpecificAllClear,
                                    onTriggerAllUkraineAlert = onTriggerAllUkraineAlert
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sound Settings Quick Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAudioSettings() }
                    .testTag("quick_audio_settings_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Звукові налаштування",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Звуки тривог та власні файли",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Прикріпіть власні аудіофайли або налаштуйте гучність",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "Звук →",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gemini AI Civil Defense & Safety Advisor Card
        item {
            AiSafetyCard(
                aiState = aiState,
                onAnalyzeSituation = onAnalyzeWithAi,
                onAskQuestion = onAskAiQuestion,
                onResetState = onResetAiState
            )
        }

        // Monitored Districts Horizontal Bar
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "МОЇ РАЙОНИ (${monitored.size}/${AlertRepository.MAX_PINNED_REGIONS})",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "${activeDangers.size} районів у тривозі",
                        color = if (activeDangers.isNotEmpty()) AlertRed else AlertGreenClear,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(monitored) { region ->
                        RegionChip(
                            region = region,
                            onClick = { onSelectRegion(region) }
                        )
                    }
                }
            }
        }

        // Event History (persisted across days)
        item {
            Column {
                Text(
                    text = "ІСТОРІЯ ОПОВІЩЕНЬ ПО РАЙОНАХ (ЗБЕРЕЖЕНО)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                events.take(15).forEach { event ->
                    AlertEventRow(event = event)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showDistrictPickerDialog) {
        DistrictPickerDialog(
            regions = regions,
            pinnedLimitMessage = pinnedLimitMessage,
            onDismissPinnedLimitMessage = onDismissPinnedLimitMessage,
            onToggleMonitored = { id ->
                val ok = onToggleMonitored(id)
                if (ok) {
                    val reg = regions.find { it.id == id }
                    if (reg != null && !reg.isMonitored) {
                        focusedDistrictId = id
                    }
                }
                ok
            },
            onDismiss = {
                onDismissPinnedLimitMessage()
                showDistrictPickerDialog = false
            }
        )
    }
}

@Composable
private fun DistrictPickerDialog(
    regions: List<RegionItem>,
    pinnedLimitMessage: String?,
    onDismissPinnedLimitMessage: () -> Unit,
    onToggleMonitored: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val pinnedCount = regions.count { it.isMonitored }
    val filtered = regions.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.oblast.contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCardDark,
        title = {
            Column {
                Text(
                    text = "Оберіть райони (${pinnedCount}/${AlertRepository.MAX_PINNED_REGIONS})",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Text(
                    text = "Можна додати до ${AlertRepository.MAX_PINNED_REGIONS} районів або громад",
                    color = UkrYellow,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val activeWarning = localError ?: pinnedLimitMessage
                if (!activeWarning.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AlertRed.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = activeWarning,
                            color = AlertRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Пошук району чи області...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkrYellow,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { region ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (region.isMonitored) Color(0xFF1F2B46) else Color(0xFF101521),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (region.isMonitored) UkrYellow else SurfaceBorderDark
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissPinnedLimitMessage()
                                    val changed = onToggleMonitored(region.id)
                                    localError = if (!changed) {
                                        "Досягнуто максимум (${AlertRepository.MAX_PINNED_REGIONS} райони). Спочатку зніміть один."
                                    } else {
                                        null
                                    }
                                }
                                .testTag("picker_district_${region.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = region.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = region.oblast,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (region.isMonitored) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (region.isMonitored) "Видалити" else "Додати",
                                    tint = if (region.isMonitored) UkrYellow else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово", color = UkrYellow, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun RegionChip(
    region: RegionItem,
    onClick: () -> Unit
) {
    val chipColor = levelMapColor(region.currentLevel)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131826),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (region.currentLevel.isDanger) chipColor else SurfaceBorderDark
            )
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(chipColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = region.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                val extraCount = region.effectiveActiveAlerts.size - 1
                Text(
                    text = if (extraCount > 0) {
                        "${region.currentLevel.shortName} (+$extraCount)"
                    } else {
                        region.currentLevel.shortName
                    },
                    color = chipColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun alertAccentColor(level: AlertLevel): Color = levelMapColor(level)

@Composable
private fun AlertEventRow(event: AlertEventLog) {
    val timeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(event.timestamp))
    val color = levelMapColor(event.level)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111520)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (event.activeLevels.contains(AlertLevel.DRONE) || event.level == AlertLevel.DRONE) {
                    ShahedDroneIcon(size = 22.dp)
                } else {
                    ThreatLevelIcon(
                        level = event.level,
                        size = 18.dp,
                        tint = color
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.regionName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (event.withoutAllClear) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[БЕЗ ВІДБОЮ]",
                                color = UkrYellow,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = timeStr,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = event.threatDetails,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}
