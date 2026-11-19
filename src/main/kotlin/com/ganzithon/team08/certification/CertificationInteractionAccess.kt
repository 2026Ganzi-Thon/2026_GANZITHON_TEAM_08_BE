// 댓글과 응원 등록을 탈퇴 처리와 직렬화하고 인증 열람 권한을 확인한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.stereotype.Component

@Component
class CertificationInteractionAccess(
    private val users: UserRepository,
    private val certifications: CertificationRepository,
) {
    fun lockForWrite(viewerId: Long, certificationId: Long): Pair<User, Certification> {
        val authorId = certifications.findAuthorId(certificationId)
        val locked = users.findAllForUpdate(setOfNotNull(viewerId, authorId)).associateBy { it.userId }
        val viewer = locked[viewerId] ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (viewer.status != UserStatus.ACTIVE) throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        if (locked[authorId]?.status != UserStatus.ACTIVE) throw BusinessException(CertificationErrorCode.CERT_NOT_FOUND)
        val certification = certifications.findById(certificationId).orElseThrow {
            BusinessException(CertificationErrorCode.CERT_NOT_FOUND)
        }
        if (!certification.isVisibleTo(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)
        return viewer to certification
    }
}
