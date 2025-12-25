package com.yourapp.news.rss

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import com.yourapp.news.article.Article
import com.yourapp.news.article.ArticleStore
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.HtmlUtils

@Service
class RssCollectorService(
    private val webClient: WebClient,
    private val rssProperties: RssProperties,
    private val articleStore: ArticleStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cutoffHours = 48L

    /**
     * 모든 RSS 피드에서 기사 수집
     * @return 저장된 기사 수
     */
    fun collectAll(): Int = collect()

    fun collect(): Int {
        if (rssProperties.sources.isEmpty()) {
            log.info("[RSS] No feeds configured, skipping collection")
            return 0
        }

        log.info("[RSS] Starting collection from {} feeds", rssProperties.sources.size)
        val startTime = System.currentTimeMillis()

        var totalSaved = 0
        var successCount = 0
        var failCount = 0

        rssProperties.sources.forEach { source ->
            runCatching { fetchFeed(source.url) }
                .onSuccess { entries ->
                    val articles = entries.mapNotNull { toArticle(it, source.publisher) }
                    val saved = if (articles.isNotEmpty()) {
                        articleStore.saveAll(articles)
                    } else 0

                    totalSaved += saved
                    successCount++
                    log.info("[RSS] Feed success: url={}, entries={}, saved={}",
                        maskUrl(source.url), entries.size, saved)
                }
                .onFailure { throwable ->
                    failCount++
                    log.warn("[RSS] Feed failed: url={}, error={}",
                        maskUrl(source.url), throwable.message)
                }
        }

        val elapsed = System.currentTimeMillis() - startTime
        log.info("[RSS] Collection complete: totalFeeds={}, success={}, failed={}, totalSaved={}, elapsedMs={}",
            rssProperties.sources.size, successCount, failCount, totalSaved, elapsed)

        return totalSaved
    }

    private fun fetchFeed(url: String): List<SyndEntry> {
        val bytes = webClient.get()
            .uri(url)
            .header("User-Agent", "yourapp-news-bot/1.0")
            .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .block() ?: return emptyList()

        val feedInput = SyndFeedInput()
        ByteArrayInputStream(bytes).use { inputStream ->
            return feedInput.build(XmlReader(inputStream)).entries
        }
    }

    private fun toArticle(entry: SyndEntry, publisher: String): Article? {
        val title = entry.title?.trim().orEmpty().decodeHtmlEntities()
        val summary = (entry.description?.value ?: "").trim().decodeHtmlEntities()
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

        val link = entry.link?.takeIf { it.isNotBlank() } ?: return null

        return Article(
            title = title,
            summary = summary,
            link = link,
            publisher = publisher, // 설정에서 지정한 매체명 사용
            publishedAt = publishedAt.truncatedTo(ChronoUnit.SECONDS),
            category = "economy",
        )
    }

    /**
     * URL에서 호스트명만 추출하여 마스킹 (보안)
     */
    private fun maskUrl(url: String): String {
        return runCatching {
            URI(url).host ?: url.take(50)
        }.getOrDefault(url.take(50))
    }

    /**
     * HTML 엔티티 디코딩 (&#039; → ', &amp; → &, &lt; → < 등)
     */
    private fun String.decodeHtmlEntities(): String {
        return HtmlUtils.htmlUnescape(this)
    }
}
