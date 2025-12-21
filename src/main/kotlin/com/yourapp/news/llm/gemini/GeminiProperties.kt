package com.yourapp.news.llm.gemini

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val timeoutMs: Long = 15000,
    val maxRetries: Int = 2
)
