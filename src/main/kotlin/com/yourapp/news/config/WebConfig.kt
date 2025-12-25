package com.yourapp.news.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
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
     * 정적 파일이 존재하면 정적 파일 반환, 없으면 index.html 반환
     */
    @Bean
    fun spaFallbackRouter(): RouterFunction<ServerResponse> = router {
        val indexHtml = ClassPathResource("static/app/index.html")

        // /app 리다이렉트
        GET("/app") {
            ServerResponse.permanentRedirect(java.net.URI.create("/app/")).build()
        }

        // /app/** 모든 경로 처리
        GET("/app/{*path}") { request ->
            val path = request.pathVariable("path")

            if (path.isEmpty()) {
                // /app/ 메인 페이지
                ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(BodyInserters.fromResource(indexHtml))
            } else {
                // 정적 파일이 존재하는지 확인
                val staticResource: Resource = ClassPathResource("static/app/$path")
                if (staticResource.exists() && staticResource.isReadable) {
                    // 정적 파일 반환
                    val contentType = getContentType(path)
                    ServerResponse.ok()
                        .contentType(contentType)
                        .body(BodyInserters.fromResource(staticResource))
                } else {
                    // SPA 라우트 - index.html 반환
                    ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(BodyInserters.fromResource(indexHtml))
                }
            }
        }
    }

    private fun getContentType(path: String): MediaType {
        return when {
            path.endsWith(".js") -> MediaType.parseMediaType("application/javascript")
            path.endsWith(".css") -> MediaType.parseMediaType("text/css")
            path.endsWith(".json") -> MediaType.APPLICATION_JSON
            path.endsWith(".html") -> MediaType.TEXT_HTML
            path.endsWith(".svg") -> MediaType.parseMediaType("image/svg+xml")
            path.endsWith(".png") -> MediaType.IMAGE_PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> MediaType.IMAGE_JPEG
            path.endsWith(".ico") -> MediaType.parseMediaType("image/x-icon")
            path.endsWith(".woff") -> MediaType.parseMediaType("font/woff")
            path.endsWith(".woff2") -> MediaType.parseMediaType("font/woff2")
            path.endsWith(".ttf") -> MediaType.parseMediaType("font/ttf")
            path.endsWith(".txt") -> MediaType.TEXT_PLAIN
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
    }
}
