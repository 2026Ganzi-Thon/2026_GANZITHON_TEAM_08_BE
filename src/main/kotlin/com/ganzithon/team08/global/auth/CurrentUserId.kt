// 컨트롤러 메서드 인자에 현재 로그인한 사용자 ID를 주입하도록 표시한다.
package com.ganzithon.team08.global.auth

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId
