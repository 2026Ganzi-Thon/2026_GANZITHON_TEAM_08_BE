// API에 공개할 에러 코드와 HTTP 상태, 안내 메시지를 정의한다.
package com.ganzithon.team08.global.exception

import org.springframework.http.HttpStatus

interface ErrorCode {
    val code: String
    val status: HttpStatus
    val message: String
}
