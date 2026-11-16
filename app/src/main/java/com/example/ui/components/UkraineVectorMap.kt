package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertLevel
import com.example.model.RegionItem
import com.example.ui.theme.AlertArtillery
import com.example.ui.theme.AlertChemical
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertCustomSpecial
import com.example.ui.theme.AlertDrone
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertMissile
import com.example.ui.theme.AlertOrangeStreetFight
import com.example.ui.theme.AlertRadiation
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertYellow
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrBlue
import com.example.ui.theme.UkrYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun levelMapColor(level: AlertLevel): Color = when (level) {
    AlertLevel.RED -> AlertRed
    AlertLevel.MISSILE -> AlertMissile
    AlertLevel.DRONE -> AlertDrone
    AlertLevel.ARTILLERY -> AlertArtillery
    AlertLevel.YELLOW -> AlertYellow
    AlertLevel.STREET_FIGHTS -> AlertOrangeStreetFight
    AlertLevel.CIVIL_DEFENSE -> AlertCivilDefense
    AlertLevel.CHEMICAL -> AlertChemical
    AlertLevel.RADIATION -> AlertRadiation
    AlertLevel.CUSTOM_ALERT -> AlertCustomSpecial
    AlertLevel.SAFE -> AlertGreenClear
}

/**
 * Authentic alerts.in.ua polygon definition for an oblast of Ukraine using normalized (0..1) vertices.
 * Interlocking vertices cover the full silhouette of Ukraine including Crimea cleanly.
 */
private data class OblastPolygon(
    val id: String,
    val title: String,
    val primaryRegionId: String,
    val matchKeywords: List<String>,
    val points: List<Pair<Float, Float>>
)

