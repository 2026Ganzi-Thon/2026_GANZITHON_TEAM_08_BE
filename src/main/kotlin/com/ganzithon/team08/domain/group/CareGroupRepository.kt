// 돌봄 그룹을 저장하고 생활 단계와 기본 유형에 맞는 그룹을 조회한다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.UserStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query

interface CareGroupRepository : JpaRepository<CareGroup, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from CareGroup target where target.groupId = :id")
    fun findForUpdate(id: Long): CareGroup?

    @Query("""
        select g.groupId from CareGroup g
        left join User u on u.group = g and u.status = :activeStatus
        where g.lifeStage = :lifeStage and g.userType.typeCode = :typeCode
        group by g.groupId
        having count(u) < :capacity
        order by g.groupId
    """)
    fun findAvailableGroupIds(
        lifeStage: LifeStage,
        typeCode: String,
        activeStatus: UserStatus,
        capacity: Long,
        page: Pageable,
    ): List<Long>

    fun findAllByLifeStageAndUserType_TypeCodeOrderByGroupIdAsc(
        lifeStage: LifeStage,
        typeCode: String,
    ): List<CareGroup>
}
