package com.yourapp.news.lifecycle

/**
 * 이슈 생애주기 단계
 */
enum class IssueLifecycleStage(
    val emoji: String,
    val label: String,
    val description: String
) {
    EMERGING("🔥", "발생", "새롭게 떠오르는 이슈"),
    SPREADING("📈", "확산", "관심이 빠르게 증가 중"),
    PEAK("⚠️", "정점", "관심이 최고조에 달함"),
    DECLINING("📉", "소강", "관심이 줄어드는 중"),
    DORMANT("💤", "종료", "이슈가 마무리됨")
}
