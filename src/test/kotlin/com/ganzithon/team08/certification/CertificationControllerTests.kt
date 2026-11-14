// 인증 등록·조회·공개범위 변경·이미지 조회 컨트롤러의 바인딩과 현재 사용자 헤더 처리를 검증한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.certification.dto.CertificationDetailResponse
import com.ganzithon.team08.certification.dto.CertificationRegisterResponse
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.global.storage.ImageFormat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
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

    @Test
    fun returnsCertificationDetailAsJson() {
        val detail = CertificationDetailResponse(
            nickname = "포근한양",
            missionTitle = "산책하기",
            imageUrl = "/api/images/1",
            content = "산책을 완료했습니다.",
            visibility = Visibility.PUBLIC,
            cheerCount = 3,
            cheered = true,
            mine = false,
            createdAt = OffsetDateTime.parse("2026-11-09T10:00:00+09:00"),
        )
        given(certificationService.getDetail(7L, 1L)).willReturn(detail)

        mockMvc.get("/api/certifications/1") {
            header("X-User-Id", "7")
        }.andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("포근한양") }
            jsonPath("$.cheerCount") { value(3) }
            jsonPath("$.cheered") { value(true) }
        }
    }

    @Test
    fun returnsImageBytesWithDetectedContentType() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        given(certificationService.getImage(7L, 1L)).willReturn(bytes to ImageFormat.PNG)

        mockMvc.get("/api/images/1") {
            header("X-User-Id", "7")
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.IMAGE_PNG) }
            content { bytes(bytes) }
        }
    }

    @Test
    fun changesVisibilityThroughJsonBody() {
        val detail = CertificationDetailResponse(
            nickname = "포근한양",
            missionTitle = "산책하기",
            imageUrl = "/api/images/1",
            content = "산책을 완료했습니다.",
            visibility = Visibility.PUBLIC,
            cheerCount = 0,
            cheered = false,
            mine = true,
            createdAt = OffsetDateTime.parse("2026-11-09T10:00:00+09:00"),
        )
        given(certificationService.changeVisibility(7L, 1L, Visibility.PUBLIC)).willReturn(detail)

        mockMvc.patch("/api/certifications/1/visibility") {
            header("X-User-Id", "7")
            contentType = MediaType.APPLICATION_JSON
            content = """{"visibility":"PUBLIC"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.visibility") { value("PUBLIC") }
        }
    }

    // Mockito.any()는 Kotlin의 null 불가 매개변수에 바로 넣으면 NPE를 유발할 수 있어, 스텁 등록에서만 쓰는 안전한 우회다.
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }
}
