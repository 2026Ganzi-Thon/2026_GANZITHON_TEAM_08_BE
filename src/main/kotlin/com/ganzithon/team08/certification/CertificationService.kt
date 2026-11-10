// 미션 인증 등록을 처리한다: 사진·글 검증, 배정 확인, 저장, 중복 변환.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageStorage
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.ZoneId

@Service
class CertificationService(
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val currentAssignmentFinder: CurrentAssignmentFinder,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun register(userId: Long, request: CertificationRegisterRequest): CertificationRegisterResponse {
        val content = validateContent(request.content)
        val user = getUser(userId)
        if (request.visibility == Visibility.GROUP && user.group == null) {
            throw BusinessException(CertificationErrorCode.CERT_INVALID_VISIBILITY)
        }

        val referenceDate = LocalDate.now(SEOUL)
        val assignment = currentAssignmentFinder.findCurrentAssignment(
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
        private val SEOUL = ZoneId.of("Asia/Seoul")
        private const val MAX_CONTENT_LENGTH = 300
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
