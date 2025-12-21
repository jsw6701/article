package com.yourapp.news.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/OpenAPI 설정
 *
 * 접속 URL:
 * - Swagger UI: /swagger-ui.html 또는 /swagger-ui/index.html
 * - OpenAPI JSON: /v3/api-docs
 *
 * 운영 환경에서 비활성화하려면 application.yml에 추가:
 *   springdoc:
 *     api-docs:
 *       enabled: false
 *     swagger-ui:
 *       enabled: false
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Economy News Briefing API")
                    .version("v1")
                    .description(
                        """
                        RSS 기반 경제 뉴스 이슈 클러스터링 및 결론 카드 조회 API
                        
                        ## 주요 기능
                        - 경제 뉴스 RSS 수집 및 저장
                        - 유사 기사 클러스터링으로 이슈 생성
                        - AI(Gemini) 기반 결론 카드 생성
                        - 카드 조회 API 제공
                        
                        ## 파이프라인
                        RSS 수집 → 이슈 클러스터링 → 카드 생성 (20분 주기)
                        """.trimIndent()
                    )
                    .contact(
                        Contact()
                            .name("API Support")
                            .email("support@yourapp.com")
                    )
            )
            .servers(
                listOf(
                    Server().url("/").description("Current Server")
                )
            )
    }
}
