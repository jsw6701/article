package com.yourapp.news.issue

/**
 * 경제 기사 상위 분류 그룹
 */
enum class CategoryGroup(
    val displayName: String,
    val titleTemplate: String,
    val keywords: Set<String>
) {
    RATE(
        displayName = "금리/통화정책",
        titleTemplate = "금리 관련 이슈",
        keywords = setOf("금리", "기준금리", "연준", "fed", "파월", "한은", "동결", "인상", "인하", "bp")
    ),
    FX(
        displayName = "환율/외환",
        titleTemplate = "환율 관련 이슈",
        keywords = setOf("환율", "달러", "원화", "엔화", "유로", "외환", "강세", "약세")
    ),
    STOCK(
        displayName = "증시/주식",
        titleTemplate = "증시 관련 이슈",
        keywords = setOf("코스피", "코스닥", "주가", "증시", "상승", "하락", "급락", "급등", "시총")
    ),
    REALESTATE(
        displayName = "부동산",
        titleTemplate = "부동산 이슈",
        keywords = setOf("부동산", "아파트", "전세", "월세", "분양", "청약", "주택", "재건축", "재개발")
    ),
    MACRO(
        displayName = "거시지표",
        titleTemplate = "거시지표 이슈",
        keywords = setOf("물가", "cpi", "ppi", "고용", "실업", "gdp", "수출", "수입", "무역수지", "경기")
    ),
    POLICY(
        displayName = "정책/제도",
        titleTemplate = "정책 이슈",
        keywords = setOf("세금", "관세", "규제", "지원", "법안", "정책", "대책", "추경", "예산", "금융위", "금감원", "기재부")
    );

    companion object {
        private val allKeywordMap: Map<String, CategoryGroup> by lazy {
            entries.flatMap { group ->
                group.keywords.map { keyword -> keyword.lowercase() to group }
            }.toMap()
        }

        /**
         * 텍스트에서 가장 많이 매칭되는 CategoryGroup을 찾는다.
         * 동점일 경우 enum 순서상 먼저 오는 것을 반환.
         * 매칭되는 키워드가 없으면 null 반환.
         */
        fun classify(text: String): CategoryGroup? {
            val lowerText = text.lowercase()
            val matchCounts = entries.associateWith { group ->
                group.keywords.count { keyword -> lowerText.contains(keyword.lowercase()) }
            }
            val maxCount = matchCounts.values.maxOrNull() ?: 0
            if (maxCount == 0) return null
            return matchCounts.entries.firstOrNull { it.value == maxCount }?.key
        }

        /**
         * 텍스트에서 매칭되는 키워드들을 추출
         */
        fun extractKeywords(text: String, group: CategoryGroup): List<String> {
            val lowerText = text.lowercase()
            return group.keywords.filter { keyword ->
                lowerText.contains(keyword.lowercase())
            }.distinct()
        }

        /**
         * 전체 카테고리에서 텍스트에 매칭되는 키워드들을 추출
         */
        fun extractAllKeywords(text: String): List<String> {
            val lowerText = text.lowercase()
            return entries.flatMap { group ->
                group.keywords.filter { keyword ->
                    lowerText.contains(keyword.lowercase())
                }
            }.distinct()
        }
    }
}
