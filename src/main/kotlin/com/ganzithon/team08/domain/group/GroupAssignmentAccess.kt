// 배정 트랜잭션에서 그룹 행을 잠근다.
package com.ganzithon.team08.domain.group

import org.springframework.stereotype.Service

@Service
class GroupAssignmentAccess(private val groups: CareGroupRepository) {
    fun lock(groupId: Long): CareGroup? = groups.findForUpdate(groupId)
}
