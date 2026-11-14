// ACCESS_TOKEN 쿠키를 검증해 Spring Security 인증 정보로 등록한다.
package com.ganzithon.team08.global.security

import com.ganzithon.team08.domain.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.cookies?.firstOrNull { it.name == "ACCESS_TOKEN" }?.value
        if (token != null) {
            val userId = runCatching { jwtProvider.parse(token).subject.toLong() }.getOrNull()
            if (userId != null) {
                val user = userRepository.findById(userId).orElse(null)
                if (user?.status?.name == "WITHDRAWN") {
                    SecurityErrorResponseWriter.write(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "ACCOUNT_WITHDRAWN",
                        "탈퇴한 계정은 이용할 수 없습니다.",
                    )
                    return
                }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList())
            }
        }
        filterChain.doFilter(request, response)
    }
}
