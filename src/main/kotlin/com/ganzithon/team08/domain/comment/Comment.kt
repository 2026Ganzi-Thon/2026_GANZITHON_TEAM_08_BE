// 사용자가 다른 사람의 인증에 남긴 댓글을 저장한다.
package com.ganzithon.team08.domain.comment

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "comments",
    indexes = [Index(name = "idx_comment_certification", columnList = "certification_id,comment_id")],
)
class Comment(user: User, certification: Certification, content: String) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "comment_id")
    var commentId: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "certification_id", nullable = false, updatable = false)
    var certification: Certification = certification
        protected set

    @field:Column(nullable = false, length = 300)
    var content: String = content
        protected set

    fun isWrittenBy(viewer: User): Boolean =
        user === viewer || (user.userId != null && user.userId == viewer.userId)
}
