package com.yourapp.news.article

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.inList
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class ArticleStore(private val database: Database) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun existsByLink(link: String): Boolean = transaction(database) {
        Articles.select { Articles.link eq link }.limit(1).any()
    }

    fun saveAll(articles: List<Article>): Int = transaction(database) {
        val uniqueCandidates = articles.distinctBy { it.link }
        if (uniqueCandidates.isEmpty()) return@transaction 0

        val links = uniqueCandidates.map { it.link }
        val existingLinks = Articles
            .slice(Articles.link)
            .select { Articles.link inList links }
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
}
