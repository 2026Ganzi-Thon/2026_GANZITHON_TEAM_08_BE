package com.ganzithon.team08.auth

import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.security.AuthCookieUtils
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoAuthControllerTests {
    private val kakaoAuthService = mock(KakaoAuthService::class.java)
    private val controller = KakaoAuthController(kakaoAuthService, AuthCookieUtils())

    @Test
    fun `요청 state가 없으면 콜백을 거부한다`() {
        assertFailsWith<BusinessException> {
            controller.callback(requestWithState("saved"), "code", null, null)
        }
    }

    @Test
    fun `저장된 state 쿠키가 없으면 콜백을 거부한다`() {
        assertFailsWith<BusinessException> {
            controller.callback(requestWithState(null), "code", "requested", null)
        }
    }

    @Test
    fun `요청 state와 쿠키 state가 다르면 콜백을 거부한다`() {
        assertFailsWith<BusinessException> {
            controller.callback(requestWithState("saved"), "code", "requested", null)
        }
    }

    @Test
    fun `state가 일치하면 로그인하고 state 쿠키를 만료한다`() {
        `when`(kakaoAuthService.login("code"))
            .thenReturn(LoginResult("access", "refresh", false))

        val response = controller.callback(requestWithState("same"), "code", "same", null)

        assertEquals(302, response.statusCode.value())
        assertEquals(4, response.headers["Set-Cookie"]?.size)
        assertEquals(true, response.headers["Set-Cookie"]?.any { it.startsWith("OAUTH_STATE=") && it.contains("Max-Age=0") })
    }

    private fun requestWithState(state: String?): HttpServletRequest = mock(HttpServletRequest::class.java).also { request ->
        `when`(request.cookies).thenReturn(state?.let { arrayOf(Cookie("OAUTH_STATE", it)) })
    }
}
