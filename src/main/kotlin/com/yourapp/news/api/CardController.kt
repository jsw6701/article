package com.yourapp.news.api

import com.yourapp.news.card.CardDetail
import com.yourapp.news.card.CardListFilter
import com.yourapp.news.card.CardListItem
import com.yourapp.news.card.CardReadService
import com.yourapp.news.card.CardStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

@Tag(name = "Cards", description = "경제 이슈 결론 카드 조회 API")
@RestController
@RequestMapping("/api/cards")
class CardController(
    private val cardReadService: CardReadService
) {

    @Operation(
        summary = "카드 리스트 조회",
        description = "필터 조건에 맞는 결론 카드 리스트를 조회합니다. 정렬은 이슈의 최근 발행 시간 기준 내림차순입니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 파라미터", content = [Content()])
    )
    @GetMapping
    fun listCards(
        @Parameter(description = "이슈 그룹 필터 (RATE, FX, STOCK, REALESTATE, MACRO, POLICY)")
        @RequestParam(required = false) group: String?,
        
        @Parameter(description = "카드 상태 필터", schema = Schema(defaultValue = "ACTIVE"))
        @RequestParam(required = false, defaultValue = "ACTIVE") status: String,
        
        @Parameter(description = "시작 시간 (ISO 8601, 예: 2024-01-01T00:00:00)")
        @RequestParam(required = false) from: String?,
        
        @Parameter(description = "종료 시간 (ISO 8601, 예: 2024-01-31T23:59:59)")
        @RequestParam(required = false) to: String?,
        
        @Parameter(description = "조회 개수 (1~100)", schema = Schema(defaultValue = "20"))
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        
        @Parameter(description = "시작 오프셋", schema = Schema(defaultValue = "0"))
        @RequestParam(required = false, defaultValue = "0") offset: Int
    ): ResponseEntity<CardListResponse> {
        // 파라미터 검증
        val validatedLimit = limit.coerceIn(1, 100)
        val validatedOffset = offset.coerceAtLeast(0)

        val cardStatus = runCatching { CardStatus.valueOf(status.uppercase()) }
            .getOrElse { CardStatus.ACTIVE }

        val fromDateTime = from?.let { parseDateTime(it) }
        val toDateTime = to?.let { parseDateTime(it) }

        val filter = CardListFilter(
            group = group?.uppercase(),
            status = cardStatus,
            from = fromDateTime,
            to = toDateTime,
            limit = validatedLimit,
            offset = validatedOffset
        )

        val cards = cardReadService.listCards(filter)

        return ResponseEntity.ok(
            CardListResponse(
                items = cards,
                count = cards.size,
                limit = validatedLimit,
                offset = validatedOffset
            )
        )
    }

    @Operation(
        summary = "오늘의 카드 조회",
        description = "최근 24시간 내 발행된 이슈의 결론 카드를 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/today")
    fun getTodayCards(
        @Parameter(description = "조회 개수 (1~20)", schema = Schema(defaultValue = "7"))
        @RequestParam(required = false, defaultValue = "7") limit: Int
    ): ResponseEntity<CardListResponse> {
        val validatedLimit = limit.coerceIn(1, 20)
        val cards = cardReadService.getTodayCards(validatedLimit)

        return ResponseEntity.ok(
            CardListResponse(
                items = cards,
                count = cards.size,
                limit = validatedLimit,
                offset = 0
            )
        )
    }

    @Operation(
        summary = "카드 상세 조회",
        description = "특정 이슈의 결론 카드 상세 정보를 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "카드를 찾을 수 없음", content = [Content()])
    )
    @GetMapping("/{issueId}")
    fun getCard(
        @Parameter(description = "이슈 ID", required = true)
        @PathVariable issueId: Long
    ): ResponseEntity<CardDetail> {
        val card = cardReadService.getCard(issueId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found for issueId: $issueId")

        return ResponseEntity.ok(card)
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/**
 * 카드 리스트 응답
 */
@Schema(description = "카드 리스트 응답")
data class CardListResponse(
    @Schema(description = "카드 목록")
    val items: List<CardListItem>,
    
    @Schema(description = "현재 페이지 카드 수")
    val count: Int,
    
    @Schema(description = "요청한 limit 값")
    val limit: Int,
    
    @Schema(description = "요청한 offset 값")
    val offset: Int
)
