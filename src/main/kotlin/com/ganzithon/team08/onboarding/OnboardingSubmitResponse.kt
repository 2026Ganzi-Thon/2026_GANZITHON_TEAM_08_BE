package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage

data class OnboardingSubmitResponse(
    val lifeStage: LifeStage,
    val typeCode: String,
    val typeName: String,
    val carePreference: AnswerChoice,
)
