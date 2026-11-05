// 사용자의 질문별 마지막 응답을 저장하고 조회한다.
package com.ganzithon.team08.domain.onboarding

import org.springframework.data.jpa.repository.JpaRepository

interface OnboardingAnswerRepository : JpaRepository<OnboardingAnswer, Long> {
    fun findAllByUser_UserIdOrderByQuestion_QuestionIdAsc(userId: Long): List<OnboardingAnswer>

    fun findByUser_UserIdAndQuestion_QuestionId(userId: Long, questionId: Int): OnboardingAnswer?
}
