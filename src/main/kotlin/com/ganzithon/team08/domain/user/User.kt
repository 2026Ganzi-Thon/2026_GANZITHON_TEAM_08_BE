// 카카오 사용자와 현재 온보딩 결과 및 그룹 소속을 저장한다.
package com.ganzithon.team08.domain.user

import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.onboarding.AnswerChoice
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
import java.security.MessageDigest

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_kakao", columnNames = ["kakao_user_id"])],
    indexes = [Index(name = "idx_user_group", columnList = "group_id")],
)
class User(kakaoUserId: String, nickname: String) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "user_id")
    var userId: Long? = null
        protected set

    @field:Column(name = "kakao_user_id", nullable = false, length = 100, updatable = false)
    @field:NotBlank
    @field:Size(max = 100)
    var kakaoUserId: String = kakaoUserId
        protected set

    @field:Column(nullable = false, length = 50)
    @field:NotBlank
    @field:Size(max = 50)
    var nickname: String = nickname
        protected set

    @field:Column(name = "onboarding_completed", nullable = false)
    var onboardingCompleted: Boolean = false
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "life_stage", length = 30)
    var lifeStage: LifeStage? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "type_code")
    var userType: UserType? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "care_preference", length = 1)
    var carePreference: AnswerChoice? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "group_id")
    var group: CareGroup? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    @field:Column(name = "refresh_token_hash", length = 64)
    var refreshTokenHash: String? = null
        protected set

    fun completeOnboarding(lifeStage: LifeStage, userType: UserType, carePreference: AnswerChoice, group: CareGroup) {
        check(!onboardingCompleted) { "이미 온보딩을 완료한 사용자입니다." }
        applyType(lifeStage, userType, carePreference, group)
        onboardingCompleted = true
    }

    fun updateType(lifeStage: LifeStage, userType: UserType, carePreference: AnswerChoice, group: CareGroup) {
        check(onboardingCompleted) { "온보딩을 먼저 완료해야 합니다." }
        applyType(lifeStage, userType, carePreference, group)
    }

    fun withdraw() {
        status = UserStatus.WITHDRAWN
        group = null
        refreshTokenHash = null
    }

    fun updateRefreshTokenHash(rawToken: String) {
        refreshTokenHash = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun hasRefreshToken(rawToken: String): Boolean {
        val storedHash = refreshTokenHash ?: return false
        val rawHash = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return storedHash == rawHash
    }

    fun clearRefreshTokenHash() {
        refreshTokenHash = null
    }

    private fun applyType(lifeStage: LifeStage, userType: UserType, carePreference: AnswerChoice, group: CareGroup) {
        check(status == UserStatus.ACTIVE) { "탈퇴한 사용자는 유형을 변경할 수 없습니다." }
        require(group.lifeStage == lifeStage && group.userType.typeCode == userType.typeCode) {
            "그룹의 생활 단계와 유형이 사용자와 일치해야 합니다."
        }
        this.lifeStage = lifeStage
        this.userType = userType
        this.carePreference = carePreference
        this.group = group
    }
}
