package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.security.JwtProvider
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenSecurityIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    private lateinit var refreshToken: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("refresh-http-${UUID.randomUUID()}", "갱신통합양"))
        refreshToken = jwtProvider.createRefreshToken(requireNotNull(user.userId))
        user.updateRefreshTokenHash(refreshToken)
        userRepository.save(user)
    }

    @Test
    fun `Access Token 없이 유효한 Refresh Token으로 Access Token을 발급한다`() {
        val csrfValue = "refresh-csrf-token"

        val response = mockMvc.perform(
            post("/api/auth/refresh")
                .cookie(
                    Cookie("REFRESH_TOKEN", refreshToken),
                    Cookie("XSRF-TOKEN", csrfValue),
                )
                .header("X-XSRF-TOKEN", csrfValue),
        ).andExpect(status().isNoContent())
            .andReturn()

        assertTrue(response.response.getHeaders("Set-Cookie").any { it.startsWith("ACCESS_TOKEN=") })
    }
}