private val UKRAINE_OBLAST_POLYGONS: List<OblastPolygon> = listOf(
    OblastPolygon(
        id = "volyn",
        title = "Волинська",
        primaryRegionId = "volyn",
        matchKeywords = listOf("волин", "луцьк"),
        points = listOf(0.14f to 0.16f, 0.22f to 0.17f, 0.23f to 0.28f, 0.17f to 0.34f, 0.12f to 0.27f)
    ),
    OblastPolygon(
        id = "rivne",
        title = "Рівненська",
        primaryRegionId = "rivne",
        matchKeywords = listOf("рівнен"),
        points = listOf(0.22f to 0.17f, 0.32f to 0.16f, 0.33f to 0.29f, 0.25f to 0.36f, 0.21f to 0.31f, 0.23f to 0.28f)
    ),
    OblastPolygon(
        id = "zhytomyr",
        title = "Житомирська",
        primaryRegionId = "zhytomyr",
        matchKeywords = listOf("житомир"),
        points = listOf(0.32f to 0.16f, 0.42f to 0.18f, 0.43f to 0.36f, 0.34f to 0.41f, 0.31f to 0.34f, 0.33f to 0.29f)
    ),
    OblastPolygon(
        id = "kyiv",
        title = "Київська",
        primaryRegionId = "kyiv_city",
        matchKeywords = listOf("київ", "бучан", "білоцерк"),
        points = listOf(0.42f to 0.18f, 0.49f to 0.18f, 0.52f to 0.28f, 0.54f to 0.39f, 0.46f to 0.44f, 0.41f to 0.38f, 0.43f to 0.36f)
    ),
    OblastPolygon(
        id = "chernihiv",
        title = "Чернігівська",
        primaryRegionId = "chernihiv",
        matchKeywords = listOf("черніг"),
        points = listOf(0.49f to 0.12f, 0.60f to 0.13f, 0.61f to 0.28f, 0.52f to 0.28f, 0.49f to 0.18f)
    ),
    OblastPolygon(
        id = "sumy",
        title = "Сумська",
        primaryRegionId = "sumy",
        matchKeywords = listOf("сумськ", "суми"),
        points = listOf(0.60f to 0.13f, 0.70f to 0.19f, 0.72f to 0.32f, 0.64f to 0.35f, 0.61f to 0.28f)
    ),
    OblastPolygon(
        id = "lviv",
        title = "Львівська",
        primaryRegionId = "lviv",
        matchKeywords = listOf("львів"),
        points = listOf(0.12f to 0.27f, 0.17f to 0.34f, 0.21f to 0.37f, 0.19f to 0.49f, 0.11f to 0.48f, 0.08f to 0.38f)
    ),
    OblastPolygon(
        id = "zakarpattia",
        title = "Закарпатська",
        primaryRegionId = "zakarpattia",
        matchKeywords = listOf("закарпат", "ужгород"),
        points = listOf(0.06f to 0.49f, 0.11f to 0.48f, 0.17f to 0.53f, 0.14f to 0.61f, 0.06f to 0.57f)
    ),
    OblastPolygon(
        id = "ivano_frankivsk",
        title = "Івано-Франківська",
        primaryRegionId = "ivano_frankivsk",
        matchKeywords = listOf("івано-франк", "франків"),
        points = listOf(0.17f to 0.53f, 0.19f to 0.49f, 0.23f to 0.49f, 0.24f to 0.60f, 0.17f to 0.62f, 0.14f to 0.61f)
    ),
    OblastPolygon(
        id = "ternopil",
        title = "Тернопільська",
        primaryRegionId = "ternopil",
        matchKeywords = listOf("терноп"),
        points = listOf(0.21f to 0.37f, 0.25f to 0.36f, 0.27f to 0.50f, 0.23f to 0.49f, 0.19f to 0.49f)
    ),
    OblastPolygon(
        id = "chernivtsi",
        title = "Чернівецька",
        primaryRegionId = "chernivtsi",
        matchKeywords = listOf("чернів"),
        points = listOf(0.24f to 0.55f, 0.31f to 0.55f, 0.32f to 0.62f, 0.23f to 0.62f)
    ),
    OblastPolygon(
        id = "khmelnytskyi",
        title = "Хмельницька",
        primaryRegionId = "khmelnytskyi",
        matchKeywords = listOf("хмельн"),
        points = listOf(0.25f to 0.36f, 0.31f to 0.34f, 0.34f to 0.41f, 0.34f to 0.54f, 0.27f to 0.52f, 0.27f to 0.50f)
    ),
    OblastPolygon(
        id = "vinnytsia",
        title = "Вінницька",
        primaryRegionId = "vinnytsia",
        matchKeywords = listOf("вінниц"),
        points = listOf(0.34f to 0.41f, 0.41f to 0.38f, 0.44f to 0.48f, 0.42f to 0.58f, 0.33f to 0.56f, 0.34f to 0.54f)
    ),
    OblastPolygon(
        id = "cherkasy",
        title = "Черкаська",
        primaryRegionId = "cherkasy",
        matchKeywords = listOf("черкас", "уман"),
        points = listOf(0.41f to 0.38f, 0.46f to 0.44f, 0.55f to 0.40f, 0.56f to 0.49f, 0.45f to 0.53f, 0.44f to 0.48f)
    ),
    OblastPolygon(
        id = "poltava",
        title = "Полтавська",
        primaryRegionId = "poltava",
        matchKeywords = listOf("полтав"),
        points = listOf(0.52f to 0.28f, 0.61f to 0.28f, 0.64f to 0.35f, 0.69f to 0.39f, 0.65f to 0.48f, 0.56f to 0.46f, 0.54f to 0.39f)
    ),
    OblastPolygon(
        id = "kharkiv",
        title = "Харківська",
        primaryRegionId = "kharkiv",
        matchKeywords = listOf("харків", "чугуїв"),
        points = listOf(0.70f to 0.27f, 0.81f to 0.29f, 0.83f to 0.41f, 0.74f to 0.47f, 0.69f to 0.39f, 0.72f to 0.32f)
    ),
    OblastPolygon(
        id = "luhansk",
        title = "Луганська",
        primaryRegionId = "luhansk",
        matchKeywords = listOf("луган", "сєвєродон"),
        points = listOf(0.81f to 0.29f, 0.90f to 0.34f, 0.94f to 0.47f, 0.87f to 0.53f, 0.83f to 0.41f)
    ),
    OblastPolygon(
        id = "donetsk",
        title = "Донецька",
        primaryRegionId = "donetsk",
        matchKeywords = listOf("донец", "краматор"),
        points = listOf(0.74f to 0.47f, 0.83f to 0.41f, 0.87f to 0.53f, 0.84f to 0.64f, 0.77f to 0.66f, 0.74f to 0.58f)
    ),
    OblastPolygon(
        id = "dnipro",
        title = "Дніпропетровська",
        primaryRegionId = "dnipro",
        matchKeywords = listOf("дніпр", "кривор"),
        points = listOf(0.56f to 0.46f, 0.65f to 0.48f, 0.74f to 0.47f, 0.74f to 0.58f, 0.63f to 0.62f, 0.56f to 0.57f)
    ),
    OblastPolygon(
        id = "kirovohrad",
        title = "Кіровоградська",
        primaryRegionId = "kirovohrad",
        matchKeywords = listOf("кіровогр", "кропив"),
        points = listOf(0.45f to 0.53f, 0.56f to 0.49f, 0.58f to 0.57f, 0.52f to 0.62f, 0.44f to 0.59f)
    ),
    OblastPolygon(
        id = "odesa",
        title = "Одеська",
        primaryRegionId = "odesa",
        matchKeywords = listOf("одес", "подільськ", "ізмаїл"),
        points = listOf(0.36f to 0.58f, 0.44f to 0.59f, 0.47f to 0.69f, 0.42f to 0.76f, 0.35f to 0.85f, 0.31f to 0.82f, 0.38f to 0.70f)
    ),
    OblastPolygon(
        id = "mykolaiv",
        title = "Миколаївська",
        primaryRegionId = "kryve_ozero",
        matchKeywords = listOf("миколаїв", "кривоозер", "первомай", "вознес", "баштан"),
        points = listOf(0.44f to 0.59f, 0.52f to 0.62f, 0.56f to 0.64f, 0.54f to 0.75f, 0.46f to 0.73f, 0.47f to 0.69f)
    ),
    OblastPolygon(
        id = "kherson",
        title = "Херсонська",
        primaryRegionId = "kherson",
        matchKeywords = listOf("херсон"),
        points = listOf(0.54f to 0.64f, 0.63f to 0.65f, 0.66f to 0.74f, 0.61f to 0.81f, 0.52f to 0.78f, 0.54f to 0.75f)
    ),
    OblastPolygon(
        id = "zaporizhzhia",
        title = "Запорізька",
        primaryRegionId = "zaporizhzhia",
        matchKeywords = listOf("запор"),
        points = listOf(0.63f to 0.62f, 0.74f to 0.58f, 0.77f to 0.66f, 0.72f to 0.74f, 0.66f to 0.74f, 0.63f to 0.65f)
    ),
    OblastPolygon(
        id = "crimea",
        title = "АР Крим",
        primaryRegionId = "crimea",
        matchKeywords = listOf("крим", "сімфероп"),
        points = listOf(0.56f to 0.82f, 0.63f to 0.81f, 0.69f to 0.87f, 0.73f to 0.89f, 0.68f to 0.94f, 0.61f to 0.96f, 0.55f to 0.91f)
    )
)

