package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertLevel
import com.example.model.SoundSettings
import com.example.model.UserRole
import com.example.ui.components.levelMapColor
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrYellow

@Composable
fun AudioSettingsScreen(
    soundSettings: SoundSettings,
    customAudioPaths: Map<AlertLevel, String> = emptyMap(),
    isAudioPlaying: Boolean,
    playingAudioTitle: String,
    userRole: UserRole,
    onUpdateSettings: (SoundSettings) -> Unit,
    onPreviewLevelSound: (AlertLevel, String) -> Unit,
    onPreviewRawSound: (Int, String) -> Unit = { _, _ -> },
    onStopAudio: () -> Unit,
    onAttachCustomAudio: (AlertLevel, Uri) -> Boolean = { _, _ -> false },
    onClearCustomAudio: (AlertLevel) -> Unit = {},
    onTestKryveOzeroAlert: (AlertLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingTargetLevel by remember { mutableStateOf<AlertLevel?>(null) }
    var audioStatusBanner by remember { mutableStateOf<String?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingTargetLevel
        pendingTargetLevel = null
        if (uri != null && target != null) {
            val saved = onAttachCustomAudio(target, uri)
            audioStatusBanner = if (saved) {
                "✓ Аудіофайл для «${target.shortName}» збережено назавжди!"
            } else {
                "⚠️ Не вдалося імпортувати вибраний аудіофайл."
            }
        }
    }

    val voiceItems = listOf(
        VoiceTrackItem(AlertLevel.RED, "Повітряна тривога", "«Увага, повітряна тривога! Негайно пройдіть до найближчого укриття.»"),
        VoiceTrackItem(AlertLevel.MISSILE, "Ракетна небезпека / Балістика", "Голосовий або сиренний сигнал ракетної небезпеки (можна прикріпити свій файл)."),
        VoiceTrackItem(AlertLevel.DRONE, "Загроза БПЛА / Шахеди", "Попередження про ударні безпілотники у напрямку району."),
        VoiceTrackItem(AlertLevel.ARTILLERY, "Загроза артобстрілу", "«Увага, загроза артобстрілу! Негайно пройдіть до найближчого укриття.»"),
        VoiceTrackItem(AlertLevel.CHEMICAL, "Хімічна загроза", "«Увага, хімічна загроза! Увімкніть телевізор або радіо для отримання інструкцій.»"),
        VoiceTrackItem(AlertLevel.RADIATION, "Радіаційна небезпека", "«Увага, радіаційна небезпека! Увімкніть телевізор або радіо для отримання інструкцій.»"),
        VoiceTrackItem(AlertLevel.STREET_FIGHTS, "Вуличні бої", "«Увага, вуличні бої! Залишайтеся в укритті і не підходьте до вікон.»"),
        VoiceTrackItem(AlertLevel.CIVIL_DEFENSE, "Сповіщення цивільного захисту", "«Увага! Важливе сповіщення цивільного захисту.»"),
        VoiceTrackItem(AlertLevel.YELLOW, "Жовтий рівень небезпеки", "«Увага, жовтий рівень небезпеки. Підвищена увага.»"),
        VoiceTrackItem(AlertLevel.CUSTOM_ALERT, "Спеціальне оповіщення", "Додатковий спеціальний сигнал адміністратора (прикріпіть свій аудіофайл)."),
        VoiceTrackItem(AlertLevel.SAFE, "Відбій тривоги", "«Увага, відбій повітряної тривоги.»")
    )

    val dedicatedClearVoiceItems = listOf(
        DedicatedClearTrackItem(
            resId = com.example.R.raw.alert_civil_defense_clear,
            title = "Відбій цивільного захисту",
            transcript = "«Увага, відбій цивільного захисту»"
        ),
        DedicatedClearTrackItem(
            resId = com.example.R.raw.alert_artillery_clear,
            title = "Відбій загрози артобстрілу",
            transcript = "«Увага, відбій загрози артобстрілу»"
        ),
        DedicatedClearTrackItem(
            resId = com.example.R.raw.alert_chemical_clear,
            title = "Відбій хімічної загрози",
            transcript = "«Увага, відбій хімічної загрози»"
        ),
        DedicatedClearTrackItem(
            resId = com.example.R.raw.alert_radiation_clear,
            title = "Відбій радіаційної небезпеки",
            transcript = "«Увага, відбій радіаційної небезпеки»"
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("audio_settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "ЗВУКОВІ СИГНАЛИ ТА ВЛАСНІ ФАЙЛИ",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Можете додати власний аудіофайл для будь-якого типу тривоги та відбою — усе зберігається постійно",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (!audioStatusBanner.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AlertGreenClear.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = audioStatusBanner ?: "",
                        color = AlertGreenClear,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101B26)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AlertGreenClear.copy(alpha = 0.5f))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AlertGreenClear,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "РЕЖИМ: ОДНОРАЗОВЕ ОЗВУЧЕННЯ (1 РАЗ)",
                            color = AlertGreenClear,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Якщо сигнал знято в режимі «Без відбою», звук відбою НЕ вмикається.",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "СИГНАЛИ ДЛЯ ВСІХ ТИПІВ ТРИВОГ (${voiceItems.size})",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        items(voiceItems.size) { index ->
            val item = voiceItems[index]
            val itemColor = levelMapColor(item.level)
            val hasCustomFile = !customAudioPaths[item.level].isNullOrBlank()
            val isThisPlaying = isAudioPlaying && playingAudioTitle.contains(item.title, ignoreCase = true)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isThisPlaying) itemColor else SurfaceBorderDark
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_track_$index")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(itemColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = itemColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (hasCustomFile) "✓ Власний файл прикріплено (збережено)" else "Вбудований сигнал • звучить 1 раз",
                                    color = if (hasCustomFile) AlertGreenClear else itemColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        ElevatedButton(
                            onClick = {
                                if (isThisPlaying) {
                                    onStopAudio()
                                } else {
                                    onPreviewLevelSound(item.level, item.title)
                                }
                            },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isThisPlaying) AlertRed else itemColor.copy(alpha = 0.25f),
                                contentColor = if (isThisPlaying) Color.White else itemColor
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("play_btn_$index")
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isThisPlaying) "Зупинити" else "Слухати",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isThisPlaying) "Стоп" else "Слухати",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.transcript,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pendingTargetLevel = item.level
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("pick_audio_btn_${item.level.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = UkrYellow,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasCustomFile) "Замінити аудіофайл" else "Обрати свій аудіофайл",
                                fontSize = 11.sp,
                                color = UkrYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasCustomFile) {
                            OutlinedButton(
                                onClick = {
                                    onClearCustomAudio(item.level)
                                    audioStatusBanner = "Файл для «${item.level.shortName}» скинуто до стандартного."
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Скинути аудіофайл",
                                    tint = AlertRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "ВБУДОВАНІ ЗАПИСИ ВІДБОЮ ПО ОКРЕМИХ ЗАГРОЗАХ (${dedicatedClearVoiceItems.size})",
                color = AlertGreenClear,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        items(dedicatedClearVoiceItems.size) { index ->
            val clearItem = dedicatedClearVoiceItems[index]
            val isThisPlaying = isAudioPlaying && playingAudioTitle.contains(clearItem.title, ignoreCase = true)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isThisPlaying) AlertGreenClear else SurfaceBorderDark
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_clear_track_$index")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clearItem.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = clearItem.transcript,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ElevatedButton(
                        onClick = {
                            if (isThisPlaying) {
                                onStopAudio()
                            } else {
                                onPreviewRawSound(clearItem.resId, clearItem.title)
                            }
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (isThisPlaying) AlertRed else AlertGreenClear.copy(alpha = 0.2f),
                            contentColor = if (isThisPlaying) Color.White else AlertGreenClear
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("play_clear_btn_$index")
                    ) {
                        Icon(
                            imageVector = if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isThisPlaying) "Зупинити" else "Слухати",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isThisPlaying) "Стоп" else "Слухати",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

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
                        text = "НАЛАШТУВАННЯ ЗВУКУ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeDown,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Гучність сповіщення:", color = TextPrimary, fontSize = 13.sp)
                        }
                        Text(
                            text = "${(soundSettings.volume * 100).toInt()}%",
                            color = UkrYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = soundSettings.volume,
                        onValueChange = { newVol ->
                            onUpdateSettings(soundSettings.copy(volume = newVol))
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = UkrYellow,
                            activeTrackColor = UkrYellow,
                            inactiveTrackColor = SurfaceBorderDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("volume_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Тактильна вібрація (короткий імпульс)",
                        subtitle = "Коротке вібро під час оголошення небезпеки",
                        checked = soundSettings.vibrationEnabled,
                        onCheckedChange = {
                            onUpdateSettings(soundSettings.copy(vibrationEnabled = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Автоматичне озвучення при надходженні сигналу",
                        subtitle = "Голос лунає один раз у момент оголошення тривоги",
                        checked = soundSettings.autoPlayVoice,
                        onCheckedChange = {
                            onUpdateSettings(soundSettings.copy(autoPlayVoice = it))
                        }
                    )
                }
            }
        }

        if (userRole == UserRole.ADMIN) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorderDark)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ШВИДКИЙ ТЕСТ СПОВІЩЕННЯ ДЛЯ КРИВОГО ОЗЕРА (1 РАЗ)",
                            color = UkrYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onTestKryveOzeroAlert(AlertLevel.RED) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("test_red_btn")
                            ) {
                                Text("Тривога", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onTestKryveOzeroAlert(AlertLevel.CIVIL_DEFENSE) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertCivilDefense),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("test_civil_btn")
                            ) {
                                Text("Цив. захист", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onTestKryveOzeroAlert(AlertLevel.SAFE) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertGreenClear),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("test_clear_btn")
                            ) {
                                Text("Відбій", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class VoiceTrackItem(
    val level: AlertLevel,
    val title: String,
    val transcript: String
)

private data class DedicatedClearTrackItem(
    val resId: Int,
    val title: String,
    val transcript: String
)

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceBorderDark
            )
        )
    }
}
