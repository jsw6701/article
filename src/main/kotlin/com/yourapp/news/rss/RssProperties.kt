package com.yourapp.news.rss

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "news.rss")
data class RssProperties(
    val feeds: List<String> = emptyList(),
)
