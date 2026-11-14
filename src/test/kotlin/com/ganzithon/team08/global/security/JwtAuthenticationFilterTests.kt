package com.ganzithon.team08.global.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.atomic.AtomicBoolean

class JwtAuthenticationFilterTests {
    private val provider = JwtProvider(
        JwtProperties(
            secret = "test-only-jwt-secret-must-be-at-least-32-bytes-long",
            accessTokenExpirationSeconds = 3600,
            refreshTokenExpirationSeconds = 1209600,
        ),
    )
    private val filter = JwtAuthenticationFilter(provider)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `ACCESS_TOKEN 쿠키의 Access Token만 인증 정보로 등록한다`() {
        val request = requestWithCookie("ACCESS_TOKEN", provider.createAccessToken(42L))
        val chainCalled = AtomicBoolean(false)

        filter.doFilter(request, MockHttpServletResponse(), chain(chainCalled))

        assertEquals(42L, SecurityContextHolder.getContext().authentication?.principal)
        assert(chainCalled.get())
    }

    @Test
    fun `Refresh Token을 ACCESS_TOKEN 쿠키에 넣어도 인증하지 않는다`() {
        val request = requestWithCookie("ACCESS_TOKEN", provider.createRefreshToken(42L))

        filter.doFilter(request, MockHttpServletResponse(), chain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    private fun requestWithCookie(name: String, value: String) = MockHttpServletRequest().apply {
        setCookies(jakarta.servlet.http.Cookie(name, value))
    }

    private fun chain(called: AtomicBoolean = AtomicBoolean(false)) = FilterChain { _, _ ->
        called.set(true)
    }
}
