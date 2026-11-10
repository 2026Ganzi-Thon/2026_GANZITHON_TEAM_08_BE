// 개인·그룹 미션 배정 조회의 정상 처리와 기간·소속 불일치 실패를 검증한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class MissionAssignmentServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val missionRepository: MissionRepository,
    private val missionAssignmentRepository: MissionAssignmentRepository,
    private val userRepository: UserRepository,
) {
    private val monday = LocalDate.of(2026, 11, 2)
    private val service by lazy { MissionAssignmentService(missionRepository, missionAssignmentRepository, userRepository) }

    @Test
    fun findsPersonalAssignmentForReferenceDate() {
        val user = persist(User("one", "양"))
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, user, monday))

        val found = service.findCurrentAssignment(user.userId!!, mission.missionId!!, MissionType.PERSONAL, monday)

        assertEquals(assignment.assignmentId, found.assignmentId)
    }

    @Test
    fun findsGroupAssignmentForAnyDayInTheSameWeek() {
        val type = persist(UserType("TYPE_01", "유형 1", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("two", "양2"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val assignment = persist(MissionAssignment.forGroup(mission, group, monday))

        val wednesday = monday.plusDays(2)
        val found = service.findCurrentAssignment(user.userId!!, mission.missionId!!, MissionType.GROUP, wednesday)

        assertEquals(assignment.assignmentId, found.assignmentId)
    }

    @Test
    fun missingMissionIsRejected() {
        val user = persist(User("three", "양3"))

        val exception = assertThrows<BusinessException> {
            service.findCurrentAssignment(user.userId!!, 999_999L, MissionType.PERSONAL, monday)
        }

        assertEquals(MissionErrorCode.MISSION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun missionTypeMismatchIsTreatedAsMissionNotFound() {
        val user = persist(User("four", "양4"))
        val groupMission = persist(Mission(MissionType.GROUP, "함께 걷기"))

        val exception = assertThrows<BusinessException> {
            service.findCurrentAssignment(user.userId!!, groupMission.missionId!!, MissionType.PERSONAL, monday)
        }

        assertEquals(MissionErrorCode.MISSION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun personalAssignmentFromAnotherDayIsRejectedAsNotAssigned() {
        val user = persist(User("five", "양5"))
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        persist(MissionAssignment.forUser(mission, user, monday))

        val exception = assertThrows<BusinessException> {
            service.findCurrentAssignment(user.userId!!, mission.missionId!!, MissionType.PERSONAL, monday.plusDays(1))
        }

        assertEquals(MissionErrorCode.MISSION_NOT_ASSIGNED, exception.errorCode)
    }

    @Test
    fun groupAssignmentIsRejectedWhenUserHasNoGroup() {
        val user = persist(User("six", "양6"))
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))

        val exception = assertThrows<BusinessException> {
            service.findCurrentAssignment(user.userId!!, mission.missionId!!, MissionType.GROUP, monday)
        }

        assertEquals(MissionErrorCode.MISSION_NOT_ASSIGNED, exception.errorCode)
    }

    @Test
    fun groupAssignmentFromAnotherWeekIsRejectedAsNotAssigned() {
        val type = persist(UserType("TYPE_02", "유형 2", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("seven", "양7"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        persist(MissionAssignment.forGroup(mission, group, monday))

        val exception = assertThrows<BusinessException> {
            service.findCurrentAssignment(user.userId!!, mission.missionId!!, MissionType.GROUP, monday.plusWeeks(1))
        }

        assertEquals(MissionErrorCode.MISSION_NOT_ASSIGNED, exception.errorCode)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }
}
