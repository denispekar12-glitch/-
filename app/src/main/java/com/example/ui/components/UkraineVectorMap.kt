package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertLevel
import com.example.model.RegionItem
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
import com.example.ui.theme.UkrBlue
import com.example.ui.theme.UkrYellow

@Composable
fun UkraineVectorMap(
    regions: List<RegionItem>,
    selectedRegion: RegionItem?,
    onSelectRegion: (RegionItem) -> Unit,
    onFocusKryveOzero: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ukraine_map_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F131D)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = listOf(SurfaceBorderDark, Color(0xFF1B2236))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header bar over map
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AlertRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ІНТЕРАКТИВНА МАПА ТРИВОГ УКРАЇНИ",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UkrBlue.copy(alpha = 0.2f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow))),
                    modifier = Modifier.clickable { onFocusKryveOzero() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Криве Озеро",
                            tint = UkrYellow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Криве Озеро",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Map View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090B10))
                    .border(1.dp, Color(0xFF1E2538), RoundedCornerShape(14.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(regions) {
                            detectTapGestures { offset ->
                                val w = size.width
                                val h = size.height
                                val normX = offset.x / w
                                val normY = offset.y / h

                                // Find closest region
                                val closest = regions.minByOrNull { region ->
                                    val dx = (region.mapNormX - normX)
                                    val dy = (region.mapNormY - normY)
                                    dx * dx + dy * dy
                                }
                                if (closest != null) {
                                    val dx = (closest.mapNormX - normX) * w
                                    val dy = (closest.mapNormY - normY) * h
                                    if (dx * dx + dy * dy <= 45f * 45f) {
                                        onSelectRegion(closest)
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // Draw subtle tactical grid lines
                    val gridCols = 8
                    val gridRows = 5
                    for (i in 1 until gridCols) {
                        val gx = (w / gridCols) * i
                        drawLine(
                            color = Color(0xFF141926),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                    }
                    for (j in 1 until gridRows) {
                        val gy = (h / gridRows) * j
                        drawLine(
                            color = Color(0xFF141926),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                    }

                    // Draw stylized Ukraine contour outline
                    val contour = Path().apply {
                        moveTo(w * 0.12f, h * 0.32f) // Volyn
                        lineTo(w * 0.28f, h * 0.20f) // Rivne/Zhytomyr north
                        lineTo(w * 0.52f, h * 0.18f) // Chernihiv
                        lineTo(w * 0.70f, h * 0.22f) // Sumy
                        lineTo(w * 0.88f, h * 0.32f) // Luhansk north
                        lineTo(w * 0.94f, h * 0.46f) // Luhansk east
                        lineTo(w * 0.86f, h * 0.64f) // Donetsk south/Azov
                        lineTo(w * 0.72f, h * 0.74f) // Zaporizhzhia/Berdyansk
                        lineTo(w * 0.64f, h * 0.82f) // Henichesk
                        lineTo(w * 0.68f, h * 0.96f) // Crimea south
                        lineTo(w * 0.58f, h * 0.95f) // Sevastopol
                        lineTo(w * 0.54f, h * 0.82f) // Kherson coast
                        lineTo(w * 0.44f, h * 0.78f) // Odesa coast / Black sea
                        lineTo(w * 0.36f, h * 0.82f) // Danube delta
                        lineTo(w * 0.34f, h * 0.66f) // Moldova border
                        lineTo(w * 0.26f, h * 0.62f) // Chernivtsi
                        lineTo(w * 0.18f, h * 0.64f) // Carpathians
                        lineTo(w * 0.10f, h * 0.62f) // Zakarpattia
                        lineTo(w * 0.12f, h * 0.46f) // Lviv
                        close()
                    }

                    // Fill territory background
                    drawPath(
                        path = contour,
                        color = Color(0xFF111726)
                    )
                    // Border of Ukraine
                    drawPath(
                        path = contour,
                        color = Color(0xFF2C395B),
                        style = Stroke(width = 2.5f)
                    )

                    // Draw connections between neighboring regions
                    regions.forEach { reg ->
                        if (reg.isHromada) {
                            // Draw radar target line from Mykolaiv center to Kryve Ozero
                            val myk = regions.find { it.id == "mykolaiv" }
                            if (myk != null) {
                                drawLine(
                                    color = UkrYellow.copy(alpha = 0.5f),
                                    start = Offset(reg.mapNormX * w, reg.mapNormY * h),
                                    end = Offset(myk.mapNormX * w, myk.mapNormY * h),
                                    strokeWidth = 1.5f
                                )
                            }
                        }
                    }

                    // Render region indicators
                    regions.forEach { reg ->
                        val px = reg.mapNormX * w
                        val py = reg.mapNormY * h
                        val isSelected = selectedRegion?.id == reg.id
                        val isKryveOzero = reg.id == "kryve_ozero"

                        val color = when (reg.currentLevel) {
                            AlertLevel.RED -> AlertRed
                            AlertLevel.YELLOW -> AlertYellow
                            AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                            AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                            AlertLevel.SAFE -> if (reg.isMonitored) AlertGreenClear else Color(0xFF43516E)
                        }

                        // Glow / pulse for active alerts or Kryve Ozero
                        if (reg.currentLevel.isDanger || isKryveOzero) {
                            drawCircle(
                                color = color.copy(alpha = pulseAlpha * 0.6f),
                                radius = (if (isKryveOzero) 24f else 18f) + pulseRadius,
                                center = Offset(px, py)
                            )
                        }

                        // Outer ring if selected
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = if (isKryveOzero) 14f else 11f,
                                center = Offset(px, py),
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Main region circle
                        drawCircle(
                            color = color,
                            radius = if (isKryveOzero) 9f else 6f,
                            center = Offset(px, py)
                        )

                        // Secondary ring when multiple simultaneous alerts are active
                        if (reg.hasMultipleAlerts) {
                            val secondLevel = reg.effectiveActiveAlerts.getOrNull(1)
                            val secondColor = when (secondLevel) {
                                AlertLevel.RED -> AlertRed
                                AlertLevel.YELLOW -> AlertYellow
                                AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                                AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                                else -> UkrYellow
                            }
                            drawCircle(
                                color = secondColor,
                                radius = if (isKryveOzero) 15.5f else 9.5f,
                                center = Offset(px, py),
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Highlight Kryve Ozero specifically with badge ring
                        if (isKryveOzero) {
                            drawCircle(
                                color = UkrYellow,
                                radius = 12f,
                                center = Offset(px, py),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }

                // Legend in bottom-left
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color(0xCC0B0E17), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    MapLegendItem(color = AlertRed, label = "Тривога")
                    MapLegendItem(color = AlertYellow, label = "Жовтий")
                    MapLegendItem(color = AlertOrangeStreetFight, label = "Вуличні бої")
                    MapLegendItem(color = AlertCivilDefense, label = "Цив. захист")
                    MapLegendItem(color = AlertGreenClear, label = "Спокійно")
                }

                // Kryve Ozero callout chip in top-right
                val kryveOzero = regions.find { it.id == "kryve_ozero" }
                if (kryveOzero != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clickable { onSelectRegion(kryveOzero) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE161C2C),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow)))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (kryveOzero.currentLevel) {
                                            AlertLevel.RED -> AlertRed
                                            AlertLevel.YELLOW -> AlertYellow
                                            AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                                            AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                                            AlertLevel.SAFE -> AlertGreenClear
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val koAlerts = kryveOzero.effectiveActiveAlerts
                            val koStatusText = if (koAlerts.size > 1) {
                                "${koAlerts.size} загрози"
                            } else {
                                kryveOzero.currentLevel.shortName
                            }
                            Text(
                                text = "Криве Озеро: $koStatusText",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Region Selected Info Bar below map
            if (selectedRegion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCardDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedRegion.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (selectedRegion.threatDetails.isNotBlank()) selectedRegion.threatDetails else "Статус: ${selectedRegion.currentLevel.title}",
                            color = when (selectedRegion.currentLevel) {
                                AlertLevel.RED -> AlertRed
                                AlertLevel.YELLOW -> AlertYellow
                                AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                                AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                                AlertLevel.SAFE -> AlertGreenClear
                            },
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (selectedRegion.currentLevel) {
                            AlertLevel.RED -> AlertRed.copy(alpha = 0.2f)
                            AlertLevel.YELLOW -> AlertYellow.copy(alpha = 0.2f)
                            AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight.copy(alpha = 0.2f)
                            AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense.copy(alpha = 0.2f)
                            AlertLevel.SAFE -> AlertGreenClear.copy(alpha = 0.2f)
                        }
                    ) {
                        val activeList = selectedRegion.effectiveActiveAlerts
                        val badgeLabel = if (activeList.size > 1) {
                            activeList.joinToString(" + ") { it.shortName }
                        } else {
                            selectedRegion.currentLevel.shortName
                        }
                        Text(
                            text = badgeLabel,
                            color = when (selectedRegion.currentLevel) {
                                AlertLevel.RED -> AlertRed
                                AlertLevel.YELLOW -> AlertYellow
                                AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
                                AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
                                AlertLevel.SAFE -> AlertGreenClear
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = TextMuted, fontSize = 9.sp)
    }
}
