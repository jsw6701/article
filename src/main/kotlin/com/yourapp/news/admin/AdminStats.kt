package com.yourapp.news.admin

import java.time.LocalDate

/**
 * 일별 가입자 통계
 */
data class DailySignupStats(
    val date: LocalDate,
    val count: Long
)

/**
 * 일별 조회수 통계
 */
data class DailyViewStats(
    val date: LocalDate,
    val count: Long
)

/**
 * 대시보드 요약 통계
 */
data class DashboardSummary(
    val totalUsers: Long,
    val todaySignups: Long,
    val totalViews: Long,
    val todayViews: Long,
    val totalCards: Long,
    val activeCards: Long
)

/**
 * 성별 통계
 */
data class GenderStats(
    val male: Long,
    val female: Long
)

/**
 * 연령대 통계
 */
data class AgeGroupStats(
    val ageGroup: String,
    val displayName: String,
    val count: Long
)

/**
 * 회원 등급별 통계
 */
data class GradeStats(
    val grade: String,
    val displayName: String,
    val level: Int,
    val count: Long
)

/**
 * 사용자 목록 응답
 */
data class UserListItem(
    val id: Long,
    val username: String,
    val email: String,
    val gender: String,
    val ageGroup: String,
    val role: String,
    val grade: String,
    val gradeDisplayName: String,
    val gradeLevel: Int,
    val emailVerified: Boolean,
    val createdAt: String
)

/**
 * 페이지네이션된 사용자 목록 응답
 */
data class UserListResponse(
    val items: List<UserListItem>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)
