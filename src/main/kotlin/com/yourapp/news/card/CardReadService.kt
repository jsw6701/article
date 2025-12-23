package com.yourapp.news.card

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
                articleCount = row.issueArticleCount,
                publisherCount = row.issuePublisherCount,
                cardStatus = row.cardStatus,
                cardUpdatedAt = row.cardUpdatedAt,
                conclusion = conclusion,
                cardJson = cardJson
            )
        }
    }

    /**
     * 카드 상세 조회 (기사 목록 포함)
     */
    fun getCard(issueId: Long): CardDetail? {
        val row = cardQuery.getCard(issueId) ?: return null

        // 2-step: 기사 목록 조회 (최신순, 최대 15개)
        val articles = cardQuery.getArticlesByIssueId(issueId, limit = 15)
            .map { articleRow ->
                ArticleSummary(
                    title = articleRow.title,
                    link = articleRow.link,
                    publisher = articleRow.publisher,
                    publishedAt = articleRow.publishedAt
                )
            }

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
            cardJson = row.cardContentJson,
            articles = articles
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
                articleCount = row.issueArticleCount,
                publisherCount = row.issuePublisherCount,
                cardStatus = row.cardStatus,
                cardUpdatedAt = row.cardUpdatedAt,
                conclusion = conclusion,
                cardJson = cardJson
            )
        }
    }

    private fun parseContentJson(json: String): Pair<String, String?> {
        return try {
            val node = objectMapper.readTree(json)
            val conclusion = node.get("conclusion")?.asText()
            json to conclusion
        } catch (e: Exception) {
            log.warn("Failed to parse card JSON: {}", e.message)
            json to null
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
    val articleCount: Int,
    val publisherCount: Int,
    val cardStatus: String,
    val cardUpdatedAt: LocalDateTime,
    val conclusion: String?,
    val cardJson: String?
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
    val cardJson: String?,
    val articles: List<ArticleSummary> = emptyList()
)

/**
 * 기사 요약 (출처/증거 표시용)
 */
data class ArticleSummary(
    val title: String,
    val link: String,
    val publisher: String,
    val publishedAt: LocalDateTime
)
