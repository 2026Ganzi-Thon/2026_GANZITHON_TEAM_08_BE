// 엔티티 생성 시 배정 대상과 주간 기준일 및 본인 응원 금지 규칙을 검증한다.
package com.ganzithon.team08.domain

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class EntityRulesTests {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun groupMissionRequiresMondayAndMatchingMissionType() {
        val group = CareGroup(LifeStage.JOB_SEEKER, UserType("TYPE_01", "유형 1", "설명"))
        val groupMission = Mission(MissionType.GROUP, "함께 걷기")
        assertThrows<IllegalArgumentException> { MissionAssignment.forGroup(groupMission, group, monday.plusDays(1)) }
        assertThrows<IllegalArgumentException> { MissionAssignment.forUser(groupMission, User("one", "양"), monday) }
        assertThrows<IllegalArgumentException> {
            MissionAssignment.forGroup(Mission(MissionType.PERSONAL, "산책하기"), group, monday)
        }
    }

    @Test
    fun anotherUsersMissionCannotBeCertifiedEvenBeforeIdsAreGenerated() {
        val owner = User("one", "양 1")
        val otherUser = User("two", "양 2")
        val assignment = MissionAssignment.forUser(Mission(MissionType.PERSONAL, "산책하기"), owner, monday)
        assertThrows<IllegalArgumentException> { Certification(otherUser, assignment, "images/walk.jpg", "인증") }
    }

    @Test
    fun selfCheerIsRejectedEvenBeforeIdsAreGenerated() {
        val user = User("one", "양")
        val assignment = MissionAssignment.forUser(Mission(MissionType.PERSONAL, "산책하기"), user, monday)
        val certification = Certification(user, assignment, "images/walk.jpg", "인증")
        assertThrows<IllegalArgumentException> { Cheer(user, certification) }
    }

    @Test
    fun groupVisibilityRequiresMembershipOnCreationAndChange() {
        val user = User("one", "양")
        val assignment = MissionAssignment.forUser(Mission(MissionType.PERSONAL, "산책하기"), user, monday)
        assertThrows<IllegalArgumentException> {
            Certification(user, assignment, "images/walk.jpg", "인증", Visibility.GROUP)
        }
        val certification = Certification(user, assignment, "images/walk.jpg", "인증")
        assertThrows<IllegalArgumentException> { certification.changeVisibility(Visibility.GROUP) }
    }
}
