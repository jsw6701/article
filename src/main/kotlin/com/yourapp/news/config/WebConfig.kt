package com.yourapp.news.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@Configuration
@EnableWebFlux
class WebConfig : WebFluxConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // /app/** 경로로 static/app/ 폴더의 정적 파일 서빙
        registry.addResourceHandler("/app/**")
            .addResourceLocations("classpath:/static/app/")
    }

    /**
     * SPA fallback: /app 하위의 모든 경로는 index.html 반환
     * (정적 파일 요청 제외 - ResourceHandler가 먼저 처리)
     */
    @Bean
    fun spaFallbackRouter(): RouterFunction<ServerResponse> = router {
        val indexHtml = ClassPathResource("static/app/index.html")

        // /app 리다이렉트
        GET("/app") {
            ServerResponse.permanentRedirect(java.net.URI.create("/app/")).build()
        }

        // /app/** 모든 SPA 라우트 처리 (확장자 없는 경로만)
        GET("/app/{*path}") { request ->
            val path = request.pathVariable("path")
            // 정적 파일 요청이 아닌 경우에만 index.html 반환
            if (path.isEmpty() || !path.contains(".")) {
                ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(BodyInserters.fromResource(indexHtml))
            } else {
                // 정적 파일은 ResourceHandler가 처리하도록 404 반환
                ServerResponse.notFound().build()
            }
        }
    }
}
