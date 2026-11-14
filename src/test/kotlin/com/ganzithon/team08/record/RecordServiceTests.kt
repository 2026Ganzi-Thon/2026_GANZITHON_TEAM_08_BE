// 월별/날짜별 기록 조회의 완료 여부 표시, 스트릭, 형식 검증을 검증한다.
package com.ganzithon.team08.record

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.global.exception.BusinessException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class RecordServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) {
    @Test
    fun monthlyRecordMarksPersonalCompletedOnExactDays() {
        val fixture = fixture()
        val month = LocalDate.of(2026, 6, 1)
        personalCertification(fixture, month.withDayOfMonth(5))
        entityManager.flush()

        val record = service().getMonthlyRecord(fixture.user.userId!!, "2026-06")

        assertEquals(30, record.days.size)
        assertTrue(record.days.single { it.date == month.withDayOfMonth(5) }.personalCompleted)
        assertTrue(record.days.filterNot { it.date == month.withDayOfMonth(5) }.none { it.personalCompleted })
    }

    @Test
    fun monthlyRecordMarksGroupCompletedForWholeWeek() {
        val fixture = fixture()
        val weekStart = LocalDate.of(2026, 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        groupCertification(fixture, weekStart)
        entityManager.flush()

        val record = service().getMonthlyRecord(fixture.user.userId!!, "2026-06")

        val weekDates = (0..6L).map { weekStart.plusDays(it) }
        assertTrue(record.days.filter { it.date in weekDates }.all { it.groupCompleted })
        assertTrue(record.days.filterNot { it.date in weekDates }.none { it.groupCompleted })
    }

    @Test
    fun longestStreakReflectsDatesOutsideTheQueriedMonth() {
        val fixture = fixture()
        personalCertification(fixture, LocalDate.of(2026, 5, 29))
        personalCertification(fixture, LocalDate.of(2026, 5, 30))
        personalCertification(fixture, LocalDate.of(2026, 5, 31))
        entityManager.flush()

        val record = service().getMonthlyRecord(fixture.user.userId!!, "2026-06")

        assertEquals(3, record.longestStreak)
    }

    @Test
    fun invalidMonthFormatIsRejected() {
        val fixture = fixture()

        val exception = assertThrows<BusinessException> {
            service().getMonthlyRecord(fixture.user.userId!!, "2026/06")
        }

        assertEquals(RecordErrorCode.INVALID_MONTH, exception.errorCode)
    }

    @Test
    fun dateRecordsReturnCertificationsWithCheerCount() {
        val fixture = fixture()
        val date = LocalDate.of(2026, 6, 5)
        val certification = personalCertification(fixture, date)
        persist(Cheer(fixture.otherUser, certification))
        entityManager.flush()

        val records = service().getDateRecords(fixture.user.userId!!, "2026-06-05")

        assertEquals(1, records.size)
        assertEquals(1L, records[0].cheerCount)
        assertEquals(certification.certificationId, records[0].certificationId)
    }

    @Test
    fun dateRecordsAreEmptyWhenNoCertificationExists() {
        val fixture = fixture()

        val records = service().getDateRecords(fixture.user.userId!!, "2026-06-05")

        assertTrue(records.isEmpty())
    }

    @Test
    fun invalidDateFormatIsRejected() {
        val fixture = fixture()

        val exception = assertThrows<BusinessException> {
            service().getDateRecords(fixture.user.userId!!, "06-05-2026")
        }

        assertEquals(RecordErrorCode.INVALID_DATE, exception.errorCode)
    }

    private fun service(): RecordService = RecordService(certificationRepository, cheerRepository)

    private fun personalCertification(fixture: Fixture, date: LocalDate): Certification {
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, fixture.user, date))
        return persist(Certification(fixture.user, assignment, "images/$date.jpg", "인증", Visibility.PRIVATE))
    }

    private fun groupCertification(fixture: Fixture, weekStart: LocalDate): Certification {
        val mission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val assignment = persist(MissionAssignment.forGroup(mission, fixture.group, weekStart))
        return persist(Certification(fixture.user, assignment, "images/$weekStart.jpg", "그룹 인증", Visibility.GROUP))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun fixture(): Fixture {
        val type = persist(UserType("TYPE_${System.nanoTime()}", "유형", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("kakao-one", "포근한양"))
        val otherUser = persist(User("kakao-two", "따뜻한양"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        otherUser.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.B, group)
        return Fixture(user, otherUser, group)
    }

    private data class Fixture(val user: User, val otherUser: User, val group: CareGroup)
}
