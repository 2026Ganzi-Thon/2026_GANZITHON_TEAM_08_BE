package com.ganzithon.team08.global.auth

import com.ganzithon.team08.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest

class CurrentUserIdArgumentResolverTests {
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `JWT principal의 사용자 ID를 우선 반환한다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(42L, null, emptyList())

        val request = MockHttpServletRequest().apply { addHeader("X-User-Id", "7") }
        val resolver = CurrentUserIdArgumentResolver(MockEnvironment().apply { setActiveProfiles("local") })

        assertEquals(42L, resolver.resolveArgument(parameter(), null, ServletWebRequest(request), null))
    }

    @Test
    fun `local 프로필에서는 JWT principal이 없을 때 X-User-Id 헤더를 사용한다`() {
        val request = MockHttpServletRequest().apply { addHeader("X-User-Id", "7") }
        val resolver = CurrentUserIdArgumentResolver(MockEnvironment().apply { setActiveProfiles("local") })

        assertEquals(7L, resolver.resolveArgument(parameter(), null, ServletWebRequest(request), null))
    }

    @Test
    fun `local이 아닌 프로필에서는 X-User-Id 헤더를 신뢰하지 않는다`() {
        val request = MockHttpServletRequest().apply { addHeader("X-User-Id", "7") }
        val resolver = CurrentUserIdArgumentResolver(MockEnvironment().apply { setActiveProfiles("test") })

        assertThrows<BusinessException> {
            resolver.resolveArgument(parameter(), null, ServletWebRequest(request), null)
        }
    }

    private fun parameter() =
        org.springframework.core.MethodParameter(
            ResolverFixture::class.java.getDeclaredMethod("resolve", Long::class.javaPrimitiveType),
            0,
        )

    private class ResolverFixture {
        @Suppress("UNUSED_PARAMETER")
        fun resolve(@CurrentUserId userId: Long) = userId
    }
}
