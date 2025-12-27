package com.yourapp.news.admin

import com.yourapp.news.auth.UserGrade
import com.yourapp.news.auth.UserRole
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val adminStore: AdminStore
) {

    /**
     * 대시보드 요약 통계
     */
    @GetMapping("/dashboard")
    fun getDashboard(): DashboardSummary {
        return adminStore.getDashboardSummary()
    }

    /**
     * 일별 가입자 통계
     */
    @GetMapping("/stats/signups")
    fun getSignupStats(
        @RequestParam(defaultValue = "30") days: Int
    ): List<DailySignupStats> {
        if (days < 1 || days > 365) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "days는 1~365 사이여야 합니다")
        }
        return adminStore.getDailySignupStats(days)
    }

    /**
     * 일별 조회수 통계
     */
    @GetMapping("/stats/views")
    fun getViewStats(
        @RequestParam(defaultValue = "30") days: Int
    ): List<DailyViewStats> {
        if (days < 1 || days > 365) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "days는 1~365 사이여야 합니다")
        }
        return adminStore.getDailyViewStats(days)
    }

    /**
     * 성별 통계
     */
    @GetMapping("/stats/gender")
    fun getGenderStats(): GenderStats {
        return adminStore.getGenderStats()
    }

    /**
     * 연령대 통계
     */
    @GetMapping("/stats/age-groups")
    fun getAgeGroupStats(): List<AgeGroupStats> {
        return adminStore.getAgeGroupStats()
    }

    /**
     * 회원 등급별 통계
     */
    @GetMapping("/stats/grades")
    fun getGradeStats(): List<GradeStats> {
        return adminStore.getGradeStats()
    }

    /**
     * 등급 목록 조회 (프론트에서 선택지로 사용)
     */
    @GetMapping("/grades")
    fun getGrades(): List<GradeInfo> {
        return UserGrade.entries.map { grade ->
            GradeInfo(
                grade = grade.name,
                displayName = grade.displayName,
                level = grade.level,
                description = grade.description
            )
        }
    }

    /**
     * 사용자 목록 조회
     */
    @GetMapping("/users")
    fun getUsers(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) search: String?
    ): UserListResponse {
        if (page < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 1 이상이어야 합니다")
        }
        if (size < 1 || size > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size는 1~100 사이여야 합니다")
        }
        return adminStore.getUsers(page, size, search)
    }

    /**
     * 사용자 역할 변경
     */
    @PatchMapping("/users/{userId}/role")
    fun updateUserRole(
        @PathVariable userId: Long,
        @RequestBody request: UpdateRoleRequest
    ): Map<String, Any> {
        val role = try {
            UserRole.valueOf(request.role.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다: ${request.role}")
        }

        val success = adminStore.updateUserRole(userId, role)
        if (!success) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다")
        }

        return mapOf("success" to true, "message" to "역할이 변경되었습니다")
    }

    /**
     * 사용자 삭제
     */
    @DeleteMapping("/users/{userId}")
    fun deleteUser(@PathVariable userId: Long): Map<String, Any> {
        val success = adminStore.deleteUser(userId)
        if (!success) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다")
        }

        return mapOf("success" to true, "message" to "사용자가 삭제되었습니다")
    }

    /**
     * 사용자 등급 변경
     */
    @PatchMapping("/users/{userId}/grade")
    fun updateUserGrade(
        @PathVariable userId: Long,
        @RequestBody request: UpdateGradeRequest
    ): Map<String, Any> {
        val grade = try {
            UserGrade.valueOf(request.grade.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 등급입니다: ${request.grade}")
        }

        val success = adminStore.updateUserGrade(userId, grade)
        if (!success) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다")
        }

        return mapOf(
            "success" to true,
            "message" to "등급이 ${grade.displayName}(으)로 변경되었습니다"
        )
    }
}

data class UpdateRoleRequest(
    val role: String
)

data class UpdateGradeRequest(
    val grade: String
)

data class GradeInfo(
    val grade: String,
    val displayName: String,
    val level: Int,
    val description: String
)
