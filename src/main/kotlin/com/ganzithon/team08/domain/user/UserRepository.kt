// 사용자를 저장하고 카카오 식별자로 조회한다.
package com.ganzithon.team08.domain.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByKakaoUserId(kakaoUserId: String): User?

    fun existsByKakaoUserId(kakaoUserId: String): Boolean

    // 인증 제출과 그룹 변경(그룹 이동·탈퇴)이 같은 사용자 row 잠금을 거쳐 순서대로 처리되도록 한다.
    // 그룹 변경 트랜잭션도 같은 방식(사용자 row 비관적 락)으로 잠궈야 경쟁 상태를 막을 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    fun findByIdForUpdate(@Param("userId") userId: Long): User?
}
