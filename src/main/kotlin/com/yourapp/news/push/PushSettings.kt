package com.yourapp.news.push

import java.time.LocalDateTime

/**
 * 푸시 알림 설정 도메인 객체
 */
data class PushSettings(
    val id: Long? = null,
    val userId: Long,
    val enabled: Boolean = true,
    val breakingNews: Boolean = true,       // 속보 알림
    val bookmarkUpdates: Boolean = true,    // 북마크 이슈 업데이트
    val dailyBriefing: Boolean = false,     // 일일 브리핑 (매일 아침)
    val trendingAlerts: Boolean = true,     // 급상승 이슈 알림
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
