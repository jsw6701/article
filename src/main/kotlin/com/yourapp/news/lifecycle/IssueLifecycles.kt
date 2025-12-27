package com.yourapp.news.lifecycle

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * 이슈 생애주기 테이블
 */
object IssueLifecycles : Table("issue_lifecycles") {
    val issueId = long("issue_id").uniqueIndex()
    val stage = varchar("stage", 20)
    val changePercent = integer("change_percent").default(0)
    val peakArticleCount = integer("peak_article_count").default(0)
    val currentArticleCount = integer("current_article_count").default(0)
    val peakDate = datetime("peak_date").nullable()
    val stageChangedAt = datetime("stage_changed_at")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(issueId)
}
