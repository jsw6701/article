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
            최근 N시간 내 기사 수, 언론사 다양성, 최신성을 기반으로 급상승 이슈를 조회합니다.

            점수 계산 공식:
            score = recentCount × 1.0 + publisherCount × 0.7 + recencyBoost × 1.2

            recencyBoost = 1 - (마지막 기사 이후 분 / 180)
        """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    fun getTrending(
        @Parameter(description = "최근 N시간 기준 (1~24)", schema = Schema(defaultValue = "3"))
        @RequestParam(required = false, defaultValue = "3") hours: Int,

        @Parameter(description = "반환할 이슈 개수 (1~20)", schema = Schema(defaultValue = "10"))
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<TrendingResponse> {
        val validatedHours = hours.coerceIn(1, 24).toLong()
        val validatedLimit = limit.coerceIn(1, 20)

        val trending = trendingService.getTrendingIssues(validatedHours, validatedLimit)

        return ResponseEntity.ok(
            TrendingResponse(
                items = trending,
                count = trending.size,
                hours = validatedHours.toInt()
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
    val count: Int,

    @Schema(description = "조회 기준 시간 (N시간)")
    val hours: Int
)
