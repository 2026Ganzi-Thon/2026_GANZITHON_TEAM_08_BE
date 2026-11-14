// ACCESS_TOKEN 쿠키를 검증해 Spring Security 인증 정보로 등록한다.
package com.ganzithon.team08.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(private val jwtProvider: JwtProvider) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.cookies?.firstOrNull { it.name == "ACCESS_TOKEN" }?.value
        if (token != null) {
            runCatching { jwtProvider.parse(token).takeIf(jwtProvider::isAccessToken)?.subject?.toLong() }
                .onSuccess { userId ->
                    if (userId == null) return@onSuccess
                    val authentication = UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList())
                    SecurityContextHolder.getContext().authentication = authentication
                }
        }
        filterChain.doFilter(request, response)
    }
}
