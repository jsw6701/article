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
        registry.addResourceHandler("/app/**")
            .addResourceLocations("classpath:/static/app/")
    }

    @Bean
    @Order(-2)
    fun appRedirectFilter(): WebFilter {
        return WebFilter { exchange: ServerWebExchange, chain: WebFilterChain ->
            val path = exchange.request.uri.path
            if (path == "/app") {
                exchange.response.statusCode = HttpStatus.PERMANENT_REDIRECT
                exchange.response.headers.location = java.net.URI.create("/app/")
                exchange.response.setComplete()
            } else {
                chain.filter(exchange)
            }
        }
    }

    @Bean
    @Order(-1)
    fun spaFallbackFilter(): WebFilter {
        return WebFilter { exchange: ServerWebExchange, chain: WebFilterChain ->
            val path = exchange.request.uri.path

            if (!path.startsWith("/app/")) {
                return@WebFilter chain.filter(exchange)
            }

            if (hasFileExtension(path)) {
                return@WebFilter chain.filter(exchange)
            }

            serveIndexHtml(exchange)
        }
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
