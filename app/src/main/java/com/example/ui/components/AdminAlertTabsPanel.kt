package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertRed
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrBlue
import com.example.ui.theme.UkrYellow

private data class TabThreatButton(
    val level: AlertLevel,
    val label: String,
    val testTag: String
)

/**
 * Tab-based Admin control panel allowing Denis to declare/clear alerts strictly via organized category tabs
 * and target chips (Selected District OR Whole Ukraine), without having to interact with the map.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminAlertTabsPanel(
    regions: List<RegionItem>,
    focusedRegion: RegionItem,
    noAllClearMode: Boolean,
    onNoAllClearChange: (Boolean) -> Unit,
    onFocusDistrict: (String) -> Unit,
    onOpenDistrictPicker: () -> Unit,
    onTriggerRegionAlert: (String, AlertLevel, Boolean) -> Unit,
    onTriggerRegionMultiple: (String, Set<AlertLevel>, Boolean) -> Unit,
    onClearRegion: (String, Boolean) -> Unit,
    onTriggerTotalAllClear: (Boolean) -> Unit,
    onTriggerStreetFightsAllClear: (String, Boolean) -> Unit,
    onTriggerSpecificAllClear: (String, AlertLevel, Boolean) -> Unit,
    onTriggerAllUkraineAlert: (AlertLevel, Set<AlertLevel>, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryTab by remember { mutableIntStateOf(0) }
    var targetAllUkraine by remember { mutableStateOf(false) }

    var multiSelectedAlerts by remember(focusedRegion.id, focusedRegion.effectiveActiveAlerts) {
        mutableStateOf(
            focusedRegion.effectiveActiveAlerts.toSet().ifEmpty {
                setOf(AlertLevel.RED, AlertLevel.DRONE)
            }
        )
    }

    val categoryTabs = listOf(
        "Повітря / Шахед",
        "Бої / Арта / ЦЗ",
        "Хімія / Радіо",
        "Комбо / Відбій"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Big Master Button: Alert Across ALL Ukraine!
        ElevatedButton(
            onClick = { onTriggerAllUkraineAlert(AlertLevel.RED, setOf(AlertLevel.RED), noAllClearMode) },
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFFD92638),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(UkrYellow, Color(0xFFFF8A80))),
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("dashboard_all_ukraine_alert_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Вся Україна",
                tint = UkrYellow,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Target Selector Row & "Без відбою" Switch (declare alerts without touching the map)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ОГОЛОСИТИ ПО ВКЛАДКАХ (БЕЗ МАПИ):",
                    color = UkrYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (targetAllUkraine) {
                        "Ціль: 🇺🇦 Вся територія України"
                    } else {
                        "Ціль: 📍 ${focusedRegion.name}"
                    },
                    color = if (targetAllUkraine) Color(0xFFFCA5A5) else TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Без відбою",
                    color = if (noAllClearMode) UkrYellow else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = noAllClearMode,
                    onCheckedChange = onNoAllClearChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = UkrYellow,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceBorderDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Target District / Whole Ukraine quick pills so Admin never needs to open Map
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = targetAllUkraine,
                    onClick = { targetAllUkraine = !targetAllUkraine },
                    label = {
                        Text(
                            text = "🇺🇦 Вся Україна",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlertRed.copy(alpha = 0.35f),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF131928),
                        labelColor = Color(0xFFFCA5A5)
                    ),
                    modifier = Modifier.testTag("tab_target_all_ukraine_chip")
                )
            }
            val quickTargets = regions.filter { it.isMonitored || it.id == focusedRegion.id }
                .distinctBy { it.id }
            items(quickTargets, key = { "tgt_${it.id}" }) { reg ->
                val isSelected = !targetAllUkraine && focusedRegion.id == reg.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        targetAllUkraine = false
                        onFocusDistrict(reg.id)
                    },
                    label = {
                        Text(
                            text = reg.name.substringBefore(" ("),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UkrBlue.copy(alpha = 0.45f),
                        selectedLabelColor = UkrYellow,
                        containerColor = Color(0xFF131928),
                        labelColor = TextSecondary
                    )
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onOpenDistrictPicker,
                    label = { Text("Інший район...", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF131928),
                        labelColor = UkrYellow
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Organized Category Tabs (Material 3 SecondaryTabRow)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0E1422),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                SecondaryTabRow(
                    selectedTabIndex = selectedCategoryTab,
                    containerColor = Color(0xFF131A2B),
                    contentColor = UkrYellow,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedCategoryTab),
                            height = 2.5.dp,
                            color = UkrYellow
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categoryTabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedCategoryTab == idx,
                            onClick = { selectedCategoryTab = idx },
                            modifier = Modifier.testTag("alert_category_tab_$idx"),
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 10.sp,
                                    fontWeight = if (selectedCategoryTab == idx) FontWeight.Black else FontWeight.SemiBold,
                                    color = if (selectedCategoryTab == idx) UkrYellow else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                    when (selectedCategoryTab) {
                        0 -> ThreatButtonsGrid(
                            buttons = listOf(
                                TabThreatButton(AlertLevel.RED, "Повітряна тривога", "admin_trigger_red_btn"),
                                TabThreatButton(AlertLevel.MISSILE, "Ракета / Балістика", "admin_trigger_missile_btn"),
                                TabThreatButton(AlertLevel.DRONE, "Шахед / БПЛА", "admin_trigger_drone_btn"),
                                TabThreatButton(AlertLevel.YELLOW, "Жовтий рівень", "admin_trigger_yellow_btn")
                            ),
                            targetAllUkraine = targetAllUkraine,
                            onTrigger = { level ->
                                if (targetAllUkraine) {
                                    onTriggerAllUkraineAlert(level, setOf(level), noAllClearMode)
                                } else {
                                    onTriggerRegionAlert(focusedRegion.id, level, noAllClearMode)
                                }
                            }
                        )

                        1 -> ThreatButtonsGrid(
                            buttons = listOf(
                                TabThreatButton(AlertLevel.ARTILLERY, "Артобстріл", "admin_trigger_artillery_btn"),
                                TabThreatButton(AlertLevel.STREET_FIGHTS, "Вуличні бої", "admin_trigger_street_btn"),
                                TabThreatButton(AlertLevel.CIVIL_DEFENSE, "Цивільний захист", "admin_trigger_civil_btn"),
                                TabThreatButton(AlertLevel.CUSTOM_ALERT, "Спецсигнал", "admin_trigger_custom_btn")
                            ),
                            targetAllUkraine = targetAllUkraine,
                            onTrigger = { level ->
                                if (targetAllUkraine) {
                                    onTriggerAllUkraineAlert(level, setOf(level), noAllClearMode)
                                } else {
                                    onTriggerRegionAlert(focusedRegion.id, level, noAllClearMode)
                                }
                            }
                        )

                        2 -> ThreatButtonsGrid(
                            buttons = listOf(
                                TabThreatButton(AlertLevel.CHEMICAL, "Хімічна загроза", "admin_trigger_chemical_btn"),
                                TabThreatButton(AlertLevel.RADIATION, "Радіаційна небезпека", "admin_trigger_radiation_btn")
                            ),
                            targetAllUkraine = targetAllUkraine,
                            onTrigger = { level ->
                                if (targetAllUkraine) {
                                    onTriggerAllUkraineAlert(level, setOf(level), noAllClearMode)
                                } else {
                                    onTriggerRegionAlert(focusedRegion.id, level, noAllClearMode)
                                }
                            }
                        )

                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Multi-Alert Builder inside Combo tab
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AlertLevel.dangerLevels.forEach { lvl ->
                                        val isPicked = multiSelectedAlerts.contains(lvl)
                                        val chipColor = levelMapColor(lvl)
                                        FilterChip(
                                            selected = isPicked,
                                            onClick = {
                                                multiSelectedAlerts = if (isPicked) {
                                                    multiSelectedAlerts - lvl
                                                } else {
                                                    multiSelectedAlerts + lvl
                                                }
                                            },
                                            leadingIcon = {
                                                ThreatLevelIcon(level = lvl, size = 14.dp)
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
                                ElevatedButton(
                                    onClick = {
                                        if (multiSelectedAlerts.isNotEmpty()) {
                                            if (targetAllUkraine) {
                                                val primary = AlertLevel.primaryOf(multiSelectedAlerts)
                                                onTriggerAllUkraineAlert(primary, multiSelectedAlerts, noAllClearMode)
                                            } else {
                                                onTriggerRegionMultiple(focusedRegion.id, multiSelectedAlerts, noAllClearMode)
                                            }
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
                                        text = if (targetAllUkraine) {
                                            "🚨 По всій Україні (${multiSelectedAlerts.size})"
                                        } else if (multiSelectedAlerts.size > 1) {
                                            "🚨 Оголосити одночасно (${multiSelectedAlerts.size})"
                                        } else {
                                            "🚨 Оголосити обрану тривогу"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                // Per-type specific all-clear buttons
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val specificClears = listOf(
                                        Triple(AlertLevel.CIVIL_DEFENSE, "📢 Відбій цив. захисту", "dashboard_civil_defense_clear_btn"),
                                        Triple(AlertLevel.ARTILLERY, "💥 Відбій артобстрілу", "dashboard_artillery_clear_btn"),
                                        Triple(AlertLevel.CHEMICAL, "☣️ Відбій хімії", "dashboard_chemical_clear_btn"),
                                        Triple(AlertLevel.RADIATION, "☢️ Відбій радіації", "dashboard_radiation_clear_btn")
                                    )
                                    specificClears.forEach { (lvl, label, tag) ->
                                        ElevatedButton(
                                            onClick = { onTriggerSpecificAllClear(focusedRegion.id, lvl, noAllClearMode) },
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = Color(0xFF183226),
                                                contentColor = AlertGreenClear
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag(tag)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    ElevatedButton(
                                        onClick = { onTriggerStreetFightsAllClear(focusedRegion.id, noAllClearMode) },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = Color(0xFF1E3A2B),
                                            contentColor = AlertGreenClear
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("dashboard_street_fights_clear_btn")
                                    ) {
                                        Text("🛡️ Відбій боїв", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Always-accessible emergency All-Clear bar directly beneath category tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedButton(
                onClick = { onClearRegion(focusedRegion.id, true) },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = AlertGreenClear,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("admin_trigger_clear_btn")
            ) {
                Text("🟢 Відбій", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            ElevatedButton(
                onClick = { onClearRegion(focusedRegion.id, false) },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF282210),
                    contentColor = UkrYellow
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("admin_trigger_clear_no_sound_btn")
            ) {
                Text("🔇 Без відбою", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            ElevatedButton(
                onClick = { onTriggerTotalAllClear(noAllClearMode) },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = AlertGreenClear.copy(alpha = 0.85f),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("dashboard_total_clear_btn")
            ) {
                Text("🟢 ВЕСЬ ВІДБІЙ", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreatButtonsGrid(
    buttons: List<TabThreatButton>,
    targetAllUkraine: Boolean,
    onTrigger: (AlertLevel) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEach { btn ->
            val btnColor = levelMapColor(btn.level)
            val darkInk = btn.level == AlertLevel.YELLOW ||
                btn.level == AlertLevel.DRONE ||
                btn.level == AlertLevel.CHEMICAL ||
                btn.level == AlertLevel.RADIATION
            ElevatedButton(
                onClick = { onTrigger(btn.level) },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = btnColor,
                    contentColor = if (darkInk) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag(btn.testTag)
            ) {
                ThreatLevelIcon(
                    level = btn.level,
                    size = 18.dp,
                    tint = if (btn.level == AlertLevel.DRONE) {
                        Color(0xFFB91C1C)
                    } else if (darkInk) {
                        Color.Black
                    } else {
                        Color.White
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (targetAllUkraine) "${btn.label} (Уся UA)" else btn.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
