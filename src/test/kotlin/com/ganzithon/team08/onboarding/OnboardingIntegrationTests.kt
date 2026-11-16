// 최초 온보딩의 그룹 정원, 미션 배정 및 전체 롤백을 검증한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.certification.CertificationService
import com.ganzithon.team08.feed.FeedService
import com.ganzithon.team08.certification.GroupCertificationPrivacyService
import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.group.DefaultGroupMatchingService
import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionAssignmentService
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
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageStorage
import com.ganzithon.team08.group.MyGroupErrorCode
import com.ganzithon.team08.group.MyGroupService
import jakarta.persistence.EntityManager
import org.mockito.Mockito.doThrow
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.util.AopTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
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
    OnboardingRetestService::class, GroupCertificationPrivacyService::class, CertificationService::class,
    MyGroupService::class, FeedService::class, MissionAssignmentService::class, OnboardingIntegrationTests.StorageConfig::class,
    MissionAllocator::class, UserAssignmentAccess::class, UserTypeMatchingAccess::class, GroupAssignmentAccess::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OnboardingIntegrationTests @Autowired constructor(
    private val entities: EntityManager,
    private val onboarding: OnboardingSubmitService,
    private val retest: OnboardingRetestService,
    private val allocator: MissionAllocator,
    private val myGroup: MyGroupService,
    private val feed: FeedService,
    private val certificationService: CertificationService,
    private val imageStorage: BlockingStorage,
    manager: PlatformTransactionManager,
) {
    @MockitoSpyBean
    private lateinit var privacy: GroupCertificationPrivacyService

    private val tx = TransactionTemplate(manager)
    private val stage = LifeStage.JOB_SEEKER
    private val request = OnboardingSubmitRequest(stage, (1..4).map { AnswerRequest(it, AnswerChoice.A) })

    @Test
    fun selectsLowestAvailableMatchingGroupAndReusesItsWeeklyMission() {
        val (userId, groupId, weeklyId) = tx.execute {
            val fixture = seed()
            val other = entities.find(UserType::class.java, "TYPE_02")
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

    @Test
    fun preferenceOnlyRetestKeepsCurrentAssignmentAndUsesNewPreferenceNextDay() {
        val (id, nextMissionId) = tx.execute {
            val fixture = seed()
            val next = persist(Mission(
                MissionType.PERSONAL, "새 선호", "설명", listOf("수행"), "인증 안내",
                com.ganzithon.team08.domain.mission.MissionPreference.B,
            ))
            fixture.users.first() to next.missionId!!
        }
        onboarding.submit(id, request)
        val before = tx.execute { personalAssignmentId(id) }
        val result = retest.submit(id, withAnswer(3, AnswerChoice.B))
        assertEquals(AnswerChoice.B, result.carePreference)
        assertFalse(result.groupRematched)
        assertNotNull(result.groupId)
        assertEquals(AnswerChoice.B, retest.getCurrentAnswers(id).answers.single { it.questionId == 3 }.choice)
        assertEquals(before, tx.execute { personalAssignmentId(id) })
        assertEquals(2L, tx.execute { count("MissionAssignment") })
        val nextDay = Instant.now().plusSeconds(86_400)
        assertEquals(nextMissionId, allocator.ensurePersonalAssignment(id, nextDay).missionId)
    }

    @Test
    fun ungroupedUserStaysUngroupedAfterNewType() {
        val id = tx.execute { seed().users.first() }
        onboarding.submit(id, request)
        tx.executeWithoutResult {
            entities.createQuery("update User u set u.group = null where u.userId = :id")
                .setParameter("id", id).executeUpdate()
        }
        val result = retest.submit(id, changedType())
        assertEquals("TYPE_02", result.typeCode)
        assertNull(result.groupId)
        assertFalse(result.groupRematched)
        assertEquals(1L, tx.execute { count("CareGroup") })
        assertEquals(2L, tx.execute { count("MissionAssignment") })
    }

    @Test
    fun missingWeeklyCandidateRollsBackGroupTypeAnswersAndCertificationVisibility() {
        val id = tx.execute { seed().users.first() }
        onboarding.submit(id, request)
        val (oldGroupId, certificateId) = tx.execute {
            val user = entities.find(User::class.java, id)
            val assignment = entities.find(MissionAssignment::class.java, personalAssignmentId(id))
            val certificate = persist(Certification(user, assignment, "private/test.jpg", "인증", Visibility.GROUP))
            // 기존 배정은 보존하면서 신규 후보에서만 제외한다.
            entities.createQuery("update Mission m set m.description = null where m.missionType = :type")
                .setParameter("type", MissionType.GROUP).executeUpdate()
            user.group!!.groupId!! to certificate.certificationId!!
        }
        assertEquals(MissionErrorCode.MISSION_POOL_EMPTY, assertThrows<BusinessException> {
            retest.submit(id, changedType())
        }.errorCode)
        assertEquals(AnswerChoice.A, retest.getCurrentAnswers(id).answers.single { it.questionId == 2 }.choice)
        tx.executeWithoutResult {
            val user = entities.find(User::class.java, id)
            assertEquals(oldGroupId, user.group?.groupId)
            assertEquals("TYPE_01", user.userType?.typeCode)
            assertEquals(Visibility.GROUP, entities.find(Certification::class.java, certificateId).visibility)
            assertEquals(1L, count("CareGroup"))
        }
    }

    @Test
    fun certificationFinishesBeforeRetestAndKeepsOriginalGroupSnapshot() {
        val (id, groupMissionId) = tx.execute {
            val fixture = seed()
            fixture.users.first() to fixture.groupMission!!.missionId!!
        }
        onboarding.submit(id, request)
        val oldGroupId = tx.execute { entities.find(User::class.java, id).group!!.groupId!! }
        val workers = Executors.newFixedThreadPool(2)
        val retestStarted = CountDownLatch(1)
        imageStorage.blockNextSave()
        try {
            val certification = workers.submit(Callable {
                certificationService.register(id, groupCertificationRequest(groupMissionId))
            })
            check(imageStorage.entered.await(5, TimeUnit.SECONDS))
            val moving = workers.submit(Callable {
                retestStarted.countDown()
                retest.submit(id, changedType())
            })
            check(retestStarted.await(5, TimeUnit.SECONDS))
            org.junit.jupiter.api.assertThrows<java.util.concurrent.TimeoutException> {
                moving.get(300, TimeUnit.MILLISECONDS)
            }
            imageStorage.release.countDown()
            val saved = certification.get(10, TimeUnit.SECONDS)
            val moved = moving.get(10, TimeUnit.SECONDS)
            assertEquals(true, moved.groupRematched)
            org.junit.jupiter.api.Assertions.assertNotEquals(oldGroupId, moved.groupId)
            tx.executeWithoutResult {
                val snapshot = entities.find(Certification::class.java, saved.certificationId)
                assertEquals(oldGroupId, snapshot.group?.groupId)
                assertEquals(oldGroupId, snapshot.assignment.group?.groupId)
                assertEquals(Visibility.PRIVATE, snapshot.visibility)
                assertEquals(3L, count("MissionAssignment"))
            }
            assertEquals("CERT_ALREADY_EXISTS", assertThrows<BusinessException> {
                certificationService.register(id, groupCertificationRequest(groupMissionId))
            }.errorCode.code)
        } finally {
            imageStorage.release.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun groupLeavePrivatizesConcurrentCertificationAndPreservesHistory() {
        val (id, groupMissionId) = tx.execute {
            val fixture = seed()
            fixture.users.first() to fixture.groupMission!!.missionId!!
        }
        onboarding.submit(id, request)
        val groupId = tx.execute { entities.find(User::class.java, id).group!!.groupId!! }
        val personalId = tx.execute { personalAssignmentId(id) }
        val publicId = tx.execute {
            val user = entities.find(User::class.java, id)
            val assignment = entities.find(MissionAssignment::class.java, personalId)
            persist(Certification(user, assignment, "images/public.jpg", "공개 인증", Visibility.PUBLIC)).certificationId!!
        }
        val workers = Executors.newFixedThreadPool(2)
        val leaveStarted = CountDownLatch(1)
        imageStorage.blockNextSave()
        try {
            val certification = workers.submit(Callable {
                certificationService.register(id, groupCertificationRequest(groupMissionId))
            })
            check(imageStorage.entered.await(5, TimeUnit.SECONDS))
            val leaving = workers.submit(Callable {
                leaveStarted.countDown()
                myGroup.leaveGroup(id, groupId)
            })
            check(leaveStarted.await(5, TimeUnit.SECONDS))
            assertThrows<java.util.concurrent.TimeoutException> { leaving.get(300, TimeUnit.MILLISECONDS) }
            imageStorage.release.countDown()
            val saved = certification.get(10, TimeUnit.SECONDS)
            assertEquals(groupId, leaving.get(10, TimeUnit.SECONDS).leftGroupId)
            assertNull(myGroup.getMyGroup(id).groupId)
            assertNull(retest.submit(id, changedType()).groupId)
            tx.executeWithoutResult {
                val user = entities.find(User::class.java, id)
                val snapshot = entities.find(Certification::class.java, saved.certificationId)
                assertEquals(groupId, user.previousGroupId)
                assertEquals(groupId, snapshot.group?.groupId)
                assertEquals(groupId, snapshot.assignment.group?.groupId)
                assertEquals(Visibility.PRIVATE, snapshot.visibility)
                assertEquals(Visibility.PUBLIC, entities.find(Certification::class.java, publicId).visibility)
                assertEquals(personalId, personalAssignmentId(id))
                assertEquals(1L, count("CareGroup"))
            }
            assertEquals("MISSION_NOT_ASSIGNED", assertThrows<BusinessException> {
                certificationService.register(id, groupCertificationRequest(groupMissionId).copy(visibility = Visibility.PRIVATE))
            }.errorCode.code)
        } finally {
            imageStorage.release.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun formerMemberLosesAccessToGroupFeedDetailAndImage() {
        val (former, author) = tx.execute { seed().users }
        onboarding.submit(former, request)
        onboarding.submit(author, request)
        val groupId = checkNotNull(myGroup.getMyGroup(former).groupId)
        val certificationId = tx.execute {
            val user = entities.find(User::class.java, author)
            val assignment = entities.find(MissionAssignment::class.java, personalAssignmentId(author))
            persist(Certification(user, assignment, "images/group.jpg", "그룹 인증", Visibility.GROUP)).certificationId!!
        }
        assertEquals("/api/images/$certificationId", certificationService.getDetail(former, certificationId).imageUrl)
        assertEquals(1, feed.getGroupFeed(former, null, 20).items.size)

        myGroup.leaveGroup(former, groupId)

        assertEquals(0, feed.getGroupFeed(former, null, 20).items.size)
        assertEquals("CERT_FORBIDDEN", assertThrows<BusinessException> {
            certificationService.getDetail(former, certificationId)
        }.errorCode.code)
        assertEquals("CERT_FORBIDDEN", assertThrows<BusinessException> {
            certificationService.getImage(former, certificationId)
        }.errorCode.code)
    }

    @Test
    fun leaveForOldGroupIsRejectedAfterConcurrentRematchCommits() {
        val id = tx.execute { seed().users.first() }
        onboarding.submit(id, request)
        val oldGroupId = checkNotNull(myGroup.getMyGroup(id).groupId)
        val workers = Executors.newFixedThreadPool(2)
        val moved = CountDownLatch(1)
        val commit = CountDownLatch(1)
        val leaveStarted = CountDownLatch(1)
        try {
            val rematch = workers.submit(Callable {
                tx.execute {
                    val result = retest.submit(id, changedType())
                    moved.countDown()
                    check(commit.await(5, TimeUnit.SECONDS))
                    result
                }
            })
            check(moved.await(5, TimeUnit.SECONDS))
            val leaving = workers.submit(Callable {
                leaveStarted.countDown()
                assertThrows<BusinessException> { myGroup.leaveGroup(id, oldGroupId) }.errorCode
            })
            check(leaveStarted.await(5, TimeUnit.SECONDS))
            assertThrows<java.util.concurrent.TimeoutException> { leaving.get(300, TimeUnit.MILLISECONDS) }
            commit.countDown()
            assertEquals(MyGroupErrorCode.GROUP_CHANGED, leaving.get(10, TimeUnit.SECONDS))
            assertEquals(rematch.get(10, TimeUnit.SECONDS).groupId, myGroup.getMyGroup(id).groupId)
        } finally {
            commit.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun leaveRollsBackMembershipWhenPrivacyUpdateFails() {
        val id = tx.execute { seed().users.first() }
        onboarding.submit(id, request)
        val groupId = checkNotNull(myGroup.getMyGroup(id).groupId)
        val target = AopTestUtils.getUltimateTargetObject<GroupCertificationPrivacyService>(privacy)
        doThrow(IllegalStateException("공개 범위 저장 실패")).`when`(target).makeGroupCertificationsPrivate(id)
        assertThrows<IllegalStateException> { myGroup.leaveGroup(id, groupId) }
        tx.executeWithoutResult {
            val user = entities.find(User::class.java, id)
            assertEquals(groupId, user.group?.groupId)
            assertNull(user.previousGroupId)
        }
    }

    private fun groupCertificationRequest(missionId: Long) = CertificationRegisterRequest(
        MockMultipartFile("image", "test.jpg", "image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)),
        "그룹 미션 완료", MissionType.GROUP, missionId, Visibility.GROUP,
    )

    private fun withAnswer(id: Int, choice: AnswerChoice) =
        request.copy(answers = request.answers.map { if (it.questionId == id) it.copy(choice = choice) else it })

    private fun changedType() = withAnswer(2, AnswerChoice.B)

    private fun personalAssignmentId(userId: Long): Long =
        entities.createQuery(
            "select a.assignmentId from MissionAssignment a where a.user.userId = :id",
            Long::class.javaObjectType,
        ).setParameter("id", userId).singleResult

    @TestConfiguration
    class StorageConfig {
        @Bean
        fun imageStorage() = BlockingStorage()
    }

    class BlockingStorage : ImageStorage {
        var entered = CountDownLatch(0)
        var release = CountDownLatch(0)

        fun blockNextSave() {
            entered = CountDownLatch(1)
            release = CountDownLatch(1)
        }

        override fun save(content: ByteArray, format: ImageFormat): String {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            return "images/test.jpg"
        }

        override fun load(storageKey: String): ByteArray = error("사진 조회는 이 테스트의 범위 밖입니다.")
        override fun delete(storageKey: String) = Unit
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
        persist(UserType("TYPE_02", "변경 유형", "설명"))
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
        entities.createQuery("delete from Certification").executeUpdate()
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
