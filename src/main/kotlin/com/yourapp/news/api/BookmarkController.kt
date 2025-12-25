package com.yourapp.news.api

import com.yourapp.news.auth.UserPrincipal
import com.yourapp.news.bookmark.BookmarkStore
import com.yourapp.news.card.CardListItem
import com.yourapp.news.card.CardReadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Bookmarks", description = "북마크 API")
@RestController
@RequestMapping("/api/bookmarks")
@SecurityRequirement(name = "bearerAuth")
class BookmarkController(
    private val bookmarkStore: BookmarkStore,
    private val cardReadService: CardReadService
) {

    @Operation(
        summary = "북마크 추가",
        description = "카드를 북마크에 추가합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "북마크 추가 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ApiResponse(responseCode = "404", description = "카드를 찾을 수 없음", content = [Content()]),
        ApiResponse(responseCode = "409", description = "이미 북마크됨", content = [Content()])
    )
    @PostMapping("/{issueId}")
    fun addBookmark(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @Parameter(description = "이슈 ID", required = true)
        @PathVariable issueId: Long
    ): ResponseEntity<BookmarkResponse> {
        val userId = principal?.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

        val cardId = cardReadService.getCardIdByIssueId(issueId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found for issueId: $issueId")

        if (bookmarkStore.exists(userId, cardId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Already bookmarked")
        }

        bookmarkStore.add(userId, cardId)

        return ResponseEntity.ok(BookmarkResponse(issueId = issueId, bookmarked = true))
    }

    @Operation(
        summary = "북마크 삭제",
        description = "카드를 북마크에서 삭제합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "북마크 삭제 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ApiResponse(responseCode = "404", description = "북마크를 찾을 수 없음", content = [Content()])
    )
    @DeleteMapping("/{issueId}")
    fun removeBookmark(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @Parameter(description = "이슈 ID", required = true)
        @PathVariable issueId: Long
    ): ResponseEntity<BookmarkResponse> {
        val userId = principal?.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

        val cardId = cardReadService.getCardIdByIssueId(issueId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found for issueId: $issueId")

        val removed = bookmarkStore.remove(userId, cardId)
        if (!removed) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Bookmark not found")
        }

        return ResponseEntity.ok(BookmarkResponse(issueId = issueId, bookmarked = false))
    }

    @Operation(
        summary = "북마크 상태 확인",
        description = "특정 카드의 북마크 상태를 확인합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()])
    )
    @GetMapping("/{issueId}/status")
    fun getBookmarkStatus(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @Parameter(description = "이슈 ID", required = true)
        @PathVariable issueId: Long
    ): ResponseEntity<BookmarkResponse> {
        val userId = principal?.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

        val cardId = cardReadService.getCardIdByIssueId(issueId)
            ?: return ResponseEntity.ok(BookmarkResponse(issueId = issueId, bookmarked = false))

        val bookmarked = bookmarkStore.exists(userId, cardId)

        return ResponseEntity.ok(BookmarkResponse(issueId = issueId, bookmarked = bookmarked))
    }

    @Operation(
        summary = "내 북마크 목록",
        description = "로그인한 사용자의 북마크 목록을 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()])
    )
    @GetMapping
    fun getMyBookmarks(
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<BookmarkListResponse> {
        val userId = principal?.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")

        val bookmarks = bookmarkStore.findByUserId(userId)
        val cardIds = bookmarks.map { it.cardId }

        // cardId -> issueId 매핑을 위해 카드 정보 조회
        val cards = cardIds.mapNotNull { cardId ->
            cardReadService.getCardByCardId(cardId)
        }

        return ResponseEntity.ok(BookmarkListResponse(items = cards, count = cards.size))
    }
}

@Schema(description = "북마크 응답")
data class BookmarkResponse(
    @Schema(description = "이슈 ID")
    val issueId: Long,

    @Schema(description = "북마크 여부")
    val bookmarked: Boolean
)

@Schema(description = "북마크 목록 응답")
data class BookmarkListResponse(
    @Schema(description = "북마크된 카드 목록")
    val items: List<CardListItem>,

    @Schema(description = "북마크 수")
    val count: Int
)
