// 카카오 로그인 시작과 콜백을 제공한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.global.security.AuthCookieUtils
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.SecureRandom
import java.util.Base64

@RestController
@RequestMapping("/api/auth")
@ConditionalOnBean(KakaoAuthService::class)
class KakaoAuthController(
    private val kakaoAuthService: KakaoAuthService,
    private val authCookieUtils: AuthCookieUtils,
) {
    @GetMapping("/kakao")
    fun start(): ResponseEntity<Void> {
        val state = newState()
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, kakaoAuthService.authorizationUrl(state))
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.tokenCookie("OAUTH_STATE", state, 300).toString())
            .build()
    }

    @GetMapping("/kakao/callback")
    fun callback(
        request: HttpServletRequest,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        if (error != null || code.isNullOrBlank()) throw BusinessException(AuthErrorCode.KAKAO_INVALID_CODE)
        val savedState = request.cookies?.firstOrNull { it.name == "OAUTH_STATE" }?.value
        if (state != savedState) throw BusinessException(AuthErrorCode.KAKAO_INVALID_STATE)
        val result = kakaoAuthService.login(code)
        val location = if (result.onboardingCompleted) "/home" else "/onboarding"
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(location))
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.accessToken(result.accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.refreshToken(result.refreshToken).toString())
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.csrfToken(newState()).toString())
            .build()
    }

    private fun newState(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
}
