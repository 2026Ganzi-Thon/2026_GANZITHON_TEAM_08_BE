// 기본 유형의 코드와 이름 및 설명을 저장한다.
package com.ganzithon.team08.domain.user

import com.ganzithon.team08.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "user_types")
class UserType(typeCode: String, typeName: String, typeDescription: String) : BaseTimeEntity() {
    @field:Id
    @field:Column(name = "type_code", length = 30, updatable = false)
    @field:NotBlank
    @field:Size(max = 30)
    var typeCode: String = typeCode
        protected set

    @field:Column(name = "type_name", nullable = false, length = 100)
    @field:NotBlank
    @field:Size(max = 100)
    var typeName: String = typeName
        protected set

    @field:Column(name = "type_description", nullable = false, length = 2000)
    @field:NotBlank
    @field:Size(max = 2000)
    var typeDescription: String = typeDescription
        protected set
}
