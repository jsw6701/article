package com.yourapp.news.article

import java.time.LocalDateTime

data class Article(
    val title: String,
    val summary: String,
    val link: String,
    val publisher: String,
    val publishedAt: LocalDateTime,
    val category: String = "economy",
)
