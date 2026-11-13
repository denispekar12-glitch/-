package com.example.model

import com.example.R

enum class AlertLevel(
    val title: String,
    val shortName: String,
    val soundResId: Int,
    val isDanger: Boolean,
    val priorityOrder: Int
) {
    SAFE(
        title = "Відбій повітряної тривоги",
        shortName = "Відбій / Спокійно",
        soundResId = R.raw.alert_all_clear,
        isDanger = false,
        priorityOrder = 0
    ),
    YELLOW(
        title = "Жовтий рівень небезпеки",
        shortName = "Жовтий рівень",
        soundResId = R.raw.alert_yellow,
        isDanger = true,
        priorityOrder = 1
    ),
    CIVIL_DEFENSE(
        title = "Важливе сповіщення цивільного захисту",
        shortName = "Цивільний захист",
        soundResId = R.raw.alert_civil_defense,
        isDanger = true,
        priorityOrder = 2
    ),
    RED(
        title = "Повітряна тривога",
        shortName = "Повітряна тривога",
        soundResId = R.raw.alert_air_raid,
        isDanger = true,
        priorityOrder = 3
    ),
    STREET_FIGHTS(
        title = "Увага! Вуличні бої",
        shortName = "Вуличні бої",
        soundResId = R.raw.alert_street_fights,
        isDanger = true,
        priorityOrder = 4
    );

    companion object {
        val dangerLevels: List<AlertLevel>
            get() = listOf(RED, YELLOW, STREET_FIGHTS, CIVIL_DEFENSE)

        fun primaryOf(levels: Set<AlertLevel>): AlertLevel {
            val dangers = levels.filter { it.isDanger }
            return dangers.maxByOrNull { it.priorityOrder } ?: SAFE
        }
    }
}

enum class UserRole {
    ADMIN,
    USER
}

data class RegionItem(
    val id: String,
    val name: String,
    val oblast: String,
    val isHromada: Boolean,
    val isMonitored: Boolean,
    val currentLevel: AlertLevel,
    val activeAlerts: Set<AlertLevel> = if (currentLevel.isDanger) setOf(currentLevel) else emptySet(),
    val alertStartTime: Long? = null,
    val threatDetails: String = "",
    val mapNormX: Float = 0.5f,
    val mapNormY: Float = 0.5f
) {
    val effectiveActiveAlerts: List<AlertLevel>
        get() {
            val base = activeAlerts.filter { it.isDanger }.ifEmpty {
                if (currentLevel.isDanger) listOf(currentLevel) else emptyList()
            }
            return base.sortedByDescending { it.priorityOrder }
        }

    val hasMultipleAlerts: Boolean
        get() = effectiveActiveAlerts.size > 1
}

data class AlertEventLog(
    val id: String,
    val regionName: String,
    val level: AlertLevel,
    val activeLevels: List<AlertLevel> = listOf(level),
    val timestamp: Long,
    val threatDetails: String,
    val source: String = "ДСНС / Адміністратор"
)

data class SoundSettings(
    val volume: Float = 1.0f,
    val vibrationEnabled: Boolean = true,
    val playOnceOnly: Boolean = true,
    val autoPlayVoice: Boolean = true
)

data class AdminBroadcast(
    val id: String,
    val regionName: String,
    val level: AlertLevel,
    val activeLevels: List<AlertLevel> = listOf(level),
    val timestamp: Long,
    val note: String,
    val adminName: String = "Адміністратор (Денис)"
)

data class AlertChangeEvent(
    val region: RegionItem,
    val levelsToPlay: List<AlertLevel>,
    val allActiveLevels: List<AlertLevel>
)

data class DiscussionComment(
    val id: String,
    val authorName: String,
    val authorRole: UserRole,
    val text: String,
    val timestamp: Long,
    val regionTag: String = "Кривоозерська громада",
    val isAdminVerified: Boolean = false
)
