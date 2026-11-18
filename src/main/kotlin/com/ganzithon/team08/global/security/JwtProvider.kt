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
    // 앱 기동 시(빈 생성 시점)에 바로 검증해서, 첫 로그인 때 잘못된 키 길이가 처음
    // 드러나며 원인불명 500으로 보이는 대신 배포 직후 기동 실패로 즉시 드러나게 한다.
    private val key = run {
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

    private fun createToken(userId: Long, expirationSeconds: Long, tokenUse: String): String {
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
