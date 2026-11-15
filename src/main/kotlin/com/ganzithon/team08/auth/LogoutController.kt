package com.ganzithon.team08.auth

import com.ganzithon.team08.global.security.AuthCookieUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@ConditionalOnBean(LogoutService::class)
class LogoutController(
    private val logoutService: LogoutService,
    private val authCookieUtils: AuthCookieUtils,
) {
    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<Void> {
        logoutService.logout(authentication.name.toLong())
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.expire("ACCESS_TOKEN").toString())
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.expire("REFRESH_TOKEN").toString())
            .header(HttpHeaders.SET_COOKIE, authCookieUtils.expireCsrfToken().toString())
            .build()
    }
}
