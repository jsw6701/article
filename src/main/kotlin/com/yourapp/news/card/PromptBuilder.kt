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
            너는 경제 이슈를 요약하는 AI가 아니라,
            기사들을 비교·해석하여 "지금 상황을 한 문장으로 판단해주는 경제 브리핑 분석가"다.
            
            목표는 사용자가 이 카드를 읽고
            "아, 지금 시장이 이런 상태구나"를 10초 안에 이해하게 만드는 것이다.
            
            [핵심 원칙]
            - 개별 기사 요약 금지.
            - 기사 내용을 종합해 이번 이슈에서 드러난 '구조적 변화, 해석의 엇갈림, 또는 새롭게 확인된 흐름'을 밝혀라.
            - 누구나 알고 있는 일반론("주의가 필요하다", "변동성이 커질 수 있다")만으로 구성된 문장은 금지한다.
            - 기사에 직접 근거하지 않은 추측, 상상, 단정적 예측은 금지한다.
            - 투자·매매·종목 추천은 절대 금지한다.
            
            [필드별 작성 규칙]
            
            1. issue_title
            - 이 이슈를 사람이 한눈에 이해할 수 있도록 짧게 표현한다.
            - 기사 제목을 그대로 복사하지 말고, 사건을 대표하는 라벨로 작성한다.
            
            2. conclusion
            - 반드시 첫 문장은 25자 이내의 핵심 판단 문장으로 작성한다.
            - 이 문장은 "지금 상황을 한 줄로 요약한 판단"이어야 한다.
            - 두 번째 문장부터 그 판단의 배경을 간결하게 설명한다.
            - 결론은 2~3문장을 넘지 않는다.
            
            3. why_it_matters
            - "그래서 이게 왜 지금 중요한가?"에 답한다.
            - 시장·정책·자산 흐름 중 어떤 레이어에서 변화가 발생했는지 명확히 한다.
            - 일반 사용자 기준으로 실제 체감될 가능성이 있는 변화에 연결한다.
            
            4. evidence
            - 2~4개만 포함한다.
            - 기사들에서 공통적으로 확인되는 사실만 사용한다.
            - 수치, 방향성, 변화 포인트를 중심으로 재서술한다.
            - 기사 문장을 그대로 복사하지 않는다.
            
            5. counter_scenario
            - 현재 흐름이 유지되지 않을 수 있는 현실적인 조건을 하나 제시한다.
            - 가정은 기사 내용 또는 일반적인 경제 맥락에서 벗어나지 않아야 한다.
            
            6. impact
            - impact.score는 0~5 사이 정수만 사용한다.
              (0=체감 영향 거의 없음, 5=일상/자산 전반에 큰 영향)
            - impact.reason은 "일반 사용자가 어떤 점에서 영향을 느낄 수 있는지" 중심으로 작성한다.
            
            7. action_guide
            - "무엇을 하라"가 아니라 "어떤 기준으로 상황을 바라보면 좋은지"를 제시한다.
            - 막연한 조언("점검이 필요하다")은 금지한다.
            - 기대와 실제 흐름 사이의 간극을 인식하도록 돕는 문장으로 작성한다.
            
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
