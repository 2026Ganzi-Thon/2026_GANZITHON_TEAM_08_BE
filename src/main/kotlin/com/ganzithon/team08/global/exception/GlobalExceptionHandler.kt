// Spring MVC와 서비스에서 발생한 예외를 공통 JSON 에러 응답으로 변환한다.
package com.ganzithon.team08.global.exception

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException, request: WebRequest): ResponseEntity<Any>? =
        respond(ex.errorCode, request)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(request: WebRequest): ResponseEntity<Any>? =
        respond(CommonErrorCode.CONFLICT, request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(request: WebRequest): ResponseEntity<Any>? =
        respond(CommonErrorCode.INTERNAL_SERVER_ERROR, request)

    // Spring 기본 처리의 예외 메시지 로깅을 피하면서 Allow 헤더를 전달한다.
    override fun handleHttpRequestMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? = handleExceptionInternal(ex, null, headers, status, request)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val code = if (statusCode.is5xxServerError) {
            CommonErrorCode.INTERNAL_SERVER_ERROR
        } else {
            CommonErrorCode.entries.firstOrNull { it.status.isSameCodeAs(statusCode) }
        }
        val response = if (code != null) {
            ErrorResponse.from(code, fieldViolations(ex))
        } else {
            ErrorResponse("HTTP_${statusCode.value()}", "요청을 처리할 수 없습니다.")
        }
        return respond(response, statusCode, headers, request)
    }

    private fun fieldViolations(ex: Exception): List<ErrorResponse.FieldViolation> = when (ex) {
        is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.map { it.toViolation() }
        is HandlerMethodValidationException -> if (ex.isForReturnValue) {
            emptyList()
        } else {
            ex.parameterValidationResults.flatMap { result ->
                if (result is ParameterErrors) {
                    result.fieldErrors.map { it.toViolation() }
                } else {
                    val field = result.methodParameter.parameterName
                    result.resolvableErrors.mapNotNull { error ->
                        field?.let { ErrorResponse.FieldViolation(it, validationMessage(error.codes?.lastOrNull())) }
                    }
                }
            }
        }
        else -> emptyList()
    }.distinct()

    // 검증 메시지와 컬렉션 경로에 들어갈 수 있는 입력값을 응답에 포함하지 않는다.
    private fun FieldError.toViolation(): ErrorResponse.FieldViolation =
        ErrorResponse.FieldViolation(field.substringBefore('['), validationMessage(code))

    private fun validationMessage(code: String?): String = when (code) {
        "NotBlank", "NotEmpty", "NotNull" -> "필수 입력 항목입니다."
        "Size" -> "허용된 길이 또는 개수를 확인해 주세요."
        else -> "입력 값이 올바르지 않습니다."
    }

    private fun respond(code: ErrorCode, request: WebRequest): ResponseEntity<Any>? =
        respond(ErrorResponse.from(code), code.status, HttpHeaders.EMPTY, request)

    private fun respond(
        body: ErrorResponse,
        status: HttpStatusCode,
        headers: HttpHeaders,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (request is ServletWebRequest && request.response?.isCommitted == true) return null

        if (status.is5xxServerError) {
            logger.error("API 요청 처리 중 서버 오류가 발생했습니다. HTTP ${status.value()}")
        }
        val responseHeaders = HttpHeaders().apply {
            putAll(headers)
            contentType = MediaType.APPLICATION_JSON
        }
        return ResponseEntity(body, responseHeaders, status)
    }
}
