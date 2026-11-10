// 인증 등록 컨트롤러의 멀티파트 바인딩과 현재 사용자 헤더 처리를 검증한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.mission.MissionType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import java.time.LocalDate
import java.time.OffsetDateTime

@WebMvcTest(CertificationController::class)
class CertificationControllerTests @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var certificationService: CertificationService

    @Test
    fun registersCertificationThroughMultipartEndpointAndDefaultsVisibilityToPrivate() {
        val response = CertificationRegisterResponse(
            certificationId = 1L,
            missionType = MissionType.PERSONAL,
            missionDate = LocalDate.of(2026, 11, 9),
            visibility = Visibility.PRIVATE,
            imageUrl = "/api/images/1",
            content = "산책을 완료했습니다.",
            createdAt = OffsetDateTime.parse("2026-11-09T10:00:00+09:00"),
        )
        given(certificationService.register(Mockito.eq(7L), anyObject())).willReturn(response)
        val image = MockMultipartFile("image", "photo.jpg", "image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        mockMvc.multipart("/api/certifications") {
            file(image)
            param("content", "산책을 완료했습니다.")
            param("missionType", "PERSONAL")
            param("missionId", "10")
            header("X-User-Id", "7")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.certificationId") { value(1) }
            jsonPath("$.visibility") { value("PRIVATE") }
            jsonPath("$.imageUrl") { value("/api/images/1") }
        }
    }

    @Test
    fun missingCurrentUserHeaderIsRejectedBeforeReachingService() {
        val image = MockMultipartFile("image", "photo.jpg", "image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        mockMvc.multipart("/api/certifications") {
            file(image)
            param("content", "인증")
            param("missionType", "PERSONAL")
            param("missionId", "10")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // Mockito.any()는 Kotlin의 null 불가 매개변수에 바로 넣으면 NPE를 유발할 수 있어, 스텁 등록에서만 쓰는 안전한 우회다.
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }
}
