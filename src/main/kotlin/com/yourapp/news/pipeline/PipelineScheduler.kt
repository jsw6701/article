package com.yourapp.news.pipeline

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 파이프라인 스케줄러
 *
 * - 설정된 cron 주기로 파이프라인 실행
 * - AtomicBoolean으로 동시 실행 방지
 */
@Component
class PipelineScheduler(
    private val properties: PipelineProperties,
    private val orchestrator: PipelineOrchestrator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실행 중 여부 플래그 (동시 실행 방지) */
    private val running = AtomicBoolean(false)

    /**
     * 스케줄 실행
     * cron 표현식은 application.yml에서 설정
     */
    @Scheduled(cron = "\${news.pipeline.cron:0 */2 * * * *}")
    fun scheduledRun() {
        if (!properties.enabled) {
            log.debug("Pipeline is disabled, skipping scheduled run")
            return
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Pipeline is already running, skipping this execution")
            return
        }

        try {
            log.info("Scheduled pipeline execution started")
            orchestrator.runOnce()
        } catch (e: Exception) {
            log.error("Unexpected error in scheduled pipeline: {}", e.message, e)
        } finally {
            running.set(false)
        }
    }

    /**
     * 수동 실행 (테스트/관리용)
     * @return true: 실행됨, false: 이미 실행 중
     */
    fun runManually(): Boolean {
        if (!running.compareAndSet(false, true)) {
            log.warn("Pipeline is already running, cannot start manual run")
            return false
        }

        try {
            log.info("Manual pipeline execution started")
            orchestrator.runOnce()
            return true
        } catch (e: Exception) {
            log.error("Unexpected error in manual pipeline: {}", e.message, e)
            return false
        } finally {
            running.set(false)
        }
    }

    /**
     * 현재 실행 중인지 확인
     */
    fun isRunning(): Boolean = running.get()
}
