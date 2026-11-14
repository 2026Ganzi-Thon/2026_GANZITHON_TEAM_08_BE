// 기본 유형을 저장하고 유형 코드로 조회한다.
package com.ganzithon.team08.domain.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserTypeRepository : JpaRepository<UserType, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserType t where t.typeCode = :code")
    fun findForUpdate(code: String): UserType?
}
