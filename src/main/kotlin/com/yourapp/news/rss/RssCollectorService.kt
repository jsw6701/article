package com.yourapp.news.rss

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import com.yourapp.news.article.Article
import com.yourapp.news.article.ArticleStore
import java.net.URI
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class RssCollectorService(
    private val webClient: WebClient,
    private val rssProperties: RssProperties,
    private val articleStore: ArticleStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cutoffHours = 48L

    fun collect(): Int {
        if (rssProperties.feeds.isEmpty()) {
            log.info("No RSS feeds configured; skipping collection")
            return 0
        }

        var savedCount = 0
        rssProperties.feeds.forEach { feedUrl ->
            runCatching { fetchFeed(feedUrl) }
                .onSuccess { entries ->
                    val articles = entries.mapNotNull { toArticle(it, feedUrl) }
                    if (articles.isNotEmpty()) {
                        savedCount += articleStore.saveAll(articles)
                    }
                }
                .onFailure { throwable ->
                    log.warn("Failed to fetch feed {}", feedUrl, throwable)
                }
        }

        return savedCount
    }

    private fun fetchFeed(url: String): List<SyndEntry> {
        val response = webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String::class.java)
            .block() ?: return emptyList()

        val feedInput = SyndFeedInput()
        ByteArrayInputStream(response.toByteArray()).use { inputStream ->
            return feedInput.build(XmlReader(inputStream)).entries
        }
    }

    private fun toArticle(entry: SyndEntry, feedUrl: String): Article? {
        val title = entry.title?.trim().orEmpty()
        val summary = (entry.description?.value ?: "").trim()
        if (title.length < 8 || summary.length < 20) {
            return null
        }

        val publishedDate = entry.publishedDate ?: entry.updatedDate
        val publishedAt = publishedDate?.toInstant()
            ?.atZone(ZoneId.of("UTC"))
            ?.toLocalDateTime()
            ?: return null

        if (publishedAt.isBefore(LocalDateTime.now(ZoneId.of("UTC")).minusHours(cutoffHours))) {
            return null
        }

        val publisherFromSource = entry.source?.title
        val publisher = publisherFromSource
            ?: entry.author?.takeIf { it.isNotBlank() }
            ?: runCatching { URI(feedUrl).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } }
                .getOrNull()
            ?: "unknown"

        val link = entry.link?.takeIf { it.isNotBlank() } ?: return null

        return Article(
            title = title,
            summary = summary,
            link = link,
            publisher = publisher,
            publishedAt = publishedAt.truncatedTo(ChronoUnit.SECONDS),
            category = "economy",
        )
    }
}
