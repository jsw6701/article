package com.yourapp.news.article

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Articles : Table("articles") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 512)
    val summary = text("summary")
    val link = varchar("link", 1024).uniqueIndex()
    val publisher = varchar("publisher", 255)
    val publishedAt = datetime("published_at")
    val category = varchar("category", 64).default("economy")

    override val primaryKey = PrimaryKey(id)
}
