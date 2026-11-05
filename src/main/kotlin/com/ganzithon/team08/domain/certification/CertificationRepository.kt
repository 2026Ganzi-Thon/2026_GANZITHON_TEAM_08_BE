// 미션 인증을 저장하고 사용자·미션 종류·기준 날짜별 인증을 조회한다.
package com.ganzithon.team08.domain.certification

import com.ganzithon.team08.domain.mission.MissionType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface CertificationRepository : JpaRepository<Certification, Long> {
    fun findByUser_UserIdAndMissionTypeAndMissionDate(
        userId: Long,
        missionType: MissionType,
        missionDate: LocalDate,
    ): Certification?

    fun existsByUser_UserIdAndMissionTypeAndMissionDate(
        userId: Long,
        missionType: MissionType,
        missionDate: LocalDate,
    ): Boolean
}
