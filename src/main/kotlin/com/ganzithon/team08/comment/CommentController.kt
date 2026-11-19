// 인증 댓글 등록·조회·삭제 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.comment

import com.ganzithon.team08.comment.dto.CommentCreateRequest
import com.ganzithon.team08.comment.dto.CommentListResponse
import com.ganzithon.team08.comment.dto.CommentResponse
import com.ganzithon.team08.global.auth.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CommentController(
    private val commentService: CommentService,
) {
    @PostMapping("/api/certifications/{certificationId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
        @Valid @RequestBody request: CommentCreateRequest,
    ): CommentResponse = commentService.register(userId, certificationId, request.content)

    @GetMapping("/api/certifications/{certificationId}/comments")
    fun list(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
    ): CommentListResponse = commentService.list(userId, certificationId)

    @DeleteMapping("/api/certifications/{certificationId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
        @PathVariable commentId: Long,
    ) = commentService.delete(userId, certificationId, commentId)
}
