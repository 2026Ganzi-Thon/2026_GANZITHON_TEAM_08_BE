package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.security.JwtProvider
import org.springframework.stereotype.Service

@Service
class RefreshTokenService(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    fun issueAccessToken(refreshToken: String): String {
        val userId = runCatching { jwtProvider.parse(refreshToken).subject.toLong() }
            .getOrElse { throw BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID) }
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID) }
        if (user.status == UserStatus.WITHDRAWN) {
            throw BusinessException(AuthErrorCode.ACCOUNT_WITHDRAWN)
        }
        if (!user.hasRefreshToken(refreshToken)) {
            throw BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID)
        }
        return jwtProvider.createAccessToken(userId)
    }
}
