// CurrentUserIdArgumentResolver를 Spring MVC 인자 리졸버로 등록한다.
package com.ganzithon.team08.global.auth

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// CurrentUserIdArgumentResolver가 local 프로필에만 등록되므로 이 설정도 같은 프로필로 묶는다.
@Configuration
@Profile("local")
class CurrentUserIdWebConfig(
    private val currentUserIdArgumentResolver: CurrentUserIdArgumentResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserIdArgumentResolver)
    }
}
