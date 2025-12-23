package com.yourapp.news.trending

import com.yourapp.news.article.Articles
import com.yourapp.news.issue.IssueArticles
import com.yourapp.news.issue.Issues
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TrendingQuery(private val database: Database) {

    /**
     * 최근 기사가 있는 이슈들의 통계 조회
     * - 최근 N시간 내 기사 수
     * - 최근 N시간 내 언론사 수
     * - 마지막 기사 발행 시간
     */
    fun getIssueStatsWithRecentArticles(
        since: LocalDateTime,
        limit: Int = 30
    ): List<IssueStats> = transaction(database) {

        // Step 1: 최근 기사가 있는 issueId 목록 조회
        val recentIssueArticles = IssueArticles.selectAll()
            .where { IssueArticles.publishedAt greaterEq since }
            .map {
                IssueArticleRow(
                    issueId = it[IssueArticles.issueId],
                    articleLink = it[IssueArticles.articleLink],
                    publishedAt = it[IssueArticles.publishedAt]
                )
            }

        if (recentIssueArticles.isEmpty()) {
            return@transaction emptyList()
        }

        // Step 2: issueId별로 그룹핑
        val issueIds = recentIssueArticles.map { it.issueId }.distinct()

        // Step 3: 해당 기사들의 publisher 조회
        val articleLinks = recentIssueArticles.map { it.articleLink }.distinct()
        val articlePublishers = Articles.selectAll()
            .where { Articles.link inList articleLinks }
            .associate { it[Articles.link] to it[Articles.publisher] }

        // Step 4: 이슈 정보 조회
        val issueInfoMap = Issues.selectAll()
            .where { Issues.id inList issueIds }
            .associate {
                it[Issues.id] to IssueInfo(
                    issueId = it[Issues.id],
                    issueTitle = it[Issues.title],
                    issueGroup = it[Issues.group]
                )
            }

        // Step 5: 이슈별 통계 계산
        val statsMap = mutableMapOf<Long, MutableIssueStats>()

        for (row in recentIssueArticles) {
            val stats = statsMap.getOrPut(row.issueId) {
                MutableIssueStats(
                    issueId = row.issueId,
                    articleLinks = mutableSetOf(),
                    publishers = mutableSetOf(),
                    lastPublishedAt = row.publishedAt
                )
            }

            stats.articleLinks.add(row.articleLink)
            articlePublishers[row.articleLink]?.let { stats.publishers.add(it) }

            if (row.publishedAt.isAfter(stats.lastPublishedAt)) {
                stats.lastPublishedAt = row.publishedAt
            }
        }

        // Step 6: 결과 변환 및 정렬
        statsMap.values
            .mapNotNull { stats ->
                val info = issueInfoMap[stats.issueId] ?: return@mapNotNull null
                IssueStats(
                    issueId = stats.issueId,
                    issueTitle = info.issueTitle,
                    issueGroup = info.issueGroup,
                    recentArticleCount = stats.articleLinks.size,
                    recentPublisherCount = stats.publishers.size,
                    lastPublishedAt = stats.lastPublishedAt
                )
            }
            .sortedByDescending { it.lastPublishedAt }
            .take(limit)
    }

    private data class IssueArticleRow(
        val issueId: Long,
        val articleLink: String,
        val publishedAt: LocalDateTime
    )

    private data class IssueInfo(
        val issueId: Long,
        val issueTitle: String,
        val issueGroup: String
    )

    private data class MutableIssueStats(
        val issueId: Long,
        val articleLinks: MutableSet<String>,
        val publishers: MutableSet<String>,
        var lastPublishedAt: LocalDateTime
    )
}

/**
 * 이슈 통계 (급상승 계산용)
 */
data class IssueStats(
    val issueId: Long,
    val issueTitle: String,
    val issueGroup: String,
    val recentArticleCount: Int,
    val recentPublisherCount: Int,
    val lastPublishedAt: LocalDateTime
)
