package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AlertLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the complete, unmistakable delta-wing Shahed-136 ("Шахед") silhouette on any Canvas.
 * Sized inside a square bounding circle of [radius] around [center] with safe padding so wings & tail never clip.
 */
fun DrawScope.drawShahedIcon(
    center: Offset,
    radius: Float,
    fillColor: Color = Color(0xFFFF2A3B),
    outlineColor: Color = Color(0xFF0A0D14),
    accentColor: Color = Color(0xFFFFD500),
    rotationDegrees: Float = -35f
) {
    rotate(degrees = rotationDegrees, pivot = center) {
        val r = radius * 0.86f
        // Full delta-wing Shahed-136 silhouette pointing UP (negative Y) before rotation
        val body = Path().apply {
            // Sharp nose cone
            moveTo(center.x, center.y - r * 0.98f)
            // Right forward fuselage & sweeping delta wing leading edge
            lineTo(center.x + r * 0.14f, center.y - r * 0.42f)
            lineTo(center.x + r * 0.88f, center.y + r * 0.56f)
            // Right wingtip stabilizer
            lineTo(center.x + r * 0.94f, center.y + r * 0.72f)
            lineTo(center.x + r * 0.72f, center.y + r * 0.72f)
            // Right trailing edge back toward tail engine housing
            lineTo(center.x + r * 0.24f, center.y + r * 0.62f)
            lineTo(center.x + r * 0.15f, center.y + r * 0.84f)
            // Tail rear flat & pusher prop boss
            lineTo(center.x - r * 0.15f, center.y + r * 0.84f)
            lineTo(center.x - r * 0.24f, center.y + r * 0.62f)
            // Left trailing edge & wingtip stabilizer
            lineTo(center.x - r * 0.72f, center.y + r * 0.72f)
            lineTo(center.x - r * 0.94f, center.y + r * 0.72f)
            lineTo(center.x - r * 0.88f, center.y + r * 0.56f)
            // Left sweeping delta wing leading edge
            lineTo(center.x - r * 0.14f, center.y - r * 0.42f)
            close()
        }

        // Subtle dark backing outline for contrast over any map fill color
        drawPath(
            path = body,
            color = outlineColor,
            style = Stroke(width = (r * 0.20f).coerceAtLeast(1.8f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = body,
            color = fillColor,
            style = Fill
        )

        // Central fuselage spine rib & rear engine ring so silhouette reads cleanly even at small icon sizes
        drawLine(
            color = accentColor.copy(alpha = 0.92f),
            start = Offset(center.x, center.y - r * 0.52f),
            end = Offset(center.x, center.y + r * 0.62f),
            strokeWidth = (r * 0.11f).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
        )
        // Pusher propeller arc at tail
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(center.x - r * 0.26f, center.y + r * 0.90f),
            end = Offset(center.x + r * 0.26f, center.y + r * 0.90f),
            strokeWidth = (r * 0.10f).coerceAtLeast(1.1f),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Draws a crisp radiation trefoil icon inside [center]/[radius].
 */
fun DrawScope.drawRadiationIcon(
    center: Offset,
    radius: Float,
    color: Color
) {
    val rInner = radius * 0.22f
    val rOuter = radius * 0.82f
    drawCircle(color = color, radius = radius * 0.15f, center = center)
    val baseAngles = listOf(-90f, 30f, 150f)
    baseAngles.forEach { deg ->
        val path = Path().apply {
            val a1 = ((deg - 24f) * PI / 180.0).toFloat()
            val a2 = ((deg + 24f) * PI / 180.0).toFloat()
            moveTo(center.x + cos(a1) * rInner, center.y + sin(a1) * rInner)
            lineTo(center.x + cos(a1) * rOuter, center.y + sin(a1) * rOuter)
            lineTo(center.x + cos((deg * PI / 180.0).toFloat()) * rOuter * 1.04f, center.y + sin((deg * PI / 180.0).toFloat()) * rOuter * 1.04f)
            lineTo(center.x + cos(a2) * rOuter, center.y + sin(a2) * rOuter)
            lineTo(center.x + cos(a2) * rInner, center.y + sin(a2) * rInner)
            close()
        }
        drawPath(path = path, color = color)
    }
}

/**
 * Full, unclipped Shahed-136 ("Шахед") Composable Icon for cards, buttons, broadcast banners, and event logs.
 */
@Composable
fun ShahedDroneIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color(0xFFFF2A3B),
    outlineColor: Color = Color(0xFF0A0D14),
    accentColor: Color = Color(0xFFFFD500),
    contentDescription: String? = "Шахед / БПЛА"
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        val radius = this.size.minDimension / 2f
        drawShahedIcon(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            radius = radius,
            fillColor = tint,
            outlineColor = outlineColor,
            accentColor = accentColor
        )
    }
}

@Composable
fun ThreatLevelIcon(
    level: AlertLevel,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = Color.Unspecified
) {
    val resolvedTint = if (tint == Color.Unspecified) levelMapColor(level) else tint
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (level) {
            AlertLevel.DRONE -> {
                ShahedDroneIcon(
                    size = size,
                    tint = if (resolvedTint == Color.Black) Color(0xFFB91C1C) else resolvedTint,
                    outlineColor = Color(0xFF090B10),
                    accentColor = Color.White,
                    contentDescription = level.shortName
                )
            }
            AlertLevel.RADIATION -> {
                Canvas(modifier = Modifier.size(size)) {
                    drawRadiationIcon(
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = this.size.minDimension / 2f,
                        color = resolvedTint
                    )
                }
            }
            AlertLevel.MISSILE -> Icon(Icons.Default.RocketLaunch, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.CHEMICAL -> Icon(Icons.Default.Science, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.CIVIL_DEFENSE -> Icon(Icons.Default.Campaign, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.STREET_FIGHTS -> Icon(Icons.Default.Security, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.RED, AlertLevel.CUSTOM_ALERT -> Icon(Icons.Default.NotificationsActive, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.ARTILLERY, AlertLevel.YELLOW -> Icon(Icons.Default.Warning, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
            AlertLevel.SAFE -> Icon(Icons.Default.CheckCircle, contentDescription = level.shortName, tint = resolvedTint, modifier = Modifier.size(size))
        }
    }
}
