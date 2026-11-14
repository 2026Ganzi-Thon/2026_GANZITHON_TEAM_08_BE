// 온보딩 질문과 생활 단계 목록을 조회한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.OnboardingQuestionRepository
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingQuestionService(private val questionRepository: OnboardingQuestionRepository) {
    @Transactional(readOnly = true)
    fun getQuestions(): OnboardingQuestionResponse {
        val questions = questionRepository.findAllByOrderByQuestionIdAsc()
        if (questions.isEmpty()) throw BusinessException(OnboardingErrorCode.ONBOARDING_QUESTION_ERROR)
        return OnboardingQuestionResponse(
            lifeStages = LifeStage.entries.map(OnboardingQuestionResponse.LifeStageResponse::from),
            questions = questions.map(OnboardingQuestionResponse.QuestionResponse::from),
        )
    }
}
