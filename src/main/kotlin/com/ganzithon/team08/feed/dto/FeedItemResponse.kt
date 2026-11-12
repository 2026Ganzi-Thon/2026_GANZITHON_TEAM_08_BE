// 피드 한 건(인증 1개)의 응답 형태를 표현한다.
package com.ganzithon.team08.feed.dto

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import java.time.OffsetDateTime
import java.time.ZoneId

data class FeedItemResponse(
    val certificationId: Long,
    val nickname: String,
    val missionTitle: String,
    val imageUrl: String,
    val content: String,
    val visibility: Visibility,
    val cheerCount: Long,
    val cheered: Boolean,
    val mine: Boolean,
    val createdAt: OffsetDateTime,
) {
    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun from(certification: Certification, cheerCount: Long, cheered: Boolean, mine: Boolean): FeedItemResponse {
            val certificationId = requireNotNull(certification.certificationId)
            return FeedItemResponse(
                certificationId = certificationId,
                nickname = certification.user.nickname,
                missionTitle = certification.assignment.mission.missionTitle,
                imageUrl = "/api/images/$certificationId",
                content = certification.content,
                visibility = certification.visibility,
                cheerCount = cheerCount,
                cheered = cheered,
                mine = mine,
                createdAt = certification.createdAt.atZone(SEOUL).toOffsetDateTime(),
            )
        }
    }
}
