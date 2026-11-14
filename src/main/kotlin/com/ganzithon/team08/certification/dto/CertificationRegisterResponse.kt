// 미션 인증 등록 결과를 응답 형태로 변환한다.
package com.ganzithon.team08.certification.dto

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.mission.MissionType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class CertificationRegisterResponse(
    val certificationId: Long,
    val missionType: MissionType,
    val missionDate: LocalDate,
    val visibility: Visibility,
    val imageUrl: String,
    val content: String,
    val createdAt: OffsetDateTime,
) {
    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun from(certification: Certification): CertificationRegisterResponse {
            val certificationId = requireNotNull(certification.certificationId)
            return CertificationRegisterResponse(
                certificationId = certificationId,
                missionType = certification.missionType,
                missionDate = certification.missionDate,
                visibility = certification.visibility,
                imageUrl = "/api/images/$certificationId",
                content = certification.content,
                createdAt = certification.createdAt.atZone(SEOUL).toOffsetDateTime(),
            )
        }
    }
}
