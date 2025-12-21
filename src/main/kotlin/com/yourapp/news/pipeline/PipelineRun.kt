package com.yourapp.news.pipeline

import java.time.LocalDateTime

/**
 * 파이프라인 실행 기록 도메인 객체
 */
data class PipelineRun(
    val id: Long? = null,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime? = null,
    val durationMs: Long? = null,
    val rssSavedCount: Int = 0,
    val issuesCreatedCount: Int = 0,
    val issuesUpdatedCount: Int = 0,
    val cardsCreatedCount: Int = 0,
    val cardsFailedCount: Int = 0,
    val status: PipelineStatus = PipelineStatus.RUNNING,
    val errorStage: PipelineStage? = null,
    val errorMessage: String? = null
)

/**
 * 파이프라인 실행 결과
 */
data class PipelineResult(
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val durationMs: Long,
    val rssSavedCount: Int,
    val issuesCreatedCount: Int,
    val issuesUpdatedCount: Int,
    val cardsCreatedCount: Int,
    val cardsFailedCount: Int,
    val status: PipelineStatus,
    val errorStage: PipelineStage? = null,
    val errorMessage: String? = null
) {
    fun toRun(): PipelineRun = PipelineRun(
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
}
