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
    private val accessTokenVerifier: AccessTokenVerifier
): OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val accessToken = extractAccessToken(request)

        if (accessToken != null) {
            val principal = accessTokenVerifier.verify(accessToken)

            if (principal != null) {
                val authentication = UsernamePasswordAuthenticationToken(
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

        val rawToken = authorization
            .removePrefix("Bearer")
            .trim()

        return AccessToken.parse(rawToken)
    }
}