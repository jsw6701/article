package com.yourapp.news.admin

import com.yourapp.news.auth.*
import com.yourapp.news.card.CardStatus
import com.yourapp.news.card.CardViews
import com.yourapp.news.card.Cards
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Repository
class AdminStore(private val database: Database) {

    /**
     * 대시보드 요약 통계 조회
     */
    fun getDashboardSummary(): DashboardSummary = transaction(database) {
        val today = LocalDate.now()
        val todayStart = LocalDateTime.of(today, LocalTime.MIN)
        val todayEnd = LocalDateTime.of(today, LocalTime.MAX)

        val totalUsers = Users.selectAll().count()
        val todaySignups = Users.selectAll()
            .where { Users.createdAt greaterEq todayStart }
            .andWhere { Users.createdAt lessEq todayEnd }
            .count()

        val totalViews = CardViews.selectAll().count()
        val todayViews = CardViews.selectAll()
            .where { CardViews.viewedAt greaterEq todayStart }
            .andWhere { CardViews.viewedAt lessEq todayEnd }
            .count()

        val totalCards = Cards.selectAll().count()
        val activeCards = Cards.selectAll()
            .where { Cards.status eq CardStatus.ACTIVE.name }
            .count()

        DashboardSummary(
            totalUsers = totalUsers,
            todaySignups = todaySignups,
            totalViews = totalViews,
            todayViews = todayViews,
            totalCards = totalCards,
            activeCards = activeCards
        )
    }

    /**
     * 일별 가입자 통계 (최근 N일)
     */
    fun getDailySignupStats(days: Int = 30): List<DailySignupStats> = transaction(database) {
        val startDate = LocalDate.now().minusDays(days.toLong() - 1)
        val startDateTime = LocalDateTime.of(startDate, LocalTime.MIN)

        // 날짜별 그룹핑을 위해 raw query 사용
        val results = mutableMapOf<LocalDate, Long>()

        // 모든 날짜를 0으로 초기화
        for (i in 0 until days) {
            results[startDate.plusDays(i.toLong())] = 0
        }

        // 실제 데이터 조회
        Users.selectAll()
            .where { Users.createdAt greaterEq startDateTime }
            .forEach { row ->
                val date = row[Users.createdAt].toLocalDate()
                results[date] = (results[date] ?: 0) + 1
            }

        results.entries
            .sortedBy { it.key }
            .map { DailySignupStats(it.key, it.value) }
    }

    /**
     * 일별 조회수 통계 (최근 N일)
     */
    fun getDailyViewStats(days: Int = 30): List<DailyViewStats> = transaction(database) {
        val startDate = LocalDate.now().minusDays(days.toLong() - 1)
        val startDateTime = LocalDateTime.of(startDate, LocalTime.MIN)

        val results = mutableMapOf<LocalDate, Long>()

        for (i in 0 until days) {
            results[startDate.plusDays(i.toLong())] = 0
        }

        CardViews.selectAll()
            .where { CardViews.viewedAt greaterEq startDateTime }
            .forEach { row ->
                val date = row[CardViews.viewedAt].toLocalDate()
                results[date] = (results[date] ?: 0) + 1
            }

        results.entries
            .sortedBy { it.key }
            .map { DailyViewStats(it.key, it.value) }
    }

    /**
     * 성별 통계
     */
    fun getGenderStats(): GenderStats = transaction(database) {
        val male = Users.selectAll()
            .where { Users.gender eq Gender.MALE.name }
            .count()
        val female = Users.selectAll()
            .where { Users.gender eq Gender.FEMALE.name }
            .count()

        GenderStats(male = male, female = female)
    }

    /**
     * 연령대 통계
     */
    fun getAgeGroupStats(): List<AgeGroupStats> = transaction(database) {
        AgeGroup.entries.map { ageGroup ->
            val count = Users.selectAll()
                .where { Users.ageGroup eq ageGroup.name }
                .count()
            AgeGroupStats(
                ageGroup = ageGroup.name,
                displayName = ageGroup.displayName,
                count = count
            )
        }
    }

    /**
     * 전체 사용자 목록 조회 (페이지네이션)
     */
    fun getUsers(page: Int, size: Int, search: String? = null): UserListResponse = transaction(database) {
        val offset = (page - 1) * size

        var query = Users.selectAll()

        if (!search.isNullOrBlank()) {
            query = query.where { Users.username like "%$search%" }
        }

        val total = query.count()
        val totalPages = ((total + size - 1) / size).toInt()

        val items = query
            .orderBy(Users.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(size)
            .offset(offset.toLong())
            .map { it.toUserListItem() }

        UserListResponse(
            items = items,
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * 사용자 역할 변경
     */
    fun updateUserRole(userId: Long, role: UserRole): Boolean = transaction(database) {
        Users.update({ Users.id eq userId }) {
            it[Users.role] = role.name
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * 사용자 삭제
     */
    fun deleteUser(userId: Long): Boolean = transaction(database) {
        // RefreshToken 먼저 삭제
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        // 사용자 삭제
        Users.deleteWhere { Users.id eq userId } > 0
    }

    private fun ResultRow.toUserListItem(): UserListItem {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return UserListItem(
            id = this[Users.id],
            username = this[Users.username],
            email = this[Users.email], // 암호화된 상태로 전송 (프론트에서 마스킹 처리)
            gender = Gender.valueOf(this[Users.gender]).displayName,
            ageGroup = AgeGroup.valueOf(this[Users.ageGroup]).displayName,
            role = this[Users.role],
            emailVerified = this[Users.emailVerified],
            createdAt = this[Users.createdAt].format(formatter)
        )
    }
}
