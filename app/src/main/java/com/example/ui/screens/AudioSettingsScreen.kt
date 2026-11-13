package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AlertLevel
import com.example.model.SoundSettings
import com.example.model.UserRole
import com.example.ui.theme.AlertCivilDefense
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertOrangeStreetFight
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertYellow
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
    isAudioPlaying: Boolean,
    playingAudioTitle: String,
    userRole: UserRole,
    onUpdateSettings: (SoundSettings) -> Unit,
    onPreviewSound: (Int, String) -> Unit,
    onStopAudio: () -> Unit,
    onTestKryveOzeroAlert: (AlertLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceItems = listOf(
        VoiceTrackItem(
            resId = R.raw.alert_air_raid,
            title = "Повітряна тривога (Червоний)",
            transcript = "«Увага, повітряна тривога! Негайно пройдіть до найближчого укриття.»",
            color = AlertRed
        ),
        VoiceTrackItem(
            resId = R.raw.alert_yellow,
            title = "Жовтий рівень (Підвищена небезпека)",
            transcript = "«Увага, повітряна тривога. Жовтий рівень небезпеки. Пройдіть до найближчого укриття.»",
            color = AlertYellow
        ),
        VoiceTrackItem(
            resId = R.raw.alert_street_fights,
            title = "Вуличні бої",
            transcript = "«Увага, вуличні бої! Залишайтеся в укритті і не підходьте до вікон.»",
            color = AlertOrangeStreetFight
        ),
        VoiceTrackItem(
            resId = R.raw.alert_civil_defense,
            title = "Сповіщення цивільного захисту",
            transcript = "«Увага! Важливе сповіщення цивільного захисту. Ознайомтеся з додатком Омастротривога.»",
            color = AlertCivilDefense
        ),
        VoiceTrackItem(
            resId = R.raw.alert_all_clear,
            title = "Відбій повітряної тривоги",
            transcript = "«Увага, відбій повітряної тривоги.»",
            color = AlertGreenClear
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("audio_settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Title
        item {
            Column {
                Text(
                    text = "ЗВУКОВІ СИГНАЛИ ТА СПОВІЩЕННЯ",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Сигнали звучать рівно 1 раз у реальному часі та автоматично зупиняються",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Single Playback Guarantee Card
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
                            text = "Сигнал відтворюється один раз і завершується сам. Тиснути «Зупинити» не потрібно.",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Voice alerts collection
        item {
            Text(
                text = "ОФІЦІЙНІ ГОЛОСОВІ ОПОВІЩЕННЯ (5 АУДІОЗАПИСІВ)",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        items(voiceItems.size) { index ->
            val item = voiceItems[index]
            val isThisPlaying = isAudioPlaying && playingAudioTitle.contains(item.title, ignoreCase = true)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isThisPlaying) item.color else SurfaceBorderDark
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
                                    .background(item.color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = item.color,
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
                                    text = "Звучить 1 раз",
                                    color = item.color,
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
                                    onPreviewSound(item.resId, item.title)
                                }
                            },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isThisPlaying) AlertRed else item.color.copy(alpha = 0.25f),
                                contentColor = if (isThisPlaying) Color.White else item.color
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.transcript,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }

        // Audio & Playback Parameters Card
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

                    // Volume Slider
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

                    // Vibration toggle
                    SettingToggleRow(
                        title = "Тактильна вібрація (короткий імпульс)",
                        subtitle = "Коротке вібро під час оголошення небезпеки",
                        checked = soundSettings.vibrationEnabled,
                        onCheckedChange = {
                            onUpdateSettings(soundSettings.copy(vibrationEnabled = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto play voice toggle
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

        // Test section
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
                    Text(
                        text = "Натисніть для перевірки відтворення сигналу в реальному часі",
                        color = TextMuted,
                        fontSize = 11.sp
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

private data class VoiceTrackItem(
    val resId: Int,
    val title: String,
    val transcript: String,
    val color: Color
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
