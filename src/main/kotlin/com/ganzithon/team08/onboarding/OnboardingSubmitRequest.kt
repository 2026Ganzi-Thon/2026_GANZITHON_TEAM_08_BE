package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class OnboardingSubmitRequest(
    @field:NotNull
    val lifeStage: LifeStage?,
    @field:NotEmpty
    @field:Valid
    val answers: List<AnswerRequest>,
)

data class AnswerRequest(
    @field:NotNull
    val questionId: Int?,
    @field:NotNull
    val choice: AnswerChoice?,
)
