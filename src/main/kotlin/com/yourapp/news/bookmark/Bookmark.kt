package com.yourapp.news.bookmark

import java.time.LocalDateTime

data class Bookmark(
    val id: Long? = null,
    val userId: Long,
    val cardId: Long,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
