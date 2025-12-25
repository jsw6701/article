package com.yourapp.news.card

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 카드 조회수 테이블
 */
object CardViews : Table("card_views") {
    val id = long("id").autoIncrement()
    val cardId = long("card_id").references(Cards.id).index()
    val viewedAt = datetime("viewed_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
