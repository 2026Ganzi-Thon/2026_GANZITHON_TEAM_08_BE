package com.ganzithon.team08.global.security

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean

class JwtAuthenticationFilterTests {
    private val provider = JwtProvider(
        JwtProperties(
            secret = "test-only-jwt-secret-must-be-at-least-32-bytes-long",
            accessTokenExpirationSeconds = 3600,
            refreshTokenExpirationSeconds = 1209600,
        ),
    )
    private val users = mock(UserRepository::class.java)
    private val filter = JwtAuthenticationFilter(provider, users)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `ACCESS_TOKEN 쿠키의 Access Token만 인증 정보로 등록한다`() {
        `when`(users.findById(42L)).thenReturn(Optional.of(User("kakao-42", "활성 회원")))
        val request = requestWithCookie("ACCESS_TOKEN", provider.createAccessToken(42L))
        val chainCalled = AtomicBoolean(false)

        filter.doFilter(request, MockHttpServletResponse(), chain(chainCalled))

        assertEquals(42L, SecurityContextHolder.getContext().authentication?.principal)
        assert(chainCalled.get())
    }

    @Test
    fun `회원이 삭제되면 서명이 유효한 토큰도 인증하지 않는다`() {
        filter.doFilter(requestWithCookie("ACCESS_TOKEN", provider.createAccessToken(42L)), MockHttpServletResponse(), chain())

        assertNull(SecurityContextHolder.getContext().authentication)
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
