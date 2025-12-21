package com.yourapp.news.pipeline

import com.yourapp.news.card.CardGenerationService
import com.yourapp.news.issue.IssueClusterService
import com.yourapp.news.rss.RssCollectorService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 파이프라인 실행 오케스트레이터
 *
 * 실행 순서: RSS 수집 → 이슈 클러스터링 → 카드 생성
 *
 * 진행 정책:
 * - RSS 실패 → 클러스터링/카드 생성 계속 진행 (이전 데이터 사용)
 * - 클러스터링 실패 → 카드 생성 중단 (대상 이슈 불명확)
 * - 카드 생성 실패 → 다음 스케줄에서 재시도
 */
@Service
class PipelineOrchestrator(
    private val rssCollectorService: RssCollectorService,
    private val issueClusterService: IssueClusterService,
    private val cardGenerationService: CardGenerationService,
    private val pipelineRunStore: PipelineRunStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 파이프라인 1회 실행
     */
    fun runOnce(): PipelineResult {
        val startedAt = LocalDateTime.now()
        log.info("========== Pipeline started at {} ==========", startedAt)

        var rssSavedCount = 0
        var issuesCreatedCount = 0
        var issuesUpdatedCount = 0
        var cardsCreatedCount = 0
        var cardsFailedCount = 0
        var status = PipelineStatus.SUCCESS
        var errorStage: PipelineStage? = null
        var errorMessage: String? = null

        // 1. RSS 수집
        val rssResult = runStage(PipelineStage.RSS_COLLECT) {
            rssCollectorService.collectAll()
        }

        if (rssResult.isSuccess) {
            rssSavedCount = rssResult.getOrDefault(0)
            log.info("[RSS] Collected {} articles", rssSavedCount)
        } else {
            log.error("[RSS] Collection failed: {}", rssResult.exceptionOrNull()?.message)
            // RSS 실패해도 계속 진행 (이전 데이터로 처리)
            status = PipelineStatus.PARTIAL
        }

        // 2. 이슈 클러스터링
        val clusterResult = runStage(PipelineStage.ISSUE_CLUSTER) {
            issueClusterService.clusterRecentArticles()
        }

        if (clusterResult.isSuccess) {
            val result = clusterResult.getOrThrow()
            issuesCreatedCount = result.created
            issuesUpdatedCount = result.updated
            log.info("[Cluster] Created {}, Updated {}, Skipped {}",
                result.created, result.updated, result.skipped)
        } else {
            val ex = clusterResult.exceptionOrNull()
            log.error("[Cluster] Clustering failed: {}", ex?.message)
            errorStage = PipelineStage.ISSUE_CLUSTER
            errorMessage = ex?.message

            // 클러스터링 실패 시 카드 생성 중단
            val finishedAt = LocalDateTime.now()
            val result = PipelineResult(
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis(),
                rssSavedCount = rssSavedCount,
                issuesCreatedCount = issuesCreatedCount,
                issuesUpdatedCount = issuesUpdatedCount,
                cardsCreatedCount = 0,
                cardsFailedCount = 0,
                status = PipelineStatus.FAILED,
                errorStage = errorStage,
                errorMessage = errorMessage
            )

            saveAndLog(result)
            return result
        }

        // 3. 카드 생성
        val cardResult = runStage(PipelineStage.CARD_GENERATE) {
            cardGenerationService.generateCardsForTargets()
        }

        if (cardResult.isSuccess) {
            val result = cardResult.getOrThrow()
            cardsCreatedCount = result.successCount
            cardsFailedCount = result.failCount
            log.info("[Card] Generated {}, Skipped {}, Failed {}",
                result.successCount, result.skipCount, result.failCount)

            // 카드 생성에 실패가 있으면 PARTIAL
            if (result.failCount > 0 && status == PipelineStatus.SUCCESS) {
                status = PipelineStatus.PARTIAL
            }
        } else {
            val ex = cardResult.exceptionOrNull()
            log.error("[Card] Generation failed: {}", ex?.message)
            status = PipelineStatus.FAILED
            errorStage = PipelineStage.CARD_GENERATE
            errorMessage = ex?.message
        }

        val finishedAt = LocalDateTime.now()
        val durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis()

        val result = PipelineResult(
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
            rssSavedCount = rssSavedCount,
            issuesCreatedCount = issuesCreatedCount,
            issuesUpdatedCount = issuesUpdatedCount,
            cardsCreatedCount = cardsCreatedCount,
            cardsFailedCount = cardsFailedCount,
            status = status,
            errorStage = errorStage,
            errorMessage = errorMessage
        )

        saveAndLog(result)
        return result
    }

    private fun <T> runStage(stage: PipelineStage, block: () -> T): Result<T> {
        val stageStart = System.currentTimeMillis()
        val result = runCatching { block() }
        val stageTime = System.currentTimeMillis() - stageStart
        log.debug("[{}] completed in {}ms", stage.name, stageTime)
        return result
    }

    private fun saveAndLog(result: PipelineResult) {
        // DB에 저장
        runCatching {
            pipelineRunStore.insert(result.toRun())
        }.onFailure { ex ->
            log.error("Failed to save pipeline run: {}", ex.message)
        }

        // 요약 로그
        log.info(
            """
            ========== Pipeline finished ==========
            Status: {}
            Duration: {}ms
            RSS Saved: {}
            Issues Created: {}, Updated: {}
            Cards Created: {}, Failed: {}
            {}
            ========================================
            """.trimIndent(),
            result.status,
            result.durationMs,
            result.rssSavedCount,
            result.issuesCreatedCount,
            result.issuesUpdatedCount,
            result.cardsCreatedCount,
            result.cardsFailedCount,
            if (result.errorStage != null) "Error at: ${result.errorStage}" else ""
        )
    }
}
