// 댓글 등록·조회·삭제의 정상 처리와 열람권한·탈퇴작성자·삭제권한 거절을 검증한다.
package com.ganzithon.team08.comment

import com.ganzithon.team08.certification.CertificationInteractionAccess

import com.ganzithon.team08.certification.CertificationErrorCode
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.comment.CommentRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZoneId

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class CommentServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val commentRepository: CommentRepository,
) {
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @Test
    fun registeringCommentReturnsItWithMineTrue() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val response = service().register(fixture.otherUser.userId!!, certification.certificationId!!, "힘내세요!")

        assertEquals("힘내세요!", response.content)
        assertEquals("따뜻한양", response.nickname)
        assertTrue(response.mine)
    }

    @Test
    fun listReturnsCommentsInWrittenOrderWithMineFlag() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        service().register(fixture.otherUser.userId!!, certification.certificationId!!, "첫 댓글")
        entityManager.flush()
        service().register(fixture.user.userId!!, certification.certificationId!!, "두번째 댓글")
        entityManager.flush()

        val response = service().list(fixture.otherUser.userId!!, certification.certificationId!!)

        assertEquals(2L, response.commentCount)
        assertEquals("첫 댓글", response.comments[0].content)
        assertTrue(response.comments[0].mine)
        assertEquals("두번째 댓글", response.comments[1].content)
        assertFalse(response.comments[1].mine)
    }

    @Test
    fun commentingOnPrivateCertificationByOthersIsForbidden() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PRIVATE))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, certification.certificationId!!, "댓글")
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun commentingOnMissingCertificationIsNotFound() {
        val fixture = fixture()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, 999_999L, "댓글")
        }

        assertEquals(CertificationErrorCode.CERT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun commentingOnCertificationByWithdrawnAuthorIsNotFound() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        fixture.user.withdraw()
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(fixture.otherUser.userId!!, certification.certificationId!!, "댓글")
        }

        assertEquals(CertificationErrorCode.CERT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun unknownViewerIsRejected() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().register(999_999L, certification.certificationId!!, "댓글")
        }

        assertEquals(CommonErrorCode.NOT_FOUND, exception.errorCode)
    }

    @Test
    fun authorCanDeleteOwnComment() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        val comment = service().register(fixture.otherUser.userId!!, certification.certificationId!!, "삭제될 댓글")
        entityManager.flush()

        service().delete(fixture.otherUser.userId!!, certification.certificationId!!, comment.commentId)

        assertEquals(0L, commentRepository.countByCertification_CertificationId(certification.certificationId!!))
    }

    @Test
    fun deletingAnothersCommentIsForbidden() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()
        val comment = service().register(fixture.otherUser.userId!!, certification.certificationId!!, "삭제 시도 대상")
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().delete(fixture.user.userId!!, certification.certificationId!!, comment.commentId)
        }

        assertEquals(CommentErrorCode.COMMENT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun deletingMissingCommentIsNotFound() {
        val fixture = fixture()
        val certification = persist(certification(fixture, Visibility.PUBLIC))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service().delete(fixture.otherUser.userId!!, certification.certificationId!!, 999_999L)
        }

        assertEquals(CommentErrorCode.COMMENT_NOT_FOUND, exception.errorCode)
    }

    private fun service(): CommentService = CommentService(
        userRepository, certificationRepository, commentRepository,
        CertificationInteractionAccess(userRepository, certificationRepository),
    )

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
