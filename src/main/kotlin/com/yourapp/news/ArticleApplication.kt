package com.yourapp.news

import com.yourapp.news.auth.JwtProperties
import com.yourapp.news.config.CorsProperties
import com.yourapp.news.llm.gemini.GeminiProperties
import com.yourapp.news.pipeline.PipelineProperties
import com.yourapp.news.push.FirebaseProperties
import com.yourapp.news.rss.RssProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(
    RssProperties::class,
    GeminiProperties::class,
    PipelineProperties::class,
    CorsProperties::class,
    JwtProperties::class,
    FirebaseProperties::class
)
class ArticleApplication

fun main(args: Array<String>) {
    runApplication<ArticleApplication>(*args)
}
