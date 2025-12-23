package com.yourapp.news.auth

import java.time.LocalDateTime

/**
 * 사용자 도메인 객체
 */
data class User(
    val id: Long? = null,
    val username: String,
    val password: String,
    val gender: Gender,
    val ageGroup: AgeGroup,
    val role: UserRole = UserRole.USER,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
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
