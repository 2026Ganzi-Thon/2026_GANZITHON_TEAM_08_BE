package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.OnboardingAnswer
import com.ganzithon.team08.domain.onboarding.OnboardingAnswerRepository
import com.ganzithon.team08.domain.onboarding.OnboardingQuestionRepository
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserTypeRepository
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.transaction.Transactional
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class OnboardingSubmitService(
    private val userRepository: UserRepository,
    private val userTypeRepository: UserTypeRepository,
    private val questionRepository: OnboardingQuestionRepository,
    private val answerRepository: OnboardingAnswerRepository,
    private val typeCalculator: OnboardingTypeCalculator,
    private val groupMatchingServiceProvider: ObjectProvider<GroupMatchingService>,
) {
    @Transactional
    fun submit(userId: Long, request: OnboardingSubmitRequest): OnboardingSubmitResponse {
        val user = userRepository.findById(userId).orElseThrow { BusinessException(OnboardingErrorCode.INVALID_ANSWER) }
        val lifeStage = request.lifeStage ?: throw BusinessException(OnboardingErrorCode.INVALID_ANSWER)
        val answers = request.answers.map {
            OnboardingAnswer(it.questionId ?: 0, it.choice ?: throw BusinessException(OnboardingErrorCode.INVALID_ANSWER))
        }
        val calculated = runCatching { typeCalculator.calculate(answers) }
            .getOrElse { throw BusinessException(OnboardingErrorCode.INVALID_ANSWER) }
        val userType = userTypeRepository.findById(calculated.type.code)
            .orElseThrow { BusinessException(OnboardingErrorCode.INVALID_ANSWER) }
        val groupMatchingService = groupMatchingServiceProvider.ifAvailable
            ?: throw BusinessException(OnboardingErrorCode.GROUP_MATCHING_NOT_READY)
        val group = groupMatchingService.match(user, lifeStage, userType)

        answers.forEach { answer ->
            val question = questionRepository.findById(answer.questionId)
                .orElseThrow { BusinessException(OnboardingErrorCode.INVALID_ANSWER) }
            val existing = user.userId?.let { answerRepository.findByUser_UserIdAndQuestion_QuestionId(it, answer.questionId) }
            if (existing == null) answerRepository.save(OnboardingAnswer(user, question, answer.choice))
            else existing.changeAnswer(answer.choice)
        }
        if (user.onboardingCompleted) user.updateType(lifeStage, userType, calculated.carePreference, group)
        else user.completeOnboarding(lifeStage, userType, calculated.carePreference, group)

        return OnboardingSubmitResponse(lifeStage, userType.typeCode, userType.typeName, calculated.carePreference)
    }
}
