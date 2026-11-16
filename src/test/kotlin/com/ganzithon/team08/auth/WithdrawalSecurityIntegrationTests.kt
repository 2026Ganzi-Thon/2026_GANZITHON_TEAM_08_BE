package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.security.JwtProvider
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WithdrawalSecurityIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    private lateinit var accessToken: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("withdraw-http-${UUID.randomUUID()}", "탈퇴통합양"))
        accessToken = jwtProvider.createAccessToken(requireNotNull(user.userId))
    }

    @Test
    fun `탈퇴하면 204와 인증 쿠키 3개의 만료를 반환한다`() {
        val csrfValue = "withdraw-csrf-token"

        val response = mockMvc.perform(
            delete("/api/auth/withdraw")
                .cookie(
                    Cookie("ACCESS_TOKEN", accessToken),
                    Cookie("XSRF-TOKEN", csrfValue),
                )
                .header("X-XSRF-TOKEN", csrfValue),
        ).andExpect(status().isNoContent())
            .andReturn()

        val cookies = response.response.getHeaders("Set-Cookie")
        assertEquals(3, cookies.size)
        assertTrue(cookies.any { it.startsWith("ACCESS_TOKEN=") && it.contains("Max-Age=0") })
        assertTrue(cookies.any { it.startsWith("REFRESH_TOKEN=") && it.contains("Max-Age=0") })
        assertTrue(cookies.any { it.startsWith("XSRF-TOKEN=") && it.contains("Max-Age=0") })
    }

    @Test
    fun `탈퇴 전에 발급된 Access Token으로 보호 API를 호출하면 403 ACCOUNT_WITHDRAWN을 반환한다`() {
        val csrfValue = "withdraw-csrf-token"

        mockMvc.perform(
            delete("/api/auth/withdraw")
                .cookie(
                    Cookie("ACCESS_TOKEN", accessToken),
                    Cookie("XSRF-TOKEN", csrfValue),
                )
                .header("X-XSRF-TOKEN", csrfValue),
        ).andExpect(status().isNoContent())

        mockMvc.perform(
            get("/api/me/type")
                .cookie(Cookie("ACCESS_TOKEN", accessToken)),
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"))
    }
}
