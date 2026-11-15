// 카카오 OAuth 및 사용자 정보 API를 호출한다.
package com.ganzithon.team08.auth

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class KakaoApiClient {
    private val restClient = RestClient.builder().build()

    fun requestAccessToken(code: String, properties: KakaoAuthProperties): KakaoTokenResponse {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", properties.clientId)
            add("redirect_uri", properties.redirectUri)
            add("code", code)
            properties.clientSecret.takeIf { it.isNotBlank() }?.let { add("client_secret", it) }
        }
        return restClient.post().uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
            .body(KakaoTokenResponse::class.java) ?: error("카카오 토큰 응답이 비어 있습니다.")
    }

    fun requestUser(accessToken: String): KakaoUserResponse =
        restClient.get().uri("https://kapi.kakao.com/v2/user/me")
            .header("Authorization", "Bearer $accessToken").retrieve()
            .body(KakaoUserResponse::class.java) ?: error("카카오 사용자 응답이 비어 있습니다.")
}

data class KakaoTokenResponse(val access_token: String)

data class KakaoUserResponse(val id: Long, val kakao_account: KakaoAccountResponse? = null)

data class KakaoAccountResponse(val profile: KakaoProfileResponse? = null)

data class KakaoProfileResponse(val nickname: String? = null)
