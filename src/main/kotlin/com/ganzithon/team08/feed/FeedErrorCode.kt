// 피드 조회 과정에서 발생하는 에러 코드를 정의한다.
package com.ganzithon.team08.feed

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class FeedErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "size는 1에서 50 사이여야 합니다."),
    ;

    override val code: String get() = name
}
