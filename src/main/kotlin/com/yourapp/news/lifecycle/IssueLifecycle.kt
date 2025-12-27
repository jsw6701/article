package com.yourapp.news.lifecycle

import java.time.LocalDateTime

/**
 * 이슈 생애주기 도메인 객체
 */
data class IssueLifecycle(
    val issueId: Long,
    val stage: IssueLifecycleStage,
    val changePercent: Int,           // 정점 대비 변화율 (%)
    val peakArticleCount: Int,        // 정점 시점 24시간 기사 수
    val currentArticleCount: Int,     // 현재 24시간 기사 수
    val peakDate: LocalDateTime?,     // 정점 도달 시점 (EMERGING일 때는 null)
    val stageChangedAt: LocalDateTime // 현재 단계 진입 시점
)

/**
 * API 응답용 생애주기 DTO
 */
data class IssueLifecycleDto(
    val stage: String,
    val changePercent: Int,
    val peakArticleCount: Int,
    val currentArticleCount: Int,
    val peakDate: LocalDateTime?,
    val stageChangedAt: LocalDateTime
) {
    companion object {
        fun from(lifecycle: IssueLifecycle): IssueLifecycleDto {
            return IssueLifecycleDto(
                stage = lifecycle.stage.name,
                changePercent = lifecycle.changePercent,
                peakArticleCount = lifecycle.peakArticleCount,
                currentArticleCount = lifecycle.currentArticleCount,
                peakDate = lifecycle.peakDate,
                stageChangedAt = lifecycle.stageChangedAt
            )
        }
    }
}
