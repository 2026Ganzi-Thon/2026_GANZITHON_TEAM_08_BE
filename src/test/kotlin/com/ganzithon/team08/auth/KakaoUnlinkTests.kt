// 카카오 연결 해제 요청의 형식과 오류 분류를 검증한다.
package com.ganzithon.team08.auth

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoUnlinkTests {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val kakao = KakaoApiClient(builder.build())

    @Test
    fun `어드민 키와 대상 카카오 번호를 전송한다`() {
        request().andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-admin-key"))
            .andExpect(content().string("target_id_type=user_id&target_id=42"))
            .andRespond(withSuccess("""{"id":42}""", MediaType.APPLICATION_JSON))

        kakao.unlink("42", "test-admin-key")

        server.verify()
    }

    @Test
    fun `이미 연결이 없어진 카카오 계정은 다시 처리해도 성공한다`() {
        request().andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""{"code":-101}""").contentType(MediaType.APPLICATION_JSON))

        kakao.unlink("42", "test-admin-key")

        server.verify()
    }

    @Test
    fun `카카오 장애와 대상 번호가 다른 응답은 작업을 완료하지 않는다`() {
        request().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        request().andRespond(withSuccess("""{"id":43}""", MediaType.APPLICATION_JSON))

        assertEquals("KAKAO_UNLINK_HTTP_503", assertFailsWith<KakaoUnlinkFailure> { kakao.unlink("42", "test-admin-key") }.reason)
        assertEquals("KAKAO_UNLINK_RESPONSE", assertFailsWith<KakaoUnlinkFailure> { kakao.unlink("42", "test-admin-key") }.reason)
        server.verify()
    }

    private fun request() = server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
}
