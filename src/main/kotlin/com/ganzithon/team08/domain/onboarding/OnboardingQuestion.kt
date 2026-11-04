// 온보딩과 재검사에서 사용하는 네 개의 질문을 저장한다.
package com.ganzithon.team08.domain.onboarding

import com.ganzithon.team08.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "onboarding_questions")
class OnboardingQuestion(questionId: Int, content: String, optionA: String, optionB: String) : BaseTimeEntity() {
    @field:Id
    @field:Column(name = "question_id", updatable = false)
    @field:Min(1)
    @field:Max(4)
    var questionId: Int = questionId
        protected set

    @field:Column(nullable = false, length = 1000)
    @field:NotBlank
    @field:Size(max = 1000)
    var content: String = content
        protected set

    @field:Column(name = "option_a", nullable = false, length = 500)
    @field:NotBlank
    @field:Size(max = 500)
    var optionA: String = optionA
        protected set

    @field:Column(name = "option_b", nullable = false, length = 500)
    @field:NotBlank
    @field:Size(max = 500)
    var optionB: String = optionB
        protected set
}
