package com.yourapp.news.card

import com.yourapp.news.issue.Issues
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 결론 카드 테이블
 */
object Cards : Table("cards") {
    val id = long("id").autoIncrement()
    val issueId = long("issue_id").references(Issues.id).uniqueIndex()
    val issueFingerprint = varchar("issue_fingerprint", 128).index()
    val model = varchar("model", 64)
    val contentJson = text("content_json")
    val status = varchar("status", 32).default(CardStatus.ACTIVE.name)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}

enum class CardStatus {
    ACTIVE,
    FAILED,
    STALE
}
