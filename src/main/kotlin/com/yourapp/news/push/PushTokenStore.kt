package com.yourapp.news.push

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PushTokenStore(private val database: Database) {

    /**
     * 토큰 저장 (기존 토큰이 있으면 업데이트)
     */
    fun save(pushToken: PushToken): PushToken = transaction(database) {
        // 동일 토큰이 이미 존재하는지 확인
        val existing = PushTokens.selectAll()
            .where { PushTokens.token eq pushToken.token }
            .singleOrNull()

        if (existing != null) {
            // 토큰이 이미 존재하면 userId와 updatedAt 업데이트
            PushTokens.update({ PushTokens.token eq pushToken.token }) {
                it[userId] = pushToken.userId
                it[platform] = pushToken.platform.name
                it[updatedAt] = LocalDateTime.now()
            }
            pushToken.copy(id = existing[PushTokens.id])
        } else {
            // 새 토큰 삽입
            val id = PushTokens.insert {
                it[userId] = pushToken.userId
                it[token] = pushToken.token
                it[platform] = pushToken.platform.name
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            } get PushTokens.id

            pushToken.copy(id = id)
        }
    }

    /**
     * 사용자의 모든 토큰 조회
     */
    fun findByUserId(userId: Long): List<PushToken> = transaction(database) {
        PushTokens.selectAll()
            .where { PushTokens.userId eq userId }
            .map { it.toPushToken() }
    }

    /**
     * 토큰으로 조회
     */
    fun findByToken(token: String): PushToken? = transaction(database) {
        PushTokens.selectAll()
            .where { PushTokens.token eq token }
            .singleOrNull()
            ?.toPushToken()
    }

    /**
     * 토큰 삭제
     */
    fun deleteByToken(token: String): Boolean = transaction(database) {
        PushTokens.deleteWhere { PushTokens.token eq token } > 0
    }

    /**
     * 사용자의 특정 토큰 삭제
     */
    fun deleteByUserIdAndToken(userId: Long, token: String): Boolean = transaction(database) {
        PushTokens.deleteWhere {
            (PushTokens.userId eq userId) and (PushTokens.token eq token)
        } > 0
    }

    /**
     * 사용자의 모든 토큰 삭제
     */
    fun deleteAllByUserId(userId: Long): Int = transaction(database) {
        PushTokens.deleteWhere { PushTokens.userId eq userId }
    }

    /**
     * 여러 사용자의 토큰 조회 (벌크 알림 발송용)
     */
    fun findByUserIds(userIds: List<Long>): List<PushToken> = transaction(database) {
        if (userIds.isEmpty()) return@transaction emptyList()

        PushTokens.selectAll()
            .where { PushTokens.userId inList userIds }
            .map { it.toPushToken() }
    }

    /**
     * 모든 토큰 조회 (전체 알림 발송용)
     */
    fun findAll(): List<PushToken> = transaction(database) {
        PushTokens.selectAll()
            .map { it.toPushToken() }
    }

    private fun ResultRow.toPushToken(): PushToken = PushToken(
        id = this[PushTokens.id],
        userId = this[PushTokens.userId],
        token = this[PushTokens.token],
        platform = Platform.valueOf(this[PushTokens.platform]),
        createdAt = this[PushTokens.createdAt],
        updatedAt = this[PushTokens.updatedAt]
    )
}
