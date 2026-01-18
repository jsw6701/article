package com.yourapp.news.push

import com.yourapp.news.auth.UserPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/push")
class PushController(
    private val pushTokenStore: PushTokenStore,
    private val pushSettingsStore: PushSettingsStore,
    private val pushService: PushService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 푸시 토큰 등록
     */
    @PostMapping("/token")
    fun registerToken(
        authentication: Authentication,
        @RequestBody request: RegisterTokenRequest
    ): ResponseEntity<TokenResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                TokenResponse(success = false, message = "인증 정보가 유효하지 않습니다.")
            )

        try {
            val platform = try {
                Platform.valueOf(request.platform.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(
                    TokenResponse(success = false, message = "유효하지 않은 플랫폼입니다.")
                )
            }

            val pushToken = PushToken(
                userId = principal.userId,
                token = request.token,
                platform = platform
            )

            pushTokenStore.save(pushToken)
            log.info("Push token registered: userId={}, platform={}", principal.userId, platform)

            return ResponseEntity.ok(
                TokenResponse(success = true, message = "토큰이 등록되었습니다.")
            )
        } catch (e: Exception) {
            log.error("Failed to register push token: {}", e.message, e)
            return ResponseEntity.status(500).body(
                TokenResponse(success = false, message = "토큰 등록 중 오류가 발생했습니다.")
            )
        }
    }

    /**
     * 푸시 토큰 해제
     */
    @DeleteMapping("/token")
    fun unregisterToken(
        authentication: Authentication,
        @RequestBody request: UnregisterTokenRequest
    ): ResponseEntity<TokenResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                TokenResponse(success = false, message = "인증 정보가 유효하지 않습니다.")
            )

        try {
            val deleted = pushTokenStore.deleteByUserIdAndToken(principal.userId, request.token)

            if (deleted) {
                log.info("Push token unregistered: userId={}", principal.userId)
                return ResponseEntity.ok(
                    TokenResponse(success = true, message = "토큰이 해제되었습니다.")
                )
            } else {
                return ResponseEntity.ok(
                    TokenResponse(success = true, message = "토큰이 이미 해제되어 있습니다.")
                )
            }
        } catch (e: Exception) {
            log.error("Failed to unregister push token: {}", e.message, e)
            return ResponseEntity.status(500).body(
                TokenResponse(success = false, message = "토큰 해제 중 오류가 발생했습니다.")
            )
        }
    }

    /**
     * 푸시 설정 조회
     */
    @GetMapping("/settings")
    fun getSettings(authentication: Authentication): ResponseEntity<PushSettingsResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                PushSettingsResponse(success = false, data = null, message = "인증 정보가 유효하지 않습니다.")
            )

        val settings = pushSettingsStore.findByUserId(principal.userId)
        val dto = settings?.toDto() ?: PushSettingsDto(
            enabled = true,
            breakingNews = true,
            bookmarkUpdates = true,
            dailyBriefing = false,
            trendingAlerts = true
        )

        return ResponseEntity.ok(
            PushSettingsResponse(success = true, data = dto, message = null)
        )
    }

    /**
     * 푸시 설정 업데이트
     */
    @PutMapping("/settings")
    fun updateSettings(
        authentication: Authentication,
        @RequestBody request: UpdatePushSettingsRequest
    ): ResponseEntity<PushSettingsResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                PushSettingsResponse(success = false, data = null, message = "인증 정보가 유효하지 않습니다.")
            )

        try {
            val settings = PushSettings(
                userId = principal.userId,
                enabled = request.enabled,
                breakingNews = request.breakingNews,
                bookmarkUpdates = request.bookmarkUpdates,
                dailyBriefing = request.dailyBriefing,
                trendingAlerts = request.trendingAlerts
            )

            pushSettingsStore.upsert(settings)
            log.info("Push settings updated: userId={}, enabled={}", principal.userId, request.enabled)

            return ResponseEntity.ok(
                PushSettingsResponse(success = true, data = settings.toDto(), message = "설정이 저장되었습니다.")
            )
        } catch (e: Exception) {
            log.error("Failed to update push settings: {}", e.message, e)
            return ResponseEntity.status(500).body(
                PushSettingsResponse(success = false, data = null, message = "설정 저장 중 오류가 발생했습니다.")
            )
        }
    }

    /**
     * 테스트 푸시 알림 발송 (본인에게)
     */
    @PostMapping("/test")
    fun sendTestPush(
        authentication: Authentication,
        @RequestBody(required = false) request: TestPushRequest?
    ): ResponseEntity<TestPushResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                TestPushResponse(success = false, message = "인증 정보가 유효하지 않습니다.", sentCount = 0)
            )

        try {
            val title = request?.title ?: "🔔 SHIFT 테스트 알림"
            val body = request?.body ?: "푸시 알림이 정상적으로 작동합니다!"
            val data = mapOf("route" to "/")

            val sentCount = pushService.sendToUser(principal.userId, title, body, data)

            return if (sentCount > 0) {
                log.info("Test push sent: userId={}, sentCount={}", principal.userId, sentCount)
                ResponseEntity.ok(
                    TestPushResponse(success = true, message = "테스트 알림이 발송되었습니다.", sentCount = sentCount)
                )
            } else {
                ResponseEntity.ok(
                    TestPushResponse(success = false, message = "등록된 푸시 토큰이 없거나 Firebase가 비활성화되어 있습니다.", sentCount = 0)
                )
            }
        } catch (e: Exception) {
            log.error("Failed to send test push: {}", e.message, e)
            return ResponseEntity.status(500).body(
                TestPushResponse(success = false, message = "테스트 알림 발송 중 오류: ${e.message}", sentCount = 0)
            )
        }
    }
}

// ========== DTOs ==========

data class RegisterTokenRequest(
    val token: String,
    val platform: String  // android, ios, web
)

data class UnregisterTokenRequest(
    val token: String
)

data class TokenResponse(
    val success: Boolean,
    val message: String?
)

data class PushSettingsDto(
    val enabled: Boolean,
    val breakingNews: Boolean,
    val bookmarkUpdates: Boolean,
    val dailyBriefing: Boolean,
    val trendingAlerts: Boolean
)

data class PushSettingsResponse(
    val success: Boolean,
    val data: PushSettingsDto?,
    val message: String?
)

data class UpdatePushSettingsRequest(
    val enabled: Boolean,
    val breakingNews: Boolean,
    val bookmarkUpdates: Boolean,
    val dailyBriefing: Boolean,
    val trendingAlerts: Boolean
)

data class TestPushRequest(
    val title: String?,
    val body: String?
)

data class TestPushResponse(
    val success: Boolean,
    val message: String?,
    val sentCount: Int
)

fun PushSettings.toDto(): PushSettingsDto = PushSettingsDto(
    enabled = enabled,
    breakingNews = breakingNews,
    bookmarkUpdates = bookmarkUpdates,
    dailyBriefing = dailyBriefing,
    trendingAlerts = trendingAlerts
)
