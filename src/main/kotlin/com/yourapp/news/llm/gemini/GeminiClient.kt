package com.yourapp.news.llm.gemini

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class GeminiClient(
    private val properties: GeminiProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .defaultHeader("x-goog-api-key", properties.apiKey)
        .build()

    /**
     * Gemini API 호출
     * @return GeminiResult (성공/실패 정보 포함)
     */
    fun generateContent(promptText: String, responseSchema: Map<String, Any>): GeminiResult {
        if (properties.apiKey.isBlank()) {
            log.warn("[Gemini] API key not configured")
            return GeminiResult.failure(0, "API key not configured", 0)
        }

        var lastResult: GeminiResult? = null
        val maxAttempts = properties.maxRetries + 1
        // 프롬프트 길이만 로깅 (전체 내용은 노출하지 않음)
        val promptLength = promptText.length

        log.info("[Gemini] Starting request, model={}, promptLength={}", properties.model, promptLength)

        for (attempt in 1..maxAttempts) {
            val startTime = System.currentTimeMillis()
            
            try {
                val result = doRequest(promptText, responseSchema, attempt, startTime)
                
                if (result.success) {
                    log.info("[Gemini] Request successful, attempt={}, latencyMs={}, responseLength={}",
                        attempt, result.latencyMs, result.jsonContent?.length ?: 0)
                    return result
                }
                
                // 4xx (인증/요청 오류)는 재시도 안 함
                if (result.httpStatus in 400..499 && result.httpStatus != 429) {
                    log.warn("[Gemini] Client error, not retrying: httpStatus={}, error={}",
                        result.httpStatus, result.errorMessage)
                    return result
                }
                
                // 429 또는 5xx는 재시도
                lastResult = result
                if (attempt < maxAttempts) {
                    log.info("[Gemini] Retrying: attempt={}, httpStatus={}, latencyMs={}",
                        attempt, result.httpStatus, result.latencyMs)
                    Thread.sleep(1000L * attempt) // 간단한 백오프
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                log.error("[Gemini] Unexpected error: attempt={}, latencyMs={}, error={}",
                    attempt, latency, e.message)
                lastResult = GeminiResult.failure(0, e.message ?: "Unknown error", latency)
                
                if (attempt < maxAttempts) {
                    Thread.sleep(1000L * attempt)
                }
            }
        }

        log.error("[Gemini] All attempts failed after {} tries", maxAttempts)
        return lastResult ?: GeminiResult.failure(0, "All attempts failed", 0)
    }

    private fun doRequest(
        promptText: String,
        responseSchema: Map<String, Any>,
        attempt: Int,
        startTime: Long
    ): GeminiResult {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = promptText))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = responseSchema
            )
        )

        return try {
            val response = webClient.post()
                .uri("/v1beta/models/${properties.model}:generateContent")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java)
                        .defaultIfEmpty("No error body")
                        .flatMap { errorBody ->
                            log.error("[Gemini] API error response: status={}, body={}", 
                                clientResponse.statusCode().value(), errorBody)
                            Mono.error(
                                WebClientResponseException.create(
                                    clientResponse.statusCode().value(),
                                    errorBody,
                                    clientResponse.headers().asHttpHeaders(),
                                    errorBody.toByteArray(),
                                    null
                                )
                            )
                        }
                }
                .bodyToMono(GeminiResponse::class.java)
                .timeout(Duration.ofMillis(properties.timeoutMs))
                .block()

            val latency = System.currentTimeMillis() - startTime

            val jsonText = response?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")

            if (jsonText.isNullOrBlank()) {
                log.warn("[Gemini] Empty response: attempt={}, latencyMs={}", attempt, latency)
                GeminiResult.failure(200, "Empty response", latency)
            } else {
                GeminiResult.success(jsonText, latency)
            }
        } catch (e: WebClientResponseException) {
            val latency = System.currentTimeMillis() - startTime
            log.warn("[Gemini] HTTP error: attempt={}, httpStatus={}, latencyMs={}",
                attempt, e.statusCode.value(), latency)
            GeminiResult.failure(e.statusCode.value(), e.message ?: "HTTP error", latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            log.error("[Gemini] Request failed: attempt={}, latencyMs={}, error={}",
                attempt, latency, e.message)
            GeminiResult.failure(0, e.message ?: "Request failed", latency)
        }
    }
}

/**
 * Gemini API 호출 결과
 */
data class GeminiResult(
    val success: Boolean,
    val jsonContent: String?,
    val httpStatus: Int,
    val errorMessage: String?,
    val latencyMs: Long
) {
    companion object {
        fun success(json: String, latencyMs: Long) = GeminiResult(
            success = true,
            jsonContent = json,
            httpStatus = 200,
            errorMessage = null,
            latencyMs = latencyMs
        )

        fun failure(httpStatus: Int, errorMessage: String, latencyMs: Long) = GeminiResult(
            success = false,
            jsonContent = null,
            httpStatus = httpStatus,
            errorMessage = errorMessage,
            latencyMs = latencyMs
        )
    }
}

// Request/Response DTOs
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: Map<String, Any>? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)
