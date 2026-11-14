// 연속일수 계산의 현재/최장 스트릭 산출을 검증한다.
package com.ganzithon.team08.record

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakCalculatorTests {
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun emptyDatesProduceZeroStreaks() {
        val result = StreakCalculator.calculate(emptyList(), today)

        assertEquals(0, result.current)
        assertEquals(0, result.longest)
    }

    @Test
    fun consecutiveRunEndingTodayCountsAsCurrent() {
        val dates = listOf(today.minusDays(2), today.minusDays(1), today)

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(3, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun consecutiveRunEndingYesterdayStillCountsAsCurrent() {
        val dates = listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(3, result.current)
    }

    @Test
    fun runEndingTwoOrMoreDaysAgoResetsCurrentButKeepsLongest() {
        val dates = listOf(today.minusDays(5), today.minusDays(4), today.minusDays(3))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(0, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun longestReflectsBestHistoricalRunEvenIfNotCurrent() {
        val dates = listOf(
            today.minusDays(20),
            today.minusDays(19),
            today.minusDays(18),
            today.minusDays(17),
            today,
        )

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(1, result.current)
        assertEquals(4, result.longest)
    }

    @Test
    fun duplicateAndUnsortedDatesAreHandledCorrectly() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(1), today.minusDays(2))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(3, result.current)
        assertEquals(3, result.longest)
    }
}
