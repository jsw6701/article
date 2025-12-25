package com.yourapp.news.issue

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object Issues : Table("issues") {
    val id = long("id").autoIncrement()
    val group = varchar("group_name", 32)
    val title = varchar("title", 512)
    val keywords = text("keywords") // 쉼표로 구분된 키워드 목록
    val firstPublishedAt = datetime("first_published_at")
    val lastPublishedAt = datetime("last_published_at")
    val articleCount = integer("article_count")
    val publisherCount = integer("publisher_count")
    val status = varchar("status", 32).default(IssueStatus.OPEN.name)
    val fingerprint = varchar("fingerprint", 128).uniqueIndex()
    val headline = varchar("headline", 255).nullable() // 사용자에게 노출되는 제목
    val signalSummary = varchar("signal_summary", 255).nullable() // 리스트 카드용 한 줄 요약

    override val primaryKey = PrimaryKey(id)
}
