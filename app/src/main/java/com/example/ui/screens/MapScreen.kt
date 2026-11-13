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
import androidx.compose.material.icons.filled.GpsFixed
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
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.model.UserRole
import com.example.ui.components.UkraineVectorMap
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertOrangeStreetFight
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertYellow
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
    onSelectRegion: (RegionItem) -> Unit,
    onFocusKryveOzero: () -> Unit,
    onToggleMonitored: (String) -> Unit,
    onSetAlertLevel: (String, AlertLevel) -> Unit,
    onToggleAlertType: (String, AlertLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0=All, 1=Active Dangers, 2=Monitored

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
        // Interactive Canvas Map
        item {
            UkraineVectorMap(
                regions = regions,
                selectedRegion = selectedRegion,
                onSelectRegion = onSelectRegion,
                onFocusKryveOzero = onFocusKryveOzero
            )
        }

        // Search & Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук області чи громади...") },
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
                            label = { Text("Всі регіони (${regions.size})") },
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
                            label = { Text("⭐ Відстежувані ($monitoredCount)") },
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

        // Region items list
        items(filteredList, key = { it.id }) { region ->
            RegionListItem(
                region = region,
                isSelected = selectedRegion?.id == region.id,
                userRole = userRole,
                onSelect = { onSelectRegion(region) },
                onToggleMonitored = { onToggleMonitored(region.id) },
                onSetLevel = { level -> onSetAlertLevel(region.id, level) },
                onToggleLevel = { level -> onToggleAlertType(region.id, level) }
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
    onSetLevel: (AlertLevel) -> Unit,
    onToggleLevel: (AlertLevel) -> Unit
) {
    val statusColor = when (region.currentLevel) {
        AlertLevel.RED -> AlertRed
        AlertLevel.YELLOW -> AlertYellow
        AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
        AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
        AlertLevel.SAFE -> AlertGreenClear
    }

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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
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
                        }
                        Text(
                            text = if (region.threatDetails.isNotBlank()) region.threatDetails else "Область: ${region.oblast}",
                            color = if (region.currentLevel.isDanger) statusColor else TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
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
                            contentDescription = "Відстежувати",
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
                        val c = when (lvl) {
                            AlertLevel.RED -> AlertRed
                            AlertLevel.YELLOW -> AlertYellow
                            AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                            AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                            AlertLevel.SAFE -> AlertGreenClear
                        }
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

            // If Admin is selected, show multi-alert toggle buttons
            if (isSelected && userRole == UserRole.ADMIN) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Додати або вимкнути кілька загроз одночасно:",
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
                    AdminLevelButton("Тривога", AlertRed, activeSet.contains(AlertLevel.RED)) {
                        onToggleLevel(AlertLevel.RED)
                    }
                    AdminLevelButton("Жовтий", AlertYellow, activeSet.contains(AlertLevel.YELLOW)) {
                        onToggleLevel(AlertLevel.YELLOW)
                    }
                    AdminLevelButton("Вуличні бої", AlertOrangeStreetFight, activeSet.contains(AlertLevel.STREET_FIGHTS)) {
                        onToggleLevel(AlertLevel.STREET_FIGHTS)
                    }
                    AdminLevelButton("Цив. захист", AlertCivilDefense, activeSet.contains(AlertLevel.CIVIL_DEFENSE)) {
                        onToggleLevel(AlertLevel.CIVIL_DEFENSE)
                    }
                    AdminLevelButton("Відбій усіх", AlertGreenClear, activeSet.isEmpty()) {
                        onSetLevel(AlertLevel.SAFE)
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
