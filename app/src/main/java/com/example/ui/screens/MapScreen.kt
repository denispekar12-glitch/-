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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlertRepository
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.model.UserRole
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import com.example.ui.components.ShahedDroneIcon
import com.example.ui.components.ThreatLevelIcon
import com.example.ui.components.UkraineVectorMap
import com.example.ui.components.levelMapColor
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertRed
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrYellow

@Composable
fun MapScreen(
    regions: List<RegionItem>,
    selectedRegion: RegionItem?,
    userRole: UserRole,
    pinnedLimitMessage: String? = null,
    onDismissPinnedLimitMessage: () -> Unit = {},
    onSelectRegion: (RegionItem) -> Unit,
    onFocusKryveOzero: () -> Unit,
    onToggleMonitored: (String) -> Unit,
    onSetAlertLevel: (String, AlertLevel) -> Unit,
    onToggleAlertType: (String, AlertLevel) -> Unit,
    onClearRegion: (String, Boolean) -> Unit = { id, _ -> onSetAlertLevel(id, AlertLevel.SAFE) },
    onClearSpecificAlert: (String, AlertLevel) -> Unit = { _, _ -> },
    onTriggerAllUkraineAlert: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0=All districts, 1=Active Dangers, 2=Pinned (max 3)

    val filteredList = regions.filter { reg ->
        val matchesQuery = reg.name.contains(searchQuery, ignoreCase = true) ||
            reg.oblast.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            1 -> reg.currentLevel.isDanger
            2 -> reg.isMonitored
            else -> true
        }
        matchesQuery && matchesFilter
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!pinnedLimitMessage.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332609)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(UkrYellow)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = pinnedLimitMessage,
                            color = UkrYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissPinnedLimitMessage, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрити",
                                tint = UkrYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Interactive Canvas Map
        item {
            UkraineVectorMap(
                regions = regions,
                selectedRegion = selectedRegion,
                onSelectRegion = onSelectRegion,
                onFocusKryveOzero = onFocusKryveOzero
            )
        }

        if (userRole == UserRole.ADMIN) {
            item {
                ElevatedButton(
                    onClick = onTriggerAllUkraineAlert,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFFD92638),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("map_all_ukraine_alert_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = UkrYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Search & Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук району, громади чи області...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Пошук",
                            tint = TextMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkrYellow,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = SurfaceCardDark,
                        unfocusedContainerColor = SurfaceCardDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedFilter == 0,
                            onClick = { selectedFilter = 0 },
                            label = { Text("Всі райони (${regions.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E283E),
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = TextSecondary
                            )
                        )
                    }
                    item {
                        val dangerCount = regions.count { it.currentLevel.isDanger }
                        FilterChip(
                            selected = selectedFilter == 1,
                            onClick = { selectedFilter = 1 },
                            label = { Text("🚨 Тривоги ($dangerCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AlertRed.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = AlertRed
                            )
                        )
                    }
                    item {
                        val monitoredCount = regions.count { it.isMonitored }
                        FilterChip(
                            selected = selectedFilter == 2,
                            onClick = { selectedFilter = 2 },
                            label = { Text("⭐ Мої райони ($monitoredCount/${AlertRepository.MAX_PINNED_REGIONS})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = UkrYellow.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = UkrYellow
                            )
                        )
                    }
                }
            }
        }

        // District items list
        items(filteredList, key = { it.id }) { region ->
            RegionListItem(
                region = region,
                isSelected = selectedRegion?.id == region.id,
                userRole = userRole,
                onSelect = { onSelectRegion(region) },
                onToggleMonitored = { onToggleMonitored(region.id) },
                onToggleLevel = { level -> onToggleAlertType(region.id, level) },
                onClearRegion = { withSound -> onClearRegion(region.id, withSound) },
                onClearSpecific = { level -> onClearSpecificAlert(region.id, level) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionListItem(
    region: RegionItem,
    isSelected: Boolean,
    userRole: UserRole,
    onSelect: () -> Unit,
    onToggleMonitored: () -> Unit,
    onToggleLevel: (AlertLevel) -> Unit,
    onClearRegion: (Boolean) -> Unit,
    onClearSpecific: (AlertLevel) -> Unit
) {
    val statusColor = levelMapColor(region.currentLevel)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF192133) else SurfaceCardDark
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) UkrYellow.copy(alpha = 0.6f) else SurfaceBorderDark
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("region_item_${region.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (region.effectiveActiveAlerts.contains(AlertLevel.DRONE)) {
                        ShahedDroneIcon(size = 20.dp)
                    } else if (region.currentLevel.isDanger) {
                        ThreatLevelIcon(level = region.currentLevel, size = 18.dp, tint = statusColor)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = region.name,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (region.id == "kryve_ozero") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = UkrYellow
                                ) {
                                    Text(
                                        text = "РІДНА ГРОМАДА",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (region.withoutAllClear) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = UkrYellow.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "БЕЗ ВІДБОЮ",
                                        color = UkrYellow,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (region.threatDetails.isNotBlank()) region.threatDetails else region.oblast,
                            color = if (region.currentLevel.isDanger) statusColor else TextMuted,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val activeAlerts = region.effectiveActiveAlerts
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (activeAlerts.size > 1) {
                                "${region.currentLevel.shortName} +${activeAlerts.size - 1}"
                            } else {
                                region.currentLevel.shortName
                            },
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(onClick = onToggleMonitored) {
                        Icon(
                            imageVector = if (region.isMonitored) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Закріпити район (макс. 3)",
                            tint = if (region.isMonitored) UkrYellow else TextMuted
                        )
                    }
                }
            }

            if (region.hasMultipleAlerts) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    region.effectiveActiveAlerts.forEach { lvl ->
                        val c = levelMapColor(lvl)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = c.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = lvl.shortName,
                                color = c,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (isSelected && userRole == UserRole.ADMIN) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Керування загрозами в районі (можна декілька):",
                    color = UkrYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                val activeSet = region.effectiveActiveAlerts.toSet()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AlertLevel.dangerLevels.forEach { lvl ->
                        AdminLevelButton(
                            title = lvl.shortName,
                            color = levelMapColor(lvl),
                            isActive = activeSet.contains(lvl)
                        ) {
                            onToggleLevel(lvl)
                        }
                    }
                    AdminLevelButton("Відбій цив. захисту", AlertGreenClear, false) {
                        onClearSpecific(AlertLevel.CIVIL_DEFENSE)
                    }
                    AdminLevelButton("Відбій артобстрілу", AlertGreenClear, false) {
                        onClearSpecific(AlertLevel.ARTILLERY)
                    }
                    AdminLevelButton("Відбій хімії", AlertGreenClear, false) {
                        onClearSpecific(AlertLevel.CHEMICAL)
                    }
                    AdminLevelButton("Відбій радіації", AlertGreenClear, false) {
                        onClearSpecific(AlertLevel.RADIATION)
                    }
                    AdminLevelButton("Зняти (З відбоєм)", AlertGreenClear, activeSet.isEmpty() && !region.withoutAllClear) {
                        onClearRegion(true)
                    }
                    AdminLevelButton("Зняти (Без відбою)", UkrYellow, activeSet.isEmpty() && region.withoutAllClear) {
                        onClearRegion(false)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLevelButton(
    title: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) color else color.copy(alpha = 0.15f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(color)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            color = if (isActive) Color.Black else color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
