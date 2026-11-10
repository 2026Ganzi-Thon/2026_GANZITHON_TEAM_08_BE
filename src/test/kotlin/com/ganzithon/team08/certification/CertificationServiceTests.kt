// 인증 등록 서비스의 정상 처리와 실패 변환을 가짜 배정 조회·이미지 저장소로 검증한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationRegisterRequest
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
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
import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageStorage
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class CertificationServiceTests @Autowired constructor(
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
) {
    private val monday = LocalDate.of(2026, 11, 2)
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @Test
    fun registersPersonalCertificationAndStoresImage() {
        val fixture = fixture()
        val storage = FakeImageStorage()
        val service = service(storage) { _, _, _, _ -> fixture.assignment }

        val response = service.register(
            fixture.user.userId!!,
            CertificationRegisterRequest(jpegImage(), "산책을 완료했습니다.", MissionType.PERSONAL, fixture.mission.missionId!!),
        )

        assertEquals(monday, response.missionDate)
        assertEquals(Visibility.PRIVATE, response.visibility)
        assertEquals("/api/images/${response.certificationId}", response.imageUrl)
        val saved = certificationRepository.findById(response.certificationId).orElseThrow()
        assertTrue(storage.saved.containsKey(saved.imageStorageKey))
    }

    @Test
    fun blankContentIsRejectedBeforeTouchingImageStorage() {
        val fixture = fixture()
        val storage = FakeImageStorage()
        val service = service(storage) { _, _, _, _ -> fixture.assignment }

        val exception = assertThrows<BusinessException> {
            service.register(
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
        val service = service(FakeImageStorage()) { _, _, _, _ -> fixture.assignment }
        val fakeImage = MockMultipartFile("image", "fake.jpg", "image/jpeg", "텍스트 파일입니다".toByteArray())

        val exception = assertThrows<BusinessException> {
            service.register(
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
        val assignment = persist(MissionAssignment.forUser(mission, user, monday))
        val service = service(FakeImageStorage()) { _, _, _, _ -> assignment }

        val exception = assertThrows<BusinessException> {
            service.register(
                user.userId!!,
                CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, mission.missionId!!, Visibility.GROUP),
            )
        }

        assertEquals(CertificationErrorCode.CERT_INVALID_VISIBILITY, exception.errorCode)
    }

    @Test
    fun assignmentFinderFailureAbortsBeforeStoringImage() {
        val fixture = fixture()
        val storage = FakeImageStorage()
        val service = service(storage) { _, _, _, _ -> throw BusinessException(CertificationErrorCode.MISSION_NOT_ASSIGNED) }

        val exception = assertThrows<BusinessException> {
            service.register(
                fixture.user.userId!!,
                CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, fixture.mission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.MISSION_NOT_ASSIGNED, exception.errorCode)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun unknownUserIsRejectedBeforeCallingAssignmentFinder() {
        val service = service(FakeImageStorage()) { _, _, _, _ -> throw IllegalStateException("호출되지 않아야 합니다.") }

        val exception = assertThrows<BusinessException> {
            service.register(999_999L, CertificationRegisterRequest(jpegImage(), "인증", MissionType.PERSONAL, 1L))
        }

        assertEquals(CommonErrorCode.NOT_FOUND, exception.errorCode)
    }

    @Test
    fun duplicateCertificationCleansUpStoredImageAndReturns409() {
        val fixture = fixture()
        persist(Certification(fixture.user, fixture.assignment, "images/existing.jpg", "이미 인증함"))
        entityManager.flush()
        val storage = FakeImageStorage()
        val service = service(storage) { _, _, _, _ -> fixture.assignment }

        val exception = assertThrows<BusinessException> {
            service.register(
                fixture.user.userId!!,
                CertificationRegisterRequest(jpegImage(), "두번째 인증", MissionType.PERSONAL, fixture.mission.missionId!!),
            )
        }

        assertEquals(CertificationErrorCode.CERT_ALREADY_EXISTS, exception.errorCode)
        assertTrue(storage.saved.isEmpty())
        assertTrue(storage.deleted.isNotEmpty())
    }

    private fun service(
        storage: ImageStorage,
        finder: (Long, Long, MissionType, LocalDate) -> MissionAssignment,
    ): CertificationService = CertificationService(
        userRepository,
        certificationRepository,
        FakeCurrentAssignmentFinder(finder),
        storage,
    )

    private fun jpegImage(): MockMultipartFile = MockMultipartFile("image", "photo.jpg", "image/jpeg", jpegBytes)

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun fixture(): Fixture {
        val type = persist(UserType("TYPE_01", "유형 1", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("kakao-one", "포근한양"))
        user.completeOnboarding(LifeStage.JOB_SEEKER, type, AnswerChoice.A, group)
        val mission = persist(Mission(MissionType.PERSONAL, "산책하기"))
        val assignment = persist(MissionAssignment.forUser(mission, user, monday))
        return Fixture(user, mission, assignment)
    }

    private data class Fixture(val user: User, val mission: Mission, val assignment: MissionAssignment)
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

private class FakeCurrentAssignmentFinder(
    private val resolver: (Long, Long, MissionType, LocalDate) -> MissionAssignment,
) : CurrentAssignmentFinder {
    override fun findCurrentAssignment(
        userId: Long,
        missionId: Long,
        missionType: MissionType,
        referenceDate: LocalDate,
    ): MissionAssignment = resolver(userId, missionId, missionType, referenceDate)
}
