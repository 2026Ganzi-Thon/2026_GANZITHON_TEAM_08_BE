// 사용자를 저장하고 카카오 식별자로 조회한다.
package com.ganzithon.team08.domain.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByKakaoUserId(kakaoUserId: String): User?

    fun existsByKakaoUserId(kakaoUserId: String): Boolean
}
