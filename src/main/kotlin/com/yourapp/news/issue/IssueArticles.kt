package com.yourapp.news.issue

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object IssueArticles : Table("issue_articles") {
    val issueId = long("issue_id").references(Issues.id)
    val articleLink = varchar("article_link", 500)  // 복합 PK: BIGINT(8) + VARCHAR(500)*4 = 2008 bytes < 3072
    val publishedAt = datetime("published_at")

    override val primaryKey = PrimaryKey(issueId, articleLink)
}
