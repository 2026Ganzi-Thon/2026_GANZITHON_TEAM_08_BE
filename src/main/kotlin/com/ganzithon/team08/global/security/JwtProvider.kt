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

    fun createAccessToken(userId: Long): String = createToken(userId, properties.accessTokenExpirationSeconds)

    fun createRefreshToken(userId: Long): String = createToken(userId, properties.refreshTokenExpirationSeconds)

    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    private fun createToken(userId: Long, expirationSeconds: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds)))
            .signWith(key)
            .compact()
    }
}
