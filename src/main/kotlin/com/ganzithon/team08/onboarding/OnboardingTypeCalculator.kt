// 온보딩 응답을 검증하고 유형과 돌봄 선호를 계산한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode

data class OnboardingAnswer(val questionId: Int, val choice: AnswerChoice)

data class OnboardingTypeResult(
    val type: OnboardingType,
    val carePreference: AnswerChoice,
)

class OnboardingTypeCalculator {
    fun calculate(answers: List<OnboardingAnswer>): OnboardingTypeResult {
        validate(answers)
        val byQuestion = answers.associateBy { it.questionId }
        val type = OnboardingType.from(
            q1 = byQuestion.getValue(1).choice,
            q2 = byQuestion.getValue(2).choice,
            q4 = byQuestion.getValue(4).choice,
        )
        return OnboardingTypeResult(type, byQuestion.getValue(3).choice)
    }

    private fun validate(answers: List<OnboardingAnswer>) {
        if (answers.size != 4 || answers.map { it.questionId }.toSet() != setOf(1, 2, 3, 4)) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
    }
}
