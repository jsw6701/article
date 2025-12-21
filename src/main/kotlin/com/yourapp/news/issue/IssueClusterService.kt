package com.yourapp.news.issue

import com.yourapp.news.article.Article
import com.yourapp.news.article.ArticleStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime

/**
 * 경제 기사 클러스터링 서비스
 *
 * 기사들을 상위 분류(CategoryGroup)로 분류하고,
 * 같은 분류 내에서 유사 기사들을 이슈로 묶는다.
 */
@Service
class IssueClusterService(
    private val articleStore: ArticleStore,
    private val issueStore: IssueStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // 클러스터링 조건
        const val MIN_COMMON_KEYWORDS = 2        // 공통 키워드 최소 개수
        const val MAX_TIME_DIFF_HOURS = 48L      // 발행 시각 차이 최대 시간
        const val MIN_ARTICLES_FOR_ISSUE = 2    // 이슈 최소 기사 수
        const val MIN_PUBLISHERS_FOR_ISSUE = 2  // 이슈 최소 출처 수
        const val MAX_KEYWORDS_PER_ISSUE = 8    // 이슈당 최대 키워드 수
        const val MIN_KEYWORDS_PER_ISSUE = 3    // 이슈당 최소 키워드 수
    }

    /**
     * 최근 기사들에 대해 클러스터링 수행
     */
    fun clusterRecentArticles(hours: Long = 48, limit: Int = 1000): ClusteringResult {
        log.info("Starting clustering for articles from last {} hours, limit={}", hours, limit)

        val articles = articleStore.findRecentArticles(hours, limit)
        if (articles.isEmpty()) {
            log.info("No recent articles found")
            return ClusteringResult(0, 0, 0)
        }

        log.info("Loaded {} articles for clustering", articles.size)
        return clusterArticles(articles)
    }

    /**
     * 주어진 기사 목록에 대해 클러스터링 수행
     */
    fun clusterArticles(articles: List<Article>): ClusteringResult {
        // Step 1: 기사별 분류 수행
        val classifiedArticles = articles.mapNotNull { article ->
            val text = "${article.title} ${article.summary}"
            val group = CategoryGroup.classify(text)
            if (group != null) {
                val keywords = CategoryGroup.extractKeywords(text, group)
                ClassifiedArticle(article, group, keywords)
            } else {
                log.debug("Article not classified: {}", article.title)
                null
            }
        }

        log.info("Classified {} out of {} articles", classifiedArticles.size, articles.size)

        // Step 2: 그룹별로 분류
        val articlesByGroup = classifiedArticles.groupBy { it.group }

        var createdCount = 0
        var updatedCount = 0
        var skippedCount = 0

        // Step 3: 각 그룹 내에서 클러스터링 수행
        for ((group, groupArticles) in articlesByGroup) {
            log.debug("Processing group {}: {} articles", group, groupArticles.size)

            val clusters = clusterWithinGroup(groupArticles)

            for (cluster in clusters) {
                // 최소 조건 검사
                if (!meetsMinimumRequirements(cluster)) {
                    log.debug("Cluster skipped (does not meet minimum requirements): {} articles, {} publishers",
                        cluster.articles.size, cluster.getPublisherCount())
                    skippedCount++
                    continue
                }

                val result = saveCluster(cluster)
                when (result) {
                    SaveResult.CREATED -> createdCount++
                    SaveResult.UPDATED -> updatedCount++
                    SaveResult.SKIPPED -> skippedCount++
                }
            }
        }

        log.info("Clustering completed: {} created, {} updated, {} skipped",
            createdCount, updatedCount, skippedCount)

        return ClusteringResult(createdCount, updatedCount, skippedCount)
    }

    /**
     * 같은 그룹 내에서 클러스터링 수행
     */
    private fun clusterWithinGroup(articles: List<ClassifiedArticle>): List<ArticleCluster> {
        if (articles.isEmpty()) return emptyList()

        val clusters = mutableListOf<ArticleCluster>()
        val used = mutableSetOf<String>() // article link

        // 발행 시각 기준 정렬
        val sorted = articles.sortedByDescending { it.article.publishedAt }

        for (article in sorted) {
            if (article.article.link in used) continue

            // 새 클러스터 시작
            val cluster = ArticleCluster(
                group = article.group,
                articles = mutableListOf(article)
            )
            used.add(article.article.link)

            // 같은 그룹 내에서 유사 기사 찾기
            for (candidate in sorted) {
                if (candidate.article.link in used) continue
                if (isSimilar(article, candidate)) {
                    cluster.articles.add(candidate)
                    used.add(candidate.article.link)
                }
            }

            clusters.add(cluster)
        }

        return clusters
    }

    /**
     * 두 기사의 유사성 판단
     */
    private fun isSimilar(a: ClassifiedArticle, b: ClassifiedArticle): Boolean {
        // 1. 같은 그룹이어야 함
        if (a.group != b.group) return false

        // 2. 발행 시각이 48시간 내여야 함
        val timeDiff = Duration.between(a.article.publishedAt, b.article.publishedAt).abs()
        if (timeDiff.toHours() > MAX_TIME_DIFF_HOURS) return false

        // 3. 공통 키워드가 2개 이상이어야 함
        val commonKeywords = a.keywords.intersect(b.keywords.toSet())
        if (commonKeywords.size < MIN_COMMON_KEYWORDS) return false

        return true
    }

    /**
     * 클러스터가 최소 조건을 만족하는지 검사
     */
    private fun meetsMinimumRequirements(cluster: ArticleCluster): Boolean {
        // 기사 수 >= 2
        if (cluster.articles.size < MIN_ARTICLES_FOR_ISSUE) return false

        // 출처 수 >= 2
        if (cluster.getPublisherCount() < MIN_PUBLISHERS_FOR_ISSUE) return false

        return true
    }

    /**
     * 클러스터를 이슈로 저장
     */
    private fun saveCluster(cluster: ArticleCluster): SaveResult {
        val allKeywords = cluster.getMergedKeywords()
            .take(MAX_KEYWORDS_PER_ISSUE)

        if (allKeywords.size < MIN_KEYWORDS_PER_ISSUE) {
            // 키워드가 충분하지 않으면 전체 키워드에서 추출
            val extraKeywords = cluster.articles
                .flatMap { CategoryGroup.extractAllKeywords("${it.article.title} ${it.article.summary}") }
                .distinct()
                .take(MAX_KEYWORDS_PER_ISSUE)

            if (extraKeywords.size < MIN_KEYWORDS_PER_ISSUE) {
                log.debug("Cluster skipped (insufficient keywords)")
                return SaveResult.SKIPPED
            }
        }

        val fingerprint = generateFingerprint(cluster.group, allKeywords)
        val title = generateTitle(cluster.group, allKeywords)

        val articles = cluster.articles.map { it.article }
        val publishedTimes = articles.map { it.publishedAt }

        val issue = Issue(
            group = cluster.group,
            title = title,
            keywords = allKeywords,
            firstPublishedAt = publishedTimes.minOrNull()!!,
            lastPublishedAt = publishedTimes.maxOrNull()!!,
            articleCount = articles.size,
            publisherCount = cluster.getPublisherCount(),
            fingerprint = fingerprint
        )

        return try {
            val (issueId, isNew) = issueStore.upsert(issue)
            issueStore.addArticlesToIssue(issueId, articles)

            if (isNew) {
                log.info("Created new issue: {} (fingerprint={})", title, fingerprint)
                SaveResult.CREATED
            } else {
                log.info("Updated existing issue: {} (fingerprint={})", title, fingerprint)
                SaveResult.UPDATED
            }
        } catch (e: Exception) {
            log.error("Failed to save issue: {}", e.message, e)
            SaveResult.SKIPPED
        }
    }

    /**
     * 이슈 fingerprint 생성
     * 규칙: group + ":" + 상위 키워드 2~3개를 정렬 후 join
     * 길이가 길면 SHA-256 해시로 축약
     */
    private fun generateFingerprint(group: CategoryGroup, keywords: List<String>): String {
        val topKeywords = keywords.take(3).sorted().joinToString(",")
        val raw = "${group.name}:$topKeywords"

        return if (raw.length <= 64) {
            raw
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(raw.toByteArray())
            "${group.name}:" + hash.take(16).joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * 이슈 대표 제목 생성
     */
    private fun generateTitle(group: CategoryGroup, keywords: List<String>): String {
        val keywordPart = keywords.take(2).joinToString("/")
        return "${group.titleTemplate}: $keywordPart"
    }

    private enum class SaveResult {
        CREATED, UPDATED, SKIPPED
    }
}

/**
 * 분류된 기사
 */
data class ClassifiedArticle(
    val article: Article,
    val group: CategoryGroup,
    val keywords: List<String>
)

/**
 * 기사 클러스터
 */
data class ArticleCluster(
    val group: CategoryGroup,
    val articles: MutableList<ClassifiedArticle>
) {
    fun getPublisherCount(): Int = articles.map { it.article.publisher }.distinct().size

    fun getMergedKeywords(): List<String> = articles
        .flatMap { it.keywords }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
}

/**
 * 클러스터링 결과
 */
data class ClusteringResult(
    val created: Int,
    val updated: Int,
    val skipped: Int
)
