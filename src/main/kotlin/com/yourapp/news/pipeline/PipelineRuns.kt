package com.yourapp.news.pipeline

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 파이프라인 실행 이력 테이블
 */
object PipelineRuns : Table("pipeline_runs") {
    val id = long("id").autoIncrement()
    val startedAt = datetime("started_at")
    val finishedAt = datetime("finished_at").nullable()
    val durationMs = long("duration_ms").nullable()
    val rssSavedCount = integer("rss_saved_count").default(0)
    val issuesCreatedCount = integer("issues_created_count").default(0)
    val issuesUpdatedCount = integer("issues_updated_count").default(0)
    val cardsCreatedCount = integer("cards_created_count").default(0)
    val cardsFailedCount = integer("cards_failed_count").default(0)
    val status = varchar("status", 16).default(PipelineStatus.RUNNING.name)
    val errorStage = varchar("error_stage", 32).nullable()
    val errorMessage = text("error_message").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * 파이프라인 실행 상태
 */
enum class PipelineStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    SKIPPED
}

/**
 * 파이프라인 단계
 */
enum class PipelineStage {
    RSS_COLLECT,
    ISSUE_CLUSTER,
    CARD_GENERATE
}
