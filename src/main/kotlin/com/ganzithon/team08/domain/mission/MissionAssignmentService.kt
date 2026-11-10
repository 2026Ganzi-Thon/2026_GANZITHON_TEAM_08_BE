// 현재 사용자(개인) 또는 현재 그룹(그룹)의 기준일 미션 배정을 조회한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.certification.CertificationErrorCode
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class MissionAssignmentService(
    private val missionRepository: MissionRepository,
    private val missionAssignmentRepository: MissionAssignmentRepository,
    private val userRepository: UserRepository,
) {
    /*
     * 인증 등록에서 쓰는 배정 조회 포트의 실제 구현이다.
     * missionId로 미션을 찾지 못하거나 요청한 missionType과 다르면 MISSION_NOT_FOUND를,
     * referenceDate 기준으로 본인(PERSONAL) 또는 현재 그룹(GROUP)에 유효한 배정이 없으면
     * (기간이 지난 배정 포함) MISSION_NOT_ASSIGNED를 던진다.
     */
    @Transactional(readOnly = true)
    fun findCurrentAssignment(
        userId: Long,
        missionId: Long,
        missionType: MissionType,
        referenceDate: LocalDate,
    ): MissionAssignment {
        val mission = missionRepository.findById(missionId)
            .filter { it.missionType == missionType }
            .orElseThrow { BusinessException(CertificationErrorCode.MISSION_NOT_FOUND) }

        val assignment = when (missionType) {
            MissionType.PERSONAL -> missionAssignmentRepository.findByMission_MissionIdAndUser_UserIdAndMissionDate(
                mission.missionId!!,
                userId,
                referenceDate,
            )
            MissionType.GROUP -> {
                val groupId = userRepository.findById(userId).orElse(null)?.group?.groupId
                groupId?.let {
                    missionAssignmentRepository.findByMission_MissionIdAndGroup_GroupIdAndMissionDate(
                        mission.missionId!!,
                        it,
                        weekStart(referenceDate),
                    )
                }
            }
        }

        return assignment ?: throw BusinessException(CertificationErrorCode.MISSION_NOT_ASSIGNED)
    }

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
