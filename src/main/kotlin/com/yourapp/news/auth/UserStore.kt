package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class UserStore(private val database: Database) {

    /**
     * 사용자 저장
     */
    fun insert(user: User): Long = transaction(database) {
        Users.insert {
            it[username] = user.username
            it[password] = user.password
            it[email] = user.email
            it[emailVerified] = user.emailVerified
            it[gender] = user.gender.name
            it[ageGroup] = user.ageGroup.name
            it[role] = user.role.name
            it[grade] = user.grade.name
            it[createdAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
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
     * 이메일(암호화된) 존재 여부 확인
     */
    fun existsByEmail(encryptedEmail: String): Boolean = transaction(database) {
        Users.selectAll()
            .where { Users.email eq encryptedEmail }
            .limit(1)
            .any()
    }

    private fun ResultRow.toUser(): User = User(
        id = this[Users.id],
        username = this[Users.username],
        password = this[Users.password],
        email = this[Users.email],
        emailVerified = this[Users.emailVerified],
        gender = Gender.valueOf(this[Users.gender]),
        ageGroup = AgeGroup.valueOf(this[Users.ageGroup]),
        role = UserRole.valueOf(this[Users.role]),
        grade = UserGrade.valueOf(this[Users.grade]),
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt]
    )
}
