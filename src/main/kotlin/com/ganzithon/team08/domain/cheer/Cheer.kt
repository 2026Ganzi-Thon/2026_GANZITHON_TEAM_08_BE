// 사용자가 다른 사람의 인증에 남긴 일회성 응원을 저장한다.
package com.ganzithon.team08.domain.cheer

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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "cheers",
    uniqueConstraints = [UniqueConstraint(name = "uk_cheer_user_certification", columnNames = ["user_id", "certification_id"])],
    indexes = [Index(name = "idx_cheer_certification", columnList = "certification_id")],
)
class Cheer(user: User, certification: Certification) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "cheer_id")
    var cheerId: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "certification_id", nullable = false, updatable = false)
    var certification: Certification = certification
        protected set

    init {
        require(user !== certification.user && (user.userId == null || user.userId != certification.user.userId)) {
            "본인의 인증에는 응원할 수 없습니다."
        }
    }
}
