package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.cleanup.CleanupTask
import com.ganzithon.team08.domain.cleanup.CleanupTaskRepository
import com.ganzithon.team08.domain.cleanup.CleanupTaskType
import com.ganzithon.team08.domain.cleanup.WithdrawalRecords
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WithdrawService(
    private val userRepository: UserRepository,
    private val tasks: CleanupTaskRepository,
    private val records: WithdrawalRecords,
) {
    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findForUpdate(userId) ?: throw BusinessException(CommonErrorCode.NOT_FOUND)
        if (user.status == UserStatus.WITHDRAWN) {
            throw BusinessException(CommonErrorCode.ACCOUNT_WITHDRAWN)
        }
        user.withdraw()
        enqueue(listOf(user))
    }

    @Transactional
    fun enqueueLegacyWithdrawals(): Int {
        val ids = userRepository.findUnscheduledWithdrawalIds(UserStatus.WITHDRAWN, PageRequest.of(0, 25))
        if (ids.isEmpty()) return 0
        val users = userRepository.findAllForUpdate(ids.toSet())
        val queuedIds = tasks.findByWithdrawnUserIdsForUpdate(ids).mapTo(mutableSetOf()) { it.withdrawnUserId }
        val pending = users.filter { it.status == UserStatus.WITHDRAWN && it.userId !in queuedIds }
        enqueue(pending)
        return pending.size
    }

    private fun enqueue(users: List<User>) {
        if (users.isEmpty()) return
        val images = records.imageKeys(users.map { requireNotNull(it.userId) }).groupBy { it.userId }
        tasks.saveAll(users.flatMap { user ->
            val id = requireNotNull(user.userId)
            images[id].orEmpty().map { CleanupTask(CleanupTaskType.DELETE_IMAGE, it.key, id) } +
                CleanupTask(CleanupTaskType.UNLINK_KAKAO, user.kakaoUserId, id)
        })
    }
}
