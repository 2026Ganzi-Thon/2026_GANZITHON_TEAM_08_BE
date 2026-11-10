// 인증 등록 서비스의 정상 처리와 실패 변환을 실제 MissionAssignmentService·가짜 이미지 저장소로 검증한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.Cheer
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.mission.Mission
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionAssignmentRepository
import com.ganzithon.team08.domain.mission.MissionAssignmentService
import com.ganzithon.team08.domain.mission.MissionRepository
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageStorage
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class CertificationServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
    private val missionRepository: MissionRepository,
    private val missionAssignmentRepository: MissionAssignmentRepository,
) {
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @Test
    fun registersPersonalCertificationAndStoresImage() {
        val fixture = fixture()
        val storage = FakeImageStorage()

        val response = service(storage).register(
            fixture.user.userId!!,
            CertificationRegisterRequest(jpegImage(), "산책을 완료했습니다.", MissionType.PERSONAL, fixture.mission.missionId!!),
        )

        assertEquals(today, response.missionDate)
        assertEquals(Visibility.PRIVATE, response.visibility)
        assertEquals("/api/images/${response.certificationId}", response.imageUrl)
        val saved = certificationRepository.findById(response.certificationId).orElseThrow()
        assertTrue(storage.saved.containsKey(saved.imageStorageKey))
    }

    @Test
    fun blankContentIsRejectedBeforeTouchingImageStorage() {
        val fixture = fixture()
        val storage = FakeImageStorage()

        val exception = assertThrows<BusinessException> {
            service(storage).register(
                fixture.user.userId!!,
                CertificationRegisterRequest(jpegImage(), "   ", MissionType.PERSONAL, fixture.mission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.CERT_INVALID_CONTENT, exception.errorCode)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun nonImageBytesAreRejected() {
        val fixture = fixture()
        val fakeImage = MockMultipartFile("image", "fake.jpg", "image/jpeg", "텍스트 파일입니다".toByteArray())

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).register(
                fixture.user.userId!!,
                CertificationRegisterRequest(fakeImage, "인증", MissionType.PERSONAL, fixture.mission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.CERT_INVALID_IMAGE, exception.errorCode)
    }

    @Test
    fun groupVisibilityWithoutGroupIsRejected() {
        val user = persist(User("solo-user", "혼자양"))
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        persist(MissionAssignment.forUser(mission, user, today))

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).register(
                user.userId!!,
                CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, mission.missionId!!, Visibility.GROUP),
            )
        }

        assertEquals(CertificationErrorCode.CERT_INVALID_VISIBILITY, exception.errorCode)
    }

    @Test
    fun missionWithoutAnAssignmentForTodayIsRejected() {
        val fixture = fixture()
        val unassignedMission = persist(Mission(MissionType.PERSONAL, "물 마시기"))
        val storage = FakeImageStorage()

        val exception = assertThrows<BusinessException> {
            service(storage).register(
                fixture.user.userId!!,
                CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, unassignedMission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.MISSION_NOT_ASSIGNED, exception.errorCode)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun unknownUserIsRejectedBeforeCallingAssignmentService() {
        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).register(999_999L, CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, 1L))
        }

        assertEquals(CommonErrorCode.NOT_FOUND, exception.errorCode)
    }

    @Test
    fun duplicateCertificationCleansUpStoredImageAndReturns409() {
        val fixture = fixture()
        persist(Certification(fixture.user, fixture.assignment, "images/existing.jpg", "이미 인증함"))
        entityManager.flush()
        val storage = FakeImageStorage()

        val exception = assertThrows<BusinessException> {
            service(storage).register(
                fixture.user.userId!!,
                CertificationRegisterRequest(jpegImage(), "두번째 인증", MissionType.PERSONAL, fixture.mission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.CERT_ALREADY_EXISTS, exception.errorCode)
        assertTrue(storage.saved.isEmpty())
        assertTrue(storage.deleted.isNotEmpty())
    }

    @Test
    fun ownerCanViewOwnPrivateCertificationDetail() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        entityManager.flush()

        val detail = service(FakeImageStorage()).getDetail(fixture.user.userId!!, certification.certificationId!!)

        assertTrue(detail.mine)
        assertFalse(detail.cheered)
        assertEquals(0L, detail.cheerCount)
        assertEquals(Visibility.PRIVATE, detail.visibility)
        assertEquals(fixture.mission.missionTitle, detail.missionTitle)
    }

    @Test
    fun privateCertificationDetailIsForbiddenToOthers() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).getDetail(fixture.otherUser.userId!!, certification.certificationId!!)
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun publicCertificationDetailIsVisibleToOthersAndReflectsCheer() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        certification.changeVisibility(Visibility.PUBLIC)
        persist(Cheer(fixture.otherUser, certification))
        entityManager.flush()

        val detail = service(FakeImageStorage()).getDetail(fixture.otherUser.userId!!, certification.certificationId!!)

        assertFalse(detail.mine)
        assertTrue(detail.cheered)
        assertEquals(1L, detail.cheerCount)
    }

    @Test
    fun groupCertificationIsVisibleToCurrentGroupMember() {
        val fixture = fixture()
        val groupMission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val groupAssignment = persist(MissionAssignment.forGroup(groupMission, fixture.user.group!!, mondayOf(today)))
        val certification = persist(Certification(fixture.user, groupAssignment, "images/group.jpg", "그룹 인증", Visibility.GROUP))
        entityManager.flush()

        val detail = service(FakeImageStorage()).getDetail(fixture.otherUser.userId!!, certification.certificationId!!)

        assertEquals(Visibility.GROUP, detail.visibility)
        assertFalse(detail.mine)
    }

    @Test
    fun groupCertificationIsForbiddenToNonGroupMember() {
        val fixture = fixture()
        val outsider = persist(User("outsider", "남양"))
        val groupMission = persist(Mission(MissionType.GROUP, "함께 걷기"))
        val groupAssignment = persist(MissionAssignment.forGroup(groupMission, fixture.user.group!!, mondayOf(today)))
        val certification = persist(Certification(fixture.user, groupAssignment, "images/group.jpg", "그룹 인증", Visibility.GROUP))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).getDetail(outsider.userId!!, certification.certificationId!!)
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun detailOfMissingCertificationIsNotFound() {
        val fixture = fixture()

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).getDetail(fixture.user.userId!!, 999_999L)
        }

        assertEquals(CertificationErrorCode.CERT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun ownerCanLoadOwnImageBytes() {
        val fixture = fixture()
        val storage = FakeImageStorage()
        val storageKey = storage.save(jpegBytes, ImageFormat.JPEG)
        val certification = persist(Certification(fixture.user, fixture.assignment, storageKey, "인증"))
        entityManager.flush()

        val (bytes, format) = service(storage).getImage(fixture.user.userId!!, certification.certificationId!!)

        assertTrue(jpegBytes.contentEquals(bytes))
        assertEquals(ImageFormat.JPEG, format)
    }

    @Test
    fun imageOfPrivateCertificationIsForbiddenToOthers() {
        val fixture = fixture()
        val storage = FakeImageStorage()
        val storageKey = storage.save(jpegBytes, ImageFormat.JPEG)
        val certification = persist(Certification(fixture.user, fixture.assignment, storageKey, "인증"))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service(storage).getImage(fixture.otherUser.userId!!, certification.certificationId!!)
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun ownerCanChangeVisibilityToPublic() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        entityManager.flush()

        val detail = service(FakeImageStorage()).changeVisibility(
            fixture.user.userId!!,
            certification.certificationId!!,
            Visibility.PUBLIC,
        )

        assertEquals(Visibility.PUBLIC, detail.visibility)
        assertEquals(Visibility.PUBLIC, certificationRepository.findById(certification.certificationId!!).orElseThrow().visibility)
    }

    @Test
    fun nonOwnerCannotChangeVisibility() {
        val fixture = fixture()
        val certification = persist(certification(fixture))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).changeVisibility(fixture.otherUser.userId!!, certification.certificationId!!, Visibility.PUBLIC)
        }

        assertEquals(CertificationErrorCode.CERT_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun changingVisibilityToGroupWithoutMatchingGroupIsRejected() {
        val user = persist(User("solo-user-2", "혼자양2"))
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, user, today))
        val certification = persist(Certification(user, assignment, "images/solo.jpg", "인증"))
        entityManager.flush()

        val exception = assertThrows<BusinessException> {
            service(FakeImageStorage()).changeVisibility(user.userId!!, certification.certificationId!!, Visibility.GROUP)
        }

        assertEquals(CertificationErrorCode.CERT_INVALID_VISIBILITY, exception.errorCode)
    }

    private fun service(storage: ImageStorage): CertificationService {
        val missionAssignmentService = MissionAssignmentService(missionRepository, missionAssignmentRepository, userRepository)
        return CertificationService(userRepository, certificationRepository, cheerRepository, missionAssignmentService, storage)
    }

    private fun jpegImage(): MockMultipartFile = MockMultipartFile("image", "photo.jpg", "image/jpeg", jpegBytes)

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun certification(fixture: Fixture): Certification =
        Certification(fixture.user, fixture.assignment, "images/walk.jpg", "산책을 완료했습니다.")

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun fixture(): Fixture {
        val type = persist(UserType("TYPE_01", "유형 1", "설명"))
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

private class FakeImageStorage : ImageStorage {
    val saved = linkedMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()
    private var sequence = 0

    override fun save(content: ByteArray, format: ImageFormat): String {
        val key = "fake-${sequence++}.${format.extension}"
        saved[key] = content
        return key
    }

    override fun load(storageKey: String): ByteArray = saved.getValue(storageKey)

    override fun delete(storageKey: String) {
        saved.remove(storageKey)
        deleted.add(storageKey)
    }
}
