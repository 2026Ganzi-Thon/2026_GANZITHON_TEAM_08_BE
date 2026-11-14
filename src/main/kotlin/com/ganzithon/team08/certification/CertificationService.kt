// 미션 인증 등록을 처리한다: 사진·글 검증, 배정 확인, 저장, 중복 변환.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationDetailResponse
import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.mission.MissionAssignmentService
import com.ganzithon.team08.domain.mission.MissionPeriod
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageStorage
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class CertificationService(
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
    private val missionAssignmentService: MissionAssignmentService,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun register(userId: Long, request: CertificationRegisterRequest): CertificationRegisterResponse {
        val content = validateContent(request.content)
        val user = getUser(userId)
        if (request.visibility == Visibility.GROUP && user.group == null) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_VISIBILITY)
        }

        val referenceDate = MissionPeriod.dateAt(Instant.now())
        val assignment = missionAssignmentService.findCurrentAssignment(
            userId,
            request.missionId,
            request.missionType,
            referenceDate,
        )

        val (imageBytes, format) = validateImage(request.image)
        val storageKey = imageStorage.save(imageBytes, format)
        val saved = try {
            val certification = Certification(user, assignment, storageKey, content, request.visibility)
            certificationRepository.save(certification)
        } catch (ex: DataIntegrityViolationException) {
            imageStorage.delete(storageKey)
            throw BusinessException(CertificationErrorCode.CERT_ALREADY_EXISTS)
        } catch (ex: RuntimeException) {
            imageStorage.delete(storageKey)
            throw ex
        }

        return CertificationRegisterResponse.from(saved)
    }

    @Transactional(readOnly = true)
    fun getDetail(viewerId: Long, certificationId: Long): CertificationDetailResponse {
        val viewer = getUser(viewerId)
        val certification = getCertificationOrThrow(certificationId)
        requireActiveAuthor(certification)
        if (!certification.isVisibleTo(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        return toDetailResponse(certification, viewer)
    }

    @Transactional(readOnly = true)
    fun getImage(viewerId: Long, certificationId: Long): Pair<ByteArray, ImageFormat> {
        val viewer = getUser(viewerId)
        val certification = getCertificationOrThrow(certificationId)
        requireActiveAuthor(certification)
        if (!certification.isVisibleTo(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        val bytes = imageStorage.load(certification.imageStorageKey)
        val format = requireNotNull(ImageFormat.fromExtension(certification.imageStorageKey.substringAfterLast('.')))
        return bytes to format
    }

    @Transactional
    fun changeVisibility(viewerId: Long, certificationId: Long, visibility: Visibility): CertificationDetailResponse {
        val viewer = getUser(viewerId)
        val certification = getCertificationOrThrow(certificationId)
        if (!certification.isOwnedBy(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        try {
            certification.changeVisibility(visibility)
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_VISIBILITY)
        }
        return toDetailResponse(certification, viewer)
    }

    private fun toDetailResponse(certification: Certification, viewer: User): CertificationDetailResponse {
        val certificationId = requireNotNull(certification.certificationId)
        val cheerCount = cheerRepository.countByCertification_CertificationId(certificationId)
        val cheered = cheerRepository.existsByUser_UserIdAndCertification_CertificationId(viewer.userId!!, certificationId)
        return CertificationDetailResponse.from(certification, cheerCount, cheered, certification.isOwnedBy(viewer))
    }

    private fun getCertificationOrThrow(certificationId: Long): Certification =
        certificationRepository.findById(certificationId).orElseThrow { BusinessException(CertificationErrorCode.CERT_NOT_FOUND) }

    // 탈퇴 MVP 단순화: 실제 삭제 대신 조회에서 숨긴다 — 작성자가 탈퇴했으면 존재하지 않는 것처럼 404.
    private fun requireActiveAuthor(certification: Certification) {
        if (certification.user.status != UserStatus.ACTIVE) throw BusinessException(CertificationErrorCode.CERT_NOT_FOUND)
    }

    private fun getUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }

    private fun validateContent(content: String): String {
        if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_CONTENT)
        }
        return content
    }

    private fun validateImage(image: MultipartFile): Pair<ByteArray, ImageFormat> {
        if (image.isEmpty) throw BusinessException(CertificationErrorCode.CERT_INVALID_IMAGE)
        val bytes = image.bytes
        if (bytes.size > MAX_IMAGE_BYTES) throw BusinessException(CertificationErrorCode.CERT_INVALID_IMAGE)
        val format = ImageFormat.detect(bytes) ?: throw BusinessException(CertificationErrorCode.CERT_INVALID_IMAGE)
        return bytes to format
    }

    companion object {
        private const val MAX_CONTENT_LENGTH = 300
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
