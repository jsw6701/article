package com.yourapp.news.bookmark

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class BookmarkStore(private val database: Database) {

    /**
     * 북마크 추가
     */
    fun add(userId: Long, cardId: Long): Long = transaction(database) {
        Bookmarks.insert {
            it[Bookmarks.userId] = userId
            it[Bookmarks.cardId] = cardId
        } get Bookmarks.id
    }

    /**
     * 북마크 삭제
     */
    fun remove(userId: Long, cardId: Long): Boolean = transaction(database) {
        val deleted = Bookmarks.deleteWhere {
            (Bookmarks.userId eq userId) and (Bookmarks.cardId eq cardId)
        }
        deleted > 0
    }

    /**
     * 북마크 존재 여부 확인
     */
    fun exists(userId: Long, cardId: Long): Boolean = transaction(database) {
        Bookmarks.selectAll()
            .where { (Bookmarks.userId eq userId) and (Bookmarks.cardId eq cardId) }
            .limit(1)
            .any()
    }

    /**
     * 사용자의 북마크 목록 조회
     */
    fun findByUserId(userId: Long): List<Bookmark> = transaction(database) {
        Bookmarks.selectAll()
            .where { Bookmarks.userId eq userId }
            .orderBy(Bookmarks.createdAt to SortOrder.DESC)
            .map { it.toBookmark() }
    }

    /**
     * 사용자의 북마크된 카드 ID 목록
     */
    fun getBookmarkedCardIds(userId: Long): Set<Long> = transaction(database) {
        Bookmarks.selectAll()
            .where { Bookmarks.userId eq userId }
            .map { it[Bookmarks.cardId] }
            .toSet()
    }

    /**
     * 특정 이슈(카드)를 북마크한 사용자 ID 목록
     */
    fun findUserIdsByIssueId(issueId: Long): List<Long> = transaction(database) {
        Bookmarks.selectAll()
            .where { Bookmarks.cardId eq issueId }
            .map { it[Bookmarks.userId] }
    }

    private fun ResultRow.toBookmark(): Bookmark = Bookmark(
        id = this[Bookmarks.id],
        userId = this[Bookmarks.userId],
        cardId = this[Bookmarks.cardId],
        createdAt = this[Bookmarks.createdAt]
    )
}
