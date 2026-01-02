package com.yourapp.news.card

import com.yourapp.news.article.Article
import com.yourapp.news.issue.Issue
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class PromptBuilder {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private const val DEFAULT_SAMPLE_SIZE = 5
        private const val MIN_SAMPLE_SIZE = 3

        // 기사 텍스트가 길면 LLM이 헤드라인을 흐리게 만드는 경향이 있어서 제한
        private const val MAX_TITLE_LEN = 120
        private const val MAX_SUMMARY_LEN = 260

        private val SYSTEM_INSTRUCTION = """
            너는 경제 뉴스 분석 서비스의 수석 에디터이자 헤드라인 작성자다.
            여러 언론사의 기사들을 종합해 “이슈 결론 카드”와 “사용자용 헤드라인”을 동시에 생성한다.

            [최우선 목표]
            1) 사용자가 제목만 보고도 “지금 무슨 일이 벌어졌는지” 즉시 이해하게 하라.
            2) 결론 카드는 개별 기사 요약이 아니라, 공통된 사실·흐름·변화를 종합 분석한 결과여야 한다.
            3) 모든 결과물은 일반 사용자 기준에서 읽기 쉬워야 한다.

            [헤드라인 작성 규칙] ★ 매우 중요
            - headline은 사용자에게 노출되는 제목이다.
            - 절대 사용 금지:
              · “~관련 이슈”, “~이슈”, “환율/달러”, “정책/대책” 같은 분류형 표현
              · 추상적 단어 남용 (“우려”, “불확실성”, “변동성”만 있는 제목)
            - 반드시 포함할 것:
              · ‘변화’ 또는 ‘사건’
              · 가능한 경우 숫자, 시간, 방향성 (상승/하락/급변/개입 등)
            - 하나의 핵심 사건만 담아라.
            - 25자 이내.
            - 실제 한국 뉴스 앱에서 쓰일 법한 자연스러운 문장으로 작성하라.

            - signal_summary는 리스트 화면에서 headline 아래에 보이는 한 줄 요약이다.
              · headline을 보조하는 설명
              · 왜 지금 중요한지 한 문장으로 요약
              · 30자 이내
            
            [impact.score 기준표] ★ 반드시 준수

            - 0점: 일반적인 시장 소음 수준. 대부분의 사람에게 영향 없음.
            - 1점: 특정 집단/업계에만 제한적 영향.
            - 2점: 일부 소비자/투자자가 체감할 수 있는 변화.
            - 3점: 다수의 사람들이 단기적으로 체감할 수 있는 변화.
            - 4점: 생활비, 대출, 투자 판단 등 일상 전반에 뚜렷한 영향.
            - 5점: 금융위기급 사건. 제도 변화 또는 광범위한 혼란 수반.
                    (매우 드문 경우에만 사용)
                    
            - 대부분의 이슈는 2~3점에 해당한다.
            - 4점은 명확한 체감 변화가 있을 때만 사용한다.
            - 5점은 극히 예외적인 경우가 아니면 사용하지 마라.

            [사건 중심 작성 규칙]
            - 기사들을 종합해 '오늘 또는 최근에 발생한 가장 이례적이거나 극단적인 변화'를 먼저 제시하라.
            - conclusion 첫 문장은 반드시 "오늘 벌어진 가장 중요한 사건"을 한 문장으로 요약한다.
              (수치 변화, 전후 비교, 변동 폭, 개입 여부 등 포함)
            - conclusion은 최대 2문장까지만 허용한다.
            - 추상적 표현("우려", "불확실성", "압력")만으로 conclusion을 시작하는 것을 금지한다.

            [금지]
            - 기사 문장 그대로 복사 금지
            - 기사에 근거하지 않은 추측/상상 금지
            - 단정적 예측 금지 (“~할 것이다” 금지, “~가능성이 있다/기조가 포착된다” 사용)
            - 투자·매매·종목 추천 절대 금지

            [필드별 작성 규칙] ★ 가독성 최우선

            ※ 문단 구조 규칙 (모든 텍스트 필드에 적용):
               - 서로 다른 내용/관점은 반드시 줄바꿈(\n)으로 구분하라
               - 한 문단은 2-3문장을 넘지 않는다
               - 긴 설명이 필요하면 여러 문단으로 나눠 작성하라

            1) issue_title
               - 내부 분류가 아니라 사람이 이해 가능한 사건 라벨로 짧게 작성

            2) why_it_matters ★ 반드시 2-3문단으로 구성
               - 1문단: 이 사건이 평소와 어떻게 다른지(강도·속도·개입 수준) 비교
               - 2문단: 일반 사용자가 체감할 영향(물가, 해외투자, 소비 등)으로 연결
               - 3문단(선택): 향후 전개 방향이나 주시할 포인트
               - 각 문단 사이에 줄바꿈(\n) 삽입 필수

            3) evidence
               - 2~4개만
               - 숫자/정책조치/시장반응 중심
               - source는 언론사 이름만 (URL/기자명 금지)

            4) counter_scenario
               - 정반대 흐름이 나오려면 필요한 조건 1개
               - 조건과 그 결과를 명확히 구분하여 작성

            5) impact
               - score는 0~5 정수
               - reason: 비전문가 기준 체감 변화 중심
               - reason이 2문장 이상이면 줄바꿈(\n)으로 구분

            6) action_guide ★ 구체적으로 작성
               - "무엇을 하라"가 아니라 "어떤 기준으로 해석할지" 제시
               - 막연한 조언("점검 필요") 금지
               - 2문장 이상이면 줄바꿈(\n)으로 구분

            [출력 규칙]
            - 반드시 JSON만 출력
            - 설명 문장/마크다운/코드블록/여분 텍스트 절대 금지
            - 문단 구분이 필요한 텍스트 필드에서는 줄바꿈 문자(\n)를 사용하라
              (JSON 문자열 내에서 \n은 이스케이프된 형태로 포함됨)

            출력 JSON 스키마:
            {
              "headline": string,
              "signal_summary": string,
              "issue_title": string,
              "conclusion": string,
              "why_it_matters": string,
              "evidence": [{"fact": string, "source": string}],
              "counter_scenario": string,
              "impact": {"score": number, "reason": string},
              "action_guide": string
            }
        """.trimIndent()

        val RESPONSE_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "required" to listOf(
                "headline",
                "signal_summary",
                "issue_title",
                "conclusion",
                "why_it_matters",
                "evidence",
                "counter_scenario",
                "impact",
                "action_guide"
            ),
            "properties" to mapOf(
                "headline" to mapOf("type" to "string"),
                "signal_summary" to mapOf("type" to "string"),
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
     * - 기사 수가 많으면 대표 3~5개만 샘플링하여 전달 (토큰/품질 개선)
     */
    fun build(issue: Issue, articles: List<Article>): String {
        val issueInfo = buildIssueInfo(issue)

        val sampleSize = if (articles.size >= 12) DEFAULT_SAMPLE_SIZE else MIN_SAMPLE_SIZE
        val sampled = sampleRepresentativeArticles(articles, sampleSize)

        val articlesInfo = buildArticlesInfo(sampled)
        val sampleNote = buildSampleNote(articles, sampled)

        return """
            $SYSTEM_INSTRUCTION

            === 이슈 정보 ===
            $issueInfo

            === 관련 기사(대표 샘플) ===
            $sampleNote
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

    private fun buildSampleNote(all: List<Article>, sampled: List<Article>): String {
        val allCount = all.size
        val sampledCount = sampled.size
        val publisherCount = all.map { it.publisher.trim() }.filter { it.isNotBlank() }.distinct().count()

        val latest = all.maxByOrNull { it.publishedAt }?.publishedAt?.format(DATE_FORMATTER) ?: "unknown"
        val earliest = all.minByOrNull { it.publishedAt }?.publishedAt?.format(DATE_FORMATTER) ?: "unknown"

        return """
            - 전체 기사: ${allCount}건 / 전체 언론사: ${publisherCount}개
            - 기사 발행 범위: ${earliest} ~ ${latest}
            - 아래는 언론사 다양성과 최신성을 고려한 대표 ${sampledCount}건 샘플이다.
        """.trimIndent()
    }

    private fun buildArticlesInfo(articles: List<Article>): String {
        return articles.mapIndexed { index, article ->
            val title = normalizeText(article.title, MAX_TITLE_LEN)
            val summary = normalizeText(article.summary, MAX_SUMMARY_LEN)

            """
            [${index + 1}] ${article.publisher.trim()}
            제목: $title
            요약: $summary
            발행: ${article.publishedAt.format(DATE_FORMATTER)}
            링크: ${article.link}
            """.trimIndent()
        }.joinToString("\n\n")
    }

    /**
     * 대표 기사 샘플링 전략 (사건성 점수 기반)
     * - 사건성 점수(급변/숫자/개입 키워드 + 최신성) 높은 기사 우선
     * - 언론사 다양성 유지 (동일 언론사 중복 최소화)
     * - 샘플 부족하면 점수순으로 보충
     */
    private fun sampleRepresentativeArticles(all: List<Article>, desired: Int): List<Article> {
        if (all.isEmpty()) return emptyList()

        val target = desired.coerceIn(MIN_SAMPLE_SIZE, DEFAULT_SAMPLE_SIZE)

        // 사건성 점수로 정렬
        val scored = all
            .distinctBy { it.link }
            .map { it to ArticleEventScorer.score(it) }
            .sortedByDescending { it.second }

        val picked = mutableListOf<Article>()
        val usedPublishers = mutableSetOf<String>()

        // 1) 사건성 점수 높은 기사 우선 + 언론사 다양성 유지
        for ((article, _) in scored) {
            if (picked.size >= target) break

            val publisher = article.publisher.trim().ifBlank { "unknown" }

            // 아직 사용 안 한 언론사면 바로 추가
            if (publisher !in usedPublishers) {
                picked.add(article)
                usedPublishers.add(publisher)
            }
        }

        // 2) 부족하면 점수순으로 보충 (언론사 중복 허용)
        if (picked.size < target) {
            for ((article, _) in scored) {
                if (picked.size >= target) break
                if (picked.none { it.link == article.link }) {
                    picked.add(article)
                }
            }
        }

        return picked
    }

    private fun normalizeText(input: String?, maxLen: Int): String {
        val s = (input ?: "").trim()
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")

        if (s.isBlank()) return "-"
        return if (s.length <= maxLen) s else s.take(maxLen - 1) + "…"
    }
}