// 온보딩 질문 조회 API의 응답 DTO를 정의한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.OnboardingQuestion
import com.ganzithon.team08.domain.user.LifeStage

data class OnboardingQuestionResponse(
    val lifeStages: List<LifeStageResponse>,
    val questions: List<QuestionResponse>,
) {
    data class LifeStageResponse(val code: String, val label: String) {
        companion object {
            fun from(lifeStage: LifeStage) = LifeStageResponse(lifeStage.name, lifeStage.label)
        }
    }

    data class QuestionResponse(
        val questionId: Int,
        val content: String,
        val optionA: String,
        val optionB: String,
    ) {
        companion object {
            fun from(question: OnboardingQuestion) = QuestionResponse(
                question.questionId,
                question.content,
                question.optionA,
                question.optionB,
            )
        }
    }
}
