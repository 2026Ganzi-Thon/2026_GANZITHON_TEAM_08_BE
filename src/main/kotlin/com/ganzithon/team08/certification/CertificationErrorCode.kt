// 인증 등록·조회 과정에서 발생하는 C파트 비즈니스 에러 코드를 정의한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class CertificationErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    CERT_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지는 JPEG, PNG, WebP 형식의 10MiB 이하 파일만 업로드할 수 있습니다."),
    CERT_INVALID_CONTENT(HttpStatus.BAD_REQUEST, "글은 1자 이상 300자 이하로 입력해 주세요."),
    CERT_INVALID_VISIBILITY(HttpStatus.BAD_REQUEST, "선택한 공개 범위를 사용할 수 없습니다."),
    MISSION_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "본인 또는 현재 그룹에 배정되지 않은 미션입니다."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 미션입니다."),
    CERT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 인증을 완료했습니다."),
    CERT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 인증입니다."),
    CERT_FORBIDDEN(HttpStatus.FORBIDDEN, "볼 수 없거나 변경할 수 없는 인증입니다."),
    ;

    override val code: String get() = name
}
