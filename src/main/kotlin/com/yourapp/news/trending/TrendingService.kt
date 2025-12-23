package com.yourapp.news.trending

import com.yourapp.news.card.CardStore
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class TrendingService(
    private val trendingQuery: TrendingQuery,
    private val cardStore: CardStore
) {
    companion object {
        // 점수 가중치
        const val WEIGHT_VELOCITY = 5.0        // 시간당 기사 수 (가장 중요)
        const val WEIGHT_PUBLISHER = 2.0       // 언론사 다양성
        const val WEIGHT_RECENCY = 3.0         // 최신성

        // 최신성 계산 윈도우 (분)
        const val RECENCY_WINDOW_MINUTES = 60.0  // 1시간 기준
    }

    /**
     * 급상승 이슈 조회
     * @param hours 최근 N시간 내 기사 기준
     * @param limit 반환할 이슈 개수
     */
    fun getTrendingIssues(hours: Long = 3, limit: Int = 10): List<TrendingIssue> {
        val now = LocalDateTime.now()
        val since = now.minusHours(hours)

        // 1. 최근 기사가 있는 이슈들의 통계 조회
        val issueStats = trendingQuery.getIssueStatsWithRecentArticles(since, limit * 3)

        if (issueStats.isEmpty()) {
            return emptyList()
        }

        // 2. 각 이슈별 점수 계산
        val scoredIssues = issueStats.map { stat ->
            val score = calculateScore(
                recentCount = stat.recentArticleCount,
                publisherCount = stat.recentPublisherCount,
                lastPublishedAt = stat.lastPublishedAt,
                now = now,
                windowHours = hours
            )

            // 카드 결론 조회 (있으면)
            val conclusion = cardStore.findConclusionByIssueId(stat.issueId)

            TrendingIssue(
                issueId = stat.issueId,
                issueTitle = stat.issueTitle,
                issueGroup = stat.issueGroup,
                articleCount = stat.recentArticleCount,
                publisherCount = stat.recentPublisherCount,
                lastPublishedAt = stat.lastPublishedAt,
                score = score,
                conclusion = conclusion
            )
        }

        // 3. 점수순 정렬 후 limit 적용
        return scoredIssues
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * 급상승 점수 계산 (개선된 공식)
     *
     * score = velocity × 5.0 + publisherBonus × 2.0 + recencyBoost × 3.0
     *
     * - velocity: 시간당 기사 수 (정규화됨)
     * - publisherBonus: 언론사 다양성 보너스 (2개 이상이면 가산)
     * - recencyBoost: 최신성 (1시간 내면 높음)
     */
    private fun calculateScore(
        recentCount: Int,
        publisherCount: Int,
        lastPublishedAt: LocalDateTime,
        now: LocalDateTime,
        windowHours: Long
    ): Double {
        // 1. 시간당 기사 수 (velocity)
        val velocity = recentCount.toDouble() / windowHours.coerceAtLeast(1)

        // 2. 언론사 다양성 보너스 (2개 이상부터 가산)
        val publisherBonus = (publisherCount - 1).coerceAtLeast(0).toDouble()

        // 3. 최신성 (마지막 기사가 얼마나 최근인지)
        val minutesSinceLast = ChronoUnit.MINUTES.between(lastPublishedAt, now).toDouble()
        val recencyBoost = (1.0 - (minutesSinceLast / RECENCY_WINDOW_MINUTES)).coerceIn(0.0, 1.0)

        return (velocity * WEIGHT_VELOCITY) +
                (publisherBonus * WEIGHT_PUBLISHER) +
                (recencyBoost * WEIGHT_RECENCY)
    }
}

/**
 * 급상승 이슈 응답
 */
data class TrendingIssue(
    val issueId: Long,
    val issueTitle: String,
    val issueGroup: String,
    val articleCount: Int,
    val publisherCount: Int,
    val lastPublishedAt: LocalDateTime,
    val score: Double,
    val conclusion: String?  // 카드 결론 (있으면)
)
