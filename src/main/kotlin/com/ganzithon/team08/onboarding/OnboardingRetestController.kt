// 인증된 사용자의 재검사 응답 조회와 제출을 처리한다.
package com.ganzithon.team08.onboarding

import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/type")
class OnboardingRetestController(private val retest: OnboardingRetestService) {
    @GetMapping("/retest")
    fun getCurrentAnswers(authentication: Authentication): OnboardingRetestResponse =
        retest.getCurrentAnswers(authentication.name.toLong())

    @PutMapping
    fun submit(
        authentication: Authentication,
        @Valid @RequestBody request: OnboardingSubmitRequest,
    ): OnboardingRetestResult = retest.submit(authentication.name.toLong(), request)
}
