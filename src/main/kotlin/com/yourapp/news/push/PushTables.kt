package com.yourapp.news.push

import com.yourapp.news.auth.Users
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * 푸시 토큰 테이블
 */
object PushTokens : Table("push_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val token = varchar("token", 512).uniqueIndex()
    val platform = varchar("platform", 20)  // ANDROID, IOS, WEB
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * 푸시 알림 설정 테이블
 */
object PushSettingsTable : Table("push_settings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id).uniqueIndex()
    val enabled = bool("enabled").default(true)
    val breakingNews = bool("breaking_news").default(true)
    val bookmarkUpdates = bool("bookmark_updates").default(true)
    val dailyBriefing = bool("daily_briefing").default(false)
    val trendingAlerts = bool("trending_alerts").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
