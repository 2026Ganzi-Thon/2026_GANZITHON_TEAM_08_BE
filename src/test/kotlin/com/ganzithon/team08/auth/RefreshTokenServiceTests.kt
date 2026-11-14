package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.security.JwtProvider
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenServiceTests @Autowired constructor(
    private val refreshTokenService: RefreshTokenService,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        user = userRepository.save(User("refresh-target-${UUID.randomUUID()}", "갱신확인양"))
    }

    @Test
    fun issuesAccessTokenOnlyForTheStoredRefreshToken() {
        val refreshToken = jwtProvider.createRefreshToken(requireNotNull(user.userId))
        user.updateRefreshTokenHash(refreshToken)
        userRepository.save(user)

        assertDoesNotThrow { refreshTokenService.issueAccessToken(refreshToken) }
    }

    @Test
    fun rejectsAccessTokenUsedAsRefreshToken() {
        val accessToken = jwtProvider.createAccessToken(requireNotNull(user.userId))

        assertThrows(BusinessException::class.java) {
            refreshTokenService.issueAccessToken(accessToken)
        }
    }

    @Test
    fun rejectsRefreshTokenAfterAccountWithdrawal() {
        val refreshToken = jwtProvider.createRefreshToken(requireNotNull(user.userId))
        user.updateRefreshTokenHash(refreshToken)
        user.withdraw()
        userRepository.save(user)

        val exception = assertThrows(BusinessException::class.java) {
            refreshTokenService.issueAccessToken(refreshToken)
        }

        assertEquals(AuthErrorCode.ACCOUNT_WITHDRAWN, exception.errorCode)
        assertEquals(UserStatus.WITHDRAWN, user.status)
    }
}
