// 카카오 로그인 흐름과 서비스 사용자 생성을 처리한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.security.JwtProvider
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class KakaoAuthService(
    private val kakaoApiClient: KakaoApiClient,
    private val kakaoAuthProperties: KakaoAuthProperties,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {
    fun authorizationUrl(state: String): String =
        "https://kauth.kakao.com/oauth/authorize?response_type=code" +
            "&client_id=${kakaoAuthProperties.clientId}" +
            "&redirect_uri=${kakaoAuthProperties.redirectUri}" +
            "&state=$state"

    @Transactional
    fun login(code: String): LoginResult {
        val kakaoUser = kakaoApiClient.requestUser(kakaoApiClient.requestAccessToken(code, kakaoAuthProperties).access_token)
        val user = userRepository.findByKakaoUserId(kakaoUser.id.toString())
            ?: userRepository.save(User(kakaoUser.id.toString(), kakaoUser.nickname()))
        if (user.status.name != "ACTIVE") throw BusinessException(AuthErrorCode.ACCOUNT_WITHDRAWN)
        val accessToken = jwtProvider.createAccessToken(requireNotNull(user.userId))
        val refreshToken = jwtProvider.createRefreshToken(requireNotNull(user.userId))
        user.updateRefreshTokenHash(refreshToken)
        return LoginResult(accessToken, refreshToken, user.onboardingCompleted)
    }

    private fun KakaoUserResponse.nickname(): String =
        kakao_account?.profile?.nickname?.takeIf { it.isNotBlank() }
            ?: "사용자-${UUID.randomUUID().toString().take(8)}"
}

data class LoginResult(val accessToken: String, val refreshToken: String, val onboardingCompleted: Boolean)
