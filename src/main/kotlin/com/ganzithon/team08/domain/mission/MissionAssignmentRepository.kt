// 미션 배정을 저장하고 개인의 일별 또는 그룹의 주별 배정을 조회한다.
package com.ganzithon.team08.domain.mission

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface MissionAssignmentRepository : JpaRepository<MissionAssignment, Long> {
    fun findByUser_UserIdAndMissionDate(userId: Long, missionDate: LocalDate): MissionAssignment?

    fun findByGroup_GroupIdAndMissionDate(groupId: Long, missionDate: LocalDate): MissionAssignment?

    fun findByMission_MissionIdAndUser_UserIdAndMissionDate(
        missionId: Long,
        userId: Long,
        missionDate: LocalDate,
    ): MissionAssignment?

    fun findByMission_MissionIdAndGroup_GroupIdAndMissionDate(
        missionId: Long,
        groupId: Long,
        missionDate: LocalDate,
    ): MissionAssignment?
}
