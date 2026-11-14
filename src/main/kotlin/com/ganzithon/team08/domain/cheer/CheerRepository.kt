// 응원을 저장하고 인증별 응원 수와 사용자의 응원 여부를 조회한다.
package com.ganzithon.team08.domain.cheer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CheerRepository : JpaRepository<Cheer, Long> {
    fun existsByUser_UserIdAndCertification_CertificationId(userId: Long, certificationId: Long): Boolean

    fun countByCertification_CertificationId(certificationId: Long): Long

    // 피드·날짜별 기록 등 여러 인증을 한 번에 보여줄 때 반복 쿼리 없이 인증별 응원 수를 모아 조회한다.
    @Query(
        "select c.certification.certificationId as certificationId, count(c) as cheerCount " +
            "from Cheer c where c.certification.certificationId in :certificationIds " +
            "group by c.certification.certificationId",
    )
    fun countByCertificationIds(@Param("certificationIds") certificationIds: Collection<Long>): List<CertificationCheerCount>

    // 피드에서 viewer가 응원한 인증 id만 추려 반복 쿼리 없이 응원 여부를 판별한다.
    @Query(
        "select c.certification.certificationId from Cheer c " +
            "where c.user.userId = :userId and c.certification.certificationId in :certificationIds",
    )
    fun findCertificationIdsCheeredBy(
        @Param("userId") userId: Long,
        @Param("certificationIds") certificationIds: Collection<Long>,
    ): List<Long>
}

interface CertificationCheerCount {
    fun getCertificationId(): Long

    fun getCheerCount(): Long
}
