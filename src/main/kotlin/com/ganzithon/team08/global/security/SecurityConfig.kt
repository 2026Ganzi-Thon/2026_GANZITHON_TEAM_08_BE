// JWT 쿠키 인증과 CSRF 보호를 설정한다.
package com.ganzithon.team08.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import java.util.function.Supplier

@Configuration
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: JwtAccessDeniedHandler,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieName("XSRF-TOKEN")
            setHeaderName("X-XSRF-TOKEN")
            setCookiePath("/")
        }

        http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(PlainCsrfTokenRequestHandler())
            }
            .sessionManagement { sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/actuator/health", "/api/auth/kakao", "/api/auth/kakao/callback").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

internal class PlainCsrfTokenRequestHandler : CsrfTokenRequestHandler {
    override fun handle(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
        csrfToken: Supplier<CsrfToken>,
    ) {
        val token = csrfToken.get()
        request.setAttribute(token.parameterName, token)
    }

    override fun resolveCsrfTokenValue(request: jakarta.servlet.http.HttpServletRequest, csrfToken: CsrfToken): String? =
        request.getHeader(csrfToken.headerName)
}
