package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BroadcastOnPersonal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.AdminBroadcast
import com.example.model.AlertEventLog
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.model.UserRole
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertOrangeStreetFight
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertYellow
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
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
    onDismissBroadcast: () -> Unit,
    onAddKryveOzero: () -> Unit,
    onSelectRegion: (RegionItem) -> Unit,
    onTriggerKryveOzero: (AlertLevel) -> Unit,
    onTriggerKryveOzeroMultiple: (Set<AlertLevel>) -> Unit,
    onToggleKryveOzeroAlert: (AlertLevel) -> Unit,
    onTriggerTotalAllClear: () -> Unit,
    onTriggerStreetFightsAllClear: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kryveOzero = regions.find { it.id == "kryve_ozero" }
    val monitored = regions.filter { it.isMonitored }
    val activeDangers = regions.filter { it.currentLevel.isDanger }
    var multiSelectedAlerts by remember(kryveOzero?.effectiveActiveAlerts) {
        mutableStateOf(
            kryveOzero?.effectiveActiveAlerts?.toSet()?.ifEmpty {
                setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
            } ?: setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time Admin Broadcast Banner (appears live when Denis presses a button)
        if (latestAdminBroadcast != null) {
            item {
                val broadcastColor = when (latestAdminBroadcast.level) {
                    AlertLevel.RED -> AlertRed
                    AlertLevel.YELLOW -> AlertYellow
                    AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                    AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                    AlertLevel.SAFE -> AlertGreenClear
                }

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
                                Icon(
                                    imageVector = Icons.Default.BroadcastOnPersonal,
                                    contentDescription = null,
                                    tint = broadcastColor,
                                    modifier = Modifier.size(22.dp)
                                )
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
                                    text = "Сигнал прозвучав по черзі (по 1 разу) у реальному часі",
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

        // Quick 1-tap Priority Button for Kryve Ozero
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(UkrYellow.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Криве Озеро",
                                tint = UkrYellow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Кривоозерська громада",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = UkrBlue
                                ) {
                                    Text(
                                        text = "Миколаївщина",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (kryveOzero?.isMonitored == true) "✓ Підключено та відстежується у реальному часі" else "Натисніть для підключення моніторингу",
                                color = if (kryveOzero?.isMonitored == true) AlertGreenClear else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    ElevatedButton(
                        onClick = onAddKryveOzero,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (kryveOzero?.isMonitored == true) Color(0xFF1E273A) else UkrYellow,
                            contentColor = if (kryveOzero?.isMonitored == true) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_add_kryve_ozero")
                    ) {
                        Icon(
                            imageVector = if (kryveOzero?.isMonitored == true) Icons.Default.Check else Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (kryveOzero?.isMonitored == true) "Активно" else "Додати",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Hero Alert Status Card (Main visual indicator for Kryve Ozero)
        item {
            val statusLevel = kryveOzero?.currentLevel ?: AlertLevel.SAFE
            val isAlertActive = statusLevel.isDanger

            val cardBg = when (statusLevel) {
                AlertLevel.RED -> Brush.verticalGradient(listOf(Color(0xFF450D15), Color(0xFF22080D)))
                AlertLevel.YELLOW -> Brush.verticalGradient(listOf(Color(0xFF382906), Color(0xFF1F1604)))
                AlertLevel.STREET_FIGHTS -> Brush.verticalGradient(listOf(Color(0xFF4D2207), Color(0xFF241003)))
                AlertLevel.CIVIL_DEFENSE -> Brush.verticalGradient(listOf(Color(0xFF3E124A), Color(0xFF1F0925)))
                AlertLevel.SAFE -> Brush.verticalGradient(listOf(Color(0xFF0D2B1C), Color(0xFF071910)))
            }

            val accentColor = when (statusLevel) {
                AlertLevel.RED -> AlertRed
                AlertLevel.YELLOW -> AlertYellow
                AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                AlertLevel.SAFE -> AlertGreenClear
            }

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
                            val activeAlerts = kryveOzero?.effectiveActiveAlerts.orEmpty()
                            if (activeAlerts.isEmpty()) {
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
                                            fontSize = 12.sp,
                                            letterSpacing = 0.6.sp
                                        )
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
                                                Modifier.clickable { onToggleKryveOzeroAlert(lvl) }
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
                                }
                            }

                            // Quick Map Shortcut
                            OutlinedButton(
                                onClick = onOpenMap,
                                shape = RoundedCornerShape(10.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Мапа тривог", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Кривоозерська громада",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )

                        Text(
                            text = if (isAlertActive) {
                                kryveOzero?.threatDetails ?: "Небезпека в регіоні! Перебувайте в укритті!"
                            } else {
                                "Повітряна тривога відсутня. Обстановка спокійна."
                            },
                            color = if (isAlertActive) Color.White.copy(alpha = 0.95f) else TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (kryveOzero?.alertStartTime != null && isAlertActive) {
                            val elapsedMinutes = (System.currentTimeMillis() - kryveOzero.alertStartTime) / (60 * 1000)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Триває вже: ${elapsedMinutes.coerceAtLeast(1)} хв",
                                color = accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Fast real-time broadcast controls for Admin Denis
                        if (userRole == UserRole.ADMIN) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Оголосити в реальному часі (Адмін):",
                                    color = UkrYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Звучить 1 раз",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                    onClick = { onTriggerKryveOzero(AlertLevel.RED) },
                                    colors = ButtonDefaults.elevatedButtonColors(containerColor = AlertRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("admin_trigger_red_btn")
                                ) {
                                    Text("Тривога", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                ElevatedButton(
                                    onClick = { onTriggerKryveOzero(AlertLevel.STREET_FIGHTS) },
                                    colors = ButtonDefaults.elevatedButtonColors(containerColor = AlertOrangeStreetFight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("admin_trigger_street_btn")
                                ) {
                                    Text("Вуличні бої", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                    onClick = { onTriggerKryveOzero(AlertLevel.CIVIL_DEFENSE) },
                                    colors = ButtonDefaults.elevatedButtonColors(containerColor = AlertCivilDefense),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("admin_trigger_civil_btn")
                                ) {
                                    Text("Цив. захист", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                ElevatedButton(
                                    onClick = { onTriggerKryveOzero(AlertLevel.SAFE) },
                                    colors = ButtonDefaults.elevatedButtonColors(containerColor = AlertGreenClear),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("admin_trigger_clear_btn")
                                 ) {
                                    Text("Відбій", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                    onClick = onTriggerTotalAllClear,
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = AlertGreenClear,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("dashboard_total_clear_btn")
                                ) {
                                    Text("🟢 ВЕСЬ ВІДБІЙ", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }

                                ElevatedButton(
                                    onClick = onTriggerStreetFightsAllClear,
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = Color(0xFF1E3A2B),
                                        contentColor = AlertGreenClear
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("dashboard_street_fights_clear_btn")
                                ) {
                                    Text("🛡️ Відбій боїв", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Multi-Alert Simultaneous Builder
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x660B0F1A), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .testTag("dashboard_multi_alert_builder")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "КОМБІНУВАТИ ДЕКІЛЬКА ТРИВОГ:",
                                        color = UkrYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Обрано: ${multiSelectedAlerts.size}",
                                        color = AlertGreenClear,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AlertLevel.dangerLevels.forEach { lvl ->
                                        val isPicked = multiSelectedAlerts.contains(lvl)
                                        val chipColor = alertAccentColor(lvl)
                                        FilterChip(
                                            selected = isPicked,
                                            onClick = {
                                                multiSelectedAlerts = if (isPicked) {
                                                    multiSelectedAlerts - lvl
                                                } else {
                                                    multiSelectedAlerts + lvl
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = lvl.shortName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = chipColor.copy(alpha = 0.28f),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0xFF131928),
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                ElevatedButton(
                                    onClick = {
                                        if (multiSelectedAlerts.isNotEmpty()) {
                                            onTriggerKryveOzeroMultiple(multiSelectedAlerts)
                                        }
                                    },
                                    enabled = multiSelectedAlerts.isNotEmpty(),
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = UkrYellow,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("dashboard_launch_multi_alert_btn")
                                ) {
                                    Text(
                                        text = if (multiSelectedAlerts.size > 1) {
                                            "🚨 Оголосити одночасно (${multiSelectedAlerts.size})"
                                        } else if (multiSelectedAlerts.size == 1) {
                                            "🚨 Оголосити обрану тривогу"
                                        } else {
                                            "Оберіть кілька тривог вище"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sound Settings Quick Card (Highlighting single playback mode)
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
                                text = "Звукові сигнали (звучать 1 раз)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Одноразовий голосовий сигнал без повторів та затримок",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "Налаштування →",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Monitored Regions Horizontal Bar
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ВІДСТЕЖУВАНІ РЕГІОНИ (${monitored.size})",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "${activeDangers.size} у тривозі по Україні",
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

        // Live DSNS alerts event feed
        item {
            Column {
                Text(
                    text = "ОСТАННІ ОПОВІЩЕННЯ У РЕАЛЬНОМУ ЧАСІ",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                events.take(8).forEach { event ->
                    AlertEventRow(event = event)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RegionChip(
    region: RegionItem,
    onClick: () -> Unit
) {
    val chipColor = when (region.currentLevel) {
        AlertLevel.RED -> AlertRed
        AlertLevel.YELLOW -> AlertYellow
        AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
        AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
        AlertLevel.SAFE -> AlertGreenClear
    }

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

private fun alertAccentColor(level: AlertLevel): Color = when (level) {
    AlertLevel.RED -> AlertRed
    AlertLevel.YELLOW -> AlertYellow
    AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
    AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
    AlertLevel.SAFE -> AlertGreenClear
}

@Composable
private fun AlertEventRow(event: AlertEventLog) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(event.timestamp))

    val color = when (event.level) {
        AlertLevel.RED -> AlertRed
        AlertLevel.YELLOW -> AlertYellow
        AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
        AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
        AlertLevel.SAFE -> AlertGreenClear
    }

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
                Icon(
                    imageVector = if (event.level.isDanger) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.regionName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
