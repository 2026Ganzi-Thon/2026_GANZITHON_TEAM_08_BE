// 현재 미션을 읽고 해당 기간에 배정이 없을 때만 보충한다.
package com.ganzithon.team08.mission

import com.ganzithon.team08.certification.MissionCertificationQueries
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.mission.MissionAssignmentRepository
import com.ganzithon.team08.domain.mission.MissionPeriod
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import com.ganzithon.team08.group.MyGroupErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class CurrentMissionService(
    manager: PlatformTransactionManager,
    private val users: UserAssignmentAccess,
    private val assignments: MissionAssignmentRepository,
    private val certifications: MissionCertificationQueries,
    private val allocator: MissionAllocator,
) {
    private val read = TransactionTemplate(manager).apply {
        isReadOnly = true
        isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
    }
    private val repair = TransactionTemplate(manager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    fun personal(userId: Long, now: Instant = Instant.now()): PersonalMissionResponse {
        val date = MissionPeriod.dateAt(now)
        return read.execute {
            requireEligible(users.find(userId))
            personalResponse(userId, now)
        } ?: checkNotNull(repair.execute {
            requireEligible(users.lock(userId))
            allocator.ensurePersonalAssignment(userId, now)
            checkNotNull(personalResponse(userId, now)) { "개인 미션 배정을 읽을 수 없습니다: ${date}" }
        })
    }

    fun group(userId: Long, now: Instant = Instant.now()): GroupMissionResponse =
        read.execute { groupResponse(userId, groupId(requireEligible(users.find(userId))), now) }
            ?: checkNotNull(repair.execute {
                val currentGroup = groupId(requireEligible(users.lock(userId)))
                allocator.ensureGroupAssignment(currentGroup, now)
                checkNotNull(groupResponse(userId, currentGroup, now))
            })

    private fun personalResponse(userId: Long, now: Instant): PersonalMissionResponse? {
        val date = MissionPeriod.dateAt(now)
        val assignment = assignments.findByUser_UserIdAndMissionDate(userId, date) ?: return null
        val details = MissionDetails.from(assignment)
        val certification = certifications.ownCertificationId(userId, MissionType.PERSONAL, date)
        return PersonalMissionResponse(details, certification != null, certification == null && details.includes(now), certification)
    }

    private fun groupResponse(userId: Long, groupId: Long, now: Instant): GroupMissionResponse? {
        val week = MissionPeriod.weekStart(MissionPeriod.dateAt(now))
        val assignment = assignments.findByGroup_GroupIdAndMissionDate(groupId, week) ?: return null
        val details = MissionDetails.from(assignment)
        val certification = certifications.ownCertificationId(userId, MissionType.GROUP, week)
        val completedIds = certifications.groupCompletedUserIds(details.assignmentId, groupId)
        val members = users.findActiveGroupMembers(groupId).map {
            val id = checkNotNull(it.userId)
            GroupMissionMember(id, it.nickname, id == userId, id in completedIds)
        }
        val completed = members.count { it.completedCurrentMission }
        val rate = if (members.isEmpty()) BigDecimal.ZERO else BigDecimal.valueOf(completed * 100L)
            .divide(BigDecimal.valueOf(members.size.toLong()), 1, RoundingMode.HALF_UP)
        return GroupMissionResponse(
            details, groupId, certification != null, userId in completedIds,
            certification == null && details.includes(now), certification, members.size, completed, rate, members,
        )
    }

    private fun requireEligible(user: User?): User {
        if (user == null) throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        if (!user.onboardingCompleted) throw BusinessException(MyGroupErrorCode.ONBOARDING_REQUIRED)
        return user
    }

    private fun groupId(user: User): Long =
        user.group?.groupId ?: throw BusinessException(MyGroupErrorCode.GROUP_NOT_FOUND)
}
