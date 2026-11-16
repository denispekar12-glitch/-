package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.AlertLevel
import java.util.concurrent.atomic.AtomicInteger

class AlertNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "omastro_emergency_channel_v2"
        const val CHANNEL_NAME = "Омастротривога: Сповіщення у реальному часі"
        private val notificationCounter = AtomicInteger(1000)
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Офіційні сповіщення тривоги, ракет, БПЛА, артобстрілів, цивільного захисту, вуличних боїв та відбою"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setShowBadge(true)
                setSound(alarmSound, audioAttributes)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showEmergencyNotification(
        regionName: String,
        levels: List<AlertLevel>,
        details: String,
        isAdminBroadcast: Boolean = true,
        withoutAllClear: Boolean = false
    ) {
        val primaryLevel = AlertLevel.primaryOf(levels.toSet())
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationCounter.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dangerLevels = levels.filter { it.isDanger }.distinct()
        val noClearSuffix = if (withoutAllClear && dangerLevels.isNotEmpty()) " [БЕЗ ВІДБОЮ]" else ""

        val title = if (dangerLevels.size > 1) {
            "🚨 [ДЕКІЛЬКА ЗАГРОЗ (${dangerLevels.size})]$noClearSuffix $regionName"
        } else {
            when (primaryLevel) {
                AlertLevel.RED -> "🚨 [ТРИВОГА В РЕАЛЬНОМУ ЧАСІ]$noClearSuffix $regionName"
                AlertLevel.MISSILE -> "🚀 [РАКЕТНА НЕБЕЗПЕКА]$noClearSuffix $regionName"
                AlertLevel.DRONE -> "🛸 [АТАКА БПЛА / ШАХЕДИ]$noClearSuffix $regionName"
                AlertLevel.ARTILLERY -> "💥 [ЗАГРОЗА АРТОБСТРІЛУ]$noClearSuffix $regionName"
                AlertLevel.CHEMICAL -> "☣️ [ХІМІЧНА ЗАГРОЗА]$noClearSuffix $regionName"
                AlertLevel.RADIATION -> "☢️ [РАДІАЦІЙНА НЕБЕЗПЕКА]$noClearSuffix $regionName"
                AlertLevel.CUSTOM_ALERT -> "📣 [СПЕЦСИГНАЛ]$noClearSuffix $regionName"
                AlertLevel.YELLOW -> "⚠️ [ЖОВТИЙ РІВЕНЬ]$noClearSuffix $regionName"
                AlertLevel.STREET_FIGHTS -> "🛑 [УВАГА! ВУЛИЧНІ БОЇ]$noClearSuffix $regionName"
                AlertLevel.CIVIL_DEFENSE -> "📢 [ЦИВІЛЬНИЙ ЗАХИСТ]$noClearSuffix $regionName"
                AlertLevel.SAFE -> "🟢 [ВІДБІЙ ТРИВОГИ] $regionName"
            }
        }

        val defaultSummary = if (dangerLevels.size > 1) {
            dangerLevels.joinToString("\n") { "• ${it.title}" }
        } else {
            primaryLevel.defaultThreatNote()
        }

        val text = if (details.isNotBlank()) details else defaultSummary

        val broadcastSubtext = if (isAdminBroadcast) {
            "Оголошено адміністратором Денисом • Омастротривога"
        } else {
            "Офіційне сповіщення • Омастротривога"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(broadcastSubtext)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\n📌 $broadcastSubtext")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

        try {
            val notificationId = 1001
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Runtime permission check handled in MainActivity
        }
    }
}
