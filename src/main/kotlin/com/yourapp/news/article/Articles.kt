package com.yourapp.news.article

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object Articles : Table("articles") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 512)
    val summary = text("summary")
    val link = varchar("link", 500).uniqueIndex()  // MySQL utf8mb4: 500*4 = 2000 bytes < 3072
    val publisher = varchar("publisher", 255)
    val publishedAt = datetime("published_at")
    val category = varchar("category", 64).default("economy")

    override val primaryKey = PrimaryKey(id)
}
