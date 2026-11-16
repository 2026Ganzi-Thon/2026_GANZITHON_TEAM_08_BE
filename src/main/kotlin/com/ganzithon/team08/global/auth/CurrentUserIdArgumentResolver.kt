// JWT 인증 principal에서 현재 사용자 ID를 주입하고 local에서만 헤더 fallback을 제공한다.
package com.ganzithon.team08.global.auth

import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.core.MethodParameter
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserIdArgumentResolver(
    private val environment: Environment,
) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUserId::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        principal?.toString()?.toLongOrNull()?.let { return it }

        if (environment.acceptsProfiles(Profiles.of(LOCAL_PROFILE))) {
            webRequest.getHeader(HEADER_NAME)?.toLongOrNull()?.let { return it }
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }

        throw BusinessException(CommonErrorCode.UNAUTHORIZED)
    }

    companion object {
        private const val HEADER_NAME = "X-User-Id"
        private const val LOCAL_PROFILE = "local"
    }
}
