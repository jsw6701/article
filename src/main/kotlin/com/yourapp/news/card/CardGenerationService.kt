package com.yourapp.news.card

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.yourapp.news.article.ArticleStore
import com.yourapp.news.issue.Issue
import com.yourapp.news.issue.IssueStore
import com.yourapp.news.llm.gemini.GeminiClient
import com.yourapp.news.llm.gemini.GeminiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CardGenerationService(
    private val issueStore: IssueStore,
    private val articleStore: ArticleStore,
    private val cardStore: CardStore,
    private val logStore: CardGenerationLogStore,
    private val geminiClient: GeminiClient,
    private val geminiProperties: GeminiProperties,
    private val promptBuilder: PromptBuilder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val MAX_ARTICLES_PER_CARD = 8
    }

    /**
     * 단일 이슈에 대해 카드 생성
     */
    fun generateCardForIssue(issueId: Long): CardGenerationResult {
        val issue = issueStore.findById(issueId)
        if (issue == null) {
            log.warn("Issue not found: {}", issueId)
            return CardGenerationResult.failure("Issue not found")
        }

        return generateCard(issue)
    }

    /**
     * 카드 생성 대상 이슈들에 대해 일괄 생성
     */
    fun generateCardsForTargets(hours: Long = 48, limit: Int = 50): BatchGenerationResult {
        val targets = issueStore.findCardGenerationTargets(hours, limit)
        
        if (targets.isEmpty()) {
            log.info("No card generation targets found")
            return BatchGenerationResult(0, 0, 0)
        }

        log.info("Found {} card generation targets", targets.size)

        var successCount = 0
        var skipCount = 0
        var failCount = 0

        for (issue in targets) {
            // ACTIVE 카드가 이미 있으면 스킵 (FAILED는 재시도)
            if (cardStore.existsActiveByIssueId(issue.id!!)) {
                log.debug("Active card already exists for issue {}", issue.id)
                skipCount++
                continue
            }

            val result = generateCard(issue)
            when {
                result.success -> successCount++
                else -> failCount++
            }
        }

        log.info("Card generation completed: {} success, {} skipped, {} failed",
            successCount, skipCount, failCount)

        return BatchGenerationResult(successCount, skipCount, failCount)
    }

    private fun generateCard(issue: Issue): CardGenerationResult {
        val issueId = issue.id!!
        val fingerprint = issue.fingerprint

        // 1. 기사 로드
        val articleLinks = issueStore.findArticleLinksByIssueId(issueId)
        val articles = articleStore.findByLinksForCard(articleLinks, MAX_ARTICLES_PER_CARD)

        if (articles.isEmpty()) {
            log.warn("No articles found for issue {}", issueId)
            return CardGenerationResult.failure("No articles found")
        }

        // 2. 프롬프트 생성
        val prompt = promptBuilder.build(issue, articles)

        // 3. Gemini API 호출
        val geminiResult = geminiClient.generateContent(prompt, PromptBuilder.RESPONSE_SCHEMA)

        // 4. 로그 기록
        logStore.insert(
            CardGenerationLog(
                issueId = issueId,
                issueFingerprint = fingerprint,
                attempt = 1,
                success = geminiResult.success,
                httpStatus = geminiResult.httpStatus,
                errorMessage = geminiResult.errorMessage,
                latencyMs = geminiResult.latencyMs
            )
        )

        if (!geminiResult.success || geminiResult.jsonContent == null) {
            log.error("Gemini API failed for issue {}: {}", issueId, geminiResult.errorMessage)
            return CardGenerationResult.failure(geminiResult.errorMessage ?: "API call failed")
        }

        // 5. JSON 검증
        val validationResult = validateCardJson(geminiResult.jsonContent)
        if (!validationResult.valid) {
            log.error("Invalid card JSON for issue {}: {}", issueId, validationResult.error)
            
            // 실패 카드 저장 (status = FAILED)
            cardStore.upsert(
                Card(
                    issueId = issueId,
                    issueFingerprint = fingerprint,
                    model = geminiProperties.model,
                    contentJson = geminiResult.jsonContent,
                    status = CardStatus.FAILED
                )
            )
            
            return CardGenerationResult.failure(validationResult.error ?: "Validation failed")
        }

        // 6. 카드 저장
        val cardId = cardStore.upsert(
            Card(
                issueId = issueId,
                issueFingerprint = fingerprint,
                model = geminiProperties.model,
                contentJson = geminiResult.jsonContent,
                status = CardStatus.ACTIVE
            )
        )

        // 7. Issue에 headline, signalSummary 저장
        updateIssueHeadline(issueId, geminiResult.jsonContent)

        log.info("Generated card {} for issue {}", cardId, issueId)
        return CardGenerationResult.success(cardId, geminiResult.jsonContent)
    }

    /**
     * GPT 응답에서 headline, signalSummary를 파싱하여 Issue에 저장
     */
    private fun updateIssueHeadline(issueId: Long, json: String) {
        try {
            val card: Map<String, Any> = objectMapper.readValue(json)
            val headline = card["headline"] as? String
            val signalSummary = card["signal_summary"] as? String

            if (headline != null || signalSummary != null) {
                issueStore.updateHeadline(issueId, headline, signalSummary)
                log.debug("Updated issue {} headline: {}", issueId, headline)
            }
        } catch (e: Exception) {
            log.warn("Failed to parse headline from card JSON for issue {}: {}", issueId, e.message)
        }
    }

    /**
     * 카드 JSON 검증
     * - 필수 필드 존재 확인
     * - evidence 2~4개
     * - impact.score 0~5
     */
    private fun validateCardJson(json: String): ValidationResult {
        return try {
            val card: Map<String, Any> = objectMapper.readValue(json)

            // 필수 필드 확인
            val requiredFields = listOf(
                "headline", "signal_summary", "issue_title", "conclusion", "why_it_matters",
                "evidence", "counter_scenario", "impact", "action_guide"
            )
            for (field in requiredFields) {
                if (!card.containsKey(field)) {
                    return ValidationResult.invalid("Missing required field: $field")
                }
            }

            // evidence 검증
            val evidence = card["evidence"]
            if (evidence !is List<*>) {
                return ValidationResult.invalid("evidence must be an array")
            }
            if (evidence.size < 2 || evidence.size > 4) {
                return ValidationResult.invalid("evidence must have 2-4 items, got ${evidence.size}")
            }

            // impact 검증
            val impact = card["impact"]
            if (impact !is Map<*, *>) {
                return ValidationResult.invalid("impact must be an object")
            }
            val score = impact["score"]
            if (score !is Number) {
                return ValidationResult.invalid("impact.score must be a number")
            }
            if (score.toInt() < 0 || score.toInt() > 5) {
                return ValidationResult.invalid("impact.score must be 0-5, got ${score.toInt()}")
            }

            ValidationResult.valid()
        } catch (e: Exception) {
            ValidationResult.invalid("JSON parse error: ${e.message}")
        }
    }
}

/**
 * 카드 생성 결과
 */
data class CardGenerationResult(
    val success: Boolean,
    val cardId: Long?,
    val contentJson: String?,
    val error: String?
) {
    companion object {
        fun success(cardId: Long, contentJson: String) = CardGenerationResult(
            success = true,
            cardId = cardId,
            contentJson = contentJson,
            error = null
        )

        fun failure(error: String) = CardGenerationResult(
            success = false,
            cardId = null,
            contentJson = null,
            error = error
        )
    }
}

/**
 * 일괄 생성 결과
 */
data class BatchGenerationResult(
    val successCount: Int,
    val skipCount: Int,
    val failCount: Int
)

/**
 * JSON 검증 결과
 */
data class ValidationResult(
    val valid: Boolean,
    val error: String?
) {
    companion object {
        fun valid() = ValidationResult(true, null)
        fun invalid(error: String) = ValidationResult(false, error)
    }
}
