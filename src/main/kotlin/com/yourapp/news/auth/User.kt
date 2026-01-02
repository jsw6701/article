package com.yourapp.news.auth

import java.time.LocalDateTime

/**
 * 사용자 도메인 객체
 */
data class User(
    val id: Long? = null,
    val username: String,
    val password: String,
    val email: String,                                  // 암호화된 이메일 (복호화 가능)
    val emailHash: String = "",                         // 이메일 해시 (검색용, 단방향)
    val emailVerified: Boolean = false,                 // 이메일 인증 여부
    val gender: Gender,
    val ageGroup: AgeGroup,
    val role: UserRole = UserRole.USER,
    val grade: UserGrade = UserGrade.BRONZE,            // 회원 등급
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 이메일 인증 코드
 */
data class EmailVerificationCode(
    val id: Long? = null,
    val email: String,                                  // 암호화된 이메일
    val code: String,                                   // 인증 코드
    val expiresAt: LocalDateTime,                       // 만료 시간
    val verified: Boolean = false,                      // 인증 완료 여부
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 성별
 */
enum class Gender(val displayName: String) {
    MALE("남성"),
    FEMALE("여성")
}

/**
 * 나이대
 */
enum class AgeGroup(val displayName: String, val range: String) {
    TEENS("10대", "10-19"),
    TWENTIES("20대", "20-29"),
    THIRTIES("30대", "30-39"),
    FORTIES("40대", "40-49"),
    FIFTIES("50대", "50-59"),
    SIXTIES_PLUS("60대 이상", "60+")
}

/**
 * 사용자 역할
 */
enum class UserRole {
    USER,
    ADMIN
}

/**
 * 회원 등급
 */
enum class UserGrade(val displayName: String, val level: Int, val description: String) {
    BRONZE("브론즈", 1, "일반 회원"),
    SILVER("실버", 2, "활동 회원"),
    GOLD("골드", 3, "우수 회원"),
    PLATINUM("플래티넘", 4, "VIP 회원"),
    DIAMOND("다이아몬드", 5, "최우수 회원")
}
