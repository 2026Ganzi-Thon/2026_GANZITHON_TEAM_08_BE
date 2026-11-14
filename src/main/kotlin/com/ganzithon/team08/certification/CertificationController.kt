// 미션 인증 등록·조회·공개범위 변경과 인증 이미지 조회 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationDetailResponse
import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.certification.dto.CertificationVisibilityChangeRequest
import com.ganzithon.team08.global.auth.CurrentUserId
import com.ganzithon.team08.global.storage.ImageFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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

    @GetMapping("/api/certifications/{certificationId}")
    fun detail(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
    ): CertificationDetailResponse = certificationService.getDetail(userId, certificationId)

    @PatchMapping("/api/certifications/{certificationId}/visibility")
    fun changeVisibility(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
        @RequestBody request: CertificationVisibilityChangeRequest,
    ): CertificationDetailResponse = certificationService.changeVisibility(userId, certificationId, request.visibility)

    @GetMapping("/api/images/{certificationId}")
    fun image(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
    ): ResponseEntity<ByteArray> {
        val (bytes, format) = certificationService.getImage(userId, certificationId)
        return ResponseEntity.ok().contentType(mediaTypeOf(format)).body(bytes)
    }

    private fun mediaTypeOf(format: ImageFormat): MediaType = when (format) {
        ImageFormat.JPEG -> MediaType.IMAGE_JPEG
        ImageFormat.PNG -> MediaType.IMAGE_PNG
        ImageFormat.WEBP -> MediaType.valueOf("image/webp")
    }
}
