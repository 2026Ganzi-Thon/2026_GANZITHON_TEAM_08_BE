// Spring Security 단계에서 발생한 인증·권한 오류를 공통 JSON 형식으로 반환한다.
package com.ganzithon.team08.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.InvalidCsrfTokenException
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = SecurityErrorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.")
}

@Component
class JwtAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        val csrfFailure = accessDeniedException is InvalidCsrfTokenException
        SecurityErrorResponseWriter.write(
            response,
            HttpServletResponse.SC_FORBIDDEN,
            if (csrfFailure) "CSRF_INVALID" else "FORBIDDEN",
            if (csrfFailure) "요청 검증에 실패했습니다." else "접근 권한이 없습니다.",
        )
    }
}

internal object SecurityErrorResponseWriter {
    fun write(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write("{\"code\":\"$code\",\"message\":\"$message\",\"errors\":[]}")
    }
}
