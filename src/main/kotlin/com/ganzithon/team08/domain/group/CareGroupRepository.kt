// 돌봄 그룹을 저장하고 생활 단계와 기본 유형에 맞는 그룹을 조회한다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.user.LifeStage
import org.springframework.data.jpa.repository.JpaRepository

interface CareGroupRepository : JpaRepository<CareGroup, Long> {
    fun findAllByLifeStageAndUserType_TypeCodeOrderByGroupIdAsc(
        lifeStage: LifeStage,
        typeCode: String,
    ): List<CareGroup>
}
