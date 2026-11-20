// 미션 인증 등록을 처리한다: 사진·글 검증, 배정 확인, 저장, 중복 변환.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationDetailResponse
import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.comment.CommentRepository
import com.ganzithon.team08.domain.mission.MissionAssignmentService
import com.ganzithon.team08.domain.mission.MissionPeriod
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageSanitizer
import com.ganzithon.team08.global.storage.ImageStorage
import com.ganzithon.team08.global.storage.InvalidImageException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class CertificationService(
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
    private val commentRepository: CommentRepository,
    private val missionAssignmentService: MissionAssignmentService,
    private val imageStorage: ImageStorage,
    private val imageSanitizer: ImageSanitizer,
) {
    @Transactional
    fun register(userId: Long, request: CertificationRegisterRequest): CertificationRegisterResponse {
        val content = validateContent(request.content)
        // 그룹 변경(이동·탈퇴)과 같은 사용자 row 잠금을 사용해 동시성을 맞춘다 — 그룹 스냅샷을
        // 읽는 동안 그룹이 바뀌면 안 되므로, 그룹 변경 트랜잭션도 같은 방식으로 잠궈야 한다.
        val user = userRepository.findForUpdate(userId) ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
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
        val cleaned = try {
            imageSanitizer.sanitize(imageBytes, format)
        } catch (_: InvalidImageException) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_IMAGE)
        }
        val storageKey = imageStorage.save(cleaned, format)
        deleteOnRollback(storageKey)
        val saved = try {
            val certification = Certification(user, assignment, storageKey, content, request.visibility).apply {
                markImageSanitized()
            }
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

    private fun deleteOnRollback(storageKey: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    runCatching { imageStorage.delete(storageKey) }.onFailure {
                        logger.warn("인증 등록 롤백 후 이미지 정리에 실패했습니다. error={}", it.javaClass.simpleName)
                    }
                }
            }
        })
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
        val viewer = userRepository.findForUpdate(viewerId) ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (viewer.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        val certification = getCertificationOrThrow(certificationId)
        if (!certification.isOwnedBy(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        try {
            certification.changeVisibility(visibility)
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_VISIBILITY)
        }
        return toDetailResponse(certification, viewer)
    }

    @Transactional
    fun changeContent(viewerId: Long, certificationId: Long, content: String): CertificationDetailResponse {
        val viewer = userRepository.findForUpdate(viewerId) ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (viewer.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        val certification = getCertificationOrThrow(certificationId)
        if (!certification.isOwnedBy(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        certification.changeContent(validateContent(content))
        return toDetailResponse(certification, viewer)
    }

    private fun toDetailResponse(certification: Certification, viewer: User): CertificationDetailResponse {
        val certificationId = requireNotNull(certification.certificationId)
        val cheerCount = cheerRepository.countByCertification_CertificationId(certificationId)
        val cheered = cheerRepository.existsByUser_UserIdAndCertification_CertificationId(viewer.userId!!, certificationId)
        val commentCount = commentRepository.countByCertification_CertificationId(certificationId)
        return CertificationDetailResponse.from(certification, cheerCount, cheered, commentCount, certification.isOwnedBy(viewer))
    }

    private fun getCertificationOrThrow(certificationId: Long): Certification =
        certificationRepository.findById(certificationId).orElseThrow { BusinessException(CertificationErrorCode.CERT_NOT_FOUND) }

    // 삭제 작업이 대기 중이어도 탈퇴 회원의 인증은 숨긴다.
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
        private val logger = LoggerFactory.getLogger(CertificationService::class.java)
        private const val MAX_CONTENT_LENGTH = 300
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
