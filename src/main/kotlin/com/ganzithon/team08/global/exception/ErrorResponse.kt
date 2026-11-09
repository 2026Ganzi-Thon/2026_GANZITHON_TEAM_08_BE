// API의 공통 에러 응답과 필드별 검증 오류를 표현한다.
package com.ganzithon.team08.global.exception

data class ErrorResponse(
    val code: String,
    val message: String,
    val errors: List<FieldViolation> = emptyList(),
) {
    data class FieldViolation(val field: String, val message: String)

    companion object {
        fun from(code: ErrorCode, errors: List<FieldViolation> = emptyList()): ErrorResponse =
            ErrorResponse(code.code, code.message, errors)
    }
}
