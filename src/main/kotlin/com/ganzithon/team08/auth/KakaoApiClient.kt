// 카카오 OAuth 및 사용자 정보 API를 호출한다.
package com.ganzithon.team08.auth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

@Component
class KakaoApiClient internal constructor(private val restClient: RestClient) {
    @Autowired
    constructor() : this(
        RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()).apply {
                setReadTimeout(Duration.ofSeconds(5))
            },
        ).build(),
    )

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

    fun unlink(userId: String, adminKey: String) {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("target_id_type", "user_id")
            add("target_id", userId)
        }
        val response = try {
            restClient.post().uri("https://kapi.kakao.com/v1/user/unlink")
                .header("Authorization", "KakaoAK $adminKey")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(KakaoUnlinkResponse::class.java)
        } catch (exception: RestClientResponseException) {
            val status = exception.statusCode.value()
            if (status == 400 && runCatching { exception.getResponseBodyAs(KakaoErrorResponse::class.java)?.code }.getOrNull() == -101) {
                return // 카카오에 이미 연결되지 않은 회원이다.
            }
            val reason = if (status in setOf(401, 403)) "KAKAO_UNLINK_AUTH" else "KAKAO_UNLINK_HTTP_$status"
            throw KakaoUnlinkFailure(reason)
        }
        if (response?.id?.toString() != userId) throw KakaoUnlinkFailure("KAKAO_UNLINK_RESPONSE")
    }
}

internal class KakaoUnlinkFailure(val reason: String) : RuntimeException()
internal data class KakaoUnlinkResponse(val id: Long)
internal data class KakaoErrorResponse(val code: Int)

data class KakaoTokenResponse(val access_token: String)

data class KakaoUserResponse(val id: Long, val kakao_account: KakaoAccountResponse? = null)

data class KakaoAccountResponse(val profile: KakaoProfileResponse? = null)

data class KakaoProfileResponse(val nickname: String? = null)
