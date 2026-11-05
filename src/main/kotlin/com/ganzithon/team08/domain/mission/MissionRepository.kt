// 미션 원본을 저장하고 개인·그룹 종류별로 조회한다.
package com.ganzithon.team08.domain.mission

import org.springframework.data.jpa.repository.JpaRepository

interface MissionRepository : JpaRepository<Mission, Long> {
    fun findAllByMissionTypeOrderByMissionIdAsc(missionType: MissionType): List<Mission>
}
