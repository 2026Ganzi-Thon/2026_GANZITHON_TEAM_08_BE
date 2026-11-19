// 댓글 등록 요청 본문을 표현한다.
package com.ganzithon.team08.comment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CommentCreateRequest(
    @field:NotBlank
    @field:Size(max = 300)
    val content: String,
)
