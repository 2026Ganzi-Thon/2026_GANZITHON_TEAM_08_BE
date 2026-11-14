// 기록 조회 과정에서 발생하는 에러 코드를 정의한다.
package com.ganzithon.team08.record

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class RecordErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_MONTH(HttpStatus.BAD_REQUEST, "month는 yyyy-MM 형식이어야 합니다."),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "date는 yyyy-MM-dd 형식이어야 합니다."),
    ;

    override val code: String get() = name
}
