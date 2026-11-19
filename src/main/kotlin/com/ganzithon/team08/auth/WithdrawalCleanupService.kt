// 탈퇴 정리 작업의 선점, 재시도 기록, 회원 데이터 최종 삭제를 맡는다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.cleanup.CleanupTaskRepository
import com.ganzithon.team08.domain.cleanup.CleanupTaskStatus
import com.ganzithon.team08.domain.cleanup.CleanupTaskType
import com.ganzithon.team08.domain.cleanup.WithdrawalRecords
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class WithdrawalCleanupService(
    private val tasks: CleanupTaskRepository,
    private val users: UserRepository,
    private val records: WithdrawalRecords,
) {
    @Transactional(readOnly = true)
    fun dueIds(now: Instant): List<Long> = tasks.findDueIds(CleanupTaskStatus.PENDING, now, PageRequest.of(0, 25))

    @Transactional
    fun claim(id: Long, now: Instant): ClaimedCleanup? {
        val task = tasks.findForUpdate(id) ?: return null
        val userId = task.withdrawnUserId ?: return null
        val lease = now.plusSeconds(120)
        when (task.status) {
            CleanupTaskStatus.PENDING -> task.start(lease)
            CleanupTaskStatus.FAILED -> {
                if (task.nextRetryAt?.isAfter(now) != false) return null
                task.start(lease)
            }
            CleanupTaskStatus.PROCESSING -> {
                if (task.nextRetryAt?.isAfter(now) != false) return null
                task.reclaim(now, lease)
            }
            CleanupTaskStatus.COMPLETED -> return null
        }
        return ClaimedCleanup(id, userId, task.taskType, task.targetReference, task.retryCount)
    }

    @Transactional
    fun finish(claim: ClaimedCleanup) {
        val user = users.findForUpdate(claim.userId) ?: return
        check(user.status == UserStatus.WITHDRAWN)
        val task = tasks.findForUpdate(claim.id) ?: return
        if (task.status != CleanupTaskStatus.PROCESSING || task.retryCount != claim.attempt) return
        task.complete()
        if (!tasks.existsByWithdrawnUserIdAndStatusNot(claim.userId, CleanupTaskStatus.COMPLETED)) {
            records.deleteOwnedData(claim.userId)
            tasks.deleteAllByWithdrawnUserId(claim.userId)
            users.delete(user)
        }
    }

    @Transactional
    fun retry(claim: ClaimedCleanup, reason: String, now: Instant) {
        val task = tasks.findForUpdate(claim.id) ?: return
        if (task.status != CleanupTaskStatus.PROCESSING || task.retryCount != claim.attempt) return
        val delay = minOf(3600L, 30L shl minOf(claim.attempt, 7))
        task.fail(reason, now.plusSeconds(delay))
    }
}

data class ClaimedCleanup(val id: Long, val userId: Long, val type: CleanupTaskType, val target: String, val attempt: Int)
