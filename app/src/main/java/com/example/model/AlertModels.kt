package com.example.model

import com.example.R

enum class AlertLevel(
    val title: String,
    val shortName: String,
    val allClearTitle: String,
    val soundResId: Int,
    val clearSoundResId: Int,
    val isDanger: Boolean,
    val priorityOrder: Int
) {
    SAFE(
        title = "Відбій повітряної тривоги",
        shortName = "Відбій / Спокійно",
        allClearTitle = "Відбій загрози",
        soundResId = R.raw.alert_all_clear,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = false,
        priorityOrder = 0
    ),
    YELLOW(
        title = "Жовтий рівень небезпеки",
        shortName = "Жовтий рівень",
        allClearTitle = "Відбій жовтого рівня",
        soundResId = R.raw.alert_yellow,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 1
    ),
    CIVIL_DEFENSE(
        title = "Важливе сповіщення цивільного захисту",
        shortName = "Цивільний захист",
        allClearTitle = "Відбій цивільного захисту",
        soundResId = R.raw.alert_civil_defense,
        clearSoundResId = R.raw.alert_civil_defense_clear,
        isDanger = true,
        priorityOrder = 2
    ),
    CUSTOM_ALERT(
        title = "Спеціальне оповіщення",
        shortName = "Спецсигнал",
        allClearTitle = "Відбій спецсигналу",
        soundResId = R.raw.alert_civil_defense,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 3
    ),
    DRONE(
        title = "Загроза ударних БПЛА (Шахеди)",
        shortName = "БПЛА / Шахеди",
        allClearTitle = "Відбій загрози БПЛА",
        soundResId = R.raw.alert_yellow,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 4
    ),
    RED(
        title = "Повітряна тривога",
        shortName = "Повітряна тривога",
        allClearTitle = "Відбій повітряної тривоги",
        soundResId = R.raw.alert_air_raid,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 5
    ),
    MISSILE(
        title = "Ракетна небезпека / Балістика",
        shortName = "Ракета / Балістика",
        allClearTitle = "Відбій ракетної небезпеки",
        soundResId = R.raw.alert_air_raid,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 6
    ),
    ARTILLERY(
        title = "Загроза артобстрілу",
        shortName = "Артобстріл",
        allClearTitle = "Відбій загрози артобстрілу",
        soundResId = R.raw.alert_artillery,
        clearSoundResId = R.raw.alert_artillery_clear,
        isDanger = true,
        priorityOrder = 7
    ),
    STREET_FIGHTS(
        title = "Увага! Вуличні бої",
        shortName = "Вуличні бої",
        allClearTitle = "Відбій вуличних боїв",
        soundResId = R.raw.alert_street_fights,
        clearSoundResId = R.raw.alert_all_clear,
        isDanger = true,
        priorityOrder = 8
    ),
    CHEMICAL(
        title = "Хімічна загроза",
        shortName = "Хімічна",
        allClearTitle = "Відбій хімічної загрози",
        soundResId = R.raw.alert_chemical,
        clearSoundResId = R.raw.alert_chemical_clear,
        isDanger = true,
        priorityOrder = 9
    ),
    RADIATION(
        title = "Радіаційна небезпека",
        shortName = "Радіаційна",
        allClearTitle = "Відбій радіаційної небезпеки",
        soundResId = R.raw.alert_radiation,
        clearSoundResId = R.raw.alert_radiation_clear,
        isDanger = true,
        priorityOrder = 10
    );

    fun defaultThreatNote(): String = when (this) {
        SAFE -> "Відбій загрози. Спокійно."
        YELLOW -> "Жовтий рівень небезпеки. Підвищена увага."
        CIVIL_DEFENSE -> "Важливе сповіщення цивільного захисту! Ознайомтеся з додатком Омастротривога."
        CUSTOM_ALERT -> "Спеціальне оповіщення від адміністратора! Слідкуйте за повідомленнями."
        DRONE -> "Атака ворожих БПЛА / Шахедів у напрямку району! Негайно в укриття!"
        RED -> "Повітряна тривога! Негайно пройдіть до найближчого укриття!"
        MISSILE -> "Ракетна небезпека / балістика! Терміново перейдіть в укриття!"
        ARTILLERY -> "Увага, загроза артобстрілу! Негайно пройдіть до найближчого укриття!"
        STREET_FIGHTS -> "Вуличні бої! Залишайтесь в укритті і не підходьте до вікон!"
        CHEMICAL -> "Увага, хімічна загроза! Увімкніть телевізор або радіо для отримання інструкцій."
        RADIATION -> "Увага, радіаційна небезпека! Увімкніть телевізор або радіо для отримання інструкцій."
    }

    companion object {
        val dangerLevels: List<AlertLevel>
            get() = listOf(
                RED,
                MISSILE,
                DRONE,
                ARTILLERY,
                CHEMICAL,
                RADIATION,
                STREET_FIGHTS,
                CIVIL_DEFENSE,
                YELLOW,
                CUSTOM_ALERT
            )

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
    val mapNormY: Float = 0.5f,
    val withoutAllClear: Boolean = false
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
    val source: String = "Адміністратор Денис",
    val withoutAllClear: Boolean = false
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
    val adminName: String = "Адміністратор (Денис)",
    val withoutAllClear: Boolean = false
)

data class AlertChangeEvent(
    val region: RegionItem,
    val levelsToPlay: List<AlertLevel>,
    val allActiveLevels: List<AlertLevel>,
    val withoutAllClear: Boolean = false,
    val explicitRawResIds: List<Int> = emptyList()
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
