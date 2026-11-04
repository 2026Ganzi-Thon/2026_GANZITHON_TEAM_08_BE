// 탈퇴 후 재시도가 필요한 외부 정리 작업의 종류를 정의한다.
package com.ganzithon.team08.domain.cleanup

enum class CleanupTaskType {
    DELETE_IMAGE,
    UNLINK_KAKAO,
}
