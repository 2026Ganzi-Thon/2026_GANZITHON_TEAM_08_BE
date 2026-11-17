// 유형 재검사 결과와 그룹 이동을 하나의 트랜잭션에서 반영한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.certification.GroupCertificationPrivacyService
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.onboarding.OnboardingAnswer as SavedAnswer
import com.ganzithon.team08.domain.onboarding.OnboardingAnswerRepository
import com.ganzithon.team08.domain.onboarding.OnboardingQuestionRepository
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.domain.user.UserTypeRepository
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OnboardingRetestService(
    private val users: UserAssignmentAccess,
    private val types: UserTypeRepository,
    private val questions: OnboardingQuestionRepository,
    private val answers: OnboardingAnswerRepository,
    private val calculator: OnboardingTypeCalculator,
    private val groups: GroupMatchingService,
    private val privacy: GroupCertificationPrivacyService,
    private val missions: MissionAllocator,
) {
    @Transactional(readOnly = true)
    fun getCurrentAnswers(userId: Long): OnboardingRetestResponse {
        val user = validateUser(users.find(userId))
        val saved = answers.findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(userId)
        return OnboardingRetestResponse(checkNotNull(user.lifeStage), saved.map {
            RetestAnswer(it.question.questionId, it.answer)
        })
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun submit(userId: Long, request: OnboardingSubmitRequest): OnboardingRetestResult {
        val referenceTime = Instant.now()
        try {
            return applyRetest(userId, request, referenceTime)
        } catch (_: PessimisticLockingFailureException) {
            throw BusinessException(OnboardingErrorCode.MATCHING_RETRY_REQUIRED)
        }
    }

    private fun applyRetest(
        userId: Long,
        request: OnboardingSubmitRequest,
        referenceTime: Instant,
    ): OnboardingRetestResult {
        val user = validateUser(users.lock(userId))
        val lifeStage = request.lifeStage ?: invalidAnswer()
        val submitted = request.answers.map {
            OnboardingAnswer(it.questionId ?: invalidAnswer(), it.choice ?: invalidAnswer())
        }
        val result = try {
            calculator.calculate(submitted)
        } catch (_: BusinessException) {
            invalidAnswer()
        }
        val type = types.findById(result.type.code).orElseThrow {
            BusinessException(OnboardingErrorCode.INVALID_ANSWER)
        }
        val questionById = questions.findAllById(submitted.map { it.questionId }).associateBy { it.questionId }
        if (questionById.size != submitted.size) invalidAnswer()
        val existing = answers.findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(userId)
            .associateBy { it.question.questionId }

        val lifeStageChanged = user.lifeStage != lifeStage
        val typeChanged = user.userType?.typeCode != type.typeCode
        val rematched = user.group != null && (lifeStageChanged || typeChanged)
        val target = if (rematched) groups.rematch(user, lifeStage, type) else null

        answers.saveAll(submitted.mapNotNull { answer ->
            val saved = existing[answer.questionId]
            if (saved == null) SavedAnswer(user, questionById.getValue(answer.questionId), answer.choice)
            else {
                saved.changeAnswer(answer.choice)
                null
            }
        })

        if (target == null) {
            user.updateType(lifeStage, type, result.carePreference)
        } else {
            user.updateType(lifeStage, type, result.carePreference, target)
            privacy.makeGroupCertificationsPrivate(userId)
            missions.ensureGroupAssignment(requireNotNull(target.groupId), referenceTime)
        }

        return OnboardingRetestResult(
            lifeStage = lifeStage,
            typeCode = type.typeCode,
            typeName = type.typeName,
            typeDescription = type.typeDescription,
            carePreference = result.carePreference,
            lifeStageChanged = lifeStageChanged,
            typeChanged = typeChanged,
            groupRematched = rematched,
            groupId = user.group?.groupId,
        )
    }

    private fun validateUser(user: User?): User {
        if (user == null) throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        if (!user.onboardingCompleted) throw BusinessException(OnboardingErrorCode.ONBOARDING_REQUIRED)
        return user
    }

    private fun invalidAnswer(): Nothing = throw BusinessException(OnboardingErrorCode.INVALID_ANSWER)
}

data class OnboardingRetestResponse(val lifeStage: LifeStage, val answers: List<RetestAnswer>)

data class RetestAnswer(val questionId: Int, val choice: AnswerChoice)

data class OnboardingRetestResult(
    val lifeStage: LifeStage,
    val typeCode: String,
    val typeName: String,
    val typeDescription: String,
    val carePreference: AnswerChoice,
    val lifeStageChanged: Boolean,
    val typeChanged: Boolean,
    val groupId: Long?,
    val groupRematched: Boolean,
)
