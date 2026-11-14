// 전체/그룹 피드 조회의 공개범위 필터링, 커서 페이징, 응원 집계를 검증한다.
package com.ganzithon.team08.feed

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
class FeedServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) {
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @Test
    fun feedReturnsOnlyPublicCertificationsNewestFirst() {
        val group = newGroup()
        val viewer = newUser(group, "viewer")
        certification(group, Visibility.PRIVATE)
        val oldPublic = certification(group, Visibility.PUBLIC)
        certification(group, Visibility.GROUP)
        val newPublic = certification(group, Visibility.PUBLIC)
        entityManager.flush()

        val feed = service().getFeed(viewer.userId!!, null, 20)

        assertEquals(listOf(newPublic.certificationId, oldPublic.certificationId), feed.items.map { it.certificationId })
        assertFalse(feed.hasNext)
        assertEquals(null, feed.nextCursor)
    }

    @Test
    fun feedPagesByCursorAndReportsCheerCountAndCheeredFlag() {
        val group = newGroup()
        val viewer = newUser(group, "viewer")
        val first = certification(group, Visibility.PUBLIC)
        val second = certification(group, Visibility.PUBLIC)
        persist(Cheer(viewer, second))
        entityManager.flush()

        val firstPage = service().getFeed(viewer.userId!!, null, 1)
        assertEquals(listOf(second.certificationId), firstPage.items.map { it.certificationId })
        assertTrue(firstPage.hasNext)
        assertEquals(second.certificationId, firstPage.nextCursor)
        assertEquals(1L, firstPage.items[0].cheerCount)
        assertTrue(firstPage.items[0].cheered)

        val secondPage = service().getFeed(viewer.userId!!, firstPage.nextCursor, 1)
        assertEquals(listOf(first.certificationId), secondPage.items.map { it.certificationId })
        assertFalse(secondPage.hasNext)
        assertEquals(0L, secondPage.items[0].cheerCount)
        assertFalse(secondPage.items[0].cheered)
    }

    @Test
    fun feedExcludesCertificationsFromWithdrawnAuthors() {
        val group = newGroup()
        val viewer = newUser(group, "viewer")
        val author = newUser(group, "author")
        val post = certification(group, Visibility.PUBLIC, author)
        entityManager.flush()
        author.withdraw()
        entityManager.flush()

        val feed = service().getFeed(viewer.userId!!, null, 20)

        assertTrue(feed.items.none { it.certificationId == post.certificationId })
    }

    @Test
    fun groupFeedIncludesCurrentGroupMembersGroupAndPublicCertifications() {
        val group = newGroup()
        val author = newUser(group, "author")
        val viewer = newUser(group, "viewer")
        val groupPost = certification(group, Visibility.GROUP, author)
        val publicPost = certification(group, Visibility.PUBLIC, author)
        certification(group, Visibility.PRIVATE, author)
        entityManager.flush()

        val feed = service().getGroupFeed(viewer.userId!!, null, 20)

        assertEquals(
            setOf(groupPost.certificationId, publicPost.certificationId),
            feed.items.map { it.certificationId }.toSet(),
        )
    }

    @Test
    fun groupFeedExcludesAuthorWhoMovedToAnotherGroup() {
        val group = newGroup()
        val viewer = newUser(group, "viewer")
        val author = newUser(group, "author")
        val groupPost = certification(group, Visibility.GROUP, author)
        entityManager.flush()
        val otherType = persist(UserType("TYPE_${System.nanoTime()}", "유형2", "설명2"))
        val otherGroup = persist(CareGroup(LifeStage.EARLY_CAREER, otherType))
        author.updateType(LifeStage.EARLY_CAREER, otherType, AnswerChoice.A, otherGroup)
        entityManager.flush()

        val feed = service().getGroupFeed(viewer.userId!!, null, 20)

        assertTrue(feed.items.none { it.certificationId == groupPost.certificationId })
    }

    @Test
    fun groupFeedIsEmptyWhenViewerHasNoGroup() {
        val soloUser = persist(User("solo", "혼자양"))
        entityManager.flush()

        val feed = service().getGroupFeed(soloUser.userId!!, null, 20)

        assertTrue(feed.items.isEmpty())
        assertFalse(feed.hasNext)
    }

    @Test
    fun invalidPageSizeIsRejected() {
        val group = newGroup()
        val viewer = newUser(group, "viewer")

        val exception = assertThrows<BusinessException> { service().getFeed(viewer.userId!!, null, 0) }

        assertEquals(FeedErrorCode.INVALID_PAGE_SIZE, exception.errorCode)
    }

    private fun service(): FeedService = FeedService(certificationRepository, cheerRepository, userRepository)

    private var userSequence = 0
    private var dateSequence = 0

    private fun newGroup(): CareGroup {
        val type = persist(UserType("TYPE_${System.nanoTime()}", "유형", "설명"))
        return persist(CareGroup(LifeStage.JOB_SEEKER, type))
    }

    private fun newUser(group: CareGroup, name: String): User {
        val user = persist(User("kakao-$name-${userSequence++}", name))
        user.completeOnboarding(LifeStage.JOB_SEEKER, group.userType, AnswerChoice.A, group)
        return user
    }

    private fun certification(group: CareGroup, visibility: Visibility, author: User = newUser(group, "author-${userSequence}")): Certification {
        val sequence = dateSequence++
        val mission = persist(Mission(MissionType.PERSONAL, "미션"))
        val assignment = persist(MissionAssignment.forUser(mission, author, today.minusDays(sequence.toLong())))
        val certification = Certification(author, assignment, "images/$sequence.jpg", "인증", visibility)
        return persist(certification)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }
}
