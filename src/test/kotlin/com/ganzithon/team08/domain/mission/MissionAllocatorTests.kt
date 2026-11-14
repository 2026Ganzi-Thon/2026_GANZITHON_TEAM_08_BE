// 미션 배정의 기간, 후보 선정, 동시 실행 및 롤백을 검증한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import(MissionAllocator::class, UserAssignmentAccess::class, GroupAssignmentAccess::class, MissionAssignmentService::class)
class MissionAllocatorTests @Autowired constructor(
    private val entities: EntityManager,
    private val allocator: MissionAllocator,
    private val lookup: MissionAssignmentService,
    private val assignments: MissionAssignmentRepository,
    manager: PlatformTransactionManager,
) {
    private val monday = LocalDate.of(2026, 11, 9)
    private val seoulMonday = Instant.parse("2026-11-08T15:00:00Z")
    private val transaction = TransactionTemplate(manager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    @Test
    fun prefersQ3AndKeepsExistingAssignmentAfterPreferenceChanges() {
        val user = member(group())
        val matching = mission(MissionType.PERSONAL, MissionPreference.A)
        mission(MissionType.PERSONAL, MissionPreference.COMMON)
        mission(MissionType.PERSONAL, MissionPreference.B)

        val first = allocator.ensurePersonalAssignment(user.userId!!, seoulMonday)
        assertTrue(first.created)
        assertEquals(matching.missionId, first.missionId)
        val group = user.group!!
        user.updateType(group.lifeStage, group.userType, AnswerChoice.B, group)

        val repeated = allocator.ensurePersonalAssignment(user.userId!!, seoulMonday)
        assertEquals(first.copy(created = false), repeated)
        assertEquals(first.assignmentId, lookup.findCurrentAssignment(
            user.userId!!, first.missionId, MissionType.PERSONAL, monday,
        ).assignmentId)
    }

    @Test
    fun fallsBackToCommonAndReusesSingleCandidateAcrossDays() {
        val user = member(group())
        val common = mission(MissionType.PERSONAL, MissionPreference.COMMON)
        mission(MissionType.PERSONAL, MissionPreference.B)
        val sunday = allocator.ensurePersonalAssignment(user.userId!!, seoulMonday.minusSeconds(1))
        val mondayResult = allocator.ensurePersonalAssignment(user.userId!!, seoulMonday)

        assertEquals(monday.minusDays(1), sunday.missionDate)
        assertEquals(monday, mondayResult.missionDate)
        assertEquals(common.missionId, sunday.missionId)
        assertEquals(common.missionId, mondayResult.missionId)
    }

    @Test
    fun assignsOneMissionPerGroupWeekAndAvoidsPreviousCandidate() {
        val group = group()
        val user = member(group)
        val first = mission(MissionType.GROUP)
        val second = mission(MissionType.GROUP)
        persist(MissionAssignment.forGroup(first, group, monday.minusWeeks(1)))

        val current = allocator.ensureGroupAssignment(group.groupId!!, seoulMonday)
        val midweek = allocator.ensureGroupAssignment(group.groupId!!, seoulMonday.plus(2, ChronoUnit.DAYS))

        assertEquals(second.missionId, current.missionId)
        assertEquals(monday, current.missionDate)
        assertEquals(current.copy(created = false), midweek)
        assertEquals(current.assignmentId, lookup.findCurrentAssignment(
            user.userId!!, current.missionId, MissionType.GROUP, monday.plusDays(2),
        ).assignmentId)
    }

    @Test
    fun excludesInactiveOrIncompleteUsersAndGroupsWithoutActiveMembers() {
        val group = group()
        val withdrawn = member(group).also { it.withdraw() }
        val incomplete = persist(User("incomplete", "온보딩 전"))
        mission(MissionType.PERSONAL)
        mission(MissionType.GROUP)

        assertEquals(MissionErrorCode.TARGET_INELIGIBLE, assertThrows<BusinessException> {
            allocator.ensurePersonalAssignment(withdrawn.userId!!, seoulMonday)
        }.errorCode)
        assertEquals(MissionErrorCode.TARGET_INELIGIBLE, assertThrows<BusinessException> {
            allocator.ensurePersonalAssignment(incomplete.userId!!, seoulMonday)
        }.errorCode)
        assertEquals(MissionErrorCode.TARGET_INELIGIBLE, assertThrows<BusinessException> {
            allocator.ensureGroupAssignment(group.groupId!!, seoulMonday)
        }.errorCode)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun missingGroupPoolRollsBackEarlierPersonalAllocation() {
        val (userId, groupId) = transaction.execute {
            val group = group()
            val user = member(group)
            mission(MissionType.PERSONAL)
            persist(Mission(MissionType.GROUP, "예전 데이터"))
            user.userId!! to group.groupId!!
        }
        try {
            val error = assertThrows<BusinessException> {
                transaction.executeWithoutResult {
                    allocator.ensurePersonalAssignment(userId, seoulMonday)
                    allocator.ensureGroupAssignment(groupId, seoulMonday)
                }
            }
            assertEquals(MissionErrorCode.MISSION_POOL_EMPTY, error.errorCode)
            assertNull(assignments.findByUser_UserIdAndMissionDate(userId, monday))
            assertNull(assignments.findByGroup_GroupIdAndMissionDate(groupId, monday))
        } finally {
            clearCommittedFixtures()
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun concurrentPersonalAndGroupRequestsReturnTheSameAssignmentForEachTarget() {
        val (userId, groupId) = transaction.execute {
            val group = group()
            val user = member(group)
            mission(MissionType.PERSONAL)
            mission(MissionType.GROUP)
            user.userId!! to group.groupId!!
        }
        try {
            assertConcurrentReuse { allocator.ensurePersonalAssignment(userId, seoulMonday) }
            assertConcurrentReuse { allocator.ensureGroupAssignment(groupId, seoulMonday) }
            assertEquals(2, assignments.count())
        } finally {
            clearCommittedFixtures()
        }
    }

    private fun assertConcurrentReuse(allocate: () -> AssignmentResult) {
        val workers = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        try {
            val jobs = (1..2).map {
                workers.submit(Callable {
                    ready.countDown()
                    check(ready.await(5, TimeUnit.SECONDS))
                    allocate()
                })
            }
            val first = jobs[0].get(10, TimeUnit.SECONDS)
            val second = jobs[1].get(10, TimeUnit.SECONDS)
            assertEquals(first.assignmentId, second.assignmentId)
            assertEquals(first.missionId, second.missionId)
            assertTrue(first.created || second.created)
            assertFalse(first.created && second.created)
        } finally {
            workers.shutdownNow()
        }
    }

    private fun clearCommittedFixtures() = transaction.executeWithoutResult {
        assignments.deleteAllInBatch()
        entities.createQuery("select m from Mission m", Mission::class.java).resultList.forEach(entities::remove)
        entities.createQuery("delete from User").executeUpdate()
        entities.createQuery("delete from CareGroup").executeUpdate()
        entities.createQuery("delete from UserType").executeUpdate()
    }

    private fun group(): CareGroup {
        val type = persist(UserType("TYPE_01", "유형", "설명"))
        return persist(CareGroup(LifeStage.JOB_SEEKER, type))
    }

    private fun member(group: CareGroup): User =
        persist(User(UUID.randomUUID().toString(), "그룹원")).also {
            it.completeOnboarding(group.lifeStage, group.userType, AnswerChoice.A, group)
        }

    private fun mission(type: MissionType, preference: MissionPreference = MissionPreference.COMMON): Mission =
        persist(Mission(type, "산책하기", "동네 산책을 합니다.", listOf("산책하기", "사진 찍기"), "사진을 등록합니다.", preference))

    private fun <T : Any> persist(entity: T): T = entity.also(entities::persist)
}
