// 외부 정리 작업의 대기·처리·성공·실패 상태를 정의한다.
package com.ganzithon.team08.domain.cleanup

enum class CleanupTaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
