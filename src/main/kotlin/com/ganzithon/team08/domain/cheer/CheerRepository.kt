// 응원을 저장하고 인증별 응원 수와 사용자의 응원 여부를 조회한다.
package com.ganzithon.team08.domain.cheer

import org.springframework.data.jpa.repository.JpaRepository

interface CheerRepository : JpaRepository<Cheer, Long> {
    fun existsByUser_UserIdAndCertification_CertificationId(userId: Long, certificationId: Long): Boolean

    fun countByCertification_CertificationId(certificationId: Long): Long
}
