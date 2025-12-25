package com.yourapp.news.rss

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "news.rss")
data class RssProperties(
    val sources: List<RssSource> = emptyList(),
)

data class RssSource(
    val url: String,
    val publisher: String,
)
