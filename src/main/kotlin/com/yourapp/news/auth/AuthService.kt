package com.yourapp.news.auth

import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class AuthService(
    private val userStore: UserStore,
    private val refreshTokenStore: RefreshTokenStore,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProperties: JwtProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 회원가입
     */
    fun signUp(request: SignUpRequest): SignUpResult {
        // 1. 아이디 중복 체크
        if (userStore.existsByUsername(request.username)) {
            return SignUpResult.failure("이미 사용 중인 아이디입니다.")
        }

        // 2. 아이디 유효성 검사 (4~20자, 영문+숫자)
        if (!isValidUsername(request.username)) {
            return SignUpResult.failure("아이디는 4~20자의 영문, 숫자만 사용 가능합니다.")
        }

        // 3. 비밀번호 유효성 검사 (8자 이상)
        if (request.password.length < 8) {
            return SignUpResult.failure("비밀번호는 8자 이상이어야 합니다.")
        }

        // 4. 성별 파싱
        val gender = try {
            Gender.valueOf(request.gender.uppercase())
        } catch (e: Exception) {
            return SignUpResult.failure("유효하지 않은 성별입니다. (MALE 또는 FEMALE)")
        }

        // 5. 나이대 파싱
        val ageGroup = try {
            AgeGroup.valueOf(request.ageGroup.uppercase())
        } catch (e: Exception) {
            return SignUpResult.failure("유효하지 않은 나이대입니다.")
        }

        // 6. 사용자 저장
        val encodedPassword = passwordEncoder.encode(request.password)
            ?: return SignUpResult.failure("비밀번호 암호화에 실패했습니다.")

        val user = User(
            username = request.username,
            password = encodedPassword,
            gender = gender,
            ageGroup = ageGroup
        )

        val userId = userStore.insert(user)
        log.info("New user registered: username={}, userId={}", request.username, userId)

        return SignUpResult.success(userId, request.username)
    }

    /**
     * 아이디 중복 체크
     */
    fun checkUsernameAvailable(username: String): UsernameCheckResult {
        if (!isValidUsername(username)) {
            return UsernameCheckResult(
                available = false,
                message = "아이디는 4~20자의 영문, 숫자만 사용 가능합니다."
            )
        }

        val exists = userStore.existsByUsername(username)
        return UsernameCheckResult(
            available = !exists,
            message = if (exists) "이미 사용 중인 아이디입니다." else "사용 가능한 아이디입니다."
        )
    }

    /**
     * 로그인
     */
    fun login(request: LoginRequest): LoginResult {
        // 1. 사용자 조회
        val user = userStore.findByUsername(request.username)
            ?: return LoginResult.failure("아이디 또는 비밀번호가 올바르지 않습니다.")

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.password, user.password)) {
            return LoginResult.failure("아이디 또는 비밀번호가 올바르지 않습니다.")
        }

        // 3. Access Token 생성
        val accessToken = jwtTokenProvider.createAccessToken(user.id!!, user.username, user.role)

        // 4. Refresh Token 생성 및 저장
        val refreshToken = jwtTokenProvider.createRefreshToken(user.id)
        val refreshTokenExpiry = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(System.currentTimeMillis() + jwtProperties.refreshTokenExpireMs),
            ZoneId.systemDefault()
        )
        refreshTokenStore.save(user.id, refreshToken, refreshTokenExpiry)

        log.info("User logged in: username={}", user.username)

        return LoginResult.success(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            username = user.username
        )
    }

    /**
     * 토큰 갱신
     */
    fun refreshToken(refreshToken: String): RefreshResult {
        // 1. Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return RefreshResult.failure("유효하지 않거나 만료된 토큰입니다.")
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            return RefreshResult.failure("Refresh Token이 아닙니다.")
        }

        // 2. DB에서 토큰 조회
        val storedToken = refreshTokenStore.findByToken(refreshToken)
            ?: return RefreshResult.failure("등록되지 않은 토큰입니다.")

        // 3. 사용자 조회
        val user = userStore.findById(storedToken.userId)
            ?: return RefreshResult.failure("사용자를 찾을 수 없습니다.")

        // 4. 새 Access Token 발급
        val newAccessToken = jwtTokenProvider.createAccessToken(user.id!!, user.username, user.role)

        // 5. Refresh Token Rotation (선택적: 새 Refresh Token 발급)
        // 기존 토큰 삭제
        refreshTokenStore.deleteByToken(refreshToken)

        // 새 Refresh Token 생성
        val newRefreshToken = jwtTokenProvider.createRefreshToken(user.id)
        val refreshTokenExpiry = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(System.currentTimeMillis() + jwtProperties.refreshTokenExpireMs),
            ZoneId.systemDefault()
        )
        refreshTokenStore.save(user.id, newRefreshToken, refreshTokenExpiry)

        log.info("Token refreshed for user: {}", user.username)

        return RefreshResult.success(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }

    /**
     * 로그아웃 (Refresh Token 삭제)
     */
    fun logout(userId: Long): Boolean {
        val deleted = refreshTokenStore.deleteAllByUserId(userId)
        log.info("User logged out: userId={}, deletedTokens={}", userId, deleted)
        return deleted > 0
    }

    private fun isValidUsername(username: String): Boolean {
        return username.length in 4..20 && username.matches(Regex("^[a-zA-Z0-9]+$"))
    }
}

// ========== Request/Response DTOs ==========

data class SignUpRequest(
    val username: String,
    val password: String,
    val gender: String,      // MALE, FEMALE
    val ageGroup: String     // TEENS, TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTIES_PLUS
)

data class SignUpResult(
    val success: Boolean,
    val userId: Long?,
    val username: String?,
    val error: String?
) {
    companion object {
        fun success(userId: Long, username: String) = SignUpResult(true, userId, username, null)
        fun failure(error: String) = SignUpResult(false, null, null, error)
    }
}

data class UsernameCheckResult(
    val available: Boolean,
    val message: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResult(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: Long?,
    val username: String?,
    val error: String?
) {
    companion object {
        fun success(accessToken: String, refreshToken: String, userId: Long, username: String) =
            LoginResult(true, accessToken, refreshToken, userId, username, null)
        fun failure(error: String) = LoginResult(false, null, null, null, null, error)
    }
}

data class RefreshResult(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val error: String?
) {
    companion object {
        fun success(accessToken: String, refreshToken: String) =
            RefreshResult(true, accessToken, refreshToken, null)
        fun failure(error: String) = RefreshResult(false, null, null, error)
    }
}
