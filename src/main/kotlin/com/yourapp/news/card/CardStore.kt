package com.yourapp.news.card

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CardStore(private val database: Database) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 카드 upsert (issueId 기준)
     * @return 생성된/업데이트된 카드 ID
     */
    fun upsert(card: Card): Long = transaction(database) {
        val existing = findByIssueId(card.issueId)
        
        if (existing != null) {
            Cards.update({ Cards.issueId eq card.issueId }) {
                it[contentJson] = card.contentJson
                it[model] = card.model
                it[status] = card.status.name
                it[updatedAt] = LocalDateTime.now()
            }
            log.debug("Updated card for issueId={}", card.issueId)
            existing.id!!
        } else {
            val newId = Cards.insert {
                it[issueId] = card.issueId
                it[issueFingerprint] = card.issueFingerprint
                it[model] = card.model
                it[contentJson] = card.contentJson
                it[status] = card.status.name
            } get Cards.id
            log.debug("Created card {} for issueId={}", newId, card.issueId)
            newId
        }
    }

    /**
     * issueId로 카드 조회
     */
    fun findByIssueId(issueId: Long): Card? = transaction(database) {
        Cards.selectAll()
            .where { Cards.issueId eq issueId }
            .singleOrNull()
            ?.toCard()
    }

    /**
     * issueFingerprint로 카드 조회
     */
    fun findByFingerprint(fingerprint: String): Card? = transaction(database) {
        Cards.selectAll()
            .where { Cards.issueFingerprint eq fingerprint }
            .singleOrNull()
            ?.toCard()
    }

    /**
     * ACTIVE 카드 존재 여부 확인 (FAILED는 재시도 대상)
     */
    fun existsActiveByIssueId(issueId: Long): Boolean = transaction(database) {
        Cards.selectAll()
            .where { (Cards.issueId eq issueId) and (Cards.status eq CardStatus.ACTIVE.name) }
            .limit(1)
            .any()
    }

    private fun ResultRow.toCard(): Card = Card(
        id = this[Cards.id],
        issueId = this[Cards.issueId],
        issueFingerprint = this[Cards.issueFingerprint],
        model = this[Cards.model],
        contentJson = this[Cards.contentJson],
        status = CardStatus.valueOf(this[Cards.status]),
        createdAt = this[Cards.createdAt],
        updatedAt = this[Cards.updatedAt]
    )
}
