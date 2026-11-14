package com.ganzithon.team08.global.security

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.csrf.DefaultCsrfToken
import kotlin.test.assertEquals

class CsrfRequestHandlerTests {
    private val handler = PlainCsrfTokenRequestHandler()
    private val token = DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-value")

    @Test
    fun `CSRF 헤더의 원문 값을 그대로 검증 값으로 사용한다`() {
        val request = MockHttpServletRequest().apply {
            addHeader(token.headerName, token.token)
        }

        assertEquals(token.token, handler.resolveCsrfTokenValue(request, token))
    }

    @Test
    fun `CSRF 헤더가 없으면 검증 값을 반환하지 않는다`() {
        val request = MockHttpServletRequest()

        assertEquals(null, handler.resolveCsrfTokenValue(request, token))
    }
}
