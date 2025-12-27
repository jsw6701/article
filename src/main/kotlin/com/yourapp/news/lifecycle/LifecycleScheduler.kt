package com.yourapp.news.lifecycle

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 이슈 생애주기 업데이트 스케줄러
 *
 * - 매 시간마다 활성 이슈들의 생애주기 업데이트
 * - 기사 수 히스토리 기록
 */
@Component
class LifecycleScheduler(
    private val lifecycleService: LifecycleService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실행 중 여부 플래그 (동시 실행 방지) */
    private val running = AtomicBoolean(false)

    /**
     * 매 시간마다 생애주기 업데이트
     * 정각에 실행 (0분 0초)
     */
    @Scheduled(cron = "0 0 * * * *")
    fun updateLifecycles() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Lifecycle update is already running, skipping this execution")
            return
        }

        try {
            log.info("Scheduled lifecycle update started")
            val updated = lifecycleService.updateAllActiveLifecycles()
            log.info("Lifecycle update completed: {} issues updated", updated)
        } catch (e: Exception) {
            log.error("Error in scheduled lifecycle update: {}", e.message, e)
        } finally {
            running.set(false)
        }
    }

    /**
     * 수동 실행 (테스트/관리용)
     */
    fun runManually(): Int {
        if (!running.compareAndSet(false, true)) {
            log.warn("Lifecycle update is already running, cannot start manual run")
            return -1
        }

        try {
            log.info("Manual lifecycle update started")
            return lifecycleService.updateAllActiveLifecycles()
        } catch (e: Exception) {
            log.error("Error in manual lifecycle update: {}", e.message, e)
            return -1
        } finally {
            running.set(false)
        }
    }

    /**
     * 현재 실행 중인지 확인
     */
    fun isRunning(): Boolean = running.get()
}
