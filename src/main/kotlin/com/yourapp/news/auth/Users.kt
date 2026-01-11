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
    val email = varchar("email", 512)                          // 암호화된 이메일 (복호화 가능)
    val emailHash = varchar("email_hash", 255).uniqueIndex()   // 이메일 해시 (검색용)
    val emailVerified = bool("email_verified").default(false)  // 이메일 인증 여부
    val gender = varchar("gender", 10)
    val ageGroup = varchar("age_group", 20)
    val role = varchar("role", 20).default(UserRole.USER.name)
    val grade = varchar("grade", 20).default(UserGrade.BRONZE.name)  // 회원 등급
    val termsAgreedAt = datetime("terms_agreed_at").nullable()       // 이용약관 동의 일시
    val privacyAgreedAt = datetime("privacy_agreed_at").nullable()   // 개인정보처리방침 동의 일시
    val pushNotificationAgreedAt = datetime("push_notification_agreed_at").nullable()  // 푸시 알림 수신 동의 일시
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * 이메일 인증 코드 테이블
 */
object EmailVerificationCodes : Table("email_verification_codes") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255)                          // 암호화된 이메일
    val code = varchar("code", 10)                             // 인증 코드 (6자리)
    val expiresAt = datetime("expires_at")                     // 만료 시간
    val verified = bool("verified").default(false)             // 인증 완료 여부
    val createdAt = datetime("created_at")

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

/**
 * 사용자 설정 테이블
 */
object UserSettingsTable : Table("user_settings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id).uniqueIndex()
    val theme = varchar("theme", 20).default(Theme.DARK.name)
    val fontSize = varchar("font_size", 20).default(FontSize.MEDIUM.name)
    val startPage = varchar("start_page", 20).default(StartPage.HOME.name)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
