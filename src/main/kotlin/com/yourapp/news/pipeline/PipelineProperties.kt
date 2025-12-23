package com.yourapp.news.pipeline

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "news.pipeline")
data class PipelineProperties(
    val enabled: Boolean = true,
    val cron: String = "0 */10 * * * *"
)
