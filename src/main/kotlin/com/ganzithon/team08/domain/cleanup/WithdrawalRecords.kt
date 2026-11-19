// 탈퇴 작업 등록과 회원 데이터 파기에 필요한 여러 테이블의 쿼리를 제공한다.
package com.ganzithon.team08.domain.cleanup

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class WithdrawalRecords(private val entityManager: EntityManager) {
    fun imageKeys(userIds: List<Long>): List<WithdrawalImage> = entityManager.createQuery(
        """
            select new com.ganzithon.team08.domain.cleanup.WithdrawalImage(c.user.userId, c.imageStorageKey)
            from Certification c where c.user.userId in :ids
        """, WithdrawalImage::class.java,
    ).setParameter("ids", userIds).resultList

    fun deleteOwnedData(userId: Long) {
        execute("""
            delete from Cheer c where c.user.userId = :id or c.certification.certificationId in
                (select target.certificationId from Certification target where target.user.userId = :id)
        """, userId)
        execute("""
            delete from Comment c where c.user.userId = :id or c.certification.certificationId in
                (select target.certificationId from Certification target where target.user.userId = :id)
        """, userId)
        execute("delete from Certification c where c.user.userId = :id", userId)
        execute("delete from OnboardingAnswer a where a.user.userId = :id", userId)
        execute("delete from MissionAssignment a where a.user.userId = :id", userId)
    }

    private fun execute(query: String, userId: Long) {
        entityManager.createQuery(query).setParameter("id", userId).executeUpdate()
    }
}

data class WithdrawalImage(val userId: Long, val key: String)
