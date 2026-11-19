// 인증에 댓글을 등록·조회·삭제한다: 열람권한·탈퇴작성자 확인, 삭제는 작성자만 허용.
package com.ganzithon.team08.comment

import com.ganzithon.team08.certification.CertificationErrorCode
import com.ganzithon.team08.certification.CertificationInteractionAccess
import com.ganzithon.team08.comment.dto.CommentListResponse
import com.ganzithon.team08.comment.dto.CommentResponse
import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.comment.Comment
import com.ganzithon.team08.domain.comment.CommentRepository
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val userRepository: UserRepository,
    private val certificationRepository: CertificationRepository,
    private val commentRepository: CommentRepository,
    private val interactionAccess: CertificationInteractionAccess,
) {
    @Transactional
    fun register(viewerId: Long, certificationId: Long, content: String): CommentResponse {
        val (viewer, certification) = interactionAccess.lockForWrite(viewerId, certificationId)
        val comment = commentRepository.save(Comment(viewer, certification, content))
        return CommentResponse.from(comment, mine = true)
    }

    @Transactional(readOnly = true)
    fun list(viewerId: Long, certificationId: Long): CommentListResponse {
        val viewer = getUser(viewerId)
        val certification = getCertificationOrThrow(certificationId)
        requireActiveAuthor(certification)
        if (!certification.isVisibleTo(viewer)) throw BusinessException(CertificationErrorCode.CERT_FORBIDDEN)

        val comments = commentRepository.findByCertification_CertificationIdOrderByCommentIdAsc(certificationId)
        val items = comments.map { CommentResponse.from(it, mine = it.isWrittenBy(viewer)) }
        return CommentListResponse(items, items.size.toLong())
    }

    @Transactional
    fun delete(viewerId: Long, certificationId: Long, commentId: Long) {
        val viewer = getUser(viewerId)
        val comment = commentRepository.findById(commentId).orElseThrow { BusinessException(CommentErrorCode.COMMENT_NOT_FOUND) }
        if (comment.certification.certificationId != certificationId) throw BusinessException(CommentErrorCode.COMMENT_NOT_FOUND)
        if (!comment.isWrittenBy(viewer)) throw BusinessException(CommentErrorCode.COMMENT_FORBIDDEN)

        commentRepository.delete(comment)
    }

    private fun getUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }

    private fun getCertificationOrThrow(certificationId: Long): Certification =
        certificationRepository.findById(certificationId).orElseThrow { BusinessException(CertificationErrorCode.CERT_NOT_FOUND) }

    // 삭제 작업이 대기 중이어도 탈퇴 회원의 인증은 숨긴다.
    private fun requireActiveAuthor(certification: Certification) {
        if (certification.user.status != UserStatus.ACTIVE) throw BusinessException(CertificationErrorCode.CERT_NOT_FOUND)
    }
}
