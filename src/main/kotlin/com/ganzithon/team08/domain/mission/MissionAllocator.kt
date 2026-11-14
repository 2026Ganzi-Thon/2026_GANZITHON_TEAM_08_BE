// 현재 기간의 개인·그룹 미션을 중복 없이 배정한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
// 바깥 작업과 합칠 때도 대상의 최신 상태를 읽도록 호출자 트랜잭션에 같은 격리 수준을 적용한다.
@Transactional(isolation = Isolation.READ_COMMITTED)
class MissionAllocator(
    private val missions: MissionRepository,
    private val assignments: MissionAssignmentRepository,
    private val users: UserAssignmentAccess,
    private val groups: GroupAssignmentAccess,
) {
    fun ensurePersonalAssignment(userId: Long, referenceTime: Instant): AssignmentResult {
        val user = users.lock(userId) ?: throw BusinessException(MissionErrorCode.TARGET_INELIGIBLE)
        if (user.status != UserStatus.ACTIVE || !user.onboardingCompleted) {
            throw BusinessException(MissionErrorCode.TARGET_INELIGIBLE)
        }

        val date = MissionPeriod.dateAt(referenceTime)
        assignments.findPersonalForUpdate(userId, date)?.let { return AssignmentResult.from(it, created = false) }

        val preference = when (user.carePreference) {
            AnswerChoice.A -> MissionPreference.A
            AnswerChoice.B -> MissionPreference.B
            null -> MissionPreference.COMMON
        }
        val all = missions.findAssignmentCandidates(MissionType.PERSONAL)
        val candidates = all.filter { (it.preference ?: MissionPreference.COMMON) == preference }.ifEmpty {
            all.filter { (it.preference ?: MissionPreference.COMMON) == MissionPreference.COMMON }
        }
        val previousId = assignments.findByUser_UserIdAndMissionDate(userId, date.minusDays(1))?.mission?.missionId
        val mission = choose(candidates, previousId)
        return AssignmentResult.from(assignments.save(MissionAssignment.forUser(mission, user, date)), created = true)
    }

    fun ensureGroupAssignment(groupId: Long, referenceTime: Instant): AssignmentResult {
        val group = groups.lock(groupId) ?: throw BusinessException(MissionErrorCode.TARGET_INELIGIBLE)
        if (!users.hasActiveGroupMember(groupId)) throw BusinessException(MissionErrorCode.TARGET_INELIGIBLE)

        val week = MissionPeriod.weekStart(MissionPeriod.dateAt(referenceTime))
        assignments.findGroupForUpdate(groupId, week)?.let { return AssignmentResult.from(it, created = false) }

        val candidates = missions.findAssignmentCandidates(MissionType.GROUP)
        val previousId = assignments.findByGroup_GroupIdAndMissionDate(groupId, week.minusWeeks(1))?.mission?.missionId
        val mission = choose(candidates, previousId)
        return AssignmentResult.from(assignments.save(MissionAssignment.forGroup(mission, group, week)), created = true)
    }

    private fun choose(candidates: List<Mission>, previousId: Long?): Mission {
        if (candidates.isEmpty()) throw BusinessException(MissionErrorCode.MISSION_POOL_EMPTY)
        if (candidates.size == 1) return candidates.single()
        return candidates.filter { it.missionId != previousId }.random()
    }
}

data class AssignmentResult(
    val assignmentId: Long,
    val missionId: Long,
    val missionDate: LocalDate,
    val created: Boolean,
) {
    companion object {
        internal fun from(assignment: MissionAssignment, created: Boolean): AssignmentResult =
            AssignmentResult(
                requireNotNull(assignment.assignmentId),
                requireNotNull(assignment.mission.missionId),
                assignment.missionDate,
                created,
            )
    }
}