private fun alertsUaPolygonColor(level: AlertLevel): Color = when (level) {
    AlertLevel.RED, AlertLevel.MISSILE -> Color(0xFFD94436) // Authentic alerts.in.ua crimson fill
    AlertLevel.DRONE, AlertLevel.YELLOW -> Color(0xFFF5B700) // Authentic alerts.in.ua amber fill
    AlertLevel.ARTILLERY -> Color(0xFFE05D25)
    AlertLevel.STREET_FIGHTS -> Color(0xFFFF6D00)
    AlertLevel.CIVIL_DEFENSE -> Color(0xFF9C4DCC)
    AlertLevel.CHEMICAL -> Color(0xFF00B8D4)
    AlertLevel.RADIATION -> Color(0xFF84CC16)
    AlertLevel.CUSTOM_ALERT -> Color(0xFF3B82F6)
    AlertLevel.SAFE -> Color(0xFF28303D) // Dark slate neutral fill matching screenshot
}

private fun oblastMatchingRegions(oblastPoly: OblastPolygon, regions: List<RegionItem>): List<RegionItem> {
    return regions.filter { reg ->
        val haystack = "${reg.id} ${reg.name} ${reg.oblast}".lowercase(Locale.ROOT)
        oblastPoly.matchKeywords.any { kw -> haystack.contains(kw) }
    }
}

