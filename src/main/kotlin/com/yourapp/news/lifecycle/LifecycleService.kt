package com.yourapp.news.lifecycle

import com.yourapp.news.issue.IssueArticles
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class LifecycleService(
    private val lifecycleStore: LifecycleStore,
    private val database: Database
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // 최소 기사 수 임계값
        const val MIN_ARTICLE_THRESHOLD = 3

        // 단계 판정 임계값 (정점 대비 변화율)
        const val THRESHOLD_PEAK = -5        // -5% 이상이면 정점 근처
        const val THRESHOLD_DECLINING = -30  // -30% 이상이면 소강
        const val THRESHOLD_DORMANT = -70    // -70% 미만이면 종료

        // 신규 이슈 판정 기준 (히스토리 레코드 수)
        const val MIN_HISTORY_FOR_ANALYSIS = 3
    }

    /**
     * 이슈의 생애주기 조회
     */
    fun getLifecycle(issueId: Long): IssueLifecycle? {
        return lifecycleStore.findByIssueId(issueId)
    }

    /**
     * 여러 이슈의 생애주기 일괄 조회
     */
    fun getLifecycles(issueIds: List<Long>): Map<Long, IssueLifecycle> {
        return lifecycleStore.findByIssueIds(issueIds)
    }

    /**
     * 이슈의 생애주기 계산 및 저장
     */
    fun updateLifecycle(issueId: Long): IssueLifecycle {
        val now = LocalDateTime.now()

        // 1. 현재 24시간 기사 수 조회
        val currentArticleCount = getCurrentArticleCount(issueId)

        // 2. 히스토리 조회
        val history = lifecycleStore.getArticleHistory(issueId, days = 7)

        // 3. 기존 생애주기 조회
        val prevLifecycle = lifecycleStore.findByIssueId(issueId)

        // 4. 새 생애주기 계산
        val lifecycle = calculateLifecycle(
            issueId = issueId,
            currentArticleCount = currentArticleCount,
            history = history,
            prevLifecycle = prevLifecycle,
            now = now
        )

        // 5. 저장
        lifecycleStore.upsert(lifecycle)

        // 6. 히스토리 기록
        lifecycleStore.saveArticleHistory(issueId, currentArticleCount, now)

        log.debug("Updated lifecycle for issue {}: {} ({}%)",
            issueId, lifecycle.stage, lifecycle.changePercent)

        return lifecycle
    }

    /**
     * 활성 이슈들의 생애주기 일괄 업데이트
     */
    fun updateAllActiveLifecycles(): Int {
        val now = LocalDateTime.now()
        val since = now.minusDays(7)

        // 최근 7일간 기사가 있는 이슈들 조회
        val activeIssueIds = getActiveIssueIds(since)

        log.info("Updating lifecycles for {} active issues", activeIssueIds.size)

        var updated = 0
        for (issueId in activeIssueIds) {
            try {
                updateLifecycle(issueId)
                updated++
            } catch (e: Exception) {
                log.error("Failed to update lifecycle for issue {}: {}", issueId, e.message)
            }
        }

        // 오래된 히스토리 정리
        val deleted = lifecycleStore.deleteOldHistory(days = 7)
        if (deleted > 0) {
            log.info("Deleted {} old history records", deleted)
        }

        return updated
    }

    /**
     * 현재 24시간 기사 수 조회
     */
    private fun getCurrentArticleCount(issueId: Long): Int = transaction(database) {
        val since = LocalDateTime.now().minusHours(24)

        IssueArticles.selectAll()
            .where {
                (IssueArticles.issueId eq issueId) and
                (IssueArticles.publishedAt greaterEq since)
            }
            .count()
            .toInt()
    }

    /**
     * 활성 이슈 ID 목록 조회 (최근 N일 내 기사가 있는 이슈)
     */
    private fun getActiveIssueIds(since: LocalDateTime): List<Long> = transaction(database) {
        IssueArticles.selectAll()
            .where { IssueArticles.publishedAt greaterEq since }
            .groupBy(IssueArticles.issueId)
            .map { it[IssueArticles.issueId] }
            .distinct()
    }

    /**
     * 생애주기 계산
     */
    private fun calculateLifecycle(
        issueId: Long,
        currentArticleCount: Int,
        history: List<ArticleHistoryRecord>,
        prevLifecycle: IssueLifecycle?,
        now: LocalDateTime
    ): IssueLifecycle {

        // 히스토리가 부족하면 신규 이슈
        if (history.size < MIN_HISTORY_FOR_ANALYSIS) {
            return IssueLifecycle(
                issueId = issueId,
                stage = IssueLifecycleStage.EMERGING,
                changePercent = 0,
                peakArticleCount = currentArticleCount,
                currentArticleCount = currentArticleCount,
                peakDate = null,
                stageChangedAt = prevLifecycle?.stageChangedAt ?: now
            )
        }

        // 정점 찾기
        val peakRecord = history.maxByOrNull { it.articleCount }!!
        val peakArticleCount = maxOf(peakRecord.articleCount, currentArticleCount)
        val peakDate = if (currentArticleCount >= peakRecord.articleCount) now else peakRecord.recordedAt

        // 변화율 계산
        val changePercent = if (peakArticleCount > 0) {
            ((currentArticleCount - peakArticleCount).toDouble() / peakArticleCount * 100).toInt()
        } else {
            0
        }

        // 최근 추세 계산
        val recentTrend = calculateTrend(history.takeLast(3), currentArticleCount)

        // 단계 판정
        val stage = determineStage(
            currentArticleCount = currentArticleCount,
            peakArticleCount = peakArticleCount,
            changePercent = changePercent,
            recentTrend = recentTrend,
            prevStage = prevLifecycle?.stage
        )

        // 단계 변경 시점 결정
        val stageChangedAt = if (prevLifecycle?.stage == stage) {
            prevLifecycle.stageChangedAt
        } else {
            now
        }

        return IssueLifecycle(
            issueId = issueId,
            stage = stage,
            changePercent = changePercent,
            peakArticleCount = peakArticleCount,
            currentArticleCount = currentArticleCount,
            peakDate = if (stage == IssueLifecycleStage.EMERGING) null else peakDate,
            stageChangedAt = stageChangedAt
        )
    }

    /**
     * 단계 판정
     */
    private fun determineStage(
        currentArticleCount: Int,
        peakArticleCount: Int,
        changePercent: Int,
        recentTrend: Double,
        prevStage: IssueLifecycleStage?
    ): IssueLifecycleStage {

        // 기사 수가 너무 적으면 종료
        if (currentArticleCount < MIN_ARTICLE_THRESHOLD) {
            return IssueLifecycleStage.DORMANT
        }

        // 정점 기사 수가 너무 적으면 아직 신규
        if (peakArticleCount < MIN_ARTICLE_THRESHOLD) {
            return IssueLifecycleStage.EMERGING
        }

        return when {
            // 정점 근처 (-5% 이상)
            changePercent >= THRESHOLD_PEAK -> {
                if (recentTrend > 0.1) {
                    // 아직 상승 중
                    if (prevStage == IssueLifecycleStage.PEAK) IssueLifecycleStage.PEAK
                    else IssueLifecycleStage.SPREADING
                } else if (recentTrend < -0.1) {
                    // 하락 시작
                    IssueLifecycleStage.DECLINING
                } else {
                    // 정점 유지
                    IssueLifecycleStage.PEAK
                }
            }

            // 소폭 하락 (-5% ~ -30%)
            changePercent >= THRESHOLD_DECLINING -> {
                if (recentTrend > 0.15) {
                    // 다시 상승 중 (반등)
                    IssueLifecycleStage.SPREADING
                } else {
                    IssueLifecycleStage.DECLINING
                }
            }

            // 중간 하락 (-30% ~ -70%)
            changePercent >= THRESHOLD_DORMANT -> {
                IssueLifecycleStage.DECLINING
            }

            // 대폭 하락 (-70% 미만)
            else -> {
                IssueLifecycleStage.DORMANT
            }
        }
    }

    /**
     * 최근 추세 계산 (정규화된 기울기)
     * 양수: 상승, 음수: 하락
     */
    private fun calculateTrend(recentHistory: List<ArticleHistoryRecord>, currentCount: Int): Double {
        if (recentHistory.isEmpty()) return 0.0

        val counts = recentHistory.map { it.articleCount } + currentCount

        if (counts.size < 2) return 0.0

        // 평균 변화량 계산
        val avgChange = (1 until counts.size).sumOf { i ->
            counts[i] - counts[i - 1]
        }.toDouble() / (counts.size - 1)

        // 평균 기사 수로 정규화
        val avgCount = counts.average()

        return if (avgCount > 0) avgChange / avgCount else 0.0
    }
}
