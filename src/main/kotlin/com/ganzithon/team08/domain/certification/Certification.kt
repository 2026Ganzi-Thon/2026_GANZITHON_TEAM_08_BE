// 사용자별 미션 인증과 사진 저장 위치 및 공개 범위를 저장한다.
package com.ganzithon.team08.domain.certification

import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.mission.MissionAssignment
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Entity
@Table(
    name = "certifications",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_certification_user_period", columnNames = ["user_id", "mission_type", "mission_date"]),
    ],
    indexes = [
        Index(name = "idx_certification_feed", columnList = "visibility,certification_id"),
        Index(name = "idx_certification_user_created", columnList = "user_id,created_at"),
    ],
)
class Certification(
    user: User,
    assignment: MissionAssignment,
    imageStorageKey: String,
    content: String,
    visibility: Visibility = Visibility.PRIVATE,
) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "certification_id")
    var certificationId: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    var assignment: MissionAssignment = assignment
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "mission_type", nullable = false, length = 20, updatable = false)
    var missionType: MissionType = assignment.mission.missionType
        protected set

    @field:Column(name = "mission_date", nullable = false, updatable = false)
    var missionDate: LocalDate = assignment.missionDate
        protected set

    @field:Column(name = "image_storage_key", nullable = false, length = 1024, updatable = false)
    @field:NotBlank
    @field:Size(max = 1024)
    var imageStorageKey: String = imageStorageKey
        protected set

    @field:Column(nullable = false, length = 300, updatable = false)
    @field:NotBlank
    @field:Size(min = 1, max = 300)
    var content: String = content
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var visibility: Visibility = visibility
        protected set

    init {
        require(assignment.isAssignedTo(user)) { "본인에게 배정된 미션만 인증할 수 있습니다." }
        validateVisibility(visibility)
    }

    fun changeVisibility(visibility: Visibility) {
        validateVisibility(visibility)
        this.visibility = visibility
    }

    private fun validateVisibility(visibility: Visibility) {
        require(visibility != Visibility.GROUP || user.group != null) { "그룹 공개를 선택하려면 소속 그룹이 있어야 합니다." }
    }
}
