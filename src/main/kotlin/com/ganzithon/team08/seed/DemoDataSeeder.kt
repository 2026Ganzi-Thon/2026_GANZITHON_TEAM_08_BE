// local 프로필에서 시연용 더미 데이터(사용자·그룹·미션·인증·응원)를 한 번만 채워 넣는다.
package com.ganzithon.team08.seed

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
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
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.domain.user.UserTypeRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Component
@Profile("local")
class DemoDataSeeder(
    private val userTypeRepository: UserTypeRepository,
    private val careGroupRepository: CareGroupRepository,
    private val userRepository: UserRepository,
    private val missionRepository: MissionRepository,
    private val missionAssignmentRepository: MissionAssignmentRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: org.springframework.boot.ApplicationArguments) {
        if (certificationRepository.count() > 0) return

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val typeA = userTypeRepository.save(UserType("TYPE_01", "부담정돈형", "부담을 나누기보다 스스로 정돈하며 회복하는 유형"))
        val typeB = userTypeRepository.save(UserType("TYPE_02", "동행충전형", "함께하는 활동으로 에너지를 채우는 유형"))
        val groupA = careGroupRepository.save(CareGroup(LifeStage.JOB_SEEKER, typeA))
        val groupB = careGroupRepository.save(CareGroup(LifeStage.EARLY_CAREER, typeB))

        val user1 = onboardedUser("demo-user-1", "포근한양", LifeStage.JOB_SEEKER, typeA, groupA)
        val user2 = onboardedUser("demo-user-2", "따뜻한양", LifeStage.JOB_SEEKER, typeA, groupA)
        val user3 = onboardedUser("demo-user-3", "차분한양", LifeStage.EARLY_CAREER, typeB, groupB)
        val user4 = userRepository.save(User("demo-user-4", "온보딩전양")) // 그룹 없음(온보딩 미완료) 시연용
        val user5 = onboardedUser("demo-user-5", "탈퇴양", LifeStage.JOB_SEEKER, typeA, groupA)

        val walkMission = missionRepository.save(Mission(MissionType.PERSONAL, "산책하기"))
        val waterMission = missionRepository.save(Mission(MissionType.PERSONAL, "물 마시기"))
        val groupMission = missionRepository.save(Mission(MissionType.GROUP, "함께 걷기"))

        // user1: 최근 5일 연속 개인미션 인증 → currentStreak=5, longestStreak=5
        (0 until 5).forEach { offset ->
            personalCertification(user1, walkMission, today.minusDays(offset.toLong()), Visibility.PUBLIC, "오늘도 산책 완료!")
        }

        // user2: 오늘과 그제만 인증(하루 공백) → 스트릭이 끊기는 케이스
        personalCertification(user2, waterMission, today, Visibility.GROUP, "물 많이 마시기")
        personalCertification(user2, waterMission, today.minusDays(2), Visibility.PRIVATE, "오늘은 비공개로")

        // user3: 다른 그룹, PUBLIC 글 하나
        val user3Cert = personalCertification(user3, walkMission, today, Visibility.PUBLIC, "저도 산책 중이에요")

        // user4: 그룹 없이도 개인미션은 인증 가능
        personalCertification(user4, walkMission, today, Visibility.PRIVATE, "혼자 걷기")

        // user5: 탈퇴 전 인증 하나 남기고 탈퇴 → 피드/기록에서 숨겨지는지 시연용
        personalCertification(user5, walkMission, today, Visibility.PUBLIC, "탈퇴 전 마지막 인증")
        user5.withdraw()

        // groupA 주간 미션: user1, user2가 각자 인증
        val groupAssignment = missionAssignmentRepository.save(MissionAssignment.forGroup(groupMission, groupA, weekStart))
        val user1GroupCert = certificationRepository.save(
            Certification(user1, groupAssignment, "images/demo-group-1.jpg", "이번 주도 같이 걸었어요", Visibility.GROUP),
        )
        certificationRepository.save(
            Certification(user2, groupAssignment, "images/demo-group-2.jpg", "그룹 미션 완료!", Visibility.PUBLIC),
        )

        // 응원 몇 개
        cheerRepository.save(Cheer(user2, user1GroupCert))
        cheerRepository.save(Cheer(user1, user3Cert))
    }

    private fun onboardedUser(kakaoUserId: String, nickname: String, lifeStage: LifeStage, userType: UserType, group: CareGroup): User {
        val user = userRepository.save(User(kakaoUserId, nickname))
        user.completeOnboarding(lifeStage, userType, AnswerChoice.A, group)
        return user
    }

    private fun personalCertification(
        user: User,
        mission: Mission,
        date: LocalDate,
        visibility: Visibility,
        content: String,
    ): Certification {
        val assignment = missionAssignmentRepository.save(MissionAssignment.forUser(mission, user, date))
        return certificationRepository.save(
            Certification(user, assignment, "images/demo-${user.userId}-$date.jpg", content, visibility),
        )
    }
}
