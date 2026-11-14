package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class WithdrawService(private val userRepository: UserRepository) {
    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }
        if (user.status == UserStatus.WITHDRAWN) {
            throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        }
        user.withdraw()
    }
}
