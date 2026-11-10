// B파트 미션 배정 조회가 연결되기 전까지 앱 구동만 되게 하는 임시 구현이다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import org.springframework.stereotype.Component
import java.time.LocalDate

// TODO: 김회윤님의 MissionAssignmentService 연동이 끝나면 이 구현을 실제 조회 로직으로 교체한다.
@Component
class PendingCurrentAssignmentFinder : CurrentAssignmentFinder {
    override fun findCurrentAssignment(
        userId: Long,
        missionId: Long,
        missionType: MissionType,
        referenceDate: LocalDate,
    ): MissionAssignment =
        throw UnsupportedOperationException("CurrentAssignmentFinder의 실제 구현이 아직 연결되지 않았습니다.")
}
