package com.yourapp.news.card

import com.yourapp.news.article.Article
import java.time.Duration
import java.time.LocalDateTime

/**
 * 기사의 "사건성 점수"를 계산
 * - 급변/돌파 키워드: +3
 * - 정책/개입 키워드: +2
 * - 시간 키워드: +2
 * - 숫자 포함: +2
 * - 최신성: +2 (24시간 내) / +1 (48시간 내)
 */
object ArticleEventScorer {

    private val STRONG_EVENT_KEYWORDS = listOf(
        "급락", "급등", "폭락", "폭등",
        "돌파", "붕괴", "사상", "최고", "최저"
    )

    private val POLICY_KEYWORDS = listOf(
        "정부", "당국", "개입", "발표", "대책", "회의"
    )

    private val TIME_KEYWORDS = listOf(
        "오늘", "하루", "단기간", "최근"
    )

    private val NUMBER_REGEX = Regex("\\d+(\\.\\d+)?(%|원|달러|bp|포인트)")

    fun score(article: Article, now: LocalDateTime = LocalDateTime.now()): Int {
        var score = 0

        val text = "${article.title} ${article.summary ?: ""}"

        STRONG_EVENT_KEYWORDS.forEach {
            if (text.contains(it)) score += 3
        }

        POLICY_KEYWORDS.forEach {
            if (text.contains(it)) score += 2
        }

        TIME_KEYWORDS.forEach {
            if (text.contains(it)) score += 2
        }

        if (NUMBER_REGEX.containsMatchIn(text)) {
            score += 2
        }

        val hoursAgo = Duration.between(article.publishedAt, now).toHours()
        score += when {
            hoursAgo <= 24 -> 2
            hoursAgo <= 48 -> 1
            else -> 0
        }

        return score
    }
}
