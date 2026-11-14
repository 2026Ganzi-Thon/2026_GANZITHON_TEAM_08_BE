// 배정 시 참조하는 미션 원본과 수행 안내를 저장한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.domain.common.BaseTimeEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
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
class Mission(
    missionType: MissionType,
    missionTitle: String,
    description: String? = null,
    steps: List<String> = emptyList(),
    certificationGuide: String? = null,
    preference: MissionPreference = MissionPreference.COMMON,
) : BaseTimeEntity() {
    init {
        require(preference == MissionPreference.COMMON || missionType == MissionType.PERSONAL)
        if (description != null || steps.isNotEmpty() || certificationGuide != null) {
            require(!description.isNullOrBlank() && description.length <= 2000)
            require(steps.isNotEmpty() && steps.all { it.isNotBlank() && it.length <= 500 })
            require(!certificationGuide.isNullOrBlank() && certificationGuide.length <= 1000)
        }
    }

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

    @field:Column(length = 2000)
    var description: String? = description
        protected set

    @field:ElementCollection
    @field:CollectionTable(name = "mission_steps", joinColumns = [JoinColumn(name = "mission_id")])
    @field:OrderColumn(name = "step_index", nullable = false)
    @field:Column(name = "instruction", nullable = false, length = 500)
    private var orderedSteps: MutableList<String> = steps.toMutableList()

    val steps: List<String> get() = orderedSteps.toList()

    @field:Column(name = "certification_guide", length = 1000)
    var certificationGuide: String? = certificationGuide
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "preference", length = 10)
    var preference: MissionPreference? = preference
        protected set
}
