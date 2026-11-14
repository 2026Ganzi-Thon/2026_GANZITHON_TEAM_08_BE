// 인증에 응원을 등록한다: 열람권한·탈퇴작성자 확인, 본인응원·중복응원 거절.
package com.ganzithon.team08.cheer

import com.ganzithon.team08.certification.CertificationErrorCode
import com.ganzithon.team08.cheer.dto.CheerResponse
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CheerService(
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) {
    @Transactional
    fun register(viewerId: Long, certificationId: Long): CheerResponse {
        val viewer = getUser(viewerId)
        val certification = getCertificationOrThrow(certificationId)
        requireActiveAuthor(certification)
        if (!certification.isVisibleTo(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)

        try {
            cheerRepository.save(Cheer(viewer, certification))
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(CheerErrorCode.CHEER_SELF)
        } catch (ex: DataIntegrityViolationException) {
            throw BusinessException(CheerErrorCode.CHEER_ALREADY_EXISTS)
        }

        val cheerCount = cheerRepository.countByCertification_CertificationId(certificationId)
        return CheerResponse(cheerCount)
    }

    private fun getUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }

    private fun getCertificationOrThrow(certificationId: Long): Certification =
        certificationRepository.findById(certificationId).orElseThrow { BusinessException(CertificationErrorCode.CERT_NOT_FOUND) }

    // 탈퇴 MVP 단순화: 실제 삭제 대신 조회에서 숨긴다 — 작성자가 탈퇴했으면 존재하지 않는 것처럼 404.
    private fun requireActiveAuthor(certification: Certification) {
        if (certification.user.status != UserStatus.ACTIVE) throw BusinessException(CertificationErrorCode.CERT_NOT_FOUND)
    }
}
