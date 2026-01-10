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
     * 비밀번호 변경
     */
    @PutMapping("/me/password")
    fun changePassword(
        authentication: Authentication,
        @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<ChangePasswordResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                ChangePasswordResponse(success = false, message = "인증 정보가 유효하지 않습니다.")
            )
        val userId = principal.userId

        val user = userStore.findById(userId)
            ?: return ResponseEntity.status(404).body(
                ChangePasswordResponse(success = false, message = "사용자를 찾을 수 없습니다.")
            )

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            return ResponseEntity.badRequest().body(
                ChangePasswordResponse(success = false, message = "현재 비밀번호가 일치하지 않습니다.")
            )
        }

        // 새 비밀번호 유효성 검사 (8자 이상, 숫자 포함, 특수문자 포함)
        val passwordValidation = validatePassword(request.newPassword)
        if (!passwordValidation.isValid) {
            return ResponseEntity.badRequest().body(
                ChangePasswordResponse(success = false, message = passwordValidation.message)
            )
        }

        // 새 비밀번호가 현재 비밀번호와 같은지 확인
        if (passwordEncoder.matches(request.newPassword, user.password)) {
            return ResponseEntity.badRequest().body(
                ChangePasswordResponse(success = false, message = "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
            )
        }

        // 비밀번호 변경
        val encodedPassword = passwordEncoder.encode(request.newPassword)!!
        val updated = userStore.updatePassword(userId, encodedPassword)

        if (updated) {
            log.info("Password changed for user: userId={}", userId)
            return ResponseEntity.ok(
                ChangePasswordResponse(success = true, message = "비밀번호가 변경되었습니다.")
            )
        } else {
            return ResponseEntity.status(500).body(
                ChangePasswordResponse(success = false, message = "비밀번호 변경 중 오류가 발생했습니다.")
            )
        }
    }

    /**
     * 프로필 수정
     */
    @PutMapping("/me/profile")
    fun updateProfile(
        authentication: Authentication,
        @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UpdateProfileResponse> {
        val principal = authentication.principal as? UserPrincipal
            ?: return ResponseEntity.status(401).body(
                UpdateProfileResponse(success = false, message = "인증 정보가 유효하지 않습니다.")
            )
        val userId = principal.userId

        val user = userStore.findById(userId)
            ?: return ResponseEntity.status(404).body(
                UpdateProfileResponse(success = false, message = "사용자를 찾을 수 없습니다.")
            )

        // 성별 파싱
        val gender = try {
            Gender.valueOf(request.gender.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                UpdateProfileResponse(success = false, message = "유효하지 않은 성별입니다.")
            )
        }

        // 연령대 파싱
        val ageGroup = try {
            AgeGroup.valueOf(request.ageGroup.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                UpdateProfileResponse(success = false, message = "유효하지 않은 연령대입니다.")
            )
        }

        // 프로필 업데이트
        val updated = userStore.updateProfile(userId, gender, ageGroup)

        if (updated) {
            log.info("Profile updated for user: userId={}, gender={}, ageGroup={}", userId, gender, ageGroup)
            return ResponseEntity.ok(
                UpdateProfileResponse(success = true, message = "프로필이 수정되었습니다.")
            )
        } else {
            return ResponseEntity.status(500).body(
                UpdateProfileResponse(success = false, message = "프로필 수정 중 오류가 발생했습니다.")
            )
        }
    }

    private fun validatePassword(password: String): PasswordValidationResult {
        if (password.length < 8) {
            return PasswordValidationResult(false, "비밀번호는 8자 이상이어야 합니다.")
        }
        if (!password.any { it.isDigit() }) {
            return PasswordValidationResult(false, "비밀번호에 숫자가 포함되어야 합니다.")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return PasswordValidationResult(false, "비밀번호에 특수문자가 포함되어야 합니다.")
        }
        return PasswordValidationResult(true, "")
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

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class ChangePasswordResponse(
    val success: Boolean,
    val message: String
)

data class UpdateProfileRequest(
    val gender: String,
    val ageGroup: String
)

data class UpdateProfileResponse(
    val success: Boolean,
    val message: String
)

data class PasswordValidationResult(
    val isValid: Boolean,
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
