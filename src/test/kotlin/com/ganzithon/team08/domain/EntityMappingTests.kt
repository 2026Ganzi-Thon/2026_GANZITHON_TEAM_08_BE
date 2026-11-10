// 실제 DB 저장·조회로 엔티티 관계와 중복 제약 및 변경 감지를 검증한다.
package com.ganzithon.team08.domain

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cleanup.CleanupTask
import com.ganzithon.team08.domain.cleanup.CleanupTaskStatus
import com.ganzithon.team08.domain.cleanup.CleanupTaskType
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.onboarding.OnboardingAnswer
import com.ganzithon.team08.domain.onboarding.OnboardingQuestion
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.domain.user.UserType
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.hibernate.Hibernate
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import jakarta.validation.ConstraintViolationException as BeanConstraintViolationException

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class EntityMappingTests @Autowired constructor(private val entityManager: EntityManager) {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun entityGraphRoundTripsWithLazyRelationships() {
        val fixture = fixture()
        val question = persist(OnboardingQuestion(1, "질문", "선택 A", "선택 B"))
        val answer = persist(OnboardingAnswer(fixture.user, question, AnswerChoice.A))
        val certification = persist(certification(fixture))
        val cheer = persist(Cheer(fixture.otherUser, certification))
        val task = persist(CleanupTask(CleanupTaskType.DELETE_IMAGE, certification.imageStorageKey))
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(Certification::class.java, certification.certificationId)
        assertFalse(Hibernate.isInitialized(loaded.user))
        assertFalse(Hibernate.isInitialized(loaded.assignment))
        assertEquals("포근한양", loaded.user.nickname)
        assertEquals("산책하기", loaded.assignment.mission.missionTitle)
        assertEquals(fixture.group.groupId, loaded.user.group?.groupId)
        assertEquals(fixture.group.groupId, loaded.group?.groupId)
        assertEquals("TYPE_01", loaded.user.userType?.typeCode)
        assertEquals(MissionType.PERSONAL, loaded.missionType)
        assertEquals(monday, loaded.missionDate)
        assertEquals(Visibility.PRIVATE, loaded.visibility)
        assertNotNull(loaded.version)
        assertEquals(loaded.createdAt, loaded.updatedAt)

        val loadedAnswer = entityManager.find(OnboardingAnswer::class.java, answer.answerId)
        assertEquals("선택 A", loadedAnswer.question.optionA)
        assertEquals(AnswerChoice.A, loadedAnswer.answer)
        assertEquals(fixture.otherUser.userId, entityManager.find(Cheer::class.java, cheer.cheerId).user.userId)
        assertEquals(CleanupTaskStatus.PENDING, entityManager.find(CleanupTask::class.java, task.taskId).status)
        assertEquals("PRIVATE", entityManager.createNativeQuery("select visibility from certifications").singleResult)
    }

    @Test
    fun newUserHasNullableOnboardingFields() {
        val user = persist(User("new-kakao-user", "새로운양"))
        entityManager.flush()
        entityManager.clear()
        val loaded = entityManager.find(User::class.java, user.userId)
        assertFalse(loaded.onboardingCompleted)
        assertEquals(UserStatus.ACTIVE, loaded.status)
        assertNull(loaded.lifeStage)
        assertNull(loaded.userType)
        assertNull(loaded.carePreference)
        assertNull(loaded.group)
    }

    @Test
    fun duplicateKakaoAccountIsRejectedByDatabase() {
        persist(User("same-kakao-id", "첫번째양"))
        assertDatabaseConstraint("uk_user_kakao") { persist(User("same-kakao-id", "두번째양")) }
    }

    @Test
    fun duplicateAnswerIsRejectedByDatabase() {
        val user = persist(User("answer-user", "응답하는양"))
        val question = persist(OnboardingQuestion(1, "질문", "A", "B"))
        persist(OnboardingAnswer(user, question, AnswerChoice.A))
        assertDatabaseConstraint("uk_answer_user_question") { persist(OnboardingAnswer(user, question, AnswerChoice.B)) }
    }

    @Test
    fun userCannotReceiveTwoPersonalMissionsOnOneDay() {
        val fixture = fixture()
        val anotherMission = persist(Mission(MissionType.PERSONAL, "물 마시기"))
        assertDatabaseConstraint("uk_assignment_user_date") {
            persist(MissionAssignment.forUser(anotherMission, fixture.user, monday))
        }
    }

    @Test
    fun groupCannotReceiveTwoMissionsInOneWeek() {
        val fixture = fixture()
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        persist(MissionAssignment.forGroup(mission, fixture.group, monday))
        assertDatabaseConstraint("uk_assignment_group_date") {
            persist(MissionAssignment.forGroup(mission, fixture.group, monday))
        }
    }

    @Test
    fun assignmentMustHaveExactlyOneTargetInDatabase() {
        val fixture = fixture()
        entityManager.flush()
        assertDatabaseConstraint("ck_assignment_one_target") {
            entityManager.createNativeQuery("update mission_assignments set group_id = :groupId where assignment_id = :id")
                .setParameter("groupId", fixture.group.groupId)
                .setParameter("id", fixture.assignment.assignmentId)
                .executeUpdate()
        }
    }

    @Test
    fun duplicatePersonalCertificationIsRejectedByDatabase() {
        val fixture = fixture()
        persist(certification(fixture))
        assertDatabaseConstraint("uk_certification_user_period") { persist(certification(fixture)) }
    }

    @Test
    fun eachGroupMemberCanCertifyTheSharedMission() {
        val fixture = fixture()
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val assignment = persist(MissionAssignment.forGroup(mission, fixture.group, monday))
        persist(Certification(fixture.user, assignment, "images/one.jpg", "첫번째 인증"))
        persist(Certification(fixture.otherUser, assignment, "images/two.jpg", "두번째 인증"))
        entityManager.flush()
        entityManager.clear()
        assertEquals(2L, entityManager.createQuery("select count(c) from Certification c", Long::class.javaObjectType).singleResult)
    }

    @Test
    fun rematchingDoesNotAllowASecondGroupCertificationInTheSameWeek() {
        val fixture = fixture()
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val firstAssignment = persist(MissionAssignment.forGroup(mission, fixture.group, monday))
        persist(Certification(fixture.user, firstAssignment, "images/one.jpg", "첫 그룹 인증"))

        val newGroup = persist(CareGroup(LifeStage.EARLY_CAREER, fixture.group.userType))
        fixture.user.updateType(LifeStage.EARLY_CAREER, newGroup.userType, AnswerChoice.B, newGroup)
        val secondAssignment = persist(MissionAssignment.forGroup(mission, newGroup, monday))
        assertDatabaseConstraint("uk_certification_user_period") {
            persist(Certification(fixture.user, secondAssignment, "images/two.jpg", "재매칭 후 인증"))
        }
    }

    @Test
    fun duplicateCheerIsRejectedByDatabase() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        persist(Cheer(fixture.otherUser, certification))
        assertDatabaseConstraint("uk_cheer_user_certification") { persist(Cheer(fixture.otherUser, certification)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun blankCertificationContentIsRejected(content: String) {
        val fixture = fixture()
        assertThrows<BeanConstraintViolationException> {
            persist(Certification(fixture.user, fixture.assignment, "images/one.jpg", content))
            entityManager.flush()
        }
    }

    @Test
    fun overlyLongCertificationContentIsRejected() {
        val fixture = fixture()
        assertThrows<BeanConstraintViolationException> {
            persist(Certification(fixture.user, fixture.assignment, "images/one.jpg", "가".repeat(301)))
            entityManager.flush()
        }
    }

    @Test
    fun retestPreservesPersonalAssignmentAndCertificationDate() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        val newType = persist(UserType("TYPE_02", "다른 유형", "다른 유형 설명"))
        val newGroup = persist(CareGroup(LifeStage.EARLY_CAREER, newType))
        fixture.user.updateType(LifeStage.EARLY_CAREER, newType, AnswerChoice.B, newGroup)
        certification.changeVisibility(Visibility.PUBLIC)
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(Certification::class.java, certification.certificationId)
        assertEquals(newGroup.groupId, loaded.user.group?.groupId)
        assertEquals(fixture.group.groupId, loaded.group?.groupId)
        assertEquals(fixture.assignment.assignmentId, loaded.assignment.assignmentId)
        assertEquals(monday, loaded.missionDate)
        assertEquals(Visibility.PUBLIC, loaded.visibility)
        assertTrue(loaded.updatedAt >= loaded.createdAt)
    }

    @Test
    fun staleAnswerChangesAreRejectedByOptimisticLocking() {
        val user = persist(User("version-user", "응답하는양"))
        val question = persist(OnboardingQuestion(1, "질문", "A", "B"))
        val answer = persist(OnboardingAnswer(user, question, AnswerChoice.A))
        entityManager.flush()
        entityManager.clear()

        val stale = entityManager.find(OnboardingAnswer::class.java, answer.answerId)
        entityManager.detach(stale)
        val current = entityManager.find(OnboardingAnswer::class.java, answer.answerId)
        current.changeAnswer(AnswerChoice.B)
        entityManager.flush()

        assertThrows<OptimisticLockException> { entityManager.merge(stale) }
    }

    @Test
    fun cleanupTaskSurvivesUserDeletionAndPersistsRetryState() {
        val user = persist(User("withdrawn-kakao-user", "떠나는양"))
        val task = persist(CleanupTask(CleanupTaskType.UNLINK_KAKAO, user.kakaoUserId))
        user.withdraw()
        entityManager.flush()
        assertEquals(UserStatus.WITHDRAWN, user.status)
        entityManager.remove(user)
        task.start()
        val retryAt = Instant.parse("2026-11-05T00:00:00Z")
        task.fail("외부 API 호출 실패", retryAt)
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(CleanupTask::class.java, task.taskId)
        assertNull(entityManager.find(User::class.java, user.userId))
        assertEquals("withdrawn-kakao-user", loaded.targetReference)
        assertEquals(CleanupTaskStatus.FAILED, loaded.status)
        assertEquals(1, loaded.retryCount)
        assertEquals(retryAt, loaded.nextRetryAt)
        loaded.start()
        loaded.complete()
        entityManager.flush()
        entityManager.clear()
        val completed = entityManager.find(CleanupTask::class.java, task.taskId)
        assertEquals(CleanupTaskStatus.COMPLETED, completed.status)
        assertNull(completed.nextRetryAt)
        assertNull(completed.lastError)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun assertDatabaseConstraint(name: String, action: () -> Unit) {
        val exception = assertThrows<ConstraintViolationException> {
            action()
            entityManager.flush()
        }
        assertTrue(exception.constraintName.orEmpty().contains(name, ignoreCase = true), exception.message)
    }

    private fun certification(fixture: Fixture): Certification =
        Certification(fixture.user, fixture.assignment, "images/walk.jpg", "산책을 완료했습니다.")

    private fun fixture(): Fixture {
        val type = persist(UserType("TYPE_01", "유형 1", "유형 설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("kakao-one", "포근한양"))
        val otherUser = persist(User("kakao-two", "따뜻한양"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        otherUser.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.B, group)
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, user, monday))
        return Fixture(user, otherUser, group, assignment)
    }

    private data class Fixture(
        val user: User,
        val otherUser: User,
        val group: CareGroup,
        val assignment: MissionAssignment,
    )
}
