package com.ganzithon.team08.onboarding

import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/onboarding")
class OnboardingSubmitController(private val submitService: OnboardingSubmitService) {
    @PostMapping
    fun submit(
        authentication: Authentication,
        @Valid @RequestBody request: OnboardingSubmitRequest,
    ): OnboardingSubmitResponse = submitService.submit(authentication.name.toLong(), request)
}
