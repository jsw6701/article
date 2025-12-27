package com.yourapp.news.lifecycle

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * 이슈별 기사 수 시계열 데이터 테이블
 * 매 시간마다 각 이슈의 24시간 기사 수를 기록
 */
object IssueArticleHistories : Table("issue_article_histories") {
    val id = long("id").autoIncrement()
    val issueId = long("issue_id").index()
    val articleCount = integer("article_count")
    val recordedAt = datetime("recorded_at").index()

    override val primaryKey = PrimaryKey(id)
}
