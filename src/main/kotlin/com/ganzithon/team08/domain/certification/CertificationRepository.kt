// 미션 인증을 저장하고 사용자·미션 종류·기준 날짜별 인증, 전체/그룹 피드, 날짜별 인증 목록과 스트릭용 인증일을 조회한다.
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

    // 전체 피드(PUBLIC)를 최신순 커서 페이징으로 조회한다. 탈퇴한 작성자의 인증은 숨긴다.
    // 작성자·배정·미션을 함께 로딩해 N+1을 막는다.
    @Query(
        """
        select c from Certification c
        join fetch c.user u
        join fetch c.assignment a
        join fetch a.mission
        where u.status = com.ganzithon.team08.domain.user.UserStatus.ACTIVE
          and c.visibility in :visibilities
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
    // GROUP 공개는 인증 당시 그룹(c.group)도 viewer 그룹과 같아야 한다 — 작성자가 그룹을 옮기면
    // 과거 GROUP 인증의 c.group은 그대로지만 u.group만 새 그룹으로 바뀌므로 둘 다 확인해야 함
    // (회윤님 리뷰, PR #10). PUBLIC은 공개 정책상 현재 그룹원이면 과거 그룹 소속 글이라도 노출한다.
    @Query(
        """
        select c from Certification c
        join fetch c.user u
        join fetch c.assignment a
        join fetch a.mission
        where u.group.groupId = :groupId
          and c.visibility in :visibilities
          and (c.visibility <> com.ganzithon.team08.domain.certification.Visibility.GROUP or c.group.groupId = :groupId)
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

    // 월별 기록(개인/그룹 완료 여부)과 스트릭 계산에 쓸 인증 날짜만 모아 조회한다. start/end가 null이면 전체 기간(스트릭용).
    @Query(
        """
        select c.missionDate from Certification c
        where c.user.userId = :userId
          and c.missionType = :missionType
          and (:start is null or c.missionDate >= :start)
          and (:end is null or c.missionDate <= :end)
        """,
    )
    fun findMissionDates(
        @Param("userId") userId: Long,
        @Param("missionType") missionType: MissionType,
        @Param("start") start: LocalDate?,
        @Param("end") end: LocalDate?,
    ): List<LocalDate>

    // 날짜별 기록 조회: 해당 날짜의 내 인증 목록을 미션 제목과 함께 N+1 없이 가져온다.
    @Query(
        """
        select c from Certification c
        join fetch c.assignment a
        join fetch a.mission
        where c.user.userId = :userId and c.missionDate = :missionDate
        order by c.missionType asc, c.certificationId asc
        """,
    )
    fun findByUserAndMissionDate(
        @Param("userId") userId: Long,
        @Param("missionDate") missionDate: LocalDate,
    ): List<Certification>
}
