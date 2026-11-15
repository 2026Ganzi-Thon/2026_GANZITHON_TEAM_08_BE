// 온보딩 질문 조회 API를 제공한다.
package com.ganzithon.team08.onboarding

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

@RestController
@RequestMapping("/api/onboarding")
@ConditionalOnBean(OnboardingQuestionService::class)
class OnboardingQuestionController(private val questionService: OnboardingQuestionService) {
    @GetMapping("/questions")
    fun getQuestions(): OnboardingQuestionResponse = questionService.getQuestions()
}
