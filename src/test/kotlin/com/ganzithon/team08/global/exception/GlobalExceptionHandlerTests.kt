// 전역 예외 처리의 HTTP 응답과 민감 정보 보호를 검증한다.
package com.ganzithon.team08.global.exception

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertFalse

@WebMvcTest
@Import(GlobalExceptionHandlerTests.TestController::class)
@ExtendWith(OutputCaptureExtension::class)
class GlobalExceptionHandlerTests @Autowired constructor(private val mockMvc: MockMvc) {
    @Test
    fun validRequestKeepsItsNormalResponse() {
        mockMvc.post("/error-test/nickname") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"양"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("양") }
            jsonPath("$.code") { doesNotExist() }
        }
    }

    @Test
    fun businessExceptionUsesItsDomainCodeAndStatus() {
        mockMvc.get("/error-test/duplicate")
            .expectError(HttpStatus.CONFLICT, "NICKNAME_ALREADY_USED")
            .andExpect {
                jsonPath("$.message") { value("이미 사용 중인 닉네임입니다.") }
                jsonPath("$.errors") { isEmpty() }
            }
    }

    @Test
    fun invalidBodyReturnsSafeFieldErrors() {
        mockMvc.post("/error-test/nickname") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":""}"""
        }.expectError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
            .andExpect {
                jsonPath("$.errors[0].field") { value("nickname") }
                jsonPath("$.errors[0].message") { value("필수 입력 항목입니다.") }
            }
    }

    @Test
    fun unreadableJsonAndInvalidParametersReturnBadRequest(output: CapturedOutput) {
        val response = mockMvc.post("/error-test/nickname") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":$INTERNAL_VALUE}"""
        }.expectError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
            .andExpect { jsonPath("$.errors") { isEmpty() } }
            .andReturn().response
        assertFalse(response.contentAsString.contains(INTERNAL_VALUE))

        mockMvc.get("/error-test/count").expectError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        mockMvc.get("/error-test/count") { param("count", "invalid") }
            .expectError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        mockMvc.get("/error-test/count") { param("count", "0") }
            .expectError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
            .andExpect { jsonPath("$.errors[0].field") { value("count") } }

        assertFalse(output.all.contains(INTERNAL_VALUE))
    }

    @Test
    fun routingErrorsKeepTheirHttpStatusAndHeaders() {
        mockMvc.get("/nonexistent-endpoint")
            .expectError(HttpStatus.NOT_FOUND, "NOT_FOUND")

        mockMvc.post("/error-test/count")
            .expectError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED")
            .andExpect { header { string(HttpHeaders.ALLOW, "GET") } }

        mockMvc.post("/error-test/nickname") {
            contentType = MediaType.TEXT_PLAIN
            content = "nickname"
        }.expectError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE")
            .andExpect { header { string(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE) } }
    }

    @Test
    fun optimisticLockConflictReturnsConflict() {
        mockMvc.get("/error-test/stale")
            .expectError(HttpStatus.CONFLICT, "CONFLICT")
            .andExpect { jsonPath("$.errors") { isEmpty() } }
    }

    @Test
    fun unexpectedErrorsHideInternalDetails(output: CapturedOutput) {
        for (endpoint in listOf("unexpected", "integrity")) {
            val response = mockMvc.get("/error-test/$endpoint")
                .expectError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
                .andExpect {
                    jsonPath("$.message") { value("서버에서 오류가 발생했습니다.") }
                    jsonPath("$.errors") { isEmpty() }
                    jsonPath("$.trace") { doesNotExist() }
                }.andReturn().response
            assertFalse(response.contentAsString.contains(INTERNAL_VALUE))
        }
        assertFalse(output.all.contains(INTERNAL_VALUE))
        assertFalse(output.all.contains("java.lang.IllegalStateException"))
        assertFalse(output.all.contains("DataIntegrityViolationException"))
    }

    private fun ResultActionsDsl.expectError(expectedStatus: HttpStatus, code: String): ResultActionsDsl =
        andExpect {
            status { isEqualTo(expectedStatus.value()) }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.code") { value(code) }
        }

    @TestComponent
    @RestController
    @RequestMapping("/error-test")
    class TestController {
        @PostMapping("/nickname", consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun nickname(@Valid @RequestBody body: NicknameRequest): NicknameRequest = body

        @GetMapping("/count")
        fun count(@RequestParam @Min(1) count: Int): Int = count

        @GetMapping("/duplicate")
        fun duplicate(): Nothing = throw BusinessException(TestErrorCode.NICKNAME_ALREADY_USED)

        @GetMapping("/stale")
        fun stale(): Nothing = throw OptimisticLockingFailureException(INTERNAL_VALUE)

        @GetMapping("/unexpected")
        fun unexpected(): Nothing = error(INTERNAL_VALUE)

        @GetMapping("/integrity")
        fun integrity(): Nothing = throw DataIntegrityViolationException(INTERNAL_VALUE)
    }

    data class NicknameRequest(@field:NotBlank(message = INTERNAL_VALUE) val nickname: String)

    private enum class TestErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
        NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");

        override val code: String get() = name
    }

    private companion object {
        const val INTERNAL_VALUE = "internal-value-for-error-test"
    }
}
