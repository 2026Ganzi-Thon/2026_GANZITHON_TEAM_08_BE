// 배정 시 참조하는 미션 원본의 종류와 제목을 저장한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "missions")
class Mission(missionType: MissionType, missionTitle: String) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "mission_id")
    var missionId: Long? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "mission_type", nullable = false, length = 20, updatable = false)
    var missionType: MissionType = missionType
        protected set

    @field:Column(name = "mission_title", nullable = false, length = 200, updatable = false)
    @field:NotBlank
    @field:Size(max = 200)
    var missionTitle: String = missionTitle
        protected set
}
