// 미션 인증 등록 멀티파트 요청을 표현한다.
package com.ganzithon.team08.certification.dto

import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.mission.MissionType
import org.springframework.web.multipart.MultipartFile

data class CertificationRegisterRequest(
    val image: MultipartFile,
    val content: String,
    val missionType: MissionType,
    val missionId: Long,
    val visibility: Visibility = Visibility.PRIVATE,
)
