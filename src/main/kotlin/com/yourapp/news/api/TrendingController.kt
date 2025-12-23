package com.yourapp.news.api

import com.yourapp.news.trending.TrendingIssue
import com.yourapp.news.trending.TrendingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Trending", description = "급상승 이슈 API")
@RestController
@RequestMapping("/api/trending")
class TrendingController(
    private val trendingService: TrendingService
) {

    @Operation(
        summary = "급상승 이슈 조회",
        description = """
            현재 시각 기준으로 급상승 이슈를 조회합니다.

            - 조회 범위: 최근 48시간 내 기사가 있는 이슈
            - 점수 계산: 기사 수 + 언론사 다양성 + 최신성(지수 감소)

            점수 공식:
            score = articleCount × 1.5 + publisherBonus × 2.0 + recencyScore × 5.0

            recencyScore = e^(-hoursSinceLast / 6)
            - 방금 나온 기사: 5.0점
            - 6시간 전: 1.85점
            - 12시간 전: 0.68점
            - 24시간 전: 0.09점
        """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    fun getTrending(
        @Parameter(description = "반환할 이슈 개수 (1~20)", schema = Schema(defaultValue = "10"))
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<TrendingResponse> {
        val validatedLimit = limit.coerceIn(1, 20)

        val trending = trendingService.getTrendingIssues(validatedLimit)

        return ResponseEntity.ok(
            TrendingResponse(
                items = trending,
                count = trending.size
            )
        )
    }
}

/**
 * 급상승 이슈 응답
 */
@Schema(description = "급상승 이슈 응답")
data class TrendingResponse(
    @Schema(description = "급상승 이슈 목록 (점수 내림차순)")
    val items: List<TrendingIssue>,

    @Schema(description = "반환된 이슈 수")
    val count: Int
)
