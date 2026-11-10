// 미션·배정 조회 과정에서 발생하는 에러 코드를 정의한다.
package com.ganzithon.team08.domain.mission

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MissionErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 미션입니다."),
    MISSION_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "본인 또는 현재 그룹에 배정되지 않은 미션입니다."),
    ;

    override val code: String get() = name
}
