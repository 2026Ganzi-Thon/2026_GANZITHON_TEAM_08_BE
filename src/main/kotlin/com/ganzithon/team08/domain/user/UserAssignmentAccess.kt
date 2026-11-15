// 배정 트랜잭션에서 사용자 잠금과 활성 그룹원 확인을 제공한다.
package com.ganzithon.team08.domain.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAssignmentAccess(private val userRepository: UserRepository) {
    @Transactional(readOnly = true)
    fun find(userId: Long): User? = userRepository.findById(userId).orElse(null)

    fun lock(userId: Long): User? = userRepository.findForUpdate(userId)

    fun countActiveGroupMembers(groupId: Long): Long =
        userRepository.countByGroup_GroupIdAndStatus(groupId, UserStatus.ACTIVE)

    fun hasActiveGroupMember(groupId: Long): Boolean =
        userRepository.existsByGroup_GroupIdAndStatus(groupId, UserStatus.ACTIVE)
}
