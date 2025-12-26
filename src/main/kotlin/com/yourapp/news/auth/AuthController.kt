package com.yourapp.news.auth

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    /**
     * 이메일 인증 코드 발송
     */
    @PostMapping("/email/send-verification")
    fun sendEmailVerification(@RequestBody request: SendEmailVerificationRequest): ResponseEntity<SendEmailVerificationResponse> {
        val result = authService.sendEmailVerification(request)
        return if (result.success) {
            ResponseEntity.ok(
                SendEmailVerificationResponse(
                    success = true,
                    expireMinutes = result.expireMinutes,
                    message = "인증 코드가 발송되었습니다."
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                SendEmailVerificationResponse(
                    success = false,
                    expireMinutes = null,
                    message = result.error
                )
            )
        }
    }

    /**
     * 이메일 인증 코드 확인
     */
    @PostMapping("/email/verify")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ResponseEntity<VerifyEmailResponse> {
        val result = authService.verifyEmail(request)
        return if (result.success) {
            ResponseEntity.ok(
                VerifyEmailResponse(
                    success = true,
                    message = "이메일 인증이 완료되었습니다."
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                VerifyEmailResponse(
                    success = false,
                    message = result.error
                )
            )
        }
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    fun signUp(@RequestBody request: SignUpRequest): ResponseEntity<SignUpResponse> {
        val result = authService.signUp(request)
        return if (result.success) {
            ResponseEntity.ok(
                SignUpResponse(
                    success = true,
                    userId = result.userId,
                    username = result.username,
                    message = "회원가입이 완료되었습니다."
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                SignUpResponse(
                    success = false,
                    userId = null,
                    username = null,
                    message = result.error
                )
            )
        }
    }

    /**
     * 아이디 중복 체크
     */
    @GetMapping("/check-username")
    fun checkUsername(@RequestParam username: String): ResponseEntity<UsernameCheckResponse> {
        val result = authService.checkUsernameAvailable(username)
        return ResponseEntity.ok(
            UsernameCheckResponse(
                available = result.available,
                message = result.message
            )
        )
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val result = authService.login(request)
        return if (result.success) {
            ResponseEntity.ok(
                LoginResponse(
                    success = true,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    userId = result.userId,
                    username = result.username,
                    message = "로그인 성공"
                )
            )
        } else {
            ResponseEntity.status(401).body(
                LoginResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    userId = null,
                    username = null,
                    message = result.error
                )
            )
        }
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<RefreshResponse> {
        val result = authService.refreshToken(request.refreshToken)
        return if (result.success) {
            ResponseEntity.ok(
                RefreshResponse(
                    success = true,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    message = "토큰이 갱신되었습니다."
                )
            )
        } else {
            ResponseEntity.status(401).body(
                RefreshResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    message = result.error
                )
            )
        }
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest): ResponseEntity<LogoutResponse> {
        val success = authService.logout(request.userId)
        return ResponseEntity.ok(
            LogoutResponse(
                success = success,
                message = if (success) "로그아웃되었습니다." else "이미 로그아웃된 상태입니다."
            )
        )
    }
}

// ========== API Response DTOs ==========

data class SendEmailVerificationResponse(
    val success: Boolean,
    val expireMinutes: Int?,
    val message: String?
)

data class VerifyEmailResponse(
    val success: Boolean,
    val message: String?
)

data class SignUpResponse(
    val success: Boolean,
    val userId: Long?,
    val username: String?,
    val message: String?
)

data class UsernameCheckResponse(
    val available: Boolean,
    val message: String
)

data class LoginResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: Long?,
    val username: String?,
    val message: String?
)

data class RefreshRequest(
    val refreshToken: String
)

data class RefreshResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val message: String?
)

data class LogoutRequest(
    val userId: Long
)

data class LogoutResponse(
    val success: Boolean,
    val message: String
)
