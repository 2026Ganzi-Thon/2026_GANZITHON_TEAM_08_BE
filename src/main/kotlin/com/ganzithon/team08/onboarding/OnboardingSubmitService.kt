package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.OnboardingAnswerRepository
import com.ganzithon.team08.domain.onboarding.OnboardingQuestionRepository
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.onboarding.OnboardingAnswer as SavedAnswer
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.domain.user.UserTypeRepository
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class OnboardingSubmitService(
    private val users: UserAssignmentAccess,
    private val userTypeRepository: UserTypeRepository,
    private val questionRepository: OnboardingQuestionRepository,
    private val answerRepository: OnboardingAnswerRepository,
    private val typeCalculator: OnboardingTypeCalculator,
    private val groupMatchingService: GroupMatchingService,
    private val missionAllocator: MissionAllocator,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun submit(userId: Long, request: OnboardingSubmitRequest): OnboardingSubmitResponse {
        val referenceTime = Instant.now()
        try {
            return submitFirstOnboarding(userId, request, referenceTime)
        } catch (_: PessimisticLockingFailureException) {
            throw BusinessException(OnboardingErrorCode.MATCHING_RETRY_REQUIRED)
        }
    }

    private fun submitFirstOnboarding(
        userId: Long,
        request: OnboardingSubmitRequest,
        referenceTime: Instant,
    ): OnboardingSubmitResponse {
        val user = users.lock(userId) ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        if (user.onboardingCompleted) throw BusinessException(OnboardingErrorCode.ONBOARDING_ALREADY_COMPLETED)

        val lifeStage = request.lifeStage ?: throw BusinessException(OnboardingErrorCode.INVALID_ANSWER)
        val answers = request.answers.map {
            OnboardingAnswer(it.questionId ?: 0, it.choice ?: throw BusinessException(OnboardingErrorCode.INVALID_ANSWER))
        }
        val calculated = try {
            typeCalculator.calculate(answers)
        } catch (_: BusinessException) {
            throw BusinessException(OnboardingErrorCode.INVALID_ANSWER)
        }
        val userType = userTypeRepository.findById(calculated.type.code)
            .orElseThrow { BusinessException(OnboardingErrorCode.INVALID_ANSWER) }
        val questions = questionRepository.findAllById(answers.map { it.questionId }).associateBy { it.questionId }
        if (questions.size != answers.size) throw BusinessException(OnboardingErrorCode.INVALID_ANSWER)
        val existing = answerRepository.findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(userId)
            .associateBy { it.question.questionId }
        val group = groupMatchingService.match(user, lifeStage, userType)

        val newAnswers = answers.mapNotNull { answer ->
            val saved = existing[answer.questionId]
            if (saved == null) {
                SavedAnswer(user, questions.getValue(answer.questionId), answer.choice)
            } else {
                saved.changeAnswer(answer.choice)
                null
            }
        }
        answerRepository.saveAll(newAnswers)
        user.completeOnboarding(lifeStage, userType, calculated.carePreference, group)
        missionAllocator.ensurePersonalAssignment(userId, referenceTime)
        missionAllocator.ensureGroupAssignment(requireNotNull(group.groupId), referenceTime)

        return OnboardingSubmitResponse(lifeStage, userType.typeCode, userType.typeName, calculated.carePreference)
    }
}
