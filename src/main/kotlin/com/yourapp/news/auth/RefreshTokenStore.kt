package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RefreshTokenStore(private val database: Database) {

    /**
     * RefreshToken 저장
     */
    fun save(userId: Long, token: String, expiresAt: LocalDateTime): Long = transaction(database) {
        RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.token] = token
            it[RefreshTokens.expiresAt] = expiresAt
            it[createdAt] = LocalDateTime.now()
        } get RefreshTokens.id
    }

    /**
     * 토큰으로 조회 (유효한 것만)
     */
    fun findByToken(token: String): RefreshTokenEntity? = transaction(database) {
        val now = LocalDateTime.now()
        RefreshTokens.selectAll()
            .where { (RefreshTokens.token eq token) and (RefreshTokens.expiresAt greaterEq now) }
            .singleOrNull()
            ?.let {
                RefreshTokenEntity(
                    id = it[RefreshTokens.id],
                    userId = it[RefreshTokens.userId],
                    token = it[RefreshTokens.token],
                    expiresAt = it[RefreshTokens.expiresAt],
                    createdAt = it[RefreshTokens.createdAt]
                )
            }
    }

    /**
     * 특정 토큰 삭제
     */
    fun deleteByToken(token: String): Int = transaction(database) {
        RefreshTokens.deleteWhere { RefreshTokens.token eq token }
    }

    /**
     * 사용자의 모든 RefreshToken 삭제 (로그아웃 시)
     */
    fun deleteAllByUserId(userId: Long): Int = transaction(database) {
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
    }

    /**
     * 만료된 토큰 정리
     */
    fun deleteExpiredTokens(): Int = transaction(database) {
        val now = LocalDateTime.now()
        RefreshTokens.deleteWhere { expiresAt less now }
    }
}

data class RefreshTokenEntity(
    val id: Long,
    val userId: Long,
    val token: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime
)
