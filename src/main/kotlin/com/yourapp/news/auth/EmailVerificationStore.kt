package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class EmailVerificationStore(private val database: Database) {

    /**
     * 인증 코드 저장
     */
    fun save(encryptedEmail: String, code: String, expiresAt: LocalDateTime): Long = transaction(database) {
        // 기존 미인증 코드 삭제
        EmailVerificationCodes.deleteWhere {
            (email eq encryptedEmail) and (verified eq false)
        }

        EmailVerificationCodes.insert {
            it[email] = encryptedEmail
            it[EmailVerificationCodes.code] = code
            it[EmailVerificationCodes.expiresAt] = expiresAt
            it[verified] = false
            it[createdAt] = LocalDateTime.now()
        } get EmailVerificationCodes.id
    }

    /**
     * 인증 코드 검증
     */
    fun findByEmailAndCode(encryptedEmail: String, code: String): EmailVerificationCode? = transaction(database) {
        EmailVerificationCodes.selectAll()
            .where { (EmailVerificationCodes.email eq encryptedEmail) and (EmailVerificationCodes.code eq code) }
            .singleOrNull()
            ?.toEmailVerificationCode()
    }

    /**
     * 인증 완료 처리
     */
    fun markAsVerified(id: Long): Boolean = transaction(database) {
        EmailVerificationCodes.update({ EmailVerificationCodes.id eq id }) {
            it[verified] = true
        } > 0
    }

    /**
     * 이메일로 인증 완료된 코드 찾기
     */
    fun findVerifiedByEmail(encryptedEmail: String): EmailVerificationCode? = transaction(database) {
        EmailVerificationCodes.selectAll()
            .where {
                (EmailVerificationCodes.email eq encryptedEmail) and
                (EmailVerificationCodes.verified eq true)
            }
            .orderBy(EmailVerificationCodes.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toEmailVerificationCode()
    }

    /**
     * 만료된 코드 삭제
     */
    fun deleteExpired(): Int = transaction(database) {
        EmailVerificationCodes.deleteWhere {
            (expiresAt less LocalDateTime.now()) and (verified eq false)
        }
    }

    /**
     * 인증 완료된 코드 삭제 (회원가입 완료 후)
     */
    fun deleteByEmail(encryptedEmail: String): Int = transaction(database) {
        EmailVerificationCodes.deleteWhere {
            email eq encryptedEmail
        }
    }

    private fun ResultRow.toEmailVerificationCode() = EmailVerificationCode(
        id = this[EmailVerificationCodes.id],
        email = this[EmailVerificationCodes.email],
        code = this[EmailVerificationCodes.code],
        expiresAt = this[EmailVerificationCodes.expiresAt],
        verified = this[EmailVerificationCodes.verified],
        createdAt = this[EmailVerificationCodes.createdAt]
    )
}
