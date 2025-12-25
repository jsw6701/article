package com.yourapp.news.issue

import java.time.LocalDateTime

/**
 * 경제 이슈 도메인 객체
 */
data class Issue(
    val id: Long? = null,
    val group: CategoryGroup,
    val title: String,
    val keywords: List<String>,
    val firstPublishedAt: LocalDateTime,
    val lastPublishedAt: LocalDateTime,
    val articleCount: Int,
    val publisherCount: Int,
    val status: IssueStatus = IssueStatus.OPEN,
    val fingerprint: String,
    val headline: String? = null, // 사용자에게 노출되는 제목 (예: "서울 아파트 가격, 다시 상승 압력")
    val signalSummary: String? = null // 리스트 카드용 한 줄 요약 (예: "왜 지금 중요한지 한 줄")
)

enum class IssueStatus {
    OPEN,
    CLOSED
}
