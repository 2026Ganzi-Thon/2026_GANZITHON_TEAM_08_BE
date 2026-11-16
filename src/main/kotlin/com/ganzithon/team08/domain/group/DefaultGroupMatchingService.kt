// 온보딩 트랜잭션 안에서 정원이 남은 그룹을 고르거나 새 그룹을 생성한다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.domain.user.UserTypeMatchingAccess
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.onboarding.GroupMatchingService
import com.ganzithon.team08.onboarding.OnboardingErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultGroupMatchingService(
    private val groups: CareGroupRepository,
    private val users: UserAssignmentAccess,
    private val types: UserTypeMatchingAccess,
) : GroupMatchingService {
    // 호출자가 사용자 행을 잠근 뒤 유형과 그룹 순서로 잠근다.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun match(user: User, lifeStage: LifeStage, userType: UserType): CareGroup {
        if (user.status != UserStatus.ACTIVE || user.group != null) {
            throw BusinessException(OnboardingErrorCode.MATCHING_RETRY_REQUIRED)
        }
        return selectGroup(lifeStage, userType, excludedGroupId = null).group
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun rematch(user: User, lifeStage: LifeStage, userType: UserType): CareGroup {
        return selectForMove(user, lifeStage, userType).group
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun selectForRejoin(user: User, lifeStage: LifeStage, userType: UserType): GroupSelection {
        if (user.status != UserStatus.ACTIVE || !user.onboardingCompleted || user.group != null) matchingFailed()
        return selectGroup(lifeStage, userType, excludedGroupId = user.previousGroupId)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun selectForMove(user: User, lifeStage: LifeStage, userType: UserType): GroupSelection {
        val previousGroupId = user.group?.groupId ?: matchingFailed()
        if (user.status != UserStatus.ACTIVE || !user.onboardingCompleted) matchingFailed()
        return selectGroup(lifeStage, userType, excludedGroupId = previousGroupId, currentGroupId = previousGroupId)
    }

    private fun selectGroup(
        lifeStage: LifeStage,
        userType: UserType,
        excludedGroupId: Long?,
        currentGroupId: Long? = null,
    ): GroupSelection {
        types.lock(userType.typeCode) ?: matchingFailed()
        val candidateId = groups.findAvailableGroupIds(
            lifeStage, userType.typeCode, UserStatus.ACTIVE, CAPACITY, excludedGroupId, PageRequest.ofSize(1),
        ).firstOrNull()

        // 기존 그룹과 대상 그룹을 같은 순서로 잠가 그룹 간 교차 이동을 직렬화한다.
        val locked = listOfNotNull(currentGroupId, candidateId).sorted().associateWith {
            groups.findForUpdate(it) ?: matchingFailed()
        }
        if (candidateId == null) return GroupSelection(groups.save(CareGroup(lifeStage, userType)), created = true)
        if (users.countActiveGroupMembers(candidateId) >= CAPACITY) matchingFailed()
        return GroupSelection(locked.getValue(candidateId), created = false)
    }

    private fun matchingFailed(): Nothing = throw BusinessException(OnboardingErrorCode.MATCHING_RETRY_REQUIRED)

    private companion object {
        const val CAPACITY = 4L
    }
}

data class GroupSelection(val group: CareGroup, val created: Boolean)
