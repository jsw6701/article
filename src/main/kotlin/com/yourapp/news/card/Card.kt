package com.yourapp.news.card

import java.time.LocalDateTime

/**
 * 결론 카드 도메인 객체
 */
data class Card(
    val id: Long? = null,
    val issueId: Long,
    val issueFingerprint: String,
    val model: String,
    val contentJson: String,
    val status: CardStatus = CardStatus.ACTIVE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 카드 생성 로그 도메인 객체
 */
data class CardGenerationLog(
    val id: Long? = null,
    val issueId: Long,
    val issueFingerprint: String,
    val attempt: Int,
    val success: Boolean,
    val httpStatus: Int? = null,
    val errorMessage: String? = null,
    val latencyMs: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
