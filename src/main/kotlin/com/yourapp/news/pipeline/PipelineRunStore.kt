package com.yourapp.news.pipeline

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class PipelineRunStore(private val database: Database) {

    /**
     * 파이프라인 실행 기록 저장
     */
    fun insert(run: PipelineRun): Long = transaction(database) {
        PipelineRuns.insert {
            it[startedAt] = run.startedAt
            it[finishedAt] = run.finishedAt
            it[durationMs] = run.durationMs
            it[rssSavedCount] = run.rssSavedCount
            it[issuesCreatedCount] = run.issuesCreatedCount
            it[issuesUpdatedCount] = run.issuesUpdatedCount
            it[cardsCreatedCount] = run.cardsCreatedCount
            it[cardsFailedCount] = run.cardsFailedCount
            it[status] = run.status.name
            it[errorStage] = run.errorStage?.name
            it[errorMessage] = run.errorMessage?.take(5000)
        } get PipelineRuns.id
    }

    /**
     * 최근 실행 기록 조회
     */
    fun findRecent(limit: Int = 20): List<PipelineRun> = transaction(database) {
        PipelineRuns.selectAll()
            .orderBy(PipelineRuns.startedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toRun() }
    }

    private fun ResultRow.toRun(): PipelineRun = PipelineRun(
        id = this[PipelineRuns.id],
        startedAt = this[PipelineRuns.startedAt],
        finishedAt = this[PipelineRuns.finishedAt],
        durationMs = this[PipelineRuns.durationMs],
        rssSavedCount = this[PipelineRuns.rssSavedCount],
        issuesCreatedCount = this[PipelineRuns.issuesCreatedCount],
        issuesUpdatedCount = this[PipelineRuns.issuesUpdatedCount],
        cardsCreatedCount = this[PipelineRuns.cardsCreatedCount],
        cardsFailedCount = this[PipelineRuns.cardsFailedCount],
        status = PipelineStatus.valueOf(this[PipelineRuns.status]),
        errorStage = this[PipelineRuns.errorStage]?.let { PipelineStage.valueOf(it) },
        errorMessage = this[PipelineRuns.errorMessage]
    )
}
