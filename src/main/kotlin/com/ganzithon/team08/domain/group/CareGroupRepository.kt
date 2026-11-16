// 돌봄 그룹을 저장하고 생활 단계와 기본 유형에 맞는 그룹을 조회한다.
package com.ganzithon.team08.domain.group

import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.UserStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import org.springframework.data.jpa.repository.Query

interface CareGroupRepository : JpaRepository<CareGroup, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from CareGroup target where target.groupId = :id")
    fun findForUpdate(id: Long): CareGroup?

    @Query("""
        select g.groupId from CareGroup g
        left join User u on u.group = g and u.status = :activeStatus
        where g.lifeStage = :lifeStage and g.userType.typeCode = :typeCode
          and (:excludedGroupId is null or g.groupId <> :excludedGroupId)
        group by g.groupId
        having count(u) < :capacity
        order by g.groupId
    """)
    fun findAvailableGroupIds(
        lifeStage: LifeStage,
        typeCode: String,
        activeStatus: UserStatus,
        capacity: Long,
        excludedGroupId: Long?,
        page: Pageable,
    ): List<Long>

    @Query("""
        select g.groupId from CareGroup g
        where g.groupId > :afterId
          and exists (select u.userId from User u where u.group = g and u.status = :status)
          and not exists (
            select a.assignmentId from MissionAssignment a where a.group = g and a.missionDate = :week
          )
        order by g.groupId
    """)
    fun findMissingWeeklyAssignmentIds(afterId: Long, status: UserStatus, week: LocalDate, page: Pageable): List<Long>

    fun findAllByLifeStageAndUserType_TypeCodeOrderByGroupIdAsc(
        lifeStage: LifeStage,
        typeCode: String,
    ): List<CareGroup>
}
