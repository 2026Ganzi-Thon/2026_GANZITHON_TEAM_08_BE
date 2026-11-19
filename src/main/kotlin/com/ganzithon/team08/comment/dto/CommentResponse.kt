// 댓글 한 건의 응답 형태를 표현한다.
package com.ganzithon.team08.comment.dto

import com.ganzithon.team08.domain.comment.Comment
import java.time.OffsetDateTime
import java.time.ZoneId

data class CommentResponse(
    val commentId: Long,
    val nickname: String,
    val content: String,
    val mine: Boolean,
    val createdAt: OffsetDateTime,
) {
    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun from(comment: Comment, mine: Boolean): CommentResponse = CommentResponse(
            commentId = requireNotNull(comment.commentId),
            nickname = comment.user.nickname,
            content = comment.content,
            mine = mine,
            createdAt = comment.createdAt.atZone(SEOUL).toOffsetDateTime(),
        )
    }
}
