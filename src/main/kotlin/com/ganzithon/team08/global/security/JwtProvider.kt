// JWT를 생성하고 검증한다.
package com.ganzithon.team08.global.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

@Component
class JwtProvider(private val properties: JwtProperties) {
    private val key by lazy {
        require(properties.secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "JWT_SECRET은 32바이트 이상이어야 합니다."
        }
        Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))
    }

    fun createAccessToken(userId: Long): String = createToken(userId, properties.accessTokenExpirationSeconds, ACCESS_TOKEN_USE)

    fun createRefreshToken(userId: Long): String = createToken(userId, properties.refreshTokenExpirationSeconds, REFRESH_TOKEN_USE)

    fun isAccessToken(claims: Claims): Boolean = claims["token_use"] == ACCESS_TOKEN_USE

    fun isRefreshToken(claims: Claims): Boolean = claims["token_use"] == REFRESH_TOKEN_USE

    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    private fun createToken(userId: Long, expirationSeconds: Long, tokenUse: String) =
        createTokenWithUse(userId, expirationSeconds, tokenUse)

    private fun createTokenWithUse(userId: Long, expirationSeconds: Long, tokenUse: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("token_use", tokenUse)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds)))
            .signWith(key)
            .compact()
    }

    companion object {
        private const val ACCESS_TOKEN_USE = "access"
        private const val REFRESH_TOKEN_USE = "refresh"
    }
}
