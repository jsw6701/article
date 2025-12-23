package com.yourapp.news.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    /**
     * Access Token 생성
     */
    fun createAccessToken(userId: Long, username: String, role: UserRole): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenExpireMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role.name)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact()
    }

    /**
     * Refresh Token 생성
     */
    fun createRefreshToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.refreshTokenExpireMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact()
    }

    /**
     * 토큰에서 userId 추출
     */
    fun getUserId(token: String): Long? {
        return try {
            val claims = parseClaims(token)
            claims.subject.toLongOrNull()
        } catch (e: Exception) {
            log.debug("Failed to extract userId from token: {}", e.message)
            null
        }
    }

    /**
     * 토큰에서 username 추출
     */
    fun getUsername(token: String): String? {
        return try {
            val claims = parseClaims(token)
            claims["username"] as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 토큰에서 role 추출
     */
    fun getRole(token: String): UserRole? {
        return try {
            val claims = parseClaims(token)
            val roleName = claims["role"] as? String
            roleName?.let { UserRole.valueOf(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            !claims.expiration.before(Date())
        } catch (e: ExpiredJwtException) {
            log.debug("Token expired: {}", e.message)
            false
        } catch (e: Exception) {
            log.debug("Invalid token: {}", e.message)
            false
        }
    }

    /**
     * Access Token인지 확인
     */
    fun isAccessToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            claims["type"] == "access"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Refresh Token인지 확인
     */
    fun isRefreshToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            claims["type"] == "refresh"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰 만료 시간 조회
     */
    fun getExpiration(token: String): Date? {
        return try {
            parseClaims(token).expiration
        } catch (e: Exception) {
            null
        }
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
