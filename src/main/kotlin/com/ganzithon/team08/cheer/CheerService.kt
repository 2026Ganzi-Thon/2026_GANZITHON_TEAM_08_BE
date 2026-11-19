// 인증에 응원을 등록한다: 열람권한·탈퇴작성자 확인, 본인응원·중복응원 거절.
package com.ganzithon.team08.cheer

import com.ganzithon.team08.certification.CertificationInteractionAccess
import com.ganzithon.team08.cheer.dto.CheerResponse
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.global.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CheerService(
    private val cheerRepository: CheerRepository,
    private val interactionAccess: CertificationInteractionAccess,
) {
    @Transactional
    fun register(viewerId: Long, certificationId: Long): CheerResponse {
        val (viewer, certification) = interactionAccess.lockForWrite(viewerId, certificationId)

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

}
