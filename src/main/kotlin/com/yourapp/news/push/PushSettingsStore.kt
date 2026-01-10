package com.yourapp.news.push

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PushSettingsStore(private val database: Database) {

    /**
     * 사용자 푸시 설정 조회
     */
    fun findByUserId(userId: Long): PushSettings? = transaction(database) {
        PushSettingsTable.selectAll()
            .where { PushSettingsTable.userId eq userId }
            .singleOrNull()
            ?.toPushSettings()
    }

    /**
     * 푸시 설정 저장 (없으면 생성, 있으면 업데이트)
     */
    fun upsert(settings: PushSettings): PushSettings = transaction(database) {
        val existing = PushSettingsTable.selectAll()
            .where { PushSettingsTable.userId eq settings.userId }
            .singleOrNull()

        if (existing == null) {
            // Insert
            val id = PushSettingsTable.insert {
                it[userId] = settings.userId
                it[enabled] = settings.enabled
                it[breakingNews] = settings.breakingNews
                it[bookmarkUpdates] = settings.bookmarkUpdates
                it[dailyBriefing] = settings.dailyBriefing
                it[trendingAlerts] = settings.trendingAlerts
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            } get PushSettingsTable.id

            settings.copy(id = id)
        } else {
            // Update
            PushSettingsTable.update({ PushSettingsTable.userId eq settings.userId }) {
                it[enabled] = settings.enabled
                it[breakingNews] = settings.breakingNews
                it[bookmarkUpdates] = settings.bookmarkUpdates
                it[dailyBriefing] = settings.dailyBriefing
                it[trendingAlerts] = settings.trendingAlerts
                it[updatedAt] = LocalDateTime.now()
            }

            settings.copy(id = existing[PushSettingsTable.id])
        }
    }

    /**
     * 특정 알림 유형이 활성화된 사용자 ID 목록 조회
     */
    fun findUserIdsWithBreakingNewsEnabled(): List<Long> = transaction(database) {
        PushSettingsTable.selectAll()
            .where { (PushSettingsTable.enabled eq true) and (PushSettingsTable.breakingNews eq true) }
            .map { it[PushSettingsTable.userId] }
    }

    fun findUserIdsWithTrendingAlertsEnabled(): List<Long> = transaction(database) {
        PushSettingsTable.selectAll()
            .where { (PushSettingsTable.enabled eq true) and (PushSettingsTable.trendingAlerts eq true) }
            .map { it[PushSettingsTable.userId] }
    }

    fun findUserIdsWithDailyBriefingEnabled(): List<Long> = transaction(database) {
        PushSettingsTable.selectAll()
            .where { (PushSettingsTable.enabled eq true) and (PushSettingsTable.dailyBriefing eq true) }
            .map { it[PushSettingsTable.userId] }
    }

    fun findUserIdsWithBookmarkUpdatesEnabled(): List<Long> = transaction(database) {
        PushSettingsTable.selectAll()
            .where { (PushSettingsTable.enabled eq true) and (PushSettingsTable.bookmarkUpdates eq true) }
            .map { it[PushSettingsTable.userId] }
    }

    private fun ResultRow.toPushSettings(): PushSettings = PushSettings(
        id = this[PushSettingsTable.id],
        userId = this[PushSettingsTable.userId],
        enabled = this[PushSettingsTable.enabled],
        breakingNews = this[PushSettingsTable.breakingNews],
        bookmarkUpdates = this[PushSettingsTable.bookmarkUpdates],
        dailyBriefing = this[PushSettingsTable.dailyBriefing],
        trendingAlerts = this[PushSettingsTable.trendingAlerts],
        createdAt = this[PushSettingsTable.createdAt],
        updatedAt = this[PushSettingsTable.updatedAt]
    )
}
