package com.yourapp.news.issue

import com.yourapp.news.article.Article
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class IssueStore(private val database: Database) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * ID로 이슈 조회
     */
    fun findById(id: Long): Issue? = transaction(database) {
        Issues.selectAll().where { Issues.id eq id }
            .singleOrNull()
            ?.toIssue()
    }

    /**
     * fingerprint로 이슈 조회
     */
    fun findByFingerprint(fingerprint: String): Issue? = transaction(database) {
        Issues.selectAll().where { Issues.fingerprint eq fingerprint }
            .singleOrNull()
            ?.toIssue()
    }

    /**
     * 이슈 신규 저장
     */
    fun insert(issue: Issue): Long = transaction(database) {
        Issues.insert {
            it[group] = issue.group.name
            it[title] = issue.title
            it[keywords] = issue.keywords.joinToString(",")
            it[firstPublishedAt] = issue.firstPublishedAt
            it[lastPublishedAt] = issue.lastPublishedAt
            it[articleCount] = issue.articleCount
            it[publisherCount] = issue.publisherCount
            it[status] = issue.status.name
            it[fingerprint] = issue.fingerprint
        } get Issues.id
    }

    /**
     * 이슈 업데이트 (기존 이슈에 기사 추가 시)
     */
    fun update(issue: Issue): Int = transaction(database) {
        Issues.update({ Issues.fingerprint eq issue.fingerprint }) {
            it[keywords] = issue.keywords.joinToString(",")
            it[lastPublishedAt] = issue.lastPublishedAt
            it[articleCount] = issue.articleCount
            it[publisherCount] = issue.publisherCount
        }
    }

    /**
     * 이슈 upsert (있으면 업데이트, 없으면 생성)
     * @return Pair<issueId, isNew>
     */
    fun upsert(issue: Issue): Pair<Long, Boolean> = transaction(database) {
        val existing = findByFingerprint(issue.fingerprint)
        if (existing != null) {
            update(issue)
            existing.id!! to false
        } else {
            val newId = insert(issue)
            newId to true
        }
    }

    /**
     * 이슈에 기사 매핑 추가
     */
    fun addArticleToIssue(issueId: Long, article: Article): Boolean = transaction(database) {
        runCatching {
            IssueArticles.insertIgnore {
                it[IssueArticles.issueId] = issueId
                it[articleLink] = article.link
                it[publishedAt] = article.publishedAt
            }.insertedCount > 0
        }.onFailure { ex ->
            log.debug("Skipped duplicate article mapping: issueId={}, link={}", issueId, article.link)
        }.getOrDefault(false)
    }

    /**
     * 이슈에 여러 기사 매핑 추가
     */
    fun addArticlesToIssue(issueId: Long, articles: List<Article>): Int = transaction(database) {
        var addedCount = 0
        articles.forEach { article ->
            if (addArticleToIssue(issueId, article)) {
                addedCount++
            }
        }
        addedCount
    }

    /**
     * 이슈에 매핑된 기사 링크 목록 조회
     */
    fun findArticleLinksByIssueId(issueId: Long): List<String> = transaction(database) {
        IssueArticles.selectAll().where { IssueArticles.issueId eq issueId }
            .map { it[IssueArticles.articleLink] }
    }

    /**
     * 전체 이슈 목록 조회 (최신순)
     */
    fun findAll(limit: Int = 100): List<Issue> = transaction(database) {
        Issues.selectAll()
            .orderBy(Issues.lastPublishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toIssue() }
    }

    /**
     * OPEN 상태인 이슈만 조회
     */
    fun findOpenIssues(limit: Int = 100): List<Issue> = transaction(database) {
        Issues.selectAll().where { Issues.status eq IssueStatus.OPEN.name }
            .orderBy(Issues.lastPublishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toIssue() }
    }

    /**
     * 카드 생성 대상 이슈 조회
     * - 최근 48시간 내 이슈
     * - publisherCount >= 2 AND articleCount >= 2
     */
    fun findCardGenerationTargets(hours: Long = 48, limit: Int = 50): List<Issue> = transaction(database) {
        val since = LocalDateTime.now().minusHours(hours)
        
        Issues.selectAll()
            .where {
                (Issues.lastPublishedAt greaterEq since) and
                (Issues.publisherCount greaterEq 2) and
                (Issues.articleCount greaterEq 2)
            }
            .orderBy(Issues.lastPublishedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toIssue() }
    }

    private fun ResultRow.toIssue(): Issue = Issue(
        id = this[Issues.id],
        group = CategoryGroup.valueOf(this[Issues.group]),
        title = this[Issues.title],
        keywords = this[Issues.keywords].split(",").filter { it.isNotBlank() },
        firstPublishedAt = this[Issues.firstPublishedAt],
        lastPublishedAt = this[Issues.lastPublishedAt],
        articleCount = this[Issues.articleCount],
        publisherCount = this[Issues.publisherCount],
        status = IssueStatus.valueOf(this[Issues.status]),
        fingerprint = this[Issues.fingerprint]
    )
}
