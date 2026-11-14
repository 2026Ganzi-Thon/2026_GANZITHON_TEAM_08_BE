// 카카오 OAuth 연동 설정을 관리한다.
package com.ganzithon.team08.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KakaoAuthProperties(
    @Value("\${kakao.client-id}") val clientId: String,
    @Value("\${kakao.client-secret:}") val clientSecret: String,
    @Value("\${kakao.redirect-uri}") val redirectUri: String,
    @Value("\${kakao.frontend-onboarding-path:/onboarding}") val onboardingPath: String,
    @Value("\${kakao.frontend-home-path:/home}") val homePath: String,
)
