// A파트 JWT 쿠키 인증이 붙기 전까지, X-User-Id 헤더로 현재 사용자 ID를 임시로 주입한다.
package com.ganzithon.team08.global.auth

import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.context.annotation.Profile
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/*
 * TODO: A파트 JWT 인증이 붙으면 이 리졸버를 걷어내고 인증 주체에서 사용자 ID를 읽도록 교체한다.
 * local 전용 임시 수단이며, 운영에서 신뢰할 수 없는 값이다. 다른 프로필에는 빈이 등록되지 않는다.
 */
@Component
@Profile("local")
class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUserId::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val header = webRequest.getHeader(HEADER_NAME)
        return header?.toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST)
    }

    companion object {
        private const val HEADER_NAME = "X-User-Id"
    }
}
