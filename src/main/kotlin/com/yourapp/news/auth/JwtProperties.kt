package com.yourapp.news.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "your-256-bit-secret-key-here-must-be-at-least-32-characters",
    val accessTokenExpireMs: Long = 1800000,      // 30분
    val refreshTokenExpireMs: Long = 604800000    // 7일
)
