package com.yourapp.news.auth

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class UserSettingsStore(private val database: Database) {

    /**
     * 사용자 설정 조회
     */
    fun findByUserId(userId: Long): UserSettings? = transaction(database) {
        UserSettingsTable.selectAll()
            .where { UserSettingsTable.userId eq userId }
            .singleOrNull()
            ?.toUserSettings()
    }

    /**
     * 사용자 설정 저장 (없으면 생성, 있으면 업데이트)
     */
    fun upsert(settings: UserSettings): UserSettings = transaction(database) {
        val existing = UserSettingsTable.selectAll()
            .where { UserSettingsTable.userId eq settings.userId }
            .singleOrNull()

        if (existing == null) {
            // Insert
            val id = UserSettingsTable.insert {
                it[userId] = settings.userId
                it[theme] = settings.theme.name
                it[fontSize] = settings.fontSize.name
                it[startPage] = settings.startPage.name
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            } get UserSettingsTable.id

            settings.copy(id = id)
        } else {
            // Update
            UserSettingsTable.update({ UserSettingsTable.userId eq settings.userId }) {
                it[theme] = settings.theme.name
                it[fontSize] = settings.fontSize.name
                it[startPage] = settings.startPage.name
                it[updatedAt] = LocalDateTime.now()
            }

            settings.copy(id = existing[UserSettingsTable.id])
        }
    }

    private fun ResultRow.toUserSettings(): UserSettings = UserSettings(
        id = this[UserSettingsTable.id],
        userId = this[UserSettingsTable.userId],
        theme = Theme.valueOf(this[UserSettingsTable.theme]),
        fontSize = FontSize.valueOf(this[UserSettingsTable.fontSize]),
        startPage = StartPage.valueOf(this[UserSettingsTable.startPage])
    )
}
