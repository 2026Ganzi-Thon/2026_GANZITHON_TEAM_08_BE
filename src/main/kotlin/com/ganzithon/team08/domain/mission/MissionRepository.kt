// 미션 원본을 저장하고 개인·그룹 종류별로 조회한다.
package com.ganzithon.team08.domain.mission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MissionRepository : JpaRepository<Mission, Long> {
    @Query("""
        select m from Mission m
        where m.missionType = :type
            and m.description is not null
            and m.certificationGuide is not null
            and m.orderedSteps is not empty
    """)
    fun findAssignmentCandidates(type: MissionType): List<Mission>

    fun findAllByMissionTypeOrderByMissionIdAsc(missionType: MissionType): List<Mission>
}
