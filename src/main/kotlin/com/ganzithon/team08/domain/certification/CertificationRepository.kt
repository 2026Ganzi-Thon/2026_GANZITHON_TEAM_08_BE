// 미션 인증을 저장하고 사용자·미션 종류·기준 날짜별 인증, 전체/그룹 피드를 조회한다.
package com.ganzithon.team08.domain.certification

import com.ganzithon.team08.domain.mission.MissionType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface CertificationRepository : JpaRepository<Certification, Long> {
    fun findByUser_UserIdAndMissionTypeAndMissionDate(
        userId: Long,
        missionType: MissionType,
        missionDate: LocalDate,
    ): Certification?

    fun existsByUser_UserIdAndMissionTypeAndMissionDate(
        userId: Long,
        missionType: MissionType,
        missionDate: LocalDate,
    ): Boolean

    // 전체 피드(PUBLIC)를 최신순 커서 페이징으로 조회한다. 작성자·배정·미션을 함께 로딩해 N+1을 막는다.
    @Query(
        """
        select c from Certification c
        join fetch c.user
        join fetch c.assignment a
        join fetch a.mission
        where c.visibility in :visibilities
          and (:cursor is null or c.certificationId < :cursor)
        order by c.certificationId desc
        """,
    )
    fun findFeed(
        @Param("visibilities") visibilities: Collection<Visibility>,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<Certification>

    // 그룹 피드: 작성자의 현재 그룹이 viewer의 그룹과 같은 인증만 최신순 커서 페이징으로 조회한다.
    @Query(
        """
        select c from Certification c
        join fetch c.user u
        join fetch c.assignment a
        join fetch a.mission
        where u.group.groupId = :groupId
          and c.visibility in :visibilities
          and (:cursor is null or c.certificationId < :cursor)
        order by c.certificationId desc
        """,
    )
    fun findGroupFeed(
        @Param("groupId") groupId: Long,
        @Param("visibilities") visibilities: Collection<Visibility>,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<Certification>
}
