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
     * SPA fallback: /app 또는 확장자 없는 경로는 index.html 반환
     */
    @Bean
    fun spaFallbackRouter(): RouterFunction<ServerResponse> = router {
        val indexHtml = ClassPathResource("static/app/index.html")

        // /app 리다이렉트
        GET("/app") {
            ServerResponse.permanentRedirect(java.net.URI.create("/app/")).build()
        }
        // /app/ 메인 페이지
        GET("/app/") {
            ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(BodyInserters.fromResource(indexHtml))
        }
        // /app/login, /app/signup 등 SPA 라우트 (확장자 없는 경로만)
        GET("/app/login") {
            ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(BodyInserters.fromResource(indexHtml))
        }
        GET("/app/signup") {
            ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(BodyInserters.fromResource(indexHtml))
        }
    }
}
