package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * 사용자 테이블
 */
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255)
    val gender = varchar("gender", 10)
    val ageGroup = varchar("age_group", 20)
    val role = varchar("role", 20).default(UserRole.USER.name)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * RefreshToken 테이블
 */
object RefreshTokens : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val token = varchar("token", 512).uniqueIndex()
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
