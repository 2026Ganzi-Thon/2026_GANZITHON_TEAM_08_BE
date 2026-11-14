// 사용자를 저장하고 카카오 식별자로 조회한다.
package com.ganzithon.team08.domain.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from User target where target.userId = :id")
    fun findForUpdate(id: Long): User?

    fun countByGroup_GroupIdAndStatus(groupId: Long, status: UserStatus): Long

    fun existsByGroup_GroupIdAndStatus(groupId: Long, status: UserStatus): Boolean

    fun findByKakaoUserId(kakaoUserId: String): User?

    fun existsByKakaoUserId(kakaoUserId: String): Boolean
}
