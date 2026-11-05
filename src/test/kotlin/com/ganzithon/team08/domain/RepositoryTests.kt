// 실제 저장 데이터로 Repository의 연관관계 조건과 정렬 및 빈 조회 결과를 검증한다.
package com.ganzithon.team08.domain

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.group.CareGroupRepository
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionAssignmentRepository
import com.ganzithon.team08.domain.mission.MissionRepository
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.onboarding.OnboardingAnswer
import com.ganzithon.team08.domain.onboarding.OnboardingAnswerRepository
import com.ganzithon.team08.domain.onboarding.OnboardingQuestion
import com.ganzithon.team08.domain.onboarding.OnboardingQuestionRepository
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.domain.user.UserTypeRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class RepositoryTests @Autowired constructor(
    private val users: UserRepository,
    private val userTypes: UserTypeRepository,
    private val groups: CareGroupRepository,
    private val questions: OnboardingQuestionRepository,
    private val answers: OnboardingAnswerRepository,
    private val missions: MissionRepository,
    private val assignments: MissionAssignmentRepository,
    private val certifications: CertificationRepository,
    private val cheers: CheerRepository,
    private val entityManager: EntityManager,
) {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun kakaoLookupDistinguishesUsersAndMissingAccounts() {
        val user = users.save(User("kakao-target", "조회할양"))
        users.save(User("kakao-other", "다른양"))
        flushAndClear()

        assertEquals(user.userId, users.findByKakaoUserId("kakao-target")?.userId)
        assertTrue(users.existsByKakaoUserId("kakao-target"))
        assertNull(users.findByKakaoUserId("missing"))
        assertFalse(users.existsByKakaoUserId("missing"))
    }

    @Test
    fun onboardingQueriesOrderQuestionsAndIsolateUserAnswers() {
        val user = users.save(User("answer-target", "응답하는양"))
        val other = users.save(User("answer-other", "다른양"))
        val savedQuestions = listOf(3, 1, 4, 2).map { id ->
            questions.save(OnboardingQuestion(id, "질문 $id", "A", "B"))
        }
        savedQuestions.forEach { answers.save(OnboardingAnswer(user, it, AnswerChoice.A)) }
        answers.save(OnboardingAnswer(other, savedQuestions.first(), AnswerChoice.B))
        flushAndClear()

        assertEquals(listOf(1, 2, 3, 4), questions.findAllByOrderByQuestionIdAsc().map { it.questionId })
        assertEquals("질문 2", questions.findByIdOrNull(2)?.content)
        val userId = requireNotNull(user.userId)
        assertEquals(
            listOf(1, 2, 3, 4),
            answers.findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(userId).map { it.question.questionId },
        )
        assertEquals(AnswerChoice.A, answers.findByUser_UserIdAndQuestion_QuestionId(userId, 3)?.answer)
        assertEquals(
            AnswerChoice.B,
            answers.findByUser_UserIdAndQuestion_QuestionId(requireNotNull(other.userId), 3)?.answer,
        )
        assertNull(answers.findByUser_UserIdAndQuestion_QuestionId(requireNotNull(other.userId), 1))
        assertTrue(answers.findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(-1L).isEmpty())
    }

    @Test
    fun matchingGroupsRequireBothLifeStageAndTypeAndAllowMultipleMatches() {
        val type = userTypes.save(UserType("TYPE_01", "유형 1", "설명"))
        val otherType = userTypes.save(UserType("TYPE_02", "유형 2", "설명"))
        val first = groups.save(CareGroup(LifeStage.JOB_SEEKER, type))
        groups.save(CareGroup(LifeStage.COLLEGE_STUDENT, type))
        groups.save(CareGroup(LifeStage.JOB_SEEKER, otherType))
        val second = groups.save(CareGroup(LifeStage.JOB_SEEKER, type))
        flushAndClear()

        assertEquals("유형 1", userTypes.findByIdOrNull("TYPE_01")?.typeName)
        assertEquals(
            listOf(first.groupId, second.groupId),
            groups.findAllByLifeStageAndUserType_TypeCodeOrderByGroupIdAsc(LifeStage.JOB_SEEKER, "TYPE_01").map { it.groupId },
        )
        assertTrue(
            groups.findAllByLifeStageAndUserType_TypeCodeOrderByGroupIdAsc(LifeStage.EARLY_CAREER, "TYPE_01").isEmpty(),
        )
    }

    @Test
    fun missionQueriesDistinguishKindsTargetsAndPeriods() {
        val fixture = fixture()
        val anotherMission = missions.save(Mission(MissionType.PERSONAL, "물 마시기"))
        val personal = assignments.save(MissionAssignment.forUser(fixture.personalMission, fixture.user, monday))
        assignments.save(MissionAssignment.forUser(anotherMission, fixture.otherUser, monday))
        assignments.save(MissionAssignment.forUser(anotherMission, fixture.user, monday.plusDays(1)))
        val group = assignments.save(MissionAssignment.forGroup(fixture.groupMission, fixture.group, monday))
        assignments.save(MissionAssignment.forGroup(fixture.groupMission, fixture.group, monday.plusWeeks(1)))
        val otherGroup = groups.save(CareGroup(fixture.group.lifeStage, fixture.group.userType))
        assignments.save(MissionAssignment.forGroup(fixture.groupMission, otherGroup, monday))
        flushAndClear()

        assertEquals(
            listOf(fixture.personalMission.missionId, anotherMission.missionId),
            missions.findAllByMissionTypeOrderByMissionIdAsc(MissionType.PERSONAL).map { it.missionId },
        )
        assertEquals(
            listOf(fixture.groupMission.missionId),
            missions.findAllByMissionTypeOrderByMissionIdAsc(MissionType.GROUP).map { it.missionId },
        )
        val userId = requireNotNull(fixture.user.userId)
        val groupId = requireNotNull(fixture.group.groupId)
        assertEquals(personal.assignmentId, assignments.findByUser_UserIdAndMissionDate(userId, monday)?.assignmentId)
        assertEquals(group.assignmentId, assignments.findByGroup_GroupIdAndMissionDate(groupId, monday)?.assignmentId)
        assertNull(assignments.findByUser_UserIdAndMissionDate(userId, monday.minusDays(1)))
        assertNull(assignments.findByGroup_GroupIdAndMissionDate(groupId, monday.minusWeeks(1)))
    }

    @Test
    fun certificationQueriesUseOwnerMissionKindAndMissionDateTogether() {
        val fixture = fixture()
        val personalAssignment = assignments.save(MissionAssignment.forUser(fixture.personalMission, fixture.user, monday))
        val groupAssignment = assignments.save(MissionAssignment.forGroup(fixture.groupMission, fixture.group, monday))
        val personal = certifications.save(Certification(fixture.user, personalAssignment, "images/personal.jpg", "개인 인증"))
        val group = certifications.save(Certification(fixture.user, groupAssignment, "images/group.jpg", "그룹 인증"))
        certifications.save(Certification(fixture.otherUser, groupAssignment, "images/other.jpg", "다른 사용자 인증"))
        flushAndClear()

        val userId = requireNotNull(fixture.user.userId)
        assertEquals(
            personal.certificationId,
            certifications.findByUser_UserIdAndMissionTypeAndMissionDate(userId, MissionType.PERSONAL, monday)?.certificationId,
        )
        assertEquals(
            group.certificationId,
            certifications.findByUser_UserIdAndMissionTypeAndMissionDate(userId, MissionType.GROUP, monday)?.certificationId,
        )
        assertTrue(certifications.existsByUser_UserIdAndMissionTypeAndMissionDate(userId, MissionType.PERSONAL, monday))
        assertFalse(
            certifications.existsByUser_UserIdAndMissionTypeAndMissionDate(
                requireNotNull(fixture.otherUser.userId), MissionType.PERSONAL, monday,
            ),
        )
        assertFalse(
            certifications.existsByUser_UserIdAndMissionTypeAndMissionDate(userId, MissionType.PERSONAL, monday.plusDays(1)),
        )
        assertNull(
            certifications.findByUser_UserIdAndMissionTypeAndMissionDate(userId, MissionType.GROUP, monday.plusWeeks(1)),
        )
    }

    @Test
    fun cheerQueriesCountEachCertificationAndCheckEachUserSeparately() {
        val fixture = fixture()
        val thirdUser = users.save(User("kakao-third", "응원하는양"))
        val personalAssignment = assignments.save(MissionAssignment.forUser(fixture.personalMission, fixture.user, monday))
        val groupAssignment = assignments.save(MissionAssignment.forGroup(fixture.groupMission, fixture.group, monday))
        val first = certifications.save(Certification(fixture.user, personalAssignment, "images/first.jpg", "첫 인증"))
        val second = certifications.save(Certification(fixture.user, groupAssignment, "images/second.jpg", "두번째 인증"))
        cheers.save(Cheer(fixture.otherUser, first))
        cheers.save(Cheer(thirdUser, first))
        cheers.save(Cheer(thirdUser, second))
        flushAndClear()

        val firstId = requireNotNull(first.certificationId)
        val secondId = requireNotNull(second.certificationId)
        val otherUserId = requireNotNull(fixture.otherUser.userId)
        assertEquals(2L, cheers.countByCertification_CertificationId(firstId))
        assertEquals(1L, cheers.countByCertification_CertificationId(secondId))
        assertEquals(0L, cheers.countByCertification_CertificationId(-1L))
        assertTrue(cheers.existsByUser_UserIdAndCertification_CertificationId(otherUserId, firstId))
        assertFalse(cheers.existsByUser_UserIdAndCertification_CertificationId(otherUserId, secondId))
        assertFalse(cheers.existsByUser_UserIdAndCertification_CertificationId(requireNotNull(fixture.user.userId), firstId))
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun fixture(): Fixture {
        val type = userTypes.save(UserType("TYPE_01", "유형 1", "유형 설명"))
        val group = groups.save(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = users.save(User("kakao-one", "포근한양"))
        val otherUser = users.save(User("kakao-two", "따뜻한양"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        otherUser.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.B, group)
        val personalMission = missions.save(Mission(MissionType.PERSONAL, "산책하기"))
        val groupMission = missions.save(Mission(MissionType.GROUP, "함께 걷기"))
        return Fixture(user, otherUser, group, personalMission, groupMission)
    }

    private data class Fixture(
        val user: User,
        val otherUser: User,
        val group: CareGroup,
        val personalMission: Mission,
        val groupMission: Mission,
    )
}
