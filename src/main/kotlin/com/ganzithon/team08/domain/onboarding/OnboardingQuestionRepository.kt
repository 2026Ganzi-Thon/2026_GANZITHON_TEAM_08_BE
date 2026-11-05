// 온보딩 질문을 저장하고 질문 번호 순서로 조회한다.
package com.ganzithon.team08.domain.onboarding

import org.springframework.data.jpa.repository.JpaRepository

interface OnboardingQuestionRepository : JpaRepository<OnboardingQuestion, Int> {
    fun findAllByOrderByQuestionIdAsc(): List<OnboardingQuestion>
}
