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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.components.ShahedDroneIcon
import com.example.ui.components.ThreatLevelIcon
import com.example.ui.components.levelMapColor
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
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
    onSetAlertLevel: (String, AlertLevel, String, Boolean) -> Unit,
    onSetRegionActiveAlerts: (String, Set<AlertLevel>, String, Boolean) -> Unit,
    onToggleRegionAlertType: (String, AlertLevel, Boolean) -> Unit,
    onSetMultipleRegionsActiveAlerts: (List<String>, Set<AlertLevel>, String, Boolean) -> Unit,
    onClearRegion: (String, Boolean) -> Unit,
    onTriggerTotalAllClear: (Boolean) -> Unit,
    onTriggerAllUkraineAlert: (AlertLevel, Set<AlertLevel>, String, Boolean) -> Unit = { _, _, _, _ -> },
    onTriggerStreetFightsAllClear: (Boolean) -> Unit,
    onTriggerSpecificAllClear: (String, AlertLevel, Boolean) -> Unit = { _, _, _ -> },
    onSyncNow: () -> Unit,
    onUpdateOnlineChannel: (String) -> Unit,
    onToggleDsnsAutoSync: (Boolean) -> Unit,
    onUpdatePin: (String) -> Unit,
    modifier: Modifier = Modifier,
    userEmail: String = "",
    isVerifiedAdmin: Boolean = false,
    onSignOut: () -> Unit = {}
) {
    if (userRole == UserRole.USER) {
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

                    if (userEmail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ваш акаунт: $userEmail",
                            color = UkrYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Адміністраторами системи є Денис (denispekar12@gmail.com) та другий адміністратор (semenenkom342@gmail.com).\n\nДля вас увімкнено режим користувача: ви отримуєте сповіщення по районах у реальному часі, коли адміністратор оголошує тривогу чи відбій.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    if (isVerifiedAdmin) {
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
                                text = "Розблокувати панель адміністратора",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_switch_account_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = UkrYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Змінити Google-акаунт",
                            color = UkrYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    } else {
        AdminControlPanel(
            regions = regions,
            isApiConnected = isApiConnected,
            isSyncing = isSyncing,
            onlineStatusMessage = onlineStatusMessage,
            onlineChannelId = onlineChannelId,
            onLockToUserRole = onLockToUserRole,
            onSetAlertLevel = onSetAlertLevel,
            onSetRegionActiveAlerts = onSetRegionActiveAlerts,
            onToggleRegionAlertType = onToggleRegionAlertType,
            onSetMultipleRegionsActiveAlerts = onSetMultipleRegionsActiveAlerts,
            onClearRegion = onClearRegion,
            onTriggerTotalAllClear = onTriggerTotalAllClear,
            onTriggerAllUkraineAlert = onTriggerAllUkraineAlert,
            onTriggerStreetFightsAllClear = onTriggerStreetFightsAllClear,
            onTriggerSpecificAllClear = onTriggerSpecificAllClear,
            onSyncNow = onSyncNow,
            onUpdateOnlineChannel = onUpdateOnlineChannel,
            onUpdatePin = onUpdatePin,
            userEmail = userEmail,
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
    onLockToUserRole: () -> Unit,
    onSetAlertLevel: (String, AlertLevel, String, Boolean) -> Unit,
    onSetRegionActiveAlerts: (String, Set<AlertLevel>, String, Boolean) -> Unit,
    onToggleRegionAlertType: (String, AlertLevel, Boolean) -> Unit,
    onSetMultipleRegionsActiveAlerts: (List<String>, Set<AlertLevel>, String, Boolean) -> Unit,
    onClearRegion: (String, Boolean) -> Unit,
    onTriggerTotalAllClear: (Boolean) -> Unit,
    onTriggerAllUkraineAlert: (AlertLevel, Set<AlertLevel>, String, Boolean) -> Unit,
    onTriggerStreetFightsAllClear: (Boolean) -> Unit,
    onTriggerSpecificAllClear: (String, AlertLevel, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onUpdateOnlineChannel: (String) -> Unit,
    onUpdatePin: (String) -> Unit,
    userEmail: String = "",
    modifier: Modifier = Modifier
) {
    val kryveOzero = regions.find { it.id == "kryve_ozero" }

    var noAllClearFlag by remember { mutableStateOf(false) }

    var kryveOzeroComboSelection by remember(kryveOzero?.effectiveActiveAlerts) {
        mutableStateOf(
            kryveOzero?.effectiveActiveAlerts?.toSet()?.ifEmpty {
                setOf(AlertLevel.RED, AlertLevel.DRONE)
            } ?: setOf(AlertLevel.RED, AlertLevel.DRONE)
        )
    }

    var selectedRegionForBroadcast by remember {
        mutableStateOf(kryveOzero ?: regions.firstOrNull())
    }
    var selectedMultiRegionIds by remember {
        mutableStateOf(setOf(kryveOzero?.id ?: "kryve_ozero"))
    }
    var selectedMultiAlertLevels by remember {
        mutableStateOf(setOf(AlertLevel.RED, AlertLevel.MISSILE))
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
        // Admin Badge Bar
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
                                    text = "АДМІНІСТРАТОР СИСТЕМИ",
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
                                        text = if (userEmail.equals("semenenkom342@gmail.com", ignoreCase = true)) {
                                            "АДМІН #2 • v2.0"
                                        } else {
                                            "ДЕНИС • v2.0"
                                        },
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${userEmail.ifBlank { "denispekar12@gmail.com" }} • Версія 2.0",
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

        // Global Mode Toggle: Без відбою
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (noAllClearFlag) UkrYellow else SurfaceBorderDark
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим оголошення: БЕЗ ВІДБОЮ",
                            color = if (noAllClearFlag) UkrYellow else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Якщо увімкнено — позначає сигнал як [БЕЗ ВІДБОЮ] і не відтворює сирену відбою",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = noAllClearFlag,
                        onCheckedChange = { noAllClearFlag = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = UkrYellow,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceBorderDark
                        )
                    )
                }
            }
        }

        // Live Broadcast for Kryvoozerska hromada
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
                        text = "КРИВООЗЕРСЬКА ГРОМАДА (ПЕРВОМАЙСЬКИЙ Р-Н)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Усі типи тривог • Зберігається навіть на наступний день",
                        color = UkrYellow,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onTriggerAllUkraineAlert(
                                AlertLevel.RED,
                                setOf(AlertLevel.RED),
                                threatNote,
                                noAllClearFlag
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD92638),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_all_ukraine_alert_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = UkrYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var adminThreatTab by remember { mutableIntStateOf(0) }
                    val adminTabNames = listOf("Повітря / Шахед", "Бої / Арта / ЦЗ", "Хімія / Радіо")
                    SecondaryTabRow(
                        selectedTabIndex = adminThreatTab,
                        containerColor = Color(0xFF0E1320),
                        contentColor = UkrYellow,
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(adminThreatTab),
                                color = UkrYellow
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        adminTabNames.forEachIndexed { idx, name ->
                            Tab(
                                selected = adminThreatTab == idx,
                                onClick = { adminThreatTab = idx },
                                text = {
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (adminThreatTab == idx) UkrYellow else TextSecondary
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val tabLevels = when (adminThreatTab) {
                        0 -> listOf(AlertLevel.RED, AlertLevel.MISSILE, AlertLevel.DRONE, AlertLevel.YELLOW)
                        1 -> listOf(AlertLevel.ARTILLERY, AlertLevel.STREET_FIGHTS, AlertLevel.CIVIL_DEFENSE, AlertLevel.CUSTOM_ALERT)
                        else -> listOf(AlertLevel.CHEMICAL, AlertLevel.RADIATION)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabLevels.forEach { lvl ->
                            val c = levelMapColor(lvl)
                            val tag = when (lvl) {
                                AlertLevel.RED -> "admin_broadcast_red"
                                AlertLevel.YELLOW -> "admin_broadcast_yellow"
                                AlertLevel.CIVIL_DEFENSE -> "admin_broadcast_civil"
                                AlertLevel.STREET_FIGHTS -> "admin_broadcast_street"
                                else -> "admin_broadcast_${lvl.name.lowercase()}"
                            }
                            val darkInk = lvl == AlertLevel.YELLOW || lvl == AlertLevel.DRONE || lvl == AlertLevel.CHEMICAL || lvl == AlertLevel.RADIATION
                            Button(
                                onClick = {
                                    onSetAlertLevel("kryve_ozero", lvl, lvl.defaultThreatNote(), noAllClearFlag)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c,
                                    contentColor = if (darkInk) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag(tag)
                            ) {
                                ThreatLevelIcon(
                                    level = lvl,
                                    size = 16.dp,
                                    tint = if (lvl == AlertLevel.DRONE) Color(0xFFB91C1C) else if (darkInk) Color.Black else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(lvl.shortName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Both Відбій AND Без відбою buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onClearRegion("kryve_ozero", true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertGreenClear,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("admin_broadcast_clear")
                        ) {
                            Text("🟢 З відбоєм", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onClearRegion("kryve_ozero", false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF282210),
                                contentColor = UkrYellow
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("admin_broadcast_clear_silent")
                        ) {
                            Text("🔇 Без відбою", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

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
                                val c = levelMapColor(lvl)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = c.copy(alpha = 0.22f),
                                    modifier = Modifier.clickable {
                                        onToggleRegionAlertType("kryve_ozero", lvl, noAllClearFlag)
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

                    // Multi-alert combination builder
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
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AlertLevel.dangerLevels.forEach { lvl ->
                                val picked = kryveOzeroComboSelection.contains(lvl)
                                val chipColor = levelMapColor(lvl)
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
                                        "Вуличні бої та важливе сповіщення цивільного захисту!",
                                        noAllClearFlag
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
                                        onSetRegionActiveAlerts("kryve_ozero", kryveOzeroComboSelection, "", noAllClearFlag)
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

        // Master Clear Controls (З відбоєм та Без відбою)
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
                                text = "ВІДБІЙ ТА ЗНЯТТЯ ТРИВОГ ПО ВСІХ РАЙОНАХ",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Можна зняти з голосовим відбоєм або тихо (без відбою)",
                                color = AlertGreenClear,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onTriggerTotalAllClear(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertGreenClear,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_trigger_total_clear_btn")
                        ) {
                            Text("🟢 ВЕСЬ ВІДБІЙ", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onTriggerTotalAllClear(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF282210),
                                contentColor = UkrYellow
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_trigger_total_silent_clear_btn")
                        ) {
                            Text("🔇 Зняти без відбою", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onTriggerStreetFightsAllClear(noAllClearFlag) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3A2B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("admin_trigger_street_fights_clear_btn")
                    ) {
                        Text(
                            text = if (noAllClearFlag) "🛡️ Зняти вуличні бої (без відбою)" else "🛡️ ВІДБІЙ ВУЛИЧНИХ БОЇВ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AlertGreenClear
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val targetClearRegionId = selectedRegionForBroadcast?.id ?: "kryve_ozero"
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val specificClears = listOf(
                            Triple(AlertLevel.CIVIL_DEFENSE, "📢 Відбій цив. захисту", "admin_trigger_civil_defense_clear_btn"),
                            Triple(AlertLevel.ARTILLERY, "💥 Відбій артобстрілу", "admin_trigger_artillery_clear_btn"),
                            Triple(AlertLevel.CHEMICAL, "☣️ Відбій хімії", "admin_trigger_chemical_clear_btn"),
                            Triple(AlertLevel.RADIATION, "☢️ Відбій радіації", "admin_trigger_radiation_clear_btn")
                        )
                        specificClears.forEach { (lvl, label, tag) ->
                            Button(
                                onClick = { onTriggerSpecificAllClear(targetClearRegionId, lvl, noAllClearFlag) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E3A2B),
                                    contentColor = AlertGreenClear
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag(tag)
                            ) {
                                Text(
                                    text = if (noAllClearFlag) "$label (без відбою)" else label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Broadcast multiple alerts across one or multiple districts (райони)
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
                        text = "ОГОЛОШЕННЯ ТРИВОГ ПО РАЙОНАХ ТА ГРОМАДАХ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Оберіть один чи кілька районів, потрібні загрози та режим відбою",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedRegionForBroadcast?.name ?: "Виберіть район",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Додати район / громаду до розсилки") },
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
                                    text = { Text("${reg.name} (${reg.oblast})") },
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
                            text = "Обрані райони (${selectedMultiRegionIds.size}):",
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
                        text = "Оберіть одну або кілька загроз:",
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
                            val c = levelMapColor(lvl)
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
                        label = { Text("Коментар (наприклад: балістика на район / шахеди)") },
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

                    Button(
                        onClick = {
                            if (selectedMultiRegionIds.isNotEmpty() && selectedMultiAlertLevels.isNotEmpty()) {
                                onSetMultipleRegionsActiveAlerts(
                                    selectedMultiRegionIds.toList(),
                                    selectedMultiAlertLevels,
                                    threatNote,
                                    noAllClearFlag
                                )
                            }
                        },
                        enabled = selectedMultiRegionIds.isNotEmpty() && selectedMultiAlertLevels.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("admin_multi_region_broadcast_btn")
                    ) {
                        Text(
                            text = "🚨 Оголосити (${selectedMultiAlertLevels.size} для ${selectedMultiRegionIds.size} р-нів)" +
                                if (noAllClearFlag) " [Без відбою]" else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedMultiRegionIds.isNotEmpty()) {
                                    onSetMultipleRegionsActiveAlerts(
                                        selectedMultiRegionIds.toList(),
                                        emptySet(),
                                        "Відбій загрози. Спокійно.",
                                        false
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertGreenClear,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text("🟢 З відбоєм", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (selectedMultiRegionIds.isNotEmpty()) {
                                    onSetMultipleRegionsActiveAlerts(
                                        selectedMultiRegionIds.toList(),
                                        emptySet(),
                                        "Тривогу знято (без відбою).",
                                        true
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF282210),
                                contentColor = UkrYellow
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text("🔇 Без відбою", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    text = "ДОВГОТРИВАЛЕ ЗБЕРЕЖЕННЯ ТА СИНХРОНІЗАЦІЯ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
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
                        text = "✓ Усі тривоги по районах, обрані до 3 райони, історія, чат і звуки не зникають на другий день.",
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
