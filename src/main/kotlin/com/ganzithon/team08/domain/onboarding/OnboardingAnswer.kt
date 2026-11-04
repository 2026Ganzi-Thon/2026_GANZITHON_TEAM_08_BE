// 사용자별 마지막 질문 응답을 중복 없이 저장한다.
package com.ganzithon.team08.domain.onboarding

import com.ganzithon.team08.domain.common.BaseTimeEntity
import com.ganzithon.team08.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "onboarding_answers",
    uniqueConstraints = [UniqueConstraint(name = "uk_answer_user_question", columnNames = ["user_id", "question_id"])],
)
class OnboardingAnswer(user: User, question: OnboardingQuestion, answer: AnswerChoice) : BaseTimeEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "answer_id")
    var answerId: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "question_id", nullable = false, updatable = false)
    var question: OnboardingQuestion = question
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 1)
    var answer: AnswerChoice = answer
        protected set

    fun changeAnswer(answer: AnswerChoice) {
        this.answer = answer
    }
}
