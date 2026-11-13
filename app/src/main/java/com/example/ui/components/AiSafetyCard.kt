package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAdvisorUiState
import com.example.ui.theme.AlertGreenClear
import com.example.ui.theme.AlertRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UkrBlue
import com.example.ui.theme.UkrYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSafetyCard(
    aiState: AiAdvisorUiState,
    onAnalyzeSituation: () -> Unit,
    onAskQuestion: (String) -> Unit,
    onResetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var questionText by remember { mutableStateOf("") }
    val isBusy = aiState is AiAdvisorUiState.Loading || aiState is AiAdvisorUiState.Streaming

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(PrimaryBlue, UkrYellow))
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_safety_advisor_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = UkrYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ШІ-РАДНИК ЦИВІЛЬНОГО ЗАХИСТУ",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.4.sp
                        )
                        Text(
                            text = "Gemini • Аналіз тривог та інструкції безпеки",
                            color = UkrYellow,
                            fontSize = 11.sp
                        )
                    }
                }

                if (aiState !is AiAdvisorUiState.Idle && !isBusy) {
                    IconButton(
                        onClick = onResetState,
                        modifier = Modifier.testTag("ai_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистити відповідь",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAnalyzeSituation,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UkrBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_analyze_situation_btn")
            ) {
                if (aiState is AiAdvisorUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Підключення до Gemini...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (aiState is AiAdvisorUiState.Streaming) "Генерація зведення..." else "Аналіз поточної ситуації",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Вуличні бої: що робити?",
                    "Що взяти в укриття?",
                    "Правило двох стін"
                ).forEach { preset ->
                    AssistChip(
                        onClick = {
                            if (!isBusy) {
                                onAskQuestion(preset)
                            }
                        },
                        enabled = !isBusy,
                        label = { Text(preset, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF101522),
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    placeholder = { Text("Запитайте про безпеку чи укриття...", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !isBusy,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkrYellow,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = TextMuted,
                        unfocusedPlaceholderColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_question_input")
                )

                Button(
                    onClick = {
                        val trimmed = questionText.trim()
                        if (trimmed.isNotEmpty() && !isBusy) {
                            onAskQuestion(trimmed)
                            questionText = ""
                        }
                    },
                    enabled = questionText.isNotBlank() && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("ai_send_question_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Надіслати запит",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when (aiState) {
                is AiAdvisorUiState.Idle -> Unit

                is AiAdvisorUiState.Loading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E1320), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = UkrYellow,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gemini аналізує дані моніторингу...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                is AiAdvisorUiState.Streaming -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0E1320),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("ai_response_streaming")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "● Онлайн-відповідь Gemini:",
                                color = UkrYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = aiState.text,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                is AiAdvisorUiState.Success -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0E1622),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AlertGreenClear.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .testTag("ai_response_success")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✓ Рекомендація Gemini",
                                color = AlertGreenClear,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = aiState.text,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                is AiAdvisorUiState.Error -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AlertRed.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AlertRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .testTag("ai_response_error")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Помилка Gemini API",
                                    color = AlertRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiState.message,
                                color = Color(0xFFFFB4AB),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onAnalyzeSituation,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Повторити аналіз", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
