package com.yourapp.news.lifecycle

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class LifecycleStore(private val database: Database) {

    /**
     * 생애주기 정보 저장 또는 업데이트
     */
    fun upsert(lifecycle: IssueLifecycle) = transaction(database) {
        val now = LocalDateTime.now()

        IssueLifecycles.upsert(IssueLifecycles.issueId) {
            it[issueId] = lifecycle.issueId
            it[stage] = lifecycle.stage.name
            it[changePercent] = lifecycle.changePercent
            it[peakArticleCount] = lifecycle.peakArticleCount
            it[currentArticleCount] = lifecycle.currentArticleCount
            it[peakDate] = lifecycle.peakDate
            it[stageChangedAt] = lifecycle.stageChangedAt
            it[updatedAt] = now
            it[createdAt] = now
        }
    }

    /**
     * 이슈 ID로 생애주기 조회
     */
    fun findByIssueId(issueId: Long): IssueLifecycle? = transaction(database) {
        IssueLifecycles.selectAll()
            .where { IssueLifecycles.issueId eq issueId }
            .map { it.toIssueLifecycle() }
            .firstOrNull()
    }

    /**
     * 여러 이슈의 생애주기 일괄 조회
     */
    fun findByIssueIds(issueIds: List<Long>): Map<Long, IssueLifecycle> {
        if (issueIds.isEmpty()) return emptyMap()

        return transaction(database) {
            IssueLifecycles.selectAll()
                .where { IssueLifecycles.issueId inList issueIds }
                .associate { it[IssueLifecycles.issueId] to it.toIssueLifecycle() }
        }
    }

    /**
     * 기사 수 히스토리 기록
     */
    fun saveArticleHistory(issueId: Long, articleCount: Int, recordedAt: LocalDateTime = LocalDateTime.now()) = transaction(database) {
        IssueArticleHistories.insert {
            it[IssueArticleHistories.issueId] = issueId
            it[IssueArticleHistories.articleCount] = articleCount
            it[IssueArticleHistories.recordedAt] = recordedAt
        }
    }

    /**
     * 최근 N일간 기사 수 히스토리 조회
     */
    fun getArticleHistory(issueId: Long, days: Int = 7): List<ArticleHistoryRecord> = transaction(database) {
        val since = LocalDateTime.now().minusDays(days.toLong())

        IssueArticleHistories.selectAll()
            .where {
                (IssueArticleHistories.issueId eq issueId) and
                (IssueArticleHistories.recordedAt greaterEq since)
            }
            .orderBy(IssueArticleHistories.recordedAt, SortOrder.ASC)
            .map {
                ArticleHistoryRecord(
                    articleCount = it[IssueArticleHistories.articleCount],
                    recordedAt = it[IssueArticleHistories.recordedAt]
                )
            }
    }

    /**
     * 오래된 히스토리 삭제 (7일 이전)
     */
    fun deleteOldHistory(days: Int = 7): Int = transaction(database) {
        val cutoff = LocalDateTime.now().minusDays(days.toLong())

        IssueArticleHistories.deleteWhere {
            recordedAt less cutoff
        }
    }

    private fun ResultRow.toIssueLifecycle(): IssueLifecycle {
        return IssueLifecycle(
            issueId = this[IssueLifecycles.issueId],
            stage = IssueLifecycleStage.valueOf(this[IssueLifecycles.stage]),
            changePercent = this[IssueLifecycles.changePercent],
            peakArticleCount = this[IssueLifecycles.peakArticleCount],
            currentArticleCount = this[IssueLifecycles.currentArticleCount],
            peakDate = this[IssueLifecycles.peakDate],
            stageChangedAt = this[IssueLifecycles.stageChangedAt]
        )
    }
}

/**
 * 기사 수 히스토리 레코드
 */
data class ArticleHistoryRecord(
    val articleCount: Int,
    val recordedAt: LocalDateTime
)
