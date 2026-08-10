package com.eooog.rushseat.adapter.inbound.security

import com.eooog.rushseat.application.shared.auth.AccessToken
import com.eooog.rushseat.application.shared.auth.provided.AccessTokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AccessTokenAuthenticationFilter(
    private val accessTokenVerifier: AccessTokenVerifier,
) : OncePerRequestFilter() {
    // SSE 등 async dispatch를 쓰는 엔드포인트에서는 Spring Security의 AuthorizationFilter가
    // async 재디스패치 시점에도 다시 실행된다(OncePerRequestFilter 기반이 아니라서). 이 필터가
    // 기본값(true)을 그대로 두면 async 재디스패치에서 스킵되어 SecurityContext가 비고,
    // 뒤이어 도는 AuthorizationFilter가 그걸 미인증으로 보고 거부한다.
    override fun shouldNotFilterAsyncDispatch(): Boolean = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val accessToken = extractAccessToken(request)

        if (accessToken != null) {
            val principal = accessTokenVerifier.verify(accessToken)

            if (principal != null) {
                val authentication =
                    UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        emptyList(),
                    )

                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractAccessToken(request: HttpServletRequest): AccessToken? {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null

        val rawToken =
            authorization
                .removePrefix("Bearer ")
                .trim()

        return AccessToken.parse(rawToken)
    }
}
