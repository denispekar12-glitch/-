package com.example.ai

import com.example.data.AlertRepository
import com.example.model.RegionItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

sealed interface AiAdvisorUiState {
    data object Idle : AiAdvisorUiState
    data object Loading : AiAdvisorUiState
    data class Streaming(val text: String) : AiAdvisorUiState
    data class Success(val text: String) : AiAdvisorUiState
    data class Error(val message: String) : AiAdvisorUiState
}

class AiSafetyAdvisor {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Ти — професійний український асистент із цивільного захисту та безпеки у додатку «Омастротривога» (фокус: Кривоозерська громада, Первомайський та Миколаївський райони, усі райони України).
                    Відповідай завжди українською мовою, чітко, структуровано, спокійно та практично.
                    Надавай конкретні кроки безпеки (укриття, правило двох стін, дії при повітряній тривозі, ракетній небезпеці, БПЛА, артобстрілах, хімічній чи радіаційній небезпеці, жовтому рівні, вуличних боях або сигналах цивільного захисту).
                    Не вигадуй поточних подій — спирайся лише на передані дані моніторингу та перевірені рекомендації ДСНС України.
                    Відповідь має бути лаконічною та зручною для швидкого читання на смартфоні.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamSituationBrief(regions: List<RegionItem>): Flow<String> {
        val contextSummary = buildRegionsContextSummary(regions)
        val prompt = """
            Ось актуальні дані моніторингу тривог по районах у додатку «Омастротривога» прямо зараз:
            $contextSummary

            Дай коротке оперативне зведення (3–5 пунктів):
            1. Поточний статус Кривоозерської громади, Первомайського та Миколаївського районів.
            2. Загальна ситуація по активних загрозах у районах України.
            3. Чіткий алгоритм дій для мешканців просто зараз.
        """.trimIndent()
        return model.generateContentStream(prompt).mapNotNull { it.text }
    }

    fun streamSafetyAnswer(question: String, regions: List<RegionItem>): Flow<String> {
        val contextSummary = buildRegionsContextSummary(regions)
        val prompt = """
            Поточний контекст тривог по районах у додатку:
            $contextSummary

            Запитання користувача: $question

            Дай практичну, безпечну та лаконічну відповідь українською мовою.
        """.trimIndent()
        return model.generateContentStream(prompt).mapNotNull { it.text }
    }

    private fun buildRegionsContextSummary(regions: List<RegionItem>): String {
        val kryveOzero = regions.find { it.id == AlertRepository.KRYVE_OZERO_ID }
        val mykolaiv = regions.find { it.id == "mykolaiv" }
        val activeDangers = regions.filter { it.currentLevel.isDanger }

        val koDesc = if (kryveOzero != null) {
            val activeNames = kryveOzero.effectiveActiveAlerts.joinToString(", ") { it.title }
                .ifBlank { kryveOzero.currentLevel.title }
            val noClearTag = if (kryveOzero.withoutAllClear) " [БЕЗ ВІДБОЮ]" else ""
            "Кривоозерська громада (Криве Озеро): $activeNames$noClearTag. Деталі: ${kryveOzero.threatDetails}"
        } else {
            "Кривоозерська громада: дані відсутні"
        }

        val mkDesc = if (mykolaiv != null) {
            "Миколаївський район: ${mykolaiv.currentLevel.title}"
        } else {
            "Миколаївський район: немає даних"
        }

        val othersDesc = if (activeDangers.isEmpty()) {
            "Активних загроз по районах немає (усе спокійно)."
        } else {
            "Райони з активною загрозою (${activeDangers.size}): " +
                activeDangers.take(12).joinToString("; ") { reg ->
                    val alerts = reg.effectiveActiveAlerts.joinToString("+") { it.shortName }
                        .ifBlank { reg.currentLevel.shortName }
                    val tag = if (reg.withoutAllClear) " (без відбою)" else ""
                    "${reg.name} [$alerts]$tag"
                }
        }

        return "$koDesc\n$mkDesc\n$othersDesc"
    }
}
