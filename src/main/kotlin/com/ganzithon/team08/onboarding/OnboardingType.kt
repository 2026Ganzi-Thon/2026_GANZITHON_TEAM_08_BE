// Q1·Q2·Q4 응답으로 분류되는 온보딩 유형을 정의한다.
package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice

enum class OnboardingType(val code: String, val displayName: String) {
    TYPE_01("TYPE_01", "부담정돈형"),
    TYPE_02("TYPE_02", "페이스회복형"),
    TYPE_03("TYPE_03", "부담나눔형"),
    TYPE_04("TYPE_04", "동행충전형"),
    TYPE_05("TYPE_05", "관계성찰형"),
    TYPE_06("TYPE_06", "감정환기형"),
    TYPE_07("TYPE_07", "마음소통형"),
    TYPE_08("TYPE_08", "나란히연결형"),
    ;

    companion object {
        fun from(q1: AnswerChoice, q2: AnswerChoice, q4: AnswerChoice): OnboardingType = when {
            q1 == AnswerChoice.A && q2 == AnswerChoice.A && q4 == AnswerChoice.A -> TYPE_01
            q1 == AnswerChoice.A && q2 == AnswerChoice.B && q4 == AnswerChoice.A -> TYPE_02
            q1 == AnswerChoice.B && q2 == AnswerChoice.A && q4 == AnswerChoice.A -> TYPE_03
            q1 == AnswerChoice.B && q2 == AnswerChoice.B && q4 == AnswerChoice.A -> TYPE_04
            q1 == AnswerChoice.A && q2 == AnswerChoice.A && q4 == AnswerChoice.B -> TYPE_05
            q1 == AnswerChoice.A && q2 == AnswerChoice.B && q4 == AnswerChoice.B -> TYPE_06
            q1 == AnswerChoice.B && q2 == AnswerChoice.A && q4 == AnswerChoice.B -> TYPE_07
            q1 == AnswerChoice.B && q2 == AnswerChoice.B && q4 == AnswerChoice.B -> TYPE_08
            else -> error("지원하지 않는 응답 조합입니다.")
        }
    }
}
