package com.ganzithon.team08.global.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Autowired
import jakarta.servlet.http.Cookie

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CsrfSecurityIntegrationTests.Config::class)
class CsrfSecurityIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtProvider: JwtProvider,
) {
    private val csrfValue = "csrf-integration-token"

    @Test
    fun `원문 CSRF 쿠키와 헤더가 일치하면 상태 변경 요청을 허용한다`() {
        val result = mockMvc.perform(
            post("/test/security/csrf")
                .cookie(Cookie("ACCESS_TOKEN", jwtProvider.createAccessToken(42L)))
                .cookie(Cookie("XSRF-TOKEN", csrfValue))
                .header("X-XSRF-TOKEN", csrfValue),
        ).andExpect(status().isNoContent())
            .andReturn()

        assertEquals(204, result.response.status)
    }

    @Test
    fun `CSRF 헤더가 없으면 상태 변경 요청을 거절한다`() {
        mockMvc.perform(
            post("/test/security/csrf")
                .cookie(Cookie("ACCESS_TOKEN", jwtProvider.createAccessToken(42L)))
                .cookie(Cookie("XSRF-TOKEN", csrfValue)),
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `CSRF 쿠키와 헤더가 다르면 상태 변경 요청을 거절한다`() {
        mockMvc.perform(
            post("/test/security/csrf")
                .cookie(Cookie("ACCESS_TOKEN", jwtProvider.createAccessToken(42L)))
                .cookie(Cookie("XSRF-TOKEN", csrfValue))
                .header("X-XSRF-TOKEN", "different-token"),
        ).andExpect(status().isForbidden())
    }

    @TestConfiguration
    class Config {
        @Bean
        fun csrfProbeController() = CsrfProbeController()
    }

    @RestController
    class CsrfProbeController {
        @PostMapping("/test/security/csrf")
        fun probe(): ResponseEntity<Void> = ResponseEntity.noContent().build()
    }
}
