package com.yourapp.news.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class AuthService(
    private val userStore: UserStore,
    private val refreshTokenStore: RefreshTokenStore,
    private val emailVerificationStore: EmailVerificationStore,
    private val emailService: EmailService,
    private val emailEncryptor: EmailEncryptor,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProperties: JwtProperties,
    @Value("\${email.verification.expire-minutes:10}") private val verificationExpireMinutes: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이메일 인증 코드 발송
     */
    fun sendEmailVerification(request: SendEmailVerificationRequest): SendEmailVerificationResult {
        // 1. 이메일 형식 검증
        if (!isValidEmail(request.email)) {
            return SendEmailVerificationResult.failure("유효하지 않은 이메일 형식입니다.")
        }

        // 2. 이메일 암호화 (검색용 해시)
        val emailHash = emailEncryptor.hash(request.email)

        // 3. 이미 등록된 이메일인지 확인
        if (userStore.existsByEmail(emailHash)) {
            return SendEmailVerificationResult.failure("이미 등록된 이메일입니다.")
        }

        // 4. 인증 코드 생성
        val code = emailService.generateVerificationCode()

        // 5. 인증 코드 저장 (암호화된 이메일 + 만료시간)
        val expiresAt = LocalDateTime.now().plusMinutes(verificationExpireMinutes.toLong())
        emailVerificationStore.save(emailHash, code, expiresAt)

        // 6. 이메일 발송
        val sent = emailService.sendVerificationEmail(request.email, code)
        if (!sent) {
            return SendEmailVerificationResult.failure("이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.")
        }

        log.info("Verification email sent: email={}", emailEncryptor.hash(request.email).take(10) + "...")

        return SendEmailVerificationResult.success(verificationExpireMinutes)
    }

    /**
     * 이메일 인증 코드 확인
     */
    fun verifyEmail(request: VerifyEmailRequest): VerifyEmailResult {
        // 1. 이메일 해시
        val emailHash = emailEncryptor.hash(request.email)

        // 2. 인증 코드 조회
        val verification = emailVerificationStore.findByEmailAndCode(emailHash, request.code)
            ?: return VerifyEmailResult.failure("잘못된 인증 코드입니다.")

        // 3. 만료 확인
        if (verification.expiresAt.isBefore(LocalDateTime.now())) {
            return VerifyEmailResult.failure("인증 코드가 만료되었습니다. 다시 발송해주세요.")
        }

        // 4. 이미 인증된 코드인지 확인
        if (verification.verified) {
            return VerifyEmailResult.success()
        }

        // 5. 인증 완료 처리
        emailVerificationStore.markAsVerified(verification.id!!)

        log.info("Email verified: emailHash={}", emailHash.take(10) + "...")

        return VerifyEmailResult.success()
    }

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

        // 4. 이메일 검증
        if (!isValidEmail(request.email)) {
            return SignUpResult.failure("유효하지 않은 이메일 형식입니다.")
        }

        // 5. 이메일 해시
        val emailHash = emailEncryptor.hash(request.email)

        // 6. 이메일 인증 여부 확인
        val verifiedEmail = emailVerificationStore.findVerifiedByEmail(emailHash)
        if (verifiedEmail == null) {
            return SignUpResult.failure("이메일 인증이 필요합니다.")
        }

        // 7. 이메일 중복 체크
        if (userStore.existsByEmail(emailHash)) {
            return SignUpResult.failure("이미 등록된 이메일입니다.")
        }

        // 8. 성별 파싱
        val gender = try {
            Gender.valueOf(request.gender.uppercase())
        } catch (e: Exception) {
            return SignUpResult.failure("유효하지 않은 성별입니다. (MALE 또는 FEMALE)")
        }

        // 9. 나이대 파싱
        val ageGroup = try {
            AgeGroup.valueOf(request.ageGroup.uppercase())
        } catch (e: Exception) {
            return SignUpResult.failure("유효하지 않은 나이대입니다.")
        }

        // 10. 사용자 저장
        val encodedPassword = passwordEncoder.encode(request.password)
            ?: return SignUpResult.failure("비밀번호 암호화에 실패했습니다.")

        val user = User(
            username = request.username,
            password = encodedPassword,
            email = emailHash,      // 해시된 이메일 저장
            emailVerified = true,   // 인증 완료
            gender = gender,
            ageGroup = ageGroup
        )

        val userId = userStore.insert(user)

        // 11. 인증 코드 삭제
        emailVerificationStore.deleteByEmail(emailHash)

        log.info("New user registered: username={}, userId={}", request.username, userId)

        return SignUpResult.success(userId, request.username)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return email.matches(emailRegex) && email.length <= 255
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
    val email: String,       // 이메일 (인증 완료된 이메일)
    val gender: String,      // MALE, FEMALE
    val ageGroup: String     // TEENS, TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTIES_PLUS
)

// ========== Email Verification DTOs ==========

data class SendEmailVerificationRequest(
    val email: String
)

data class SendEmailVerificationResult(
    val success: Boolean,
    val expireMinutes: Int?,
    val error: String?
) {
    companion object {
        fun success(expireMinutes: Int) = SendEmailVerificationResult(true, expireMinutes, null)
        fun failure(error: String) = SendEmailVerificationResult(false, null, error)
    }
}

data class VerifyEmailRequest(
    val email: String,
    val code: String
)

data class VerifyEmailResult(
    val success: Boolean,
    val error: String?
) {
    companion object {
        fun success() = VerifyEmailResult(true, null)
        fun failure(error: String) = VerifyEmailResult(false, error)
    }
}

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
