package com.ganzithon.team08.global.security

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtProviderTests {
    private val provider = JwtProvider(
        JwtProperties(
            secret = "test-only-jwt-secret-must-be-at-least-32-bytes-long",
            accessTokenExpirationSeconds = 3600,
            refreshTokenExpirationSeconds = 1209600,
        ),
    )

    @Test
    fun `Access Token은 access 용도로만 식별된다`() {
        val accessClaims = provider.parse(provider.createAccessToken(1L))
        val refreshClaims = provider.parse(provider.createRefreshToken(1L))

        assertTrue(provider.isAccessToken(accessClaims))
        assertFalse(provider.isRefreshToken(accessClaims))
        assertFalse(provider.isAccessToken(refreshClaims))
        assertTrue(provider.isRefreshToken(refreshClaims))
    }
}
