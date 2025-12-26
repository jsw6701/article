package com.yourapp.news.config

import com.yourapp.news.article.Articles
import com.yourapp.news.auth.EmailVerificationCodes
import com.yourapp.news.auth.RefreshTokens
import com.yourapp.news.auth.Users
import com.yourapp.news.bookmark.Bookmarks
import com.yourapp.news.card.CardGenerationLogs
import com.yourapp.news.card.Cards
import com.yourapp.news.card.CardViews
import com.yourapp.news.issue.IssueArticles
import com.yourapp.news.issue.Issues
import com.yourapp.news.pipeline.PipelineRuns
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
class ExposedConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun database(dataSource: DataSource): Database = Database.connect(dataSource)

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    @Bean
    fun schemaInitializer(database: Database) = ApplicationRunner {
        transaction(database) {
            log.info("Ensuring database tables exist")
            @Suppress("DEPRECATION")
            SchemaUtils.createMissingTablesAndColumns(
                Articles,
                Issues,
                IssueArticles,
                Cards,
                CardGenerationLogs,
                CardViews,
                PipelineRuns,
                Users,
                RefreshTokens,
                Bookmarks,
                EmailVerificationCodes
            )
        }
    }
}
