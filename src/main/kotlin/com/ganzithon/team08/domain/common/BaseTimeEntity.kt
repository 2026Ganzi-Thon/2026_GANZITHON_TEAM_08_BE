// 엔티티의 생성·수정 시각과 낙관적 잠금 버전을 관리한다.
package com.ganzithon.team08.domain.common

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Version
import java.time.Instant

@MappedSuperclass
abstract class BaseTimeEntity {
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @field:Version
    var version: Long? = null
        protected set

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
