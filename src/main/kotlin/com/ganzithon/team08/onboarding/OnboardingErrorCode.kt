// 온보딩 질문 조회에서 사용하는 에러 코드를 정의한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class OnboardingErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    ONBOARDING_QUESTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "온보딩 질문을 조회할 수 없습니다."),
    INVALID_ANSWER(HttpStatus.BAD_REQUEST, "온보딩 응답을 확인해 주세요."),
    GROUP_MATCHING_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "그룹 매칭 기능이 아직 준비되지 않았습니다.");

    override val code: String get() = name
}
