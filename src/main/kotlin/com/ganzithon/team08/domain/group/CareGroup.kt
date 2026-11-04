// 생활 단계와 기본 유형을 기준으로 구성한 돌봄 그룹을 저장한다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.UserType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "care_groups", indexes = [Index(name = "idx_group_matching", columnList = "life_stage,type_code")])
class CareGroup(lifeStage: LifeStage, userType: UserType) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "group_id")
    var groupId: Long? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "life_stage", nullable = false, length = 30, updatable = false)
    var lifeStage: LifeStage = lifeStage
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "type_code", nullable = false, updatable = false)
    var userType: UserType = userType
        protected set
}
