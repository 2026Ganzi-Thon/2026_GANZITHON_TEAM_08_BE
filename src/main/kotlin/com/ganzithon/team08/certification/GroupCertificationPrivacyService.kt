// 그룹 이동 트랜잭션에서 기존 그룹 공개 인증을 비공개로 전환한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class GroupCertificationPrivacyService(private val certifications: CertificationRepository) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun makeGroupCertificationsPrivate(userId: Long) {
        certifications.findAllByUser_UserIdAndVisibility(userId, Visibility.GROUP)
            .forEach { it.changeVisibility(Visibility.PRIVATE) }
    }
}
