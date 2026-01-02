package com.yourapp.news.auth

/**
 * 사용자 설정 도메인 객체
 */
data class UserSettings(
    val id: Long? = null,
    val userId: Long,
    val theme: Theme = Theme.DARK,
    val fontSize: FontSize = FontSize.MEDIUM,
    val startPage: StartPage = StartPage.HOME
)

/**
 * 테마
 */
enum class Theme {
    LIGHT, DARK
}

/**
 * 글꼴 크기
 */
enum class FontSize {
    SMALL, MEDIUM, LARGE
}

/**
 * 시작 페이지
 */
enum class StartPage {
    HOME, FEED, TRENDING
}
