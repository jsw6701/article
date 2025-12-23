package com.yourapp.news.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : WebFilter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val token = resolveToken(exchange)

        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            val userId = jwtTokenProvider.getUserId(token)
            val username = jwtTokenProvider.getUsername(token)
            val role = jwtTokenProvider.getRole(token)

            if (userId != null && username != null && role != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
                val authentication = UsernamePasswordAuthenticationToken(
                    UserPrincipal(userId, username, role),
                    null,
                    authorities
                )
                log.debug("Authenticated user: {} with role: {}", username, role)

                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }
        }

        return chain.filter(exchange)
    }

    private fun resolveToken(exchange: ServerWebExchange): String? {
        val bearerToken = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}

/**
 * 인증된 사용자 정보
 */
data class UserPrincipal(
    val userId: Long,
    val username: String,
    val role: UserRole
)
