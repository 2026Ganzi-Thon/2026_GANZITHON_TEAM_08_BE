// 댓글 등록·삭제 과정에서 발생하는 에러 코드를 정의한다.
package com.ganzithon.team08.comment

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class CommentErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다."),
    ;

    override val code: String get() = name
}
