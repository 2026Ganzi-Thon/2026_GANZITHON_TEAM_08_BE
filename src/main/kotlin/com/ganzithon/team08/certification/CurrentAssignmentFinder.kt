// 인증 등록에 쓸 현재 배정을 찾는 경계. 구현은 B파트 미션 배정 조회에 위임한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import java.time.LocalDate

/*
 * MissionAssignmentRepository를 C 서비스에 직접 주입하지 않기 위한 포트다.
 * 구현체는 missionId로 미션을 찾지 못하면 BusinessException(CertificationErrorCode.MISSION_NOT_FOUND)을,
 * referenceDate 기준으로 본인(PERSONAL) 또는 현재 그룹(GROUP)에 배정된 것이 없으면(기간이 지난 배정 포함)
 * BusinessException(CertificationErrorCode.MISSION_NOT_ASSIGNED)을 던진다.
 */
interface CurrentAssignmentFinder {
    fun findCurrentAssignment(
        userId: Long,
        missionId: Long,
        missionType: MissionType,
        referenceDate: LocalDate,
    ): MissionAssignment
}
