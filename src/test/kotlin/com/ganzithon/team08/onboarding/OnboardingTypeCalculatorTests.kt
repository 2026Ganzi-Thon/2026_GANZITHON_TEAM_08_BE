package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class OnboardingTypeCalculatorTests {
    private val calculator = OnboardingTypeCalculator()

    @Test
    fun `Q1 Q2 Q4가 같으면 Q3가 달라도 같은 유형으로 계산한다`() {
        val resultA = calculator.calculate(answers(AnswerChoice.A, AnswerChoice.A, AnswerChoice.A, AnswerChoice.A))
        val resultB = calculator.calculate(answers(AnswerChoice.A, AnswerChoice.A, AnswerChoice.B, AnswerChoice.A))

        assertEquals(OnboardingType.TYPE_01, resultA.type)
        assertEquals(OnboardingType.TYPE_01, resultB.type)
        assertEquals(AnswerChoice.A, resultA.carePreference)
        assertEquals(AnswerChoice.B, resultB.carePreference)
    }

    @Test
    fun `Q1 Q2 Q4 조합별 유형을 계산한다`() {
        assertEquals(OnboardingType.TYPE_02, calculator.calculate(answers(AnswerChoice.A, AnswerChoice.B, AnswerChoice.A, AnswerChoice.A)).type)
        assertEquals(OnboardingType.TYPE_03, calculator.calculate(answers(AnswerChoice.B, AnswerChoice.A, AnswerChoice.A, AnswerChoice.A)).type)
        assertEquals(OnboardingType.TYPE_04, calculator.calculate(answers(AnswerChoice.B, AnswerChoice.B, AnswerChoice.A, AnswerChoice.A)).type)
        assertEquals(OnboardingType.TYPE_05, calculator.calculate(answers(AnswerChoice.A, AnswerChoice.A, AnswerChoice.A, AnswerChoice.B)).type)
        assertEquals(OnboardingType.TYPE_06, calculator.calculate(answers(AnswerChoice.A, AnswerChoice.B, AnswerChoice.A, AnswerChoice.B)).type)
        assertEquals(OnboardingType.TYPE_07, calculator.calculate(answers(AnswerChoice.B, AnswerChoice.A, AnswerChoice.A, AnswerChoice.B)).type)
        assertEquals(OnboardingType.TYPE_08, calculator.calculate(answers(AnswerChoice.B, AnswerChoice.B, AnswerChoice.A, AnswerChoice.B)).type)
    }

    @Test
    fun `질문이 누락되면 요청 오류를 던진다`() {
        assertThrows<BusinessException> {
            calculator.calculate(
                listOf(
                    OnboardingAnswer(1, AnswerChoice.A),
                    OnboardingAnswer(2, AnswerChoice.A),
                    OnboardingAnswer(3, AnswerChoice.A),
                ),
            )
        }
    }

    private fun answers(q1: AnswerChoice, q2: AnswerChoice, q3: AnswerChoice, q4: AnswerChoice) = listOf(
        OnboardingAnswer(1, q1),
        OnboardingAnswer(2, q2),
        OnboardingAnswer(3, q3),
        OnboardingAnswer(4, q4),
    )
}
