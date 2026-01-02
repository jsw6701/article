package com.yourapp.news.api

import com.yourapp.news.auth.*
import com.yourapp.news.bookmark.BookmarkStore
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userStore: UserStore,
    private val userSettingsStore: UserSettingsStore,
    private val bookmarkStore: BookmarkStore,
    private val refreshTokenStore: RefreshTokenStore,
    private val passwordEncoder: PasswordEncoder,
    private val emailEncryptor: EmailEncryptor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 내 프로필 조회
     */
    @GetMapping("/me")
    fun getMyProfile(authentication: Authentication): ResponseEntity<MyProfileResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                MyProfileResponse(
                    success = false,
                    data = null,
                    message = "인증 정보가 유효하지 않습니다."
                )
            )
        val userId = principal.userId

        val user = userStore.findById(userId)
            ?: return ResponseEntity.status(404).body(
                MyProfileResponse(
                    success = false,
                    data = null,
                    message = "사용자를 찾을 수 없습니다."
                )
            )

        val bookmarkCount = bookmarkStore.findByUserId(userId).size

        // 이메일 복호화
        val decryptedEmail = try {
            emailEncryptor.decrypt(user.email)
        } catch (e: Exception) {
            log.warn("Failed to decrypt email for user {}: {}", userId, e.message)
            user.email  // 복호화 실패 시 원본 반환
        }

        return ResponseEntity.ok(
            MyProfileResponse(
                success = true,
                data = MyProfileData(
                    id = user.id!!,
                    username = user.username,
                    email = decryptedEmail,
                    gender = user.gender.name,
                    ageGroup = user.ageGroup.name,
                    role = user.role.name,
                    grade = user.grade.name,
                    gradeDisplayName = user.grade.displayName,
                    gradeLevel = user.grade.level,
                    emailVerified = user.emailVerified,
                    createdAt = user.createdAt.toString(),
                    bookmarkCount = bookmarkCount
                ),
                message = null
            )
        )
    }

    /**
     * 내 설정 조회
     */
    @GetMapping("/settings")
    fun getMySettings(authentication: Authentication): ResponseEntity<UserSettingsResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                UserSettingsResponse(success = false, data = null, message = "인증 정보가 유효하지 않습니다.")
            )

        val settings = userSettingsStore.findByUserId(principal.userId)

        return ResponseEntity.ok(
            UserSettingsResponse(
                success = true,
                data = settings?.toDto() ?: UserSettingsDto(
                    theme = Theme.DARK.name.lowercase(),
                    fontSize = FontSize.MEDIUM.name.lowercase(),
                    startPage = StartPage.HOME.name.lowercase()
                ),
                message = null
            )
        )
    }

    /**
     * 내 설정 업데이트
     */
    @PutMapping("/settings")
    fun updateMySettings(
        authentication: Authentication,
        @RequestBody request: UpdateSettingsRequest
    ): ResponseEntity<UpdateSettingsResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                UpdateSettingsResponse(success = false, message = "인증 정보가 유효하지 않습니다.")
            )

        try {
            val theme = Theme.valueOf(request.theme.uppercase())
            val fontSize = FontSize.valueOf(request.fontSize.uppercase())
            val startPage = StartPage.valueOf(request.startPage.uppercase())

            val settings = UserSettings(
                userId = principal.userId,
                theme = theme,
                fontSize = fontSize,
                startPage = startPage
            )

            userSettingsStore.upsert(settings)

            log.info("User settings updated: userId={}, theme={}, fontSize={}, startPage={}",
                principal.userId, theme, fontSize, startPage)

            return ResponseEntity.ok(
                UpdateSettingsResponse(success = true, message = "설정이 저장되었습니다.")
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                UpdateSettingsResponse(success = false, message = "잘못된 설정 값입니다.")
            )
        }
    }

    /**
     * 회원 탈퇴
     */
    @DeleteMapping("/me")
    fun deleteMyAccount(
        authentication: Authentication,
        @RequestBody request: DeleteAccountRequest
    ): ResponseEntity<DeleteAccountResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                DeleteAccountResponse(
                    success = false,
                    message = "인증 정보가 유효하지 않습니다."
                )
            )
        val userId = principal.userId

        val user = userStore.findById(userId)
            ?: return ResponseEntity.status(404).body(
                DeleteAccountResponse(
                    success = false,
                    message = "사용자를 찾을 수 없습니다."
                )
            )

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.password, user.password)) {
            return ResponseEntity.badRequest().body(
                DeleteAccountResponse(
                    success = false,
                    message = "비밀번호가 일치하지 않습니다."
                )
            )
        }

        // Refresh Token 삭제
        refreshTokenStore.deleteAllByUserId(userId)

        // 사용자 삭제
        val deleted = userStore.deleteById(userId)

        if (deleted) {
            log.info("User account deleted: userId={}, username={}", userId, user.username)
            return ResponseEntity.ok(
                DeleteAccountResponse(
                    success = true,
                    message = "회원 탈퇴가 완료되었습니다."
                )
            )
        } else {
            return ResponseEntity.status(500).body(
                DeleteAccountResponse(
                    success = false,
                    message = "탈퇴 처리 중 오류가 발생했습니다."
                )
            )
        }
    }
}

// ========== DTOs ==========

data class MyProfileResponse(
    val success: Boolean,
    val data: MyProfileData?,
    val message: String?
)

data class MyProfileData(
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
    val createdAt: String,
    val bookmarkCount: Int
)

data class DeleteAccountRequest(
    val password: String
)

data class DeleteAccountResponse(
    val success: Boolean,
    val message: String
)

// ========== Settings DTOs ==========

data class UserSettingsDto(
    val theme: String,
    val fontSize: String,
    val startPage: String
)

data class UserSettingsResponse(
    val success: Boolean,
    val data: UserSettingsDto?,
    val message: String?
)

data class UpdateSettingsRequest(
    val theme: String,
    val fontSize: String,
    val startPage: String
)

data class UpdateSettingsResponse(
    val success: Boolean,
    val message: String
)

fun UserSettings.toDto(): UserSettingsDto = UserSettingsDto(
    theme = theme.name.lowercase(),
    fontSize = fontSize.name.lowercase(),
    startPage = startPage.name.lowercase()
)
