// JWT 쿠키 인증에 사용하는 설정값을 관리한다.
package com.ganzithon.team08.global.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtProperties(
    @Value("\${jwt.secret}") val secret: String,
    @Value("\${jwt.access-token-expiration:3600}") val accessTokenExpirationSeconds: Long,
    @Value("\${jwt.refresh-token-expiration:1209600}") val refreshTokenExpirationSeconds: Long,
)
