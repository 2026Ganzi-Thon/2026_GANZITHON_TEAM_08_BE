// 탈퇴 작업의 재시도, DB 참조 정리, 기존 사진 전환을 실제 저장소로 검증한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.certification.LegacyImagePrivacyService
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cleanup.CleanupTaskRepository
import com.ganzithon.team08.domain.cleanup.CleanupTaskType
import com.ganzithon.team08.domain.comment.Comment
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.onboarding.OnboardingAnswer
import com.ganzithon.team08.domain.onboarding.OnboardingQuestion
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.security.JwtProvider
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageSanitizer
import com.ganzithon.team08.global.storage.ImageStorage
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = ["app.privacy.cleanup.delay-ms=3600000"])
class WithdrawalCleanupIntegrationTests @Autowired constructor(
    private val withdrawal: WithdrawService,
    private val cleanup: WithdrawalCleanupService,
    private val tasks: CleanupTaskRepository,
    private val users: UserRepository,
    private val entities: EntityManager,
    private val tx: TransactionTemplate,
    private val images: ImageStorage,
    private val photoPrivacy: LegacyImagePrivacyService,
    private val sanitizer: ImageSanitizer,
    private val jwt: JwtProvider,
    private val mvc: MockMvc,
) {
    @Test
    fun `탈퇴 시 사진과 카카오 작업을 등록하고 재시도 성공 후 회원의 참조 데이터를 모두 삭제한다`() {
        val photo = images.save(originalPhoto(), ImageFormat.JPEG)
        val (retired, remaining) = requireNotNull(tx.execute {
            val retired = createPost(photo)
            val remaining = createPost("other.jpg")
            val question = entities.find(OnboardingQuestion::class.java, 1)
                ?: persist(OnboardingQuestion(1, "질문", "선택 A", "선택 B"))
            persist(OnboardingAnswer(retired.user, question, AnswerChoice.A))
            persist(Cheer(retired.user, remaining))
            persist(Cheer(remaining.user, retired))
            persist(Comment(retired.user, remaining, "탈퇴자가 남긴 댓글"))
            persist(Comment(remaining.user, retired, "탈퇴자의 글에 달린 댓글"))
            postIds(retired) to postIds(remaining)
        })
        val token = jwt.createAccessToken(retired.user)
        val now = Instant.now()
        withdrawal.withdraw(retired.user)

        val due = cleanup.dueIds(now).toSet()
        val work = tasks.findAll().filter { it.withdrawnUserId == retired.user && it.taskId in due }
            .mapNotNull { cleanup.claim(requireNotNull(it.taskId), now) }
        assertEquals(setOf(CleanupTaskType.DELETE_IMAGE, CleanupTaskType.UNLINK_KAKAO), work.map { it.type }.toSet())
        assertTrue(work.all { cleanup.claim(it.id, now) == null })
        images.delete(work.single { it.type == CleanupTaskType.DELETE_IMAGE }.target)
        cleanup.finish(work.single { it.type == CleanupTaskType.DELETE_IMAGE })

        val unlink = work.single { it.type == CleanupTaskType.UNLINK_KAKAO }
        cleanup.retry(unlink, "KAKAO_UNLINK_HTTP_503", now)
        assertFalse(cleanup.dueIds(now).contains(unlink.id))
        assertEquals(UserStatus.WITHDRAWN, users.findById(retired.user).orElseThrow().status)
        assertFailsWith<FileNotFoundException> { images.load(photo) }

        val retry = assertNotNull(cleanup.claim(unlink.id, now.plusSeconds(31)))
        cleanup.finish(unlink) // 이전 처리기의 결과는 새 시도를 완료할 수 없다.
        assertTrue(users.existsById(retired.user))
        cleanup.finish(retry)

        assertFalse(users.existsById(retired.user))
        assertTrue(work.none { tasks.existsById(it.id) })
        tx.executeWithoutResult {
            assertNull(entities.find(Certification::class.java, retired.certification))
            assertNull(entities.find(MissionAssignment::class.java, retired.assignment))
            assertNotNull(entities.find(Certification::class.java, remaining.certification))
        }
        mvc.perform(get("/api/me/type").cookie(Cookie("ACCESS_TOKEN", token))).andExpect(status().isUnauthorized())
    }

    @Test
    fun `처리기가 종료되면 임대 시간이 지난 작업을 새 처리기가 가져간다`() {
        val id = requireNotNull(users.save(User("lease-${UUID.randomUUID()}", "임대 테스트 회원")).userId)
        withdrawal.withdraw(id)
        val taskId = requireNotNull(tasks.findAll().single { it.withdrawnUserId == id }.taskId)
        val now = Instant.now()
        val original = assertNotNull(cleanup.claim(taskId, now))
        assertNull(cleanup.claim(taskId, now.plusSeconds(119)))

        val reclaimed = assertNotNull(cleanup.claim(taskId, now.plusSeconds(121)))
        cleanup.finish(original)
        assertTrue(users.existsById(id))
        cleanup.finish(reclaimed)
        assertFalse(users.existsById(id))
    }

    @Test
    fun `기존 탈퇴 회원은 작업을 한 번만 등록한다`() {
        val id = requireNotNull(tx.execute {
            val user = persist(User("legacy-${UUID.randomUUID()}", "기존 탈퇴 회원"))
            user.withdraw()
            user.userId
        })

        withdrawal.enqueueLegacyWithdrawals()
        withdrawal.enqueueLegacyWithdrawals()

        val jobs = tasks.findAll().filter { it.withdrawnUserId == id }
        assertEquals(listOf(CleanupTaskType.UNLINK_KAKAO), jobs.map { it.taskType })
    }

    @Test
    fun `기존 사진은 같은 키에 한 번만 교체하고 탈퇴 후에는 다시 쓰지 않는다`() {
        val photo = images.save(originalPhoto(), ImageFormat.JPEG)
        try {
            val id = requireNotNull(tx.execute { createPost(photo).certificationId })
            val image = assertNotNull(photoPrivacy.pending(Instant.now()).firstOrNull { it.id == id })
            val cleaned = sanitizer.sanitize(images.load(image.key), ImageFormat.JPEG)
            photoPrivacy.replace(image, cleaned)
            photoPrivacy.replace(image, originalPhoto())

            assertContentEquals(cleaned, images.load(photo))
            assertEquals(8, ImageIO.read(images.load(photo).inputStream()).width)
            assertTrue(photoPrivacy.pending(Instant.now()).none { it.id == id })

            withdrawal.withdraw(image.userId)
            images.delete(photo)
            photoPrivacy.replace(image, cleaned)
            assertFailsWith<FileNotFoundException> { images.load(photo) }
        } finally {
            images.delete(photo)
        }
    }

    private fun createPost(photo: String): Certification {
        val user = persist(User("withdraw-${UUID.randomUUID()}", "테스트 회원"))
        val mission = persist(Mission(MissionType.PERSONAL, "테스트 미션"))
        val assignment = persist(MissionAssignment.forUser(mission, user, LocalDate.of(2026, 11, 20)))
        return persist(Certification(user, assignment, photo, "인증 글", Visibility.PUBLIC))
    }

    private fun postIds(post: Certification) = PostIds(post.user.userId!!, post.certificationId!!, post.assignment.assignmentId!!)

    private fun originalPhoto() = requireNotNull(javaClass.getResourceAsStream("/privacy/gps-orientation.jpg")).use { it.readAllBytes() }

    private fun <T : Any> persist(entity: T): T = entity.also(entities::persist)

    private data class PostIds(val user: Long, val certification: Long, val assignment: Long)
}
