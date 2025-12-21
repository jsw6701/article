package com.yourapp.news.card

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 카드 생성 로그 테이블
 */
object CardGenerationLogs : Table("card_generation_logs") {
    val id = long("id").autoIncrement()
    val issueId = long("issue_id")
    val issueFingerprint = varchar("issue_fingerprint", 128)
    val attempt = integer("attempt")
    val success = bool("success")
    val httpStatus = integer("http_status").nullable()
    val errorMessage = text("error_message").nullable()
    val latencyMs = long("latency_ms").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
