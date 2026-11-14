package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.stereotype.Service

@Service
class MyTypeService(private val userRepository: UserRepository) {
    fun getMyType(userId: Long): MyTypeResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }
        if (user.status == com.ganzithon.team08.domain.user.UserStatus.WITHDRAWN) {
            throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        }
        if (!user.onboardingCompleted || user.lifeStage == null || user.userType == null || user.carePreference == null) {
            throw BusinessException(OnboardingErrorCode.ONBOARDING_NOT_COMPLETED)
        }
        return MyTypeResponse.from(user)
    }
}
