// 카카오 인증 과정에서 사용하는 에러 코드를 정의한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    KAKAO_INVALID_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 없거나 유효하지 않습니다."),
    KAKAO_INVALID_STATE(HttpStatus.BAD_REQUEST, "유효하지 않은 로그인 요청입니다."),
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 API 호출에 실패했습니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정은 이용할 수 없습니다."),
    ;

    override val code: String get() = name
}
