package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User

data class MyTypeResponse(
    val lifeStage: String,
    val lifeStageLabel: String,
    val typeCode: String,
    val typeName: String,
    val typeDescription: String,
    val carePreference: AnswerChoice,
    val onboardingCompleted: Boolean,
) {
    companion object {
        fun from(user: User): MyTypeResponse {
            val lifeStage = requireNotNull(user.lifeStage)
            val userType = requireNotNull(user.userType)
            val carePreference = requireNotNull(user.carePreference)
            return MyTypeResponse(
                lifeStage = lifeStage.name,
                lifeStageLabel = lifeStage.label,
                typeCode = userType.typeCode,
                typeName = userType.typeName,
                typeDescription = userType.typeDescription,
                carePreference = carePreference,
                onboardingCompleted = user.onboardingCompleted,
            )
        }
    }
}
