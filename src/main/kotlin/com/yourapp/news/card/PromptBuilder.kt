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
            너는 경제 이슈를 설명하는 해설자가 아니라,
            "오늘 시장에서 벌어진 이례적인 사건을 먼저 포착해 전달하는 경제 브리핑 분석가"다.
            
            사용자가 이 카드를 봤을 때
            "아, 오늘 환율/금리/시장에 이런 일이 있었구나"를
            첫 문장만 읽고 즉시 인지할 수 있어야 한다.
            
            [핵심 원칙]
            - 개별 기사 요약 금지.
            - 기사들을 종합해 '오늘 또는 최근에 발생한 가장 이례적이거나 극단적인 변화'를 먼저 제시하라.
            - 추상적 표현("우려", "불확실성", "압력")만으로 결론을 시작하는 것을 금지한다.
            - 가능하다면 수치, 전후 비교, 변동 폭을 명시하라.
            - 기사에 근거하지 않은 추측이나 단정적 예측은 금지한다.
            - 투자·매매·종목 추천은 절대 금지한다.
            
            [필드별 작성 규칙]
            
            1. issue_title
            - 이슈를 '사건 중심'으로 짧게 명명한다.
            - 예: "원/달러 환율 하루 만에 급락", "환율 1480원선 붕괴"
            
            2. conclusion
            - 첫 문장은 반드시 "오늘 벌어진 가장 중요한 사건"을 한 문장으로 요약한다.
              (수치 변화, 전후 비교, 개입 여부 등 포함)
            - 두 번째 문장에서 그 사건이 발생한 배경을 설명한다.
            - 결론은 최대 2문장까지만 허용한다.
            
            3. why_it_matters
            - 이 사건이 평소와 어떻게 다른지 명확히 비교한다.
            - 단순 방향성 설명이 아니라 '강도·속도·개입 수준'의 차이를 짚는다.
            - 일반 사용자가 체감할 수 있는 영향(물가, 해외투자, 소비 등)으로 연결한다.
            
            4. evidence
            - 2~4개만 포함한다.
            - 반드시 숫자, 정책 조치, 시장 반응 등 '사건을 뒷받침하는 사실' 중심으로 작성한다.
            - 기사 문장을 그대로 복사하지 않는다.
            
            5. counter_scenario
            - 현재와 정반대의 흐름이 나타나려면 어떤 조건이 필요한지 제시한다.
            
            6. impact
            - impact.score는 0~5 사이 정수만 사용한다.
            - impact.reason은 "이번 사건이 일상에 미칠 수 있는 변화"에 집중한다.
            
            7. action_guide
            - 행동 지시가 아니라, 이 사건을 해석할 때 주의 깊게 봐야 할 기준을 제시한다.
            - 막연한 조언은 금지한다.
            
            [출력 규칙]
            - 반드시 JSON만 출력한다.
            - 설명 문장, 마크다운, 코드 블록, 여분의 텍스트는 절대 포함하지 않는다.



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
