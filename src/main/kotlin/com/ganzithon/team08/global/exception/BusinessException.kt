// 비즈니스 규칙 위반을 공개 에러 코드로 전달한다.
package com.ganzithon.team08.global.exception

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
