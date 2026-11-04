// 미션 원본을 개인의 하루 또는 그룹의 한 주에 배정한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.DayOfWeek
import java.time.LocalDate

@Entity
@Table(
    name = "mission_assignments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_assignment_user_date", columnNames = ["user_id", "mission_date"]),
        UniqueConstraint(name = "uk_assignment_group_date", columnNames = ["group_id", "mission_date"]),
    ],
    check = [
        CheckConstraint(
            name = "ck_assignment_one_target",
            constraint = "(user_id is not null and group_id is null) or (user_id is null and group_id is not null)",
        ),
    ],
)
class MissionAssignment private constructor(
    mission: Mission,
    missionDate: LocalDate,
    user: User?,
    group: CareGroup?,
) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "assignment_id")
    var assignmentId: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "mission_id", nullable = false, updatable = false)
    var mission: Mission = mission
        protected set

    @field:Column(name = "mission_date", nullable = false, updatable = false)
    var missionDate: LocalDate = missionDate
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", updatable = false)
    var user: User? = user
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "group_id", updatable = false)
    var group: CareGroup? = group
        protected set

    fun isAssignedTo(candidate: User): Boolean {
        val assignedUser = user
        if (assignedUser != null) {
            return assignedUser === candidate || (assignedUser.userId != null && assignedUser.userId == candidate.userId)
        }
        val assignedGroup = group ?: return false
        val currentGroup = candidate.group ?: return false
        return assignedGroup === currentGroup ||
            (assignedGroup.groupId != null && assignedGroup.groupId == currentGroup.groupId)
    }

    companion object {
        fun forUser(mission: Mission, user: User, date: LocalDate): MissionAssignment {
            require(mission.missionType == MissionType.PERSONAL) { "개인에게는 개인 미션만 배정할 수 있습니다." }
            return MissionAssignment(mission, date, user, null)
        }

        fun forGroup(mission: Mission, group: CareGroup, weekStart: LocalDate): MissionAssignment {
            require(mission.missionType == MissionType.GROUP) { "그룹에는 그룹 미션만 배정할 수 있습니다." }
            require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "그룹 미션의 기준 날짜는 월요일이어야 합니다." }
            return MissionAssignment(mission, weekStart, null, group)
        }
    }
}
