// 미션 화면에 필요한 본인의 인증과 그룹 완료 사용자 ID를 조회한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.mission.MissionType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class MissionCertificationQueries(private val certifications: CertificationRepository) {
    fun ownCertificationId(userId: Long, type: MissionType, date: LocalDate): Long? =
        certifications.findByUser_UserIdAndMissionTypeAndMissionDate(userId, type, date)?.certificationId

    fun groupCompletedUserIds(assignmentId: Long, groupId: Long): Set<Long> =
        certifications.findGroupAssignmentCompletedUserIds(assignmentId, groupId).toSet()
}
