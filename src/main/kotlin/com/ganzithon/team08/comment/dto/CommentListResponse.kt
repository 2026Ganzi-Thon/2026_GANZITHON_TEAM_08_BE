// 인증 한 건에 달린 댓글 목록과 총 개수를 표현한다.
package com.ganzithon.team08.comment.dto

data class CommentListResponse(
    val comments: List<CommentResponse>,
    val commentCount: Long,
)
