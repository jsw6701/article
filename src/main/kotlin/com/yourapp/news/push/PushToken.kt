package com.yourapp.news.push

import java.time.LocalDateTime

/**
 * 푸시 토큰 도메인 객체
 */
data class PushToken(
    val id: Long? = null,
    val userId: Long,
    val token: String,
    val platform: Platform,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 플랫폼 종류
 */
enum class Platform {
    ANDROID, IOS, WEB
}
