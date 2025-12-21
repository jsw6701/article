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
    val fingerprint: String
)

enum class IssueStatus {
    OPEN,
    CLOSED
}
