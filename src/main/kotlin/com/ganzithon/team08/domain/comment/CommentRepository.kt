// 댓글을 저장하고 인증별 댓글 목록·개수를 조회한다.
package com.ganzithon.team08.domain.comment

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentRepository : JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = ["user"])
    @Query("""
        select c from Comment c where c.certification.certificationId = :certificationId
          and c.user.status = com.ganzithon.team08.domain.user.UserStatus.ACTIVE order by c.commentId
    """)
    fun findByCertification_CertificationIdOrderByCommentIdAsc(certificationId: Long): List<Comment>

    @Query("""
        select count(c) from Comment c where c.certification.certificationId = :certificationId
          and c.user.status = com.ganzithon.team08.domain.user.UserStatus.ACTIVE
    """)
    fun countByCertification_CertificationId(certificationId: Long): Long

    // 피드에서 여러 인증의 댓글 수를 반복 쿼리 없이 한 번에 조회한다.
    @Query(
        "select c.certification.certificationId as certificationId, count(c) as commentCount " +
            "from Comment c where c.certification.certificationId in :certificationIds " +
            "and c.user.status = com.ganzithon.team08.domain.user.UserStatus.ACTIVE " +
            "group by c.certification.certificationId",
    )
    fun countByCertificationIds(@Param("certificationIds") certificationIds: Collection<Long>): List<CertificationCommentCount>
}

interface CertificationCommentCount {
    fun getCertificationId(): Long

    fun getCommentCount(): Long
}
