// 사용자 데이터가 삭제된 뒤에도 재시도할 외부 정리 작업을 보관한다.
package com.ganzithon.team08.domain.cleanup

import com.ganzithon.team08.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

@Entity
@Table(name = "cleanup_tasks", indexes = [Index(name = "idx_cleanup_retry", columnList = "status,next_retry_at")])
class CleanupTask(taskType: CleanupTaskType, targetReference: String) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "task_id")
    var taskId: Long? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "task_type", nullable = false, length = 30, updatable = false)
    var taskType: CleanupTaskType = taskType
        protected set

    @field:Column(name = "target_reference", nullable = false, length = 1024, updatable = false)
    @field:NotBlank
    @field:Size(max = 1024)
    var targetReference: String = targetReference
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: CleanupTaskStatus = CleanupTaskStatus.PENDING
        protected set

    @field:Column(name = "retry_count", nullable = false)
    @field:PositiveOrZero
    var retryCount: Int = 0
        protected set

    @field:Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null
        protected set

    @field:Column(name = "last_error", length = 2000)
    var lastError: String? = null
        protected set

    fun start() {
        check(status == CleanupTaskStatus.PENDING || status == CleanupTaskStatus.FAILED) {
            "대기 중이거나 실패한 작업만 시작할 수 있습니다."
        }
        status = CleanupTaskStatus.PROCESSING
        nextRetryAt = null
    }

    fun fail(error: String, nextRetryAt: Instant) {
        check(status == CleanupTaskStatus.PROCESSING) { "처리 중인 작업만 실패 처리할 수 있습니다." }
        status = CleanupTaskStatus.FAILED
        retryCount += 1
        lastError = error.take(2000)
        this.nextRetryAt = nextRetryAt
    }

    fun complete() {
        check(status == CleanupTaskStatus.PROCESSING) { "처리 중인 작업만 완료할 수 있습니다." }
        status = CleanupTaskStatus.COMPLETED
        nextRetryAt = null
        lastError = null
    }
}
