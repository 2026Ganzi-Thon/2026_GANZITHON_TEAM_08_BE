// 응원 등록의 정상 처리와 본인응원·중복응원·열람권한·탈퇴작성자 거절을 검증한다.
package com.ganzithon.team08.cheer

import com.ganzithon.team08.certification.CertificationErrorCode
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
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
import com.ganzithon.team08.global.exception.CommonErrorCode
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZoneId

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class CheerServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) {
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @Test
    fun registeringCheerIncrementsCount() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val response = service().register(fixture.otherUser.userId!!, certification.certificationId!!)

        assertEquals(1L, response.cheerCount)
    }

    @Test
    fun selfCheerIsRejected() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.user.userId!!, certification.certificationId!!)
        }

        assertEquals(CheerErrorCode.CHEER_SELF, exception.errorCode)
    }

    @Test
    fun duplicateCheerIsRejected() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        service().register(fixture.otherUser.userId!!, certification.certificationId!!)
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, certification.certificationId!!)
        }

        assertEquals(CheerErrorCode.CHEER_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun cheeringPrivateCertificationByOthersIsForbidden() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PRIVATE))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, certification.certificationId!!)
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun cheeringMissingCertificationIsNotFound() {
        val fixture = fixture()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, 999_999L)
        }

        assertEquals(CertificationErrorCode.CERT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun cheeringCertificationByWithdrawnAuthorIsNotFound() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        fixture.user.withdraw()
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, certification.certificationId!!)
        }

        assertEquals(CertificationErrorCode.CERT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun unknownViewerIsRejected() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(999_999L, certification.certificationId!!)
        }

        assertEquals(CommonErrorCode.NOT_FOUND, exception.errorCode)
    }

    private fun service(): CheerService = CheerService(userRepository, certificationRepository, cheerRepository)

    private fun certification(fixture: Fixture, visibility: Visibility): Certification =
        Certification(fixture.user, fixture.assignment, "images/walk.jpg", "산책을 완료했습니다.", visibility)

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun fixture(): Fixture {
        val type = persist(UserType("TYPE_${System.nanoTime()}", "유형", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("kakao-one", "포근한양"))
        val otherUser = persist(User("kakao-two", "따뜻한양"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        otherUser.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.B, group)
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, user, today))
        return Fixture(user, otherUser, mission, assignment)
    }

    private data class Fixture(val user: User, val otherUser: User, val mission: Mission, val assignment: MissionAssignment)
}
