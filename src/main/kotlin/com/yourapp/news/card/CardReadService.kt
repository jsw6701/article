package com.yourapp.news.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CardReadService(
    private val cardQuery: CardQuery
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    /**
     * 카드 리스트 조회
     */
    fun listCards(filter: CardListFilter): List<CardListItem> {
        return cardQuery.listCards(filter).map { row ->
            val (cardJson, conclusion) = parseContentJson(row.cardContentJson)
            
            CardListItem(
                issueId = row.issueId,
                issueFingerprint = row.issueFingerprint,
                issueGroup = row.issueGroup,
                issueTitle = row.issueTitle,
                issueLastPublishedAt = row.issueLastPublishedAt,
                cardStatus = row.cardStatus,
                cardUpdatedAt = row.cardUpdatedAt,
                conclusion = conclusion,
                cardJson = cardJson
            )
        }
    }

    /**
     * 카드 상세 조회
     */
    fun getCard(issueId: Long): CardDetail? {
        val row = cardQuery.getCard(issueId) ?: return null
        val cardJson = parseJson(row.cardContentJson)

        return CardDetail(
            issueId = row.issueId,
            issueFingerprint = row.issueFingerprint,
            issueGroup = row.issueGroup,
            issueTitle = row.issueTitle,
            issueFirstPublishedAt = row.issueFirstPublishedAt,
            issueLastPublishedAt = row.issueLastPublishedAt,
            issueArticleCount = row.issueArticleCount,
            issuePublisherCount = row.issuePublisherCount,
            cardStatus = row.cardStatus,
            cardModel = row.cardModel,
            cardCreatedAt = row.cardCreatedAt,
            cardUpdatedAt = row.cardUpdatedAt,
            cardJson = cardJson
        )
    }

    /**
     * 오늘(최근 24시간) 카드 조회
     */
    fun getTodayCards(limit: Int = 7): List<CardListItem> {
        return cardQuery.getTodayCards(limit).map { row ->
            val (cardJson, conclusion) = parseContentJson(row.cardContentJson)

            CardListItem(
                issueId = row.issueId,
                issueFingerprint = row.issueFingerprint,
                issueGroup = row.issueGroup,
                issueTitle = row.issueTitle,
                issueLastPublishedAt = row.issueLastPublishedAt,
                cardStatus = row.cardStatus,
                cardUpdatedAt = row.cardUpdatedAt,
                conclusion = conclusion,
                cardJson = cardJson
            )
        }
    }

    private fun parseContentJson(json: String): Pair<JsonNode?, String?> {
        return try {
            val node = objectMapper.readTree(json)
            val conclusion = node.get("conclusion")?.asText()
            node to conclusion
        } catch (e: Exception) {
            log.warn("Failed to parse card JSON: {}", e.message)
            null to null
        }
    }

    private fun parseJson(json: String): JsonNode? {
        return try {
            objectMapper.readTree(json)
        } catch (e: Exception) {
            log.warn("Failed to parse card JSON: {}", e.message)
            null
        }
    }
}

/**
 * 카드 리스트 아이템 (API 응답용)
 */
data class CardListItem(
    val issueId: Long,
    val issueFingerprint: String,
    val issueGroup: String,
    val issueTitle: String,
    val issueLastPublishedAt: LocalDateTime,
    val cardStatus: String,
    val cardUpdatedAt: LocalDateTime,
    val conclusion: String?,
    val cardJson: JsonNode?
)

/**
 * 카드 상세 (API 응답용)
 */
data class CardDetail(
    val issueId: Long,
    val issueFingerprint: String,
    val issueGroup: String,
    val issueTitle: String,
    val issueFirstPublishedAt: LocalDateTime,
    val issueLastPublishedAt: LocalDateTime,
    val issueArticleCount: Int,
    val issuePublisherCount: Int,
    val cardStatus: String,
    val cardModel: String,
    val cardCreatedAt: LocalDateTime,
    val cardUpdatedAt: LocalDateTime,
    val cardJson: JsonNode?
)
