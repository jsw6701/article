package com.yourapp.news.card

import com.yourapp.news.article.Article
import com.yourapp.news.issue.Issue
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class PromptBuilder {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private val SYSTEM_INSTRUCTION = """
            너는 경제 이슈 분석 전문가다. 여러 기사에서 공통 사실을 종합해 "이슈 결론 카드"를 만든다.

            규칙:
            - 개별 기사 요약을 하지 말고, 공통된 사실과 흐름을 종합 분석해라.
            - 기사 문장을 그대로 복사하지 마라.
            - 단정적 예측 금지. "~할 것이다"가 아니라 "~할 가능성이 있다", "~기조가 예상된다" 등 가능성/방향성 표현만 사용.
            - 투자/매매 추천 금지. "~를 사라/팔아라"는 절대 쓰지 마라.
            - impact.score는 반드시 0~5 사이 정수만 사용 (0=영향 없음, 5=매우 큰 영향). 절대로 5를 초과하면 안 됨.
            - impact.reason은 일반 사용자(비전문가) 기준으로 작성.
            - evidence는 2~4개 항목만 포함.
            - 반드시 JSON만 출력. 설명 텍스트, 마크다운, 코드블럭 금지.
        """.trimIndent()

        val RESPONSE_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "required" to listOf("issue_title", "conclusion", "why_it_matters", "evidence", "counter_scenario", "impact", "action_guide"),
            "properties" to mapOf(
                "issue_title" to mapOf("type" to "string"),
                "conclusion" to mapOf("type" to "string"),
                "why_it_matters" to mapOf("type" to "string"),
                "evidence" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "required" to listOf("fact", "source"),
                        "properties" to mapOf(
                            "fact" to mapOf("type" to "string"),
                            "source" to mapOf("type" to "string")
                        )
                    )
                ),
                "counter_scenario" to mapOf("type" to "string"),
                "impact" to mapOf(
                    "type" to "object",
                    "required" to listOf("score", "reason"),
                    "properties" to mapOf(
                        "score" to mapOf("type" to "integer"),
                        "reason" to mapOf("type" to "string")
                    )
                ),
                "action_guide" to mapOf("type" to "string")
            )
        )
    }

    /**
     * 이슈와 기사 목록으로 프롬프트 생성
     */
    fun build(issue: Issue, articles: List<Article>): String {
        val issueInfo = buildIssueInfo(issue)
        val articlesInfo = buildArticlesInfo(articles)

        return """
            $SYSTEM_INSTRUCTION

            === 이슈 정보 ===
            $issueInfo

            === 관련 기사 목록 ===
            $articlesInfo

            위 기사들을 종합 분석하여 결론 카드 JSON을 생성해라.
        """.trimIndent()
    }

    private fun buildIssueInfo(issue: Issue): String {
        return """
            - 분류: ${issue.group.displayName}
            - 핑거프린트: ${issue.fingerprint}
            - 키워드: ${issue.keywords.joinToString(", ")}
            - 최초 발행: ${issue.firstPublishedAt.format(DATE_FORMATTER)}
            - 최근 발행: ${issue.lastPublishedAt.format(DATE_FORMATTER)}
            - 기사 수: ${issue.articleCount}
            - 출처 수: ${issue.publisherCount}
        """.trimIndent()
    }

    private fun buildArticlesInfo(articles: List<Article>): String {
        return articles.mapIndexed { index, article ->
            """
            [${index + 1}] ${article.publisher}
            제목: ${article.title}
            요약: ${article.summary}
            발행: ${article.publishedAt.format(DATE_FORMATTER)}
            """.trimIndent()
        }.joinToString("\n\n")
    }
}
