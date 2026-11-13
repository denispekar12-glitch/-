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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    userRole: UserRole,
    regions: List<RegionItem>,
    isApiConnected: Boolean,
    isSyncing: Boolean,
    onlineStatusMessage: String,
    onlineChannelId: String,
    dsnsAutoSyncEnabled: Boolean,
    onOpenPinDialog: () -> Unit,
    onLockToUserRole: () -> Unit,
    onSetAlertLevel: (String, AlertLevel, String) -> Unit,
    onSetRegionActiveAlerts: (String, Set<AlertLevel>, String) -> Unit,
    onToggleRegionAlertType: (String, AlertLevel) -> Unit,
    onSetMultipleRegionsActiveAlerts: (List<String>, Set<AlertLevel>, String) -> Unit,
    onTriggerTotalAllClear: () -> Unit,
    onTriggerStreetFightsAllClear: () -> Unit,
    onSyncNow: () -> Unit,
    onUpdateOnlineChannel: (String) -> Unit,
    onToggleDsnsAutoSync: (Boolean) -> Unit,
    onUpdatePin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (userRole == UserRole.USER) {
        // Restricted View for non-admin users
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("admin_locked_view"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(UkrYellow.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = UkrYellow,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ДОСТУП ОБМЕЖЕНО",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 0.6.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Єдиним адміністратором системи є Денис (denispekar12@gmail.com).\n\nДля вас увімкнено режим користувача: ви отримуєте сповіщення у реальному часі, коли адміністратор або ДСНС оголошує тривогу, вуличні бої чи відбій. Сигнал звучить рівно один раз.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onOpenPinDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("unlock_admin_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Увійти як адміністратор Денис",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    } else {
        // Full Admin Control Room for Denis
        AdminControlPanel(
            regions = regions,
            isApiConnected = isApiConnected,
            isSyncing = isSyncing,
            onlineStatusMessage = onlineStatusMessage,
            onlineChannelId = onlineChannelId,
            dsnsAutoSyncEnabled = dsnsAutoSyncEnabled,
            onLockToUserRole = onLockToUserRole,
            onSetAlertLevel = onSetAlertLevel,
            onSetRegionActiveAlerts = onSetRegionActiveAlerts,
            onToggleRegionAlertType = onToggleRegionAlertType,
            onSetMultipleRegionsActiveAlerts = onSetMultipleRegionsActiveAlerts,
            onTriggerTotalAllClear = onTriggerTotalAllClear,
            onTriggerStreetFightsAllClear = onTriggerStreetFightsAllClear,
            onSyncNow = onSyncNow,
            onUpdateOnlineChannel = onUpdateOnlineChannel,
            onToggleDsnsAutoSync = onToggleDsnsAutoSync,
            onUpdatePin = onUpdatePin,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdminControlPanel(
    regions: List<RegionItem>,
    isApiConnected: Boolean,
    isSyncing: Boolean,
    onlineStatusMessage: String,
    onlineChannelId: String,
    dsnsAutoSyncEnabled: Boolean,
    onLockToUserRole: () -> Unit,
    onSetAlertLevel: (String, AlertLevel, String) -> Unit,
    onSetRegionActiveAlerts: (String, Set<AlertLevel>, String) -> Unit,
    onToggleRegionAlertType: (String, AlertLevel) -> Unit,
    onSetMultipleRegionsActiveAlerts: (List<String>, Set<AlertLevel>, String) -> Unit,
    onTriggerTotalAllClear: () -> Unit,
    onTriggerStreetFightsAllClear: () -> Unit,
    onSyncNow: () -> Unit,
    onUpdateOnlineChannel: (String) -> Unit,
    onToggleDsnsAutoSync: (Boolean) -> Unit,
    onUpdatePin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val kryveOzero = regions.find { it.id == "kryve_ozero" }

    var kryveOzeroComboSelection by remember(kryveOzero?.effectiveActiveAlerts) {
        mutableStateOf(
            kryveOzero?.effectiveActiveAlerts?.toSet()?.ifEmpty {
                setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
            } ?: setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
        )
    }

    var selectedRegionForBroadcast by remember {
        mutableStateOf(kryveOzero ?: regions.firstOrNull())
    }
    var selectedMultiRegionIds by remember {
        mutableStateOf(setOf(kryveOzero?.id ?: "kryve_ozero"))
    }
    var selectedMultiAlertLevels by remember {
        mutableStateOf(setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE))
    }
    var threatNote by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var channelInput by remember(onlineChannelId) { mutableStateOf(onlineChannelId) }
    var channelSavedNotice by remember { mutableStateOf<String?>(null) }

    var newPinText by remember { mutableStateOf("") }
    var pinSuccessMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_control_panel"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Admin Badge Bar - Denis Owner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2438)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(UkrYellow.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = UkrYellow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ГОЛОВНИЙ АДМІНІСТРАТОР",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = UkrYellow
                                ) {
                                    Text(
                                        text = "ДЕНИС",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "denispekar12@gmail.com • Повний доступ",
                                color = UkrYellow,
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLockToUserRole,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("lock_admin_mode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Заблокувати", fontSize = 11.sp)
                    }
                }
            }
        }

        // Live Real-Time Emergency Broadcast for Kryvoozerska hromada
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ПРЯМИЙ ЕФІР: КРИВООЗЕРСЬКА ГРОМАДА (КРИВЕ ОЗЕРО)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Один дотик відправляє сповіщення в реальному часі та озвучує сигнал 1 раз",
                        color = UkrYellow,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSetAlertLevel(
                                    "kryve_ozero",
                                    AlertLevel.RED,
                                    "Повітряна тривога: загроза балістики та БПЛА! Перейдіть в укриття!"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_broadcast_red")
                        ) {
                            Text("🚨 Тривога (1 раз)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onSetAlertLevel(
                                    "kryve_ozero",
                                    AlertLevel.YELLOW,
                                    "Жовтий рівень: підвищена загроза удару!"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertYellow),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_broadcast_yellow")
                        ) {
                            Text("⚠️ Жовтий (1 раз)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSetAlertLevel(
                                "kryve_ozero",
                                AlertLevel.CIVIL_DEFENSE,
                                "Важливе сповіщення цивільного захисту! Ознайомтеся з додатком Омастротривога."
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertCivilDefense),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("admin_broadcast_civil")
                    ) {
                        Text("📢 Важливе сповіщення цивільного захисту (1 раз)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSetAlertLevel(
                                    "kryve_ozero",
                                    AlertLevel.STREET_FIGHTS,
                                    "Вуличні бої в межах населеного пункту! Залишайтеся в укритті і не підходьте до вікон!"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertOrangeStreetFight),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_broadcast_street")
                        ) {
                            Text("🛑 Вуличні бої", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onSetAlertLevel(
                                    "kryve_ozero",
                                    AlertLevel.SAFE,
                                    "Відбій повітряної тривоги. Небезпека минула."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertGreenClear),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_broadcast_clear")
                        ) {
                            Text("🟢 Відбій (1 раз)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    // Active multi-alert status chips for Kryve Ozero
                    val activeKoAlerts = kryveOzero?.effectiveActiveAlerts.orEmpty()
                    if (activeKoAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ЗАРАЗ АКТИВНО (натисніть × щоб зняти окремо):",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activeKoAlerts.forEach { lvl ->
                                val c = adminAlertColor(lvl)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = c.copy(alpha = 0.22f),
                                    modifier = Modifier.clickable {
                                        onToggleRegionAlertType("kryve_ozero", lvl)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = lvl.shortName,
                                            color = c,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Зняти ${lvl.shortName}",
                                            tint = c,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-alert combination builder for Kryve Ozero
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E1320), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .testTag("admin_multi_alert_builder")
                    ) {
                        Text(
                            text = "ЗАПУСТИТИ ДЕКІЛЬКА ТРИВОГ ОДНОЧАСНО",
                            color = UkrYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Наприклад: Вуличні бої + Оповіщення цивільного захисту",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AlertLevel.dangerLevels.forEach { lvl ->
                                val picked = kryveOzeroComboSelection.contains(lvl)
                                val chipColor = adminAlertColor(lvl)
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        kryveOzeroComboSelection = if (picked) {
                                            kryveOzeroComboSelection - lvl
                                        } else {
                                            kryveOzeroComboSelection + lvl
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
                                        containerColor = SurfaceCardDark,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val preset = setOf(AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE)
                                    kryveOzeroComboSelection = preset
                                    onSetRegionActiveAlerts(
                                        "kryve_ozero",
                                        preset,
                                        "Вуличні бої та важливе сповіщення цивільного захисту!"
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("admin_preset_street_civil_btn")
                            ) {
                                Text("Бої + Цив. захист", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (kryveOzeroComboSelection.isNotEmpty()) {
                                        onSetRegionActiveAlerts("kryve_ozero", kryveOzeroComboSelection, "")
                                    }
                                },
                                enabled = kryveOzeroComboSelection.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UkrYellow,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("admin_launch_multi_ko_btn")
                            ) {
                                Text(
                                    text = "Оголосити (${kryveOzeroComboSelection.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // Master All-Clear Controls (Весь відбій по Україні та Відбій вуличних боїв)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E17)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(AlertGreenClear, UkrYellow))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_master_clear_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AlertGreenClear.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AlertGreenClear,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ТОТАЛЬНИЙ ВІДБІЙ ТА ВУЛИЧНІ БОЇ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Миттєве скасування небезпеки одним дотиком",
                                    color = AlertGreenClear,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1-Tap Total All Clear across all Ukraine
                    Button(
                        onClick = onTriggerTotalAllClear,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlertGreenClear,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_trigger_total_clear_btn")
                    ) {
                        Text(
                            text = "🟢 ВЕСЬ ВІДБІЙ (ДЛЯ ВСІЄЇ УКРАЇНИ)",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1-Tap Street Fights All Clear
                    Button(
                        onClick = onTriggerStreetFightsAllClear,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3A2B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("admin_trigger_street_fights_clear_btn")
                    ) {
                        Text(
                            text = "🛡️ ВІДБІЙ ВУЛИЧНИХ БОЇВ (СИТУАЦІЯ ПІД КОНТРОЛЕМ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AlertGreenClear
                        )
                    }
                }
            }
        }

        // Broadcast multiple alerts across one or multiple regions
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ОГОЛОШЕННЯ КІЛЬКОХ ТРИВОГ ПО ОБЛАСТЯХ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Оберіть одну чи кілька областей та поєднайте потрібні типи тривог",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedRegionForBroadcast?.name ?: "Виберіть область",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Додати область / громаду до розсилки") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UkrYellow,
                                unfocusedBorderColor = SurfaceBorderDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            regions.forEach { reg ->
                                DropdownMenuItem(
                                    text = { Text(reg.name) },
                                    onClick = {
                                        selectedRegionForBroadcast = reg
                                        selectedMultiRegionIds = selectedMultiRegionIds + reg.id
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedMultiRegionIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Обрані регіони (${selectedMultiRegionIds.size}):",
                            color = UkrYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedMultiRegionIds.forEach { regId ->
                                val reg = regions.find { it.id == regId }
                                if (reg != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = UkrBlue.copy(alpha = 0.25f),
                                        modifier = Modifier.clickable {
                                            if (selectedMultiRegionIds.size > 1) {
                                                selectedMultiRegionIds = selectedMultiRegionIds - regId
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = reg.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (selectedMultiRegionIds.size > 1) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Видалити",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Оберіть одну або кілька тривог:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AlertLevel.dangerLevels.forEach { lvl ->
                            val picked = selectedMultiAlertLevels.contains(lvl)
                            val c = adminAlertColor(lvl)
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selectedMultiAlertLevels = if (picked) {
                                        selectedMultiAlertLevels - lvl
                                    } else {
                                        selectedMultiAlertLevels + lvl
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
                                    selectedContainerColor = c.copy(alpha = 0.28f),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF101522),
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = threatNote,
                        onValueChange = { threatNote = it },
                        label = { Text("Коментар загрози (наприклад: шахеди, вуличні бої)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UkrYellow,
                            unfocusedBorderColor = SurfaceBorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedMultiRegionIds.isNotEmpty() && selectedMultiAlertLevels.isNotEmpty()) {
                                    onSetMultipleRegionsActiveAlerts(
                                        selectedMultiRegionIds.toList(),
                                        selectedMultiAlertLevels,
                                        threatNote
                                    )
                                }
                            },
                            enabled = selectedMultiRegionIds.isNotEmpty() && selectedMultiAlertLevels.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(44.dp)
                                .testTag("admin_multi_region_broadcast_btn")
                        ) {
                            Text(
                                text = "🚨 Оголосити (${selectedMultiAlertLevels.size} для ${selectedMultiRegionIds.size} рег.)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (selectedMultiRegionIds.isNotEmpty()) {
                                    onSetMultipleRegionsActiveAlerts(
                                        selectedMultiRegionIds.toList(),
                                        emptySet(),
                                        "Відбій загрози. Спокійно."
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertGreenClear,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(44.dp)
                        ) {
                            Text("Відбій", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Online Sync & Persistent Auto-Save Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isApiConnected) AlertGreenClear.copy(alpha = 0.6f) else SurfaceBorderDark
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_online_sync_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isApiConnected) AlertGreenClear else AlertRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ОНЛАЙН-ЕФІР ТА АВТОЗБЕРЕЖЕННЯ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = onlineStatusMessage,
                                    color = if (isApiConnected) AlertGreenClear else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("admin_sync_now_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSyncing) "Синхр..." else "Оновити",
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Офіційний онлайн-канал тривог України",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Автоматично оновлює області в реальному часі",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = dsnsAutoSyncEnabled,
                            onCheckedChange = onToggleDsnsAutoSync,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AlertGreenClear
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = {
                            channelInput = it
                            channelSavedNotice = null
                        },
                        label = { Text("Канал синхронізації (однаковий на всіх пристроях)") },
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

                    if (channelSavedNotice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = channelSavedNotice ?: "",
                            color = AlertGreenClear,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onUpdateOnlineChannel(channelInput)
                            channelSavedNotice = "✓ Онлайн-канал збережено та підключено!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Text("Зберегти канал та синхронізувати", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Усі тривоги, відстежувані громади, чат і звуки зберігаються автоматично.",
                        color = AlertGreenClear,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Change Admin PIN Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "БЕЗПЕКА ТА ЗМІНА СЕКРЕТНОГО PIN-КОДУ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = {
                            if (it.length <= 8) {
                                newPinText = it
                                pinSuccessMessage = null
                            }
                        },
                        label = { Text("Новий PIN-код для входу адміністратора") },
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

                    if (pinSuccessMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pinSuccessMessage ?: "",
                            color = AlertGreenClear,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newPinText.isNotBlank()) {
                                onUpdatePin(newPinText)
                                pinSuccessMessage = "✓ Новий PIN-код успішно збережено!"
                                newPinText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockReset,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Зберегти новий PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun adminAlertColor(level: AlertLevel): Color = when (level) {
    AlertLevel.RED -> AlertRed
    AlertLevel.YELLOW -> AlertYellow
    AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
    AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
    AlertLevel.SAFE -> AlertGreenClear
}
