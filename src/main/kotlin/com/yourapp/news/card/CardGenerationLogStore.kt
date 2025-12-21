package com.yourapp.news.card

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class CardGenerationLogStore(private val database: Database) {

    /**
     * 생성 로그 저장
     */
    fun insert(log: CardGenerationLog): Long = transaction(database) {
        CardGenerationLogs.insert {
            it[issueId] = log.issueId
            it[issueFingerprint] = log.issueFingerprint
            it[attempt] = log.attempt
            it[success] = log.success
            it[httpStatus] = log.httpStatus
            it[errorMessage] = log.errorMessage?.take(2000) // 최대 길이 제한
            it[latencyMs] = log.latencyMs
        } get CardGenerationLogs.id
    }
}
