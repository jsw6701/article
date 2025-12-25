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
        const val WEIGHT_ARTICLE_COUNT = 1.5   // 기사 수
        const val WEIGHT_PUBLISHER = 2.0       // 언론사 다양성
        const val WEIGHT_RECENCY = 5.0         // 최신성 (가장 중요)

        // 최신성 계산 기준 (시간)
        const val RECENCY_DECAY_HOURS = 6.0    // 6시간 기준 decay

        // 조회 범위 (고정)
        const val FETCH_WINDOW_HOURS = 48L     // 최근 48시간 데이터 조회
    }

    /**
     * 급상승 이슈 조회
     * - 조회 범위: 최근 48시간 (고정)
     * - 점수 계산: 현재 시각 기준 최신성
     * - 최소 보장: limit 개수만큼 (데이터 있는 한)
     *
     * @param limit 반환할 이슈 개수 (기본 10개)
     */
    fun getTrendingIssues(limit: Int = 10): List<TrendingIssue> {
        val now = LocalDateTime.now()
        val since = now.minusHours(FETCH_WINDOW_HOURS)

        // 1. 최근 48시간 내 기사가 있는 이슈들 조회 (넉넉하게)
        val issueStats = trendingQuery.getIssueStatsWithRecentArticles(since, limit * 5)

        if (issueStats.isEmpty()) {
            return emptyList()
        }

        // 2. 각 이슈별 점수 계산 (현재 시각 기준)
        val scoredIssues = issueStats.map { stat ->
            val score = calculateScore(
                articleCount = stat.recentArticleCount,
                publisherCount = stat.recentPublisherCount,
                lastPublishedAt = stat.lastPublishedAt,
                now = now
            )

            // 카드 결론 조회 (있으면)
            val conclusion = cardStore.findConclusionByIssueId(stat.issueId)

            TrendingIssue(
                issueId = stat.issueId,
                issueTitle = stat.issueTitle,
                issueGroup = stat.issueGroup,
                headline = stat.headline,
                signalSummary = stat.signalSummary,
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
     * 급상승 점수 계산
     *
     * score = articleCount × 1.5 + publisherBonus × 2.0 + recencyScore × 5.0
     *
     * - articleCount: 기사 수 (많을수록)
     * - publisherBonus: 언론사 다양성 (2개 이상부터 가산)
     * - recencyScore: 최신성 (지수 감소, 최근일수록 높음)
     *
     * 최신성 계산: e^(-hoursSinceLast / 6)
     * - 방금 나온 기사: 1.0
     * - 6시간 전: 0.37
     * - 12시간 전: 0.14
     * - 24시간 전: 0.02
     */
    private fun calculateScore(
        articleCount: Int,
        publisherCount: Int,
        lastPublishedAt: LocalDateTime,
        now: LocalDateTime
    ): Double {
        // 1. 기사 수 점수
        val articleScore = articleCount.toDouble()

        // 2. 언론사 다양성 보너스 (2개 이상부터 가산)
        val publisherBonus = (publisherCount - 1).coerceAtLeast(0).toDouble()

        // 3. 최신성 점수 (지수 감소)
        val hoursSinceLast = ChronoUnit.MINUTES.between(lastPublishedAt, now).toDouble() / 60.0
        val recencyScore = kotlin.math.exp(-hoursSinceLast / RECENCY_DECAY_HOURS)

        return (articleScore * WEIGHT_ARTICLE_COUNT) +
                (publisherBonus * WEIGHT_PUBLISHER) +
                (recencyScore * WEIGHT_RECENCY)
    }
}

/**
 * 급상승 이슈 응답
 */
data class TrendingIssue(
    val issueId: Long,
    val issueTitle: String,
    val issueGroup: String,
    val headline: String?, // 사용자에게 노출되는 제목
    val signalSummary: String?, // 리스트 카드용 한 줄 요약
    val articleCount: Int,
    val publisherCount: Int,
    val lastPublishedAt: LocalDateTime,
    val score: Double,
    val conclusion: String?  // 카드 결론 (있으면)
)
