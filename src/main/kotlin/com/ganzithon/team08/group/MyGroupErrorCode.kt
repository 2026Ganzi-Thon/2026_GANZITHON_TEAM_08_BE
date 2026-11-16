// 내 그룹 조회와 탈퇴 요청의 비즈니스 오류를 정의한다.
package com.ganzithon.team08.group

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MyGroupErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
    INVALID_GROUP_ID(HttpStatus.BAD_REQUEST, "expectedGroupId는 1 이상의 정수여야 합니다."),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 먼저 완료해 주세요."),
    GROUP_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 그룹에 소속되어 있습니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "현재 소속된 그룹이 없습니다."),
    GROUP_CHANGED(HttpStatus.CONFLICT, "그룹이 변경되었습니다. 현재 그룹을 다시 조회해 주세요.");

    override val code: String get() = name
}
