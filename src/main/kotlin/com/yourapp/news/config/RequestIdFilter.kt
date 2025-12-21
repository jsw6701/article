package com.yourapp.news.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebFlux용 요청 ID 필터
 *
 * - X-Request-Id 헤더가 있으면 사용, 없으면 생성
 * - MDC에 requestId 저장 (로그 패턴에서 %X{requestId}로 출력)
 * - 응답 헤더에 X-Request-Id 추가
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : WebFilter {

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_MDC_KEY = "requestId"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 요청 헤더에서 requestId 추출 또는 생성
        val requestId = exchange.request.headers.getFirst(REQUEST_ID_HEADER)
            ?: generateRequestId()

        // 응답 헤더에 requestId 추가
        exchange.response.headers.add(REQUEST_ID_HEADER, requestId)

        // MDC에 저장 (WebFlux에서는 contextWrite 사용)
        return chain.filter(exchange)
            .contextWrite { ctx -> ctx.put(REQUEST_ID_MDC_KEY, requestId) }
            .doFirst { MDC.put(REQUEST_ID_MDC_KEY, requestId) }
            .doFinally { MDC.remove(REQUEST_ID_MDC_KEY) }
    }

    private fun generateRequestId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
