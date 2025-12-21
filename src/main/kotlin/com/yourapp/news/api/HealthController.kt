package com.yourapp.news.api

import com.yourapp.news.pipeline.PipelineRunStore
import com.yourapp.news.pipeline.PipelineStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "Health", description = "시스템 헬스 체크 API")
@RestController
@RequestMapping("/api/health")
class HealthController(
    private val pipelineRunStore: PipelineRunStore
) {

    @Operation(
        summary = "파이프라인 상태 조회",
        description = "마지막 파이프라인 실행 결과를 조회합니다. RSS 수집, 이슈 클러스터링, 카드 생성 현황을 확인할 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/pipeline")
    fun getPipelineStatus(): ResponseEntity<PipelineHealthResponse> {
        val recentRuns = pipelineRunStore.findRecent(1)
        
        return if (recentRuns.isEmpty()) {
            ResponseEntity.ok(
                PipelineHealthResponse(
                    status = "UNKNOWN",
                    message = "No pipeline runs found",
                    lastRun = null
                )
            )
        } else {
            val lastRun = recentRuns.first()
            ResponseEntity.ok(
                PipelineHealthResponse(
                    status = lastRun.status.name,
                    message = when (lastRun.status) {
                        PipelineStatus.SUCCESS -> "Pipeline completed successfully"
                        PipelineStatus.PARTIAL -> "Pipeline completed with some failures"
                        PipelineStatus.FAILED -> "Pipeline failed at ${lastRun.errorStage?.name ?: "unknown stage"}"
                        PipelineStatus.RUNNING -> "Pipeline is currently running"
                        PipelineStatus.SKIPPED -> "Pipeline was skipped"
                    },
                    lastRun = LastRunInfo(
                        startedAt = lastRun.startedAt,
                        finishedAt = lastRun.finishedAt,
                        durationMs = lastRun.durationMs,
                        rssSavedCount = lastRun.rssSavedCount,
                        issuesCreatedCount = lastRun.issuesCreatedCount,
                        issuesUpdatedCount = lastRun.issuesUpdatedCount,
                        cardsCreatedCount = lastRun.cardsCreatedCount,
                        cardsFailedCount = lastRun.cardsFailedCount,
                        errorStage = lastRun.errorStage?.name,
                        errorMessage = lastRun.errorMessage
                    )
                )
            )
        }
    }

    @Operation(
        summary = "기본 헬스 체크",
        description = "서버 상태를 확인합니다."
    )
    @ApiResponse(responseCode = "200", description = "서버 정상")
    @GetMapping
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "timestamp" to LocalDateTime.now().toString()
            )
        )
    }
}

@Schema(description = "파이프라인 헬스 응답")
data class PipelineHealthResponse(
    @Schema(description = "파이프라인 상태", example = "SUCCESS")
    val status: String,
    
    @Schema(description = "상태 메시지")
    val message: String,
    
    @Schema(description = "마지막 실행 정보")
    val lastRun: LastRunInfo?
)

@Schema(description = "마지막 파이프라인 실행 정보")
data class LastRunInfo(
    @Schema(description = "시작 시간")
    val startedAt: LocalDateTime,
    
    @Schema(description = "종료 시간")
    val finishedAt: LocalDateTime?,
    
    @Schema(description = "소요 시간 (ms)")
    val durationMs: Long?,
    
    @Schema(description = "RSS 저장 기사 수")
    val rssSavedCount: Int,
    
    @Schema(description = "생성된 이슈 수")
    val issuesCreatedCount: Int,
    
    @Schema(description = "업데이트된 이슈 수")
    val issuesUpdatedCount: Int,
    
    @Schema(description = "생성된 카드 수")
    val cardsCreatedCount: Int,
    
    @Schema(description = "실패한 카드 수")
    val cardsFailedCount: Int,
    
    @Schema(description = "에러 발생 단계")
    val errorStage: String?,
    
    @Schema(description = "에러 메시지")
    val errorMessage: String?
)
