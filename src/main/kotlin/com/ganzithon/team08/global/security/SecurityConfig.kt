// JWT 쿠키 인증과 CSRF 보호를 설정한다.
package com.ganzithon.team08.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import com.ganzithon.team08.domain.user.UserRepository

@Configuration
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: JwtAccessDeniedHandler,
    private val userRepository: UserRepository,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieName("XSRF-TOKEN")
            setHeaderName("X-XSRF-TOKEN")
            setCookiePath("/")
        }

        http
            .csrf { csrf -> csrf.csrfTokenRepository(csrfRepository) }
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
            .addFilterBefore(JwtAuthenticationFilter(jwtProvider, userRepository), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
