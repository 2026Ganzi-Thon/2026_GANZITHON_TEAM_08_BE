// 응원 등록 과정에서 발생하는 에러 코드를 정의한다.
package com.ganzithon.team08.cheer

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class CheerErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    CHEER_SELF(HttpStatus.BAD_REQUEST, "본인의 인증에는 응원할 수 없습니다."),
    CHEER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 응원한 인증입니다."),
    ;

    override val code: String get() = name
}
