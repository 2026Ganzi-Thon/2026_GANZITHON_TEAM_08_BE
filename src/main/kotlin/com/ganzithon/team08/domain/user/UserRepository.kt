// 사용자를 저장하고 카카오 식별자로 조회한다.
package com.ganzithon.team08.domain.user

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from User target where target.userId = :id")
    fun findForUpdate(id: Long): User?

    @Query("select u.userId from User u where u.kakaoUserId = :kakaoUserId")
    fun findUserIdByKakaoUserId(kakaoUserId: String): Long?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId in :ids order by u.userId")
    fun findAllForUpdate(ids: Set<Long>): List<User>

    @Query("""
        select u.userId from User u where u.status = :status and not exists (
            select t.taskId from CleanupTask t where t.withdrawnUserId = u.userId
        ) order by u.userId
    """)
    fun findUnscheduledWithdrawalIds(status: UserStatus, page: Pageable): List<Long>

    fun findAllByGroup_GroupIdAndStatusOrderByUserIdAsc(groupId: Long, status: UserStatus): List<User>

    fun countByGroup_GroupIdAndStatus(groupId: Long, status: UserStatus): Long

    fun existsByGroup_GroupIdAndStatus(groupId: Long, status: UserStatus): Boolean

    @Query("""
        select u.userId from User u
        where u.userId > :afterId and u.status = :status and u.onboardingCompleted = true
          and not exists (
            select a.assignmentId from MissionAssignment a where a.user = u and a.missionDate = :date
          )
        order by u.userId
    """)
    fun findMissingPersonalAssignmentIds(afterId: Long, status: UserStatus, date: LocalDate, page: Pageable): List<Long>

    fun findByKakaoUserId(kakaoUserId: String): User?

    fun existsByKakaoUserId(kakaoUserId: String): Boolean
}
