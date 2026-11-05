// 기본 유형을 저장하고 유형 코드로 조회한다.
package com.ganzithon.team08.domain.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserTypeRepository : JpaRepository<UserType, String>
