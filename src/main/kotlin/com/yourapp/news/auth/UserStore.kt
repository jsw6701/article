package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class UserStore(private val database: Database) {

    /**
     * 사용자 저장
     */
    fun insert(user: User): Long = transaction(database) {
        val now = LocalDateTime.now()
        Users.insert {
            it[username] = user.username
            it[password] = user.password
            it[email] = user.email
            it[emailHash] = user.emailHash
            it[emailVerified] = user.emailVerified
            it[gender] = user.gender.name
            it[ageGroup] = user.ageGroup.name
            it[role] = user.role.name
            it[grade] = user.grade.name
            it[termsAgreedAt] = user.termsAgreedAt ?: now
            it[privacyAgreedAt] = user.privacyAgreedAt ?: now
            it[createdAt] = now
            it[updatedAt] = now
        } get Users.id
    }

    /**
     * ID로 사용자 조회
     */
    fun findById(id: Long): User? = transaction(database) {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    /**
     * username으로 사용자 조회
     */
    fun findByUsername(username: String): User? = transaction(database) {
        Users.selectAll()
            .where { Users.username eq username }
            .singleOrNull()
            ?.toUser()
    }

    /**
     * username 존재 여부 확인
     */
    fun existsByUsername(username: String): Boolean = transaction(database) {
        Users.selectAll()
            .where { Users.username eq username }
            .limit(1)
            .any()
    }

    /**
     * 이메일 해시로 존재 여부 확인
     */
    fun existsByEmail(emailHash: String): Boolean = transaction(database) {
        Users.selectAll()
            .where { Users.emailHash eq emailHash }
            .limit(1)
            .any()
    }

    /**
     * 사용자 삭제
     */
    fun deleteById(id: Long): Boolean = transaction(database) {
        val deleted = Users.deleteWhere { Users.id eq id }
        deleted > 0
    }

    /**
     * 이메일 해시로 사용자 조회
     */
    fun findByEmailHash(emailHash: String): User? = transaction(database) {
        Users.selectAll()
            .where { Users.emailHash eq emailHash }
            .singleOrNull()
            ?.toUser()
    }

    /**
     * 비밀번호 업데이트
     */
    fun updatePassword(userId: Long, encodedPassword: String): Boolean = transaction(database) {
        val updated = Users.update({ Users.id eq userId }) {
            it[password] = encodedPassword
            it[updatedAt] = LocalDateTime.now()
        }
        updated > 0
    }

    /**
     * 프로필 업데이트 (성별, 연령대)
     */
    fun updateProfile(userId: Long, gender: Gender, ageGroup: AgeGroup): Boolean = transaction(database) {
        val updated = Users.update({ Users.id eq userId }) {
            it[Users.gender] = gender.name
            it[Users.ageGroup] = ageGroup.name
            it[updatedAt] = LocalDateTime.now()
        }
        updated > 0
    }

    /**
     * 약관 동의 정보 업데이트
     */
    fun updateTermsAgreement(userId: Long): Boolean = transaction(database) {
        val now = LocalDateTime.now()
        val updated = Users.update({ Users.id eq userId }) {
            it[termsAgreedAt] = now
            it[privacyAgreedAt] = now
            it[updatedAt] = now
        }
        updated > 0
    }

    private fun ResultRow.toUser(): User = User(
        id = this[Users.id],
        username = this[Users.username],
        password = this[Users.password],
        email = this[Users.email],
        emailHash = this[Users.emailHash],
        emailVerified = this[Users.emailVerified],
        gender = Gender.valueOf(this[Users.gender]),
        ageGroup = AgeGroup.valueOf(this[Users.ageGroup]),
        role = UserRole.valueOf(this[Users.role]),
        grade = UserGrade.valueOf(this[Users.grade]),
        termsAgreedAt = this[Users.termsAgreedAt],
        privacyAgreedAt = this[Users.privacyAgreedAt],
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt]
    )
}
