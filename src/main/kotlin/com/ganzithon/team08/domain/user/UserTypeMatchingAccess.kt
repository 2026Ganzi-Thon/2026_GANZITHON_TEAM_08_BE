// 같은 유형의 그룹 매칭을 직렬화할 때 유형 행을 잠근다.
package com.ganzithon.team08.domain.user

import org.springframework.stereotype.Service

@Service
class UserTypeMatchingAccess(private val types: UserTypeRepository) {
    fun lock(typeCode: String): UserType? = types.findForUpdate(typeCode)
}
