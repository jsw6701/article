package com.yourapp.news.card

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class CardViewStore(private val database: Database) {

    /**
     * 조회 기록 추가
     */
    fun addView(cardId: Long): Long = transaction(database) {
        CardViews.insert {
            it[CardViews.cardId] = cardId
        } get CardViews.id
    }

    /**
     * 특정 카드의 조회수 조회
     */
    fun getViewCount(cardId: Long): Long = transaction(database) {
        CardViews.selectAll()
            .where { CardViews.cardId eq cardId }
            .count()
    }

    /**
     * 여러 카드의 조회수 일괄 조회
     */
    fun getViewCounts(cardIds: List<Long>): Map<Long, Long> = transaction(database) {
        if (cardIds.isEmpty()) return@transaction emptyMap()

        CardViews.select(CardViews.cardId, CardViews.cardId.count())
            .where { CardViews.cardId inList cardIds }
            .groupBy(CardViews.cardId)
            .associate { it[CardViews.cardId] to it[CardViews.cardId.count()] }
    }

    /**
     * 조회수 기준 상위 카드 ID 목록
     */
    fun getTopViewedCardIds(limit: Int): List<Pair<Long, Long>> = transaction(database) {
        CardViews.select(CardViews.cardId, CardViews.cardId.count())
            .groupBy(CardViews.cardId)
            .orderBy(CardViews.cardId.count() to SortOrder.DESC)
            .limit(limit)
            .map { it[CardViews.cardId] to it[CardViews.cardId.count()] }
    }
}
