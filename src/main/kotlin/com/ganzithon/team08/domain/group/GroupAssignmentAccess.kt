// 배정 트랜잭션에서 그룹 행을 잠근다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.user.UserStatus
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class GroupAssignmentAccess(private val groups: CareGroupRepository) {
    @Transactional(readOnly = true)
    fun findMissingWeeklyAssignmentIds(afterId: Long, week: LocalDate, limit: Int): List<Long> =
        groups.findMissingWeeklyAssignmentIds(afterId, UserStatus.ACTIVE, week, PageRequest.ofSize(limit))

    fun lock(groupId: Long): CareGroup? = groups.findForUpdate(groupId)
}
