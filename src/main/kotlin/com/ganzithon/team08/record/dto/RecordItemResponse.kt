// 특정 날짜의 내 인증 한 건을 표현한다.
package com.ganzithon.team08.record.dto

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.mission.MissionType
import java.time.OffsetDateTime
import java.time.ZoneId

data class RecordItemResponse(
    val certificationId: Long,
    val missionType: MissionType,
    val missionTitle: String,
    val imageUrl: String,
    val content: String,
    val visibility: Visibility,
    val cheerCount: Long,
    val createdAt: OffsetDateTime,
) {
    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun from(certification: Certification, cheerCount: Long): RecordItemResponse {
            val certificationId = requireNotNull(certification.certificationId)
            return RecordItemResponse(
                certificationId = certificationId,
                missionType = certification.missionType,
                missionTitle = certification.assignment.mission.missionTitle,
                imageUrl = "/api/images/$certificationId",
                content = certification.content,
                visibility = certification.visibility,
                cheerCount = cheerCount,
                createdAt = certification.createdAt.atZone(SEOUL).toOffsetDateTime(),
            )
        }
    }
}
