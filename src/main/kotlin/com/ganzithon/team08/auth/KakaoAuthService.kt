// 카카오 로그인 흐름과 서비스 사용자 생성을 처리한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.security.JwtProvider
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
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
        val kakaoUser = fetchKakaoUser(code)
        val existingId = userRepository.findUserIdByKakaoUserId(kakaoUser.profile.id.toString())
        val user = existingId?.let(userRepository::findForUpdate) ?: run {
            // 가입 전에 다시 확인해 탈퇴 정리 중 발급됐지만 연결 해제로 폐기된 토큰을 거부한다.
            val profile = withKakaoErrors { kakaoApiClient.requestUser(kakaoUser.accessToken) }
            userRepository.save(User(profile.id.toString(), profile.nickname()))
        }
        if (user.status != UserStatus.ACTIVE) throw BusinessException(AuthErrorCode.ACCOUNT_WITHDRAWN)
        val accessToken = jwtProvider.createAccessToken(requireNotNull(user.userId))
        val refreshToken = jwtProvider.createRefreshToken(requireNotNull(user.userId))
        user.updateRefreshTokenHash(refreshToken)
        return LoginResult(accessToken, refreshToken, user.onboardingCompleted)
    }

    // 카카오 토큰 교환·사용자정보 조회 실패를 INTERNAL_SERVER_ERROR로 뭉개지 않고
    // 의도된 카카오 전용 에러코드로 구분해 응답한다(원인 설정값은 로그 정책상 남기지 않는다).
    private fun fetchKakaoUser(code: String): KakaoLoginProfile = withKakaoErrors {
        val token = kakaoApiClient.requestAccessToken(code, kakaoAuthProperties).access_token
        KakaoLoginProfile(kakaoApiClient.requestUser(token), token)
    }

    private inline fun <T> withKakaoErrors(request: () -> T): T =
        try {
            request()
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED)
        } catch (ex: RestClientException) {
            throw BusinessException(AuthErrorCode.KAKAO_API_ERROR)
        }

    private fun KakaoUserResponse.nickname(): String =
        kakao_account?.profile?.nickname?.takeIf { it.isNotBlank() }
            ?: "사용자-${UUID.randomUUID().toString().take(8)}"

    private data class KakaoLoginProfile(val profile: KakaoUserResponse, val accessToken: String)
}

data class LoginResult(val accessToken: String, val refreshToken: String, val onboardingCompleted: Boolean)
