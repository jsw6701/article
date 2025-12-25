package com.yourapp.news.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Configuration
@EnableWebFlux
class WebConfig : WebFluxConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // /app/** 경로로 static/app/ 폴더의 정적 파일 서빙
        registry.addResourceHandler("/app/**")
            .addResourceLocations("classpath:/static/app/")
    }

    /**
     * /app 요청을 /app/로 리다이렉트
     */
    @Bean
    @Order(-2)
    fun appRedirectFilter(): WebFilter = WebFilter { exchange, chain ->
        val path = exchange.request.uri.path
        if (path == "/app") {
            exchange.response.statusCode = HttpStatus.PERMANENT_REDIRECT
            exchange.response.headers.location = java.net.URI.create("/app/")
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }

    /**
     * SPA fallback: /app/** 경로에서 404 발생 시 index.html 반환
     */
    @Bean
    @Order(-1)
    fun spaFallbackFilter(): WebFilter = WebFilter { exchange, chain ->
        val path = exchange.request.uri.path

        // /app/** 경로가 아니면 그냥 통과
        if (!path.startsWith("/app/")) {
            return@WebFilter chain.filter(exchange)
        }

        // 정적 파일 요청은 그냥 통과 (ResourceHandler가 처리)
        if (hasFileExtension(path)) {
            return@WebFilter chain.filter(exchange)
        }

        // SPA 라우트 요청 - index.html 반환
        serveIndexHtml(exchange)
    }

    private fun hasFileExtension(path: String): Boolean {
        val lastSegment = path.substringAfterLast('/')
        return lastSegment.contains('.') && !lastSegment.startsWith('.')
    }

    private fun serveIndexHtml(exchange: ServerWebExchange): Mono<Void> {
        val indexHtml = ClassPathResource("static/app/index.html")
        exchange.response.statusCode = HttpStatus.OK
        exchange.response.headers.contentType = MediaType.TEXT_HTML

        return exchange.response.writeWith(
            Mono.fromCallable {
                val bytes = indexHtml.inputStream.readBytes()
                exchange.response.bufferFactory().wrap(bytes)
            }
        )
    }
}
