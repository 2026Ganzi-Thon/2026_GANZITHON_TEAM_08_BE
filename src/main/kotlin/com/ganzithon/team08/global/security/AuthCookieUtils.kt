// 인증 및 CSRF 쿠키의 공통 정책을 관리한다.
package com.ganzithon.team08.global.security

import jakarta.servlet.http.Cookie
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

@Component
class AuthCookieUtils {
    fun accessToken(value: String): ResponseCookie = tokenCookie("ACCESS_TOKEN", value, 3600)

    fun refreshToken(value: String): ResponseCookie = tokenCookie("REFRESH_TOKEN", value, 1209600)

    fun expire(name: String): ResponseCookie = tokenCookie(name, "", 0)

    fun tokenCookie(name: String, value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()

    fun read(cookie: Cookie?, name: String): String? = cookie?.takeIf { it.name == name }?.value
}
