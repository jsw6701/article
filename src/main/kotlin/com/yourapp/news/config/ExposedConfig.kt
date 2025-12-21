package com.yourapp.news.config

import com.yourapp.news.article.Articles
import javax.sql.DataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager

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
            log.info("Ensuring Articles table exists")
            SchemaUtils.createMissingTablesAndColumns(Articles)
        }
    }
}