private fun pointInPolygon(x: Float, y: Float, poly: List<Pair<Float, Float>>): Boolean {
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val xi = poly[i].first
        val yi = poly[i].second
        val xj = poly[j].first
        val yj = poly[j].second
        val intersect = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1e-6f) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UkraineVectorMap(
    regions: List<RegionItem>,
    selectedRegion: RegionItem?,
    onSelectRegion: (RegionItem) -> Unit,
    onFocusKryveOzero: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLegend by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val statusTimestamp = remember(regions) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }
    val activeDangerCount = regions.count { it.currentLevel.isDanger }
    val droneRegionsCount = regions.count { it.effectiveActiveAlerts.contains(AlertLevel.DRONE) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ukraine_map_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0E1117)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF232A38), Color(0xFF151B26))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Authentic alerts.in.ua Header Bar ("Мапа тривог" + "Станом на ...")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(UkrBlue, UkrYellow))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "UA",
                            color = Color(0xFF0A0D14),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Мапа тривог",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = "v2.0",
                                    color = UkrYellow,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Станом на $statusTimestamp • У тривозі: $activeDangerCount р-нів",
                            color = if (activeDangerCount > 0) Color(0xFFFCA5A5) else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showLegend = !showLegend },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("map_legend_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Легенда мапи",
                            tint = if (showLegend) UkrYellow else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UkrBlue.copy(alpha = 0.22f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow))
                        ),
                        modifier = Modifier.clickable { onFocusKryveOzero() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Криве Озеро",
                                tint = UkrYellow,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Криве Озеро",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Dark Slate Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0D13))
                    .border(1.dp, Color(0xFF1D2432), RoundedCornerShape(14.dp))
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

                                val hitClosestDistrict = regions.minByOrNull { reg ->
                                    val dx = reg.mapNormX - normX
                                    val dy = reg.mapNormY - normY
                                    dx * dx + dy * dy
                                }
                                if (hitClosestDistrict != null) {
                                    val dxPx = (hitClosestDistrict.mapNormX - normX) * w
                                    val dyPx = (hitClosestDistrict.mapNormY - normY) * h
                                    if (dxPx * dxPx + dyPx * dyPx <= 32f * 32f) {
                                        onSelectRegion(hitClosestDistrict)
                                        return@detectTapGestures
                                    }
                                }

                                // Otherwise check oblast polygon hit so tapping anywhere inside an oblast selects it
                                val hitPoly = UKRAINE_OBLAST_POLYGONS.firstOrNull { poly ->
                                    pointInPolygon(normX, normY, poly.points)
                                }
                                if (hitPoly != null) {
                                    val matched = oblastMatchingRegions(hitPoly, regions)
                                    val chosen = matched.firstOrNull { it.currentLevel.isDanger }
                                        ?: regions.find { it.id == hitPoly.primaryRegionId }
                                        ?: matched.firstOrNull()
                                    if (chosen != null) {
                                        onSelectRegion(chosen)
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Render every Oblast Polygon with realistic fill, dark separators & partial hatched stripes
                    UKRAINE_OBLAST_POLYGONS.forEach { poly ->
                        val path = Path().apply {
                            poly.points.forEachIndexed { idx, (nx, ny) ->
                                if (idx == 0) moveTo(nx * w, ny * h) else lineTo(nx * w, ny * h)
                            }
                            close()
                        }

                        val matching = oblastMatchingRegions(poly, regions)
                        val dangers = matching.filter { it.currentLevel.isDanger }
                        val allInDanger = matching.isNotEmpty() && dangers.size == matching.size
                        val isPartial = dangers.isNotEmpty() && !allInDanger
                        val dominantDanger = dangers.maxByOrNull { it.currentLevel.priorityOrder }
                        val hasSelectedInside = matching.any { it.id == selectedRegion?.id }

                        val fillColor = when {
                            allInDanger && dominantDanger != null -> alertsUaPolygonColor(dominantDanger.currentLevel)
                            isPartial && dominantDanger != null -> Color(0xFF28303D)
                            else -> Color(0xFF28303D)
                        }
                        drawPath(path = path, color = fillColor)

                        // Partial alert or Artillery/Yellow-with-Red: diagonal hatched alerts.in.ua stripe pattern!
                        if (isPartial && dominantDanger != null) {
                            val stripeColor = alertsUaPolygonColor(dominantDanger.currentLevel)
                            clipPath(path) {
                                val step = 12f
                                val span = w + h
                                var sx = -h
                                while (sx < span) {
                                    drawLine(
                                        color = stripeColor.copy(alpha = 0.78f),
                                        start = Offset(sx, 0f),
                                        end = Offset(sx + h, h),
                                        strokeWidth = 5f
                                    )
                                    sx += step
                                }
                            }
                        }

                        // Subtle district divider mesh + crisp dark oblast boundary
                        drawPath(
                            path = path,
                            color = if (hasSelectedInside) Color.White.copy(alpha = 0.85f) else Color(0xFF10141C),
                            style = Stroke(
                                width = if (hasSelectedInside) 2.5f else 1.8f,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // 2. Render subtle internal raion dots for quiet monitored areas
                    regions.forEach { reg ->
                        val px = reg.mapNormX * w
                        val py = reg.mapNormY * h
                        val isKryveOzero = reg.id == "kryve_ozero"
                        val isSelected = reg.id == selectedRegion?.id
                        val hasDrone = reg.effectiveActiveAlerts.contains(AlertLevel.DRONE)
                        val isDanger = reg.currentLevel.isDanger

                        if (isDanger || isKryveOzero) {
                            val glowColor = if (isDanger) levelMapColor(reg.currentLevel) else UkrYellow
                            drawCircle(
                                color = glowColor.copy(alpha = pulseAlpha * 0.45f),
                                radius = (if (isKryveOzero) 16f else 13f) + pulseRadius,
                                center = Offset(px, py)
                            )
                        }

                        if (hasDrone) {
                            // Full, prominent unclipped Shahed-136 delta-wing silhouette on the map!
                            drawCircle(
                                color = Color(0xCC0A0D13),
                                radius = 15f,
                                center = Offset(px, py)
                            )
                            drawShahedIcon(
                                center = Offset(px, py),
                                radius = 14f,
                                fillColor = Color(0xFFFF2A3B),
                                outlineColor = Color(0xFF0A0D13),
                                accentColor = UkrYellow
                            )
                        } else if (isDanger) {
                            val dotColor = levelMapColor(reg.currentLevel)
                            drawCircle(color = Color(0xFF0E1117), radius = 7.5f, center = Offset(px, py))
                            drawCircle(color = dotColor, radius = 5.5f, center = Offset(px, py))
                            if (reg.hasMultipleAlerts) {
                                val c2 = reg.effectiveActiveAlerts.getOrNull(1)?.let { levelMapColor(it) } ?: UkrYellow
                                drawCircle(color = c2, radius = 9.5f, center = Offset(px, py), style = Stroke(width = 2f))
                            }
                        } else {
                            // Quiet district subtle dot
                            drawCircle(
                                color = if (reg.isMonitored) AlertGreenClear else Color(0xFF475569),
                                radius = if (isKryveOzero) 5.5f else 3f,
                                center = Offset(px, py)
                            )
                        }

                        if (isKryveOzero) {
                            drawCircle(
                                color = UkrYellow,
                                radius = if (hasDrone) 17f else 9.5f,
                                center = Offset(px, py),
                                style = Stroke(width = 2f)
                            )
                        }
                        if (isSelected && !isKryveOzero) {
                            drawCircle(
                                color = Color.White,
                                radius = if (hasDrone) 17f else 9.5f,
                                center = Offset(px, py),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }

                // Active Shahed counter pill when drones are on radar
                if (droneRegionsCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .testTag("map_shahed_badge"),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xEE181014),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(AlertRed, UkrYellow))
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShahedDroneIcon(size = 18.dp, tint = Color(0xFFFF2A3B), accentColor = UkrYellow)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Шахеди: $droneRegionsCount",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Kryve Ozero live quick-status pill (Top Right)
                val kryveOzero = regions.find { it.id == "kryve_ozero" }
                if (kryveOzero != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clickable { onSelectRegion(kryveOzero) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE131824),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(UkrBlue, UkrYellow))
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (kryveOzero.effectiveActiveAlerts.contains(AlertLevel.DRONE)) {
                                ShahedDroneIcon(size = 14.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(levelMapColor(kryveOzero.currentLevel))
                                )
                            }
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Collapsible (i) Legend Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLegend,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xEB0B0F17),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                        )
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MapLegendItem(color = Color(0xFFD94436), label = "Тривога")
                            MapLegendItem(color = Color(0xFFF5B700), label = "Жовтий / Частково")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShahedDroneIcon(size = 13.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Шахед", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            MapLegendItem(color = AlertCivilDefense, label = "Цив. захист")
                            MapLegendItem(color = AlertChemical, label = "Хімія")
                            MapLegendItem(color = AlertRadiation, label = "Радіація")
                            MapLegendItem(color = Color(0xFF28303D), label = "Спокійно")
                        }
                    }
                }
            }

            if (selectedRegion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val selColor = levelMapColor(selectedRegion.currentLevel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCardDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedRegion.effectiveActiveAlerts.contains(AlertLevel.DRONE)) {
                            ShahedDroneIcon(size = 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedRegion.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (selectedRegion.withoutAllClear) {
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
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (selectedRegion.threatDetails.isNotBlank()) {
                                    selectedRegion.threatDetails
                                } else {
                                    "Статус: ${selectedRegion.currentLevel.title}"
                                },
                                color = selColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = selColor.copy(alpha = 0.2f)
                    ) {
                        val activeList = selectedRegion.effectiveActiveAlerts
                        val badgeLabel = if (activeList.size > 1) {
                            activeList.joinToString(" + ") { it.shortName }
                        } else {
                            selectedRegion.currentLevel.shortName
                        }
                        Text(
                            text = badgeLabel,
                            color = selColor,
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
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color(0xFFD1D5DB), fontSize = 10.sp)
    }
}
