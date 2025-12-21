package com.yourapp.news.card

import com.yourapp.news.issue.Issues
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CardQuery(private val database: Database) {

    /**
     * 카드 리스트 조회 (issues와 join)
     */
    fun listCards(filter: CardListFilter): List<CardListRow> = transaction(database) {
        val joinedTable = Cards.join(Issues, JoinType.INNER, Cards.issueId, Issues.id)

        // 조건 빌드
        var condition = Cards.status eq (filter.status?.name ?: CardStatus.ACTIVE.name)

        filter.group?.let { group ->
            condition = condition and (Issues.group eq group)
        }

        filter.from?.let { from ->
            condition = condition and (Issues.lastPublishedAt greaterEq from)
        }

        filter.to?.let { to ->
            condition = condition and (Issues.lastPublishedAt lessEq to)
        }

        joinedTable.selectAll()
            .where { condition }
            .orderBy(Issues.lastPublishedAt, SortOrder.DESC)
            .limit(filter.limit)
            .offset(filter.offset.toLong())
            .map { row -> row.toCardListRow() }
    }

    /**
     * 카드 상세 조회
     */
    fun getCard(issueId: Long): CardDetailRow? = transaction(database) {
        val joinedTable = Cards.join(Issues, JoinType.INNER, Cards.issueId, Issues.id)

        joinedTable.selectAll()
            .where { Cards.issueId eq issueId }
            .singleOrNull()
            ?.toCardDetailRow()
    }

    /**
     * 오늘(최근 24시간) 카드 조회
     */
    fun getTodayCards(limit: Int = 7): List<CardListRow> = transaction(database) {
        val since = LocalDateTime.now().minusHours(24)
        val joinedTable = Cards.join(Issues, JoinType.INNER, Cards.issueId, Issues.id)

        joinedTable.selectAll()
            .where {
                (Cards.status eq CardStatus.ACTIVE.name) and
                (Issues.lastPublishedAt greaterEq since)
            }
            .orderBy(Issues.lastPublishedAt, SortOrder.DESC)
            .limit(limit)
            .map { row -> row.toCardListRow() }
    }

    private fun ResultRow.toCardListRow(): CardListRow = CardListRow(
        issueId = this[Issues.id],
        issueFingerprint = this[Issues.fingerprint],
        issueGroup = this[Issues.group],
        issueTitle = this[Issues.title],
        issueLastPublishedAt = this[Issues.lastPublishedAt],
        cardStatus = this[Cards.status],
        cardUpdatedAt = this[Cards.updatedAt],
        cardContentJson = this[Cards.contentJson]
    )

    private fun ResultRow.toCardDetailRow(): CardDetailRow = CardDetailRow(
        issueId = this[Issues.id],
        issueFingerprint = this[Issues.fingerprint],
        issueGroup = this[Issues.group],
        issueTitle = this[Issues.title],
        issueFirstPublishedAt = this[Issues.firstPublishedAt],
        issueLastPublishedAt = this[Issues.lastPublishedAt],
        issueArticleCount = this[Issues.articleCount],
        issuePublisherCount = this[Issues.publisherCount],
        cardStatus = this[Cards.status],
        cardModel = this[Cards.model],
        cardCreatedAt = this[Cards.createdAt],
        cardUpdatedAt = this[Cards.updatedAt],
        cardContentJson = this[Cards.contentJson]
    )
}

/**
 * 카드 리스트 필터
 */
data class CardListFilter(
    val group: String? = null,
    val status: CardStatus? = CardStatus.ACTIVE,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val limit: Int = 20,
    val offset: Int = 0
)

/**
 * 카드 리스트 Row (DB 조회 결과)
 */
data class CardListRow(
    val issueId: Long,
    val issueFingerprint: String,
    val issueGroup: String,
    val issueTitle: String,
    val issueLastPublishedAt: LocalDateTime,
    val cardStatus: String,
    val cardUpdatedAt: LocalDateTime,
    val cardContentJson: String
)

/**
 * 카드 상세 Row (DB 조회 결과)
 */
data class CardDetailRow(
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
    val cardContentJson: String
)
