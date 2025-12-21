package com.yourapp.news.article

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ArticleStore(private val database: Database) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun existsByLink(link: String): Boolean = transaction(database) {
        Articles.selectAll().where { Articles.link eq link }.limit(1).any()
    }

    fun saveAll(articles: List<Article>): Int = transaction(database) {
        val uniqueCandidates = articles.distinctBy { it.link }
        if (uniqueCandidates.isEmpty()) return@transaction 0

        val links = uniqueCandidates.map { it.link }
        val existingLinks = Articles
            .selectAll()
            .where { Articles.link inList links }
            .map { it[Articles.link] }
            .toSet()

        val newArticles = uniqueCandidates.filter { it.link !in existingLinks }
        if (newArticles.isEmpty()) return@transaction 0

        val insertedCount = runCatching {
            Articles.batchInsert(newArticles) { article ->
                this[Articles.title] = article.title
                this[Articles.summary] = article.summary
                this[Articles.link] = article.link
                this[Articles.publisher] = article.publisher
                this[Articles.publishedAt] = article.publishedAt
                this[Articles.category] = article.category
            }.size
        }.onFailure { ex ->
            log.warn("Skipped inserting some articles due to constraint violation or other issue", ex)
        }.getOrDefault(0)

        if (insertedCount > 0) {
            log.info("Inserted {} new articles", insertedCount)
        }

        insertedCount
    }

    /**
     * 최근 N시간 내 발행된 기사 조회
     * @param hours 조회할 시간 범위 (기본 48시간)
     * @param limit 최대 조회 건수 (기본 1000건)
     */
    fun findRecentArticles(hours: Long = 48, limit: Int = 1000): List<Article> = transaction(database) {
        val since = LocalDateTime.now().minusHours(hours)
        Articles.selectAll()
            .where { Articles.publishedAt greaterEq since }
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toArticle() }
    }

    /**
     * 특정 시점 이후 발행된 기사 조회
     */
    fun findArticlesSince(since: LocalDateTime, limit: Int = 1000): List<Article> = transaction(database) {
        Articles.selectAll()
            .where { Articles.publishedAt greaterEq since }
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toArticle() }
    }

    /**
     * 링크 목록으로 기사 조회
     */
    fun findByLinks(links: List<String>): List<Article> = transaction(database) {
        if (links.isEmpty()) return@transaction emptyList()
        Articles.selectAll()
            .where { Articles.link inList links }
            .map { it.toArticle() }
    }

    /**
     * 링크 목록으로 기사 조회 (최신순 정렬, 최대 limit개)
     * 카드 생성용
     */
    fun findByLinksForCard(links: List<String>, limit: Int = 8): List<Article> = transaction(database) {
        if (links.isEmpty()) return@transaction emptyList()
        Articles.selectAll()
            .where { Articles.link inList links }
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toArticle() }
    }

    private fun ResultRow.toArticle(): Article = Article(
        title = this[Articles.title],
        summary = this[Articles.summary],
        link = this[Articles.link],
        publisher = this[Articles.publisher],
        publishedAt = this[Articles.publishedAt],
        category = this[Articles.category]
    )
}
