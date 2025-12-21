package com.yourapp.news.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * WebFlux용 CORS 설정
 *
 * 주의:
 * - allowCredentials=true일 때 allowedOrigins에 "*"를 사용하면 안 됩니다.
 * - 운영 환경에서는 반드시 구체적인 도메인만 허용하세요.
 */
@Configuration
class CorsConfig(
    private val corsProperties: CorsProperties
) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val corsConfig = CorsConfiguration().apply {
            // 허용할 오리진 목록
            // 주의: credentials와 함께 "*"를 사용하면 브라우저에서 거부됩니다.
            allowedOrigins = corsProperties.allowedOrigins
            
            // 허용할 HTTP 메서드
            allowedMethods = corsProperties.allowedMethods
            
            // 허용할 헤더
            allowedHeaders = corsProperties.allowedHeaders
            
            // 인증정보(쿠키 등) 포함 허용
            allowCredentials = corsProperties.allowCredentials
            
            // Preflight 캐시 시간 (초)
            maxAge = 3600L
        }

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", corsConfig)
        }

        return CorsWebFilter(source)
    }
}
