package com.yourapp.news.push

import com.google.firebase.messaging.*
import com.yourapp.news.bookmark.BookmarkStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 푸시 알림 발송 서비스
 */
@Service
class PushService(
    private val firebaseMessaging: FirebaseMessaging?,
    private val pushTokenStore: PushTokenStore,
    private val pushSettingsStore: PushSettingsStore,
    private val bookmarkStore: BookmarkStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 단일 사용자에게 푸시 알림 발송
     */
    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        val tokens = pushTokenStore.findByUserId(userId)
        if (tokens.isEmpty()) {
            log.debug("No push tokens found for user: {}", userId)
            return 0
        }

        return sendToTokens(tokens.map { it.token }, title, body, data)
    }

    /**
     * 여러 사용자에게 푸시 알림 발송
     */
    fun sendToUsers(
        userIds: List<Long>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        if (userIds.isEmpty()) return 0

        val tokens = pushTokenStore.findByUserIds(userIds)
        if (tokens.isEmpty()) {
            log.debug("No push tokens found for users: {}", userIds)
            return 0
        }

        return sendToTokens(tokens.map { it.token }, title, body, data)
    }

    /**
     * 토큰 목록에 푸시 알림 발송
     */
    fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        if (firebaseMessaging == null) {
            log.warn("Firebase Messaging is not initialized")
            return 0
        }

        if (tokens.isEmpty()) return 0

        // 500개씩 배치 처리 (FCM 제한)
        var successCount = 0
        tokens.chunked(500).forEach { batch ->
            try {
                val message = MulticastMessage.builder()
                    .setNotification(
                        Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
                    )
                    .putAllData(data)
                    .addAllTokens(batch)
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(
                                AndroidNotification.builder()
                                    .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                    .build()
                            )
                            .build()
                    )
                    .setApnsConfig(
                        ApnsConfig.builder()
                            .setAps(
                                Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build()
                            )
                            .build()
                    )
                    .build()

                val response = firebaseMessaging.sendEachForMulticast(message)
                successCount += response.successCount

                // 실패한 토큰 처리
                if (response.failureCount > 0) {
                    response.responses.forEachIndexed { index, sendResponse ->
                        if (!sendResponse.isSuccessful) {
                            val exception = sendResponse.exception
                            val errorCode = exception?.messagingErrorCode

                            // 유효하지 않은 토큰 삭제
                            if (errorCode == MessagingErrorCode.UNREGISTERED ||
                                errorCode == MessagingErrorCode.INVALID_ARGUMENT
                            ) {
                                val invalidToken = batch[index]
                                pushTokenStore.deleteByToken(invalidToken)
                                log.info("Removed invalid push token: {}", invalidToken.take(20))
                            }
                        }
                    }
                }

                log.info(
                    "Push notification sent: success={}, failure={}",
                    response.successCount, response.failureCount
                )
            } catch (e: Exception) {
                log.error("Failed to send push notification: {}", e.message, e)
            }
        }

        return successCount
    }

    /**
     * 속보 알림 발송 (설정이 켜진 사용자에게만)
     */
    fun sendBreakingNews(
        title: String,
        body: String,
        issueId: Long? = null
    ): Int {
        val userIds = pushSettingsStore.findUserIdsWithBreakingNewsEnabled()
        val data = mutableMapOf<String, String>()
        if (issueId != null) {
            data["issueId"] = issueId.toString()
            data["route"] = "/cards/$issueId"
        }
        return sendToUsers(userIds, title, body, data)
    }

    /**
     * 급상승 이슈 알림 발송
     */
    fun sendTrendingAlert(
        title: String,
        body: String,
        issueId: Long? = null
    ): Int {
        val userIds = pushSettingsStore.findUserIdsWithTrendingAlertsEnabled()
        val data = mutableMapOf<String, String>()
        if (issueId != null) {
            data["issueId"] = issueId.toString()
            data["route"] = "/cards/$issueId"
        }
        data["route"] = data["route"] ?: "/trending"
        return sendToUsers(userIds, title, body, data)
    }

    /**
     * 일일 브리핑 알림 발송
     */
    fun sendDailyBriefing(
        title: String = "오늘의 경제 브리핑",
        body: String
    ): Int {
        val userIds = pushSettingsStore.findUserIdsWithDailyBriefingEnabled()
        val data = mapOf("route" to "/")
        return sendToUsers(userIds, title, body, data)
    }

    /**
     * 북마크 이슈 업데이트 알림 발송
     */
    fun sendBookmarkUpdate(
        issueId: Long,
        title: String,
        body: String
    ): Int {
        // 해당 이슈를 북마크한 사용자 조회
        val bookmarkedUserIds = bookmarkStore.findUserIdsByIssueId(issueId)
        if (bookmarkedUserIds.isEmpty()) return 0

        // 북마크 업데이트 알림이 켜진 사용자만 필터링
        val enabledUserIds = pushSettingsStore.findUserIdsWithBookmarkUpdatesEnabled()
        val targetUserIds = bookmarkedUserIds.filter { it in enabledUserIds }

        if (targetUserIds.isEmpty()) return 0

        val data = mapOf(
            "issueId" to issueId.toString(),
            "route" to "/cards/$issueId"
        )

        return sendToUsers(targetUserIds, title, body, data)
    }

    /**
     * 전체 사용자에게 알림 발송 (공지사항 등)
     */
    fun sendToAll(
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        val tokens = pushTokenStore.findAll()
        return sendToTokens(tokens.map { it.token }, title, body, data)
    }
}
