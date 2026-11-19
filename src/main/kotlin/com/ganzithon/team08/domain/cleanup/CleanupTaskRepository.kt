// 외부 정리 작업과 재시도 상태를 저장하고 조회한다.
package com.ganzithon.team08.domain.cleanup

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface CleanupTaskRepository : JpaRepository<CleanupTask, Long> {
    fun existsByWithdrawnUserIdAndStatusNot(withdrawnUserId: Long, status: CleanupTaskStatus): Boolean
    fun deleteAllByWithdrawnUserId(withdrawnUserId: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CleanupTask t where t.taskId = :id")
    fun findForUpdate(id: Long): CleanupTask?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CleanupTask t where t.withdrawnUserId in :ids")
    fun findByWithdrawnUserIdsForUpdate(ids: List<Long>): List<CleanupTask>

    @Query("""
        select t.taskId from CleanupTask t
        where t.withdrawnUserId is not null
          and (t.status = :pending or t.nextRetryAt <= :now)
        order by t.taskId
    """)
    fun findDueIds(pending: CleanupTaskStatus, now: Instant, page: Pageable): List<Long>
}
