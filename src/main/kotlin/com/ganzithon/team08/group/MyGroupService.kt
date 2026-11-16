// 현재 그룹과 활성 멤버를 조회하고 그룹 탈퇴의 상태 변경을 함께 저장한다.
package com.ganzithon.team08.group

import com.ganzithon.team08.certification.GroupCertificationPrivacyService
import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.group.DefaultGroupMatchingService
import com.ganzithon.team08.domain.group.GroupSelection
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.onboarding.OnboardingErrorCode
import org.springframework.dao.PessimisticLockingFailureException
import java.time.Instant
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class MyGroupService(
    private val users: UserAssignmentAccess,
    private val groups: GroupAssignmentAccess,
    private val privacy: GroupCertificationPrivacyService,
    private val matching: DefaultGroupMatchingService,
    private val allocator: MissionAllocator,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun getMyGroup(userId: Long): MyGroupResponse {
        val user = requireEligible(users.find(userId))
        val members = user.group?.groupId?.let(users::findActiveGroupMembers).orEmpty()
        return MyGroupResponse.from(user, members)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun leaveGroup(userId: Long, expectedGroupId: Long): LeaveGroupResponse {
        if (expectedGroupId <= 0) throw BusinessException(MyGroupErrorCode.INVALID_GROUP_ID)
        val user = requireEligible(users.lock(userId))
        val currentGroupId = user.group?.groupId ?: return LeaveGroupResponse(expectedGroupId)
        if (currentGroupId != expectedGroupId) throw BusinessException(MyGroupErrorCode.GROUP_CHANGED)

        // 그룹 매칭과 같은 사용자 → 그룹 순서로 잠근다.
        checkNotNull(groups.lock(currentGroupId))
        user.leaveGroup()
        privacy.makeGroupCertificationsPrivate(userId)
        return LeaveGroupResponse(currentGroupId)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun match(userId: Long): GroupMatchResponse = withMatchingErrors {
        val now = Instant.now()
        val user = requireEligible(users.lock(userId))
        if (user.group != null) throw BusinessException(MyGroupErrorCode.GROUP_ALREADY_JOINED)
        val selection = matching.selectForRejoin(user, checkNotNull(user.lifeStage), checkNotNull(user.userType))
        user.joinGroup(selection.group)
        matchResponse(selection, now)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun rematch(userId: Long, expectedGroupId: Long): GroupMatchResponse = withMatchingErrors {
        if (expectedGroupId <= 0) throw BusinessException(MyGroupErrorCode.INVALID_GROUP_ID)
        val now = Instant.now()
        val user = requireEligible(users.lock(userId))
        val currentId = user.group?.groupId ?: throw BusinessException(MyGroupErrorCode.GROUP_NOT_FOUND)
        if (currentId != expectedGroupId) throw BusinessException(MyGroupErrorCode.GROUP_CHANGED)
        val selection = matching.selectForMove(user, checkNotNull(user.lifeStage), checkNotNull(user.userType))
        user.moveGroup(selection.group)
        privacy.makeGroupCertificationsPrivate(userId)
        matchResponse(selection, now, currentId)
    }

    private fun matchResponse(selection: GroupSelection, now: Instant, previousId: Long? = null): GroupMatchResponse {
        val group = selection.group
        val groupId = checkNotNull(group.groupId)
        allocator.ensureGroupAssignment(groupId, now)
        return GroupMatchResponse(
            groupId, group.lifeStage, group.userType.typeCode, selection.created,
            users.countActiveGroupMembers(groupId), previousId, previousId != null,
        )
    }

    private fun withMatchingErrors(action: () -> GroupMatchResponse): GroupMatchResponse = try {
        action()
    } catch (_: PessimisticLockingFailureException) {
        throw BusinessException(OnboardingErrorCode.MATCHING_RETRY_REQUIRED)
    }

    private fun requireEligible(user: User?): User {
        if (user == null) throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        if (!user.onboardingCompleted) throw BusinessException(MyGroupErrorCode.ONBOARDING_REQUIRED)
        return user
    }
}
