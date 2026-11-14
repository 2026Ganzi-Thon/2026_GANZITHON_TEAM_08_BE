// 최초 온보딩의 그룹 정원, 미션 배정 및 전체 롤백을 검증한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.group.DefaultGroupMatchingService
import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionErrorCode
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.onboarding.OnboardingQuestion
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.domain.user.UserTypeMatchingAccess
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import(
    OnboardingSubmitService::class, OnboardingTypeCalculator::class, DefaultGroupMatchingService::class,
    MissionAllocator::class, UserAssignmentAccess::class, UserTypeMatchingAccess::class, GroupAssignmentAccess::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OnboardingIntegrationTests @Autowired constructor(
    private val entities: EntityManager,
    private val onboarding: OnboardingSubmitService,
    manager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(manager)
    private val stage = LifeStage.JOB_SEEKER
    private val request = OnboardingSubmitRequest(stage, (1..4).map { AnswerRequest(it, AnswerChoice.A) })

    @Test
    fun selectsLowestAvailableMatchingGroupAndReusesItsWeeklyMission() {
        val (userId, groupId, weeklyId) = tx.execute {
            val fixture = seed()
            val other = persist(UserType("TYPE_02", "다른 유형", "설명"))
            persist(CareGroup(stage, other))
            val group = persist(CareGroup(stage, fixture.type))
            val weekly = persist(MissionAssignment.forGroup(fixture.groupMission!!, group, weekStart()))
            persist(CareGroup(stage, fixture.type))
            Triple(fixture.users.first(), group.groupId!!, weekly.assignmentId!!)
        }

        assertEquals("TYPE_01", onboarding.submit(userId, request).typeCode)

        tx.executeWithoutResult {
            assertEquals(groupId, entities.find(User::class.java, userId).group?.groupId)
            assertEquals(4L, count("OnboardingAnswer"))
            assertEquals(2L, count("MissionAssignment"))
            assertNotNull(entities.find(MissionAssignment::class.java, weeklyId))
        }
    }

    @Test
    fun missingWeeklyPoolRollsBackNewGroupAnswersAndPersonalMission() {
        val userId = tx.execute { seed(withGroupMission = false).users.first() }

        assertEquals(MissionErrorCode.MISSION_POOL_EMPTY, assertThrows<BusinessException> {
            onboarding.submit(userId, request)
        }.errorCode)

        tx.executeWithoutResult {
            val user = entities.find(User::class.java, userId)
            assertFalse(user.onboardingCompleted)
            assertNull(user.group)
            assertNull(user.userType)
            assertEquals(0L, count("CareGroup"))
            assertEquals(0L, count("OnboardingAnswer"))
            assertEquals(0L, count("MissionAssignment"))
        }
    }

    @Test
    fun concurrentFirstUsersReuseOneNewGroup() {
        val ids = tx.execute { seed().users }
        assertEquals(listOf("OK", "OK"), submitConcurrently(ids))
        assertEquals(listOf(2L), groupSizes())
    }

    @Test
    fun concurrentApplicantsForLastSeatNeverExceedCapacity() {
        val ids = tx.execute {
            val fixture = seed()
            val group = persist(CareGroup(stage, fixture.type))
            repeat(3) { index ->
                persist(User("member-$index", "기존 멤버")).completeOnboarding(stage, fixture.type, AnswerChoice.B, group)
            }
            fixture.users
        }

        assertEquals(listOf("OK", "OK"), submitConcurrently(ids))
        assertEquals(listOf(4L, 1L), groupSizes())
    }

    @Test
    fun concurrentSubmissionsForSameUserCompleteOnlyOnce() {
        val id = tx.execute { seed().users.first() }
        assertEquals(listOf("OK", "ONBOARDING_ALREADY_COMPLETED"), submitConcurrently(listOf(id, id)))
        tx.executeWithoutResult {
            assertEquals(4L, count("OnboardingAnswer"))
            assertEquals(2L, count("MissionAssignment"))
        }
    }

    private fun submitConcurrently(ids: List<Long>): List<String> {
        val workers = Executors.newFixedThreadPool(ids.size)
        val ready = CountDownLatch(ids.size)
        try {
            return ids.map { userId ->
                workers.submit(Callable {
                    ready.countDown()
                    check(ready.await(5, TimeUnit.SECONDS))
                    try {
                        onboarding.submit(userId, request)
                        "OK"
                    } catch (ex: BusinessException) {
                        ex.errorCode.code
                    }
                })
            }.map { it.get(15, TimeUnit.SECONDS) }.sorted()
        } finally {
            workers.shutdownNow()
        }
    }

    private fun groupSizes(): List<Long> = tx.execute {
        entities.createQuery(
            "select count(u) from User u group by u.group.groupId having u.group.groupId is not null order by count(u) desc",
            Long::class.javaObjectType,
        ).resultList
    }

    private fun weekStart(): LocalDate =
        LocalDate.now(ZoneId.of("Asia/Seoul")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun seed(withGroupMission: Boolean = true): Fixture {
        val type = persist(UserType("TYPE_01", "기본 유형", "설명"))
        (1..4).forEach { persist(OnboardingQuestion(it, "질문", "선택 A", "선택 B")) }
        val users = (1..2).map { persist(User("onboarding-$it", "신규 사용자")).userId!! }
        persist(Mission(MissionType.PERSONAL, "개인 미션", "설명", listOf("수행"), "인증 안내"))
        val groupMission = if (withGroupMission) {
            persist(Mission(MissionType.GROUP, "그룹 미션", "설명", listOf("함께 수행"), "인증 안내"))
        } else null
        return Fixture(type, users, groupMission)
    }

    @AfterEach
    fun removeFixtures() = tx.executeWithoutResult {
        entities.createQuery("delete from OnboardingAnswer").executeUpdate()
        entities.createQuery("delete from MissionAssignment").executeUpdate()
        entities.createQuery("select m from Mission m", Mission::class.java).resultList.forEach(entities::remove)
        entities.createQuery("delete from User").executeUpdate()
        entities.createQuery("delete from CareGroup").executeUpdate()
        entities.createQuery("delete from UserType").executeUpdate()
        entities.createQuery("delete from OnboardingQuestion").executeUpdate()
    }

    private fun count(entity: String): Long =
        entities.createQuery("select count(e) from $entity e", Long::class.javaObjectType).singleResult

    private fun <T : Any> persist(entity: T): T = entity.also(entities::persist)

    private data class Fixture(val type: UserType, val users: List<Long>, val groupMission: Mission?)
}
