package com.yourapp.news.bookmark

import com.yourapp.news.auth.Users
import com.yourapp.news.card.Cards
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 북마크 테이블
 */
object Bookmarks : Table("bookmarks") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
    val cardId = long("card_id").references(Cards.id, onDelete = ReferenceOption.CASCADE).index()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, cardId)
    }
}
