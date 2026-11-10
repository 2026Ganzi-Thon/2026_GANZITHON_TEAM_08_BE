// 미션 인증 등록 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.global.auth.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CertificationController(
    private val certificationService: CertificationService,
) {
    @PostMapping("/api/certifications", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @CurrentUserId userId: Long,
        @ModelAttribute request: CertificationRegisterRequest,
    ): CertificationRegisterResponse = certificationService.register(userId, request)
}
