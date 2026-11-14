package com.ganzithon.team08.auth

import com.ganzithon.team08.global.security.AuthCookieUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@ConditionalOnBean(RefreshTokenService::class)
class RefreshTokenController(
    private val refreshTokenService: RefreshTokenService,
    private val authCookieUtils: AuthCookieUtils,
) {
    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest): ResponseEntity<Void> {
        val refreshToken = request.cookies
            ?.firstOrNull { it.name == "REFRESH_TOKEN" }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: throw com.ganzithon.team08.global.exception.BusinessException(AuthErrorCode.REFRESH_TOKEN_MISSING)
        val accessToken = refreshTokenService.issueAccessToken(refreshToken)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.accessToken(accessToken).toString())
            .build()
    }
}
