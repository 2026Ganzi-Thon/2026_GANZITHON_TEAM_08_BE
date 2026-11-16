// 개인·그룹 미션 화면에 배정 상세와 인증 상태를 반환한다.
package com.ganzithon.team08.mission

import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionPeriod
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.http.HttpStatus
import com.ganzithon.team08.global.exception.ErrorCode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class MissionDetails(
    val assignmentId: Long,
    val missionId: Long,
    val missionType: MissionType,
    val missionTitle: String,
    val missionDescription: String,
    val steps: List<String>,
    val certificationGuide: String,
    val missionDate: LocalDate,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
) {
    fun includes(now: Instant): Boolean = now >= startsAt.toInstant() && now < endsAt.toInstant()

    companion object {
        fun from(assignment: MissionAssignment): MissionDetails {
            val mission = assignment.mission
            val date = assignment.missionDate
            val end = if (mission.missionType == MissionType.PERSONAL) date.plusDays(1) else date.plusWeeks(1)
            val steps = mission.steps
            val description = mission.description
            val guide = mission.certificationGuide
            if (description.isNullOrBlank() || steps.isEmpty() || guide.isNullOrBlank()) {
                throw BusinessException(MissionViewErrorCode.MISSION_CONTENT_NOT_READY)
            }
            return MissionDetails(
                checkNotNull(assignment.assignmentId), checkNotNull(mission.missionId), mission.missionType,
                mission.missionTitle, description, steps, guide, date, MissionPeriod.startOf(date), MissionPeriod.startOf(end),
            )
        }
    }
}

data class PersonalMissionResponse(
    @get:com.fasterxml.jackson.annotation.JsonUnwrapped val mission: MissionDetails,
    val completed: Boolean,
    val canCertify: Boolean,
    val certificationId: Long?,
)

data class GroupMissionResponse(
    @get:com.fasterxml.jackson.annotation.JsonUnwrapped val mission: MissionDetails,
    val groupId: Long,
    val completedThisWeek: Boolean,
    val completedCurrentMission: Boolean,
    val canCertify: Boolean,
    val certificationId: Long?,
    val memberCount: Int,
    val completedMemberCount: Int,
    val participationRate: java.math.BigDecimal,
    val members: List<GroupMissionMember>,
)

data class GroupMissionMember(
    val userId: Long,
    val nickname: String,
    val isMe: Boolean,
    val completedCurrentMission: Boolean,
)

enum class MissionViewErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
    MISSION_CONTENT_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "배정된 미션의 수행 안내를 준비 중입니다.");

    override val code: String get() = name
}
