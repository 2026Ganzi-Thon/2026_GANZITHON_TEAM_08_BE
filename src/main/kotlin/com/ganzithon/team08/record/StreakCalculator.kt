// 개인 미션 인증 날짜로 현재/최장 연속일수를 계산한다.
// P08 "주1회 쉬어도 이어지기" 규칙은 팀 미확정이라 기본 연속일수 모델만 구현하고,
// 규칙이 확정되면 이 계산만 바꿔 끼울 수 있도록 분리했다.
package com.ganzithon.team08.record

import java.time.LocalDate

object StreakCalculator {
    fun calculate(missionDates: Collection<LocalDate>, today: LocalDate): StreakResult {
        val sorted = missionDates.toSortedSet().toList()
        if (sorted.isEmpty()) return StreakResult(current = 0, longest = 0)

        var longest = 1
        var runLength = 1
        for (i in 1 until sorted.size) {
            runLength = if (sorted[i] == sorted[i - 1].plusDays(1)) runLength + 1 else 1
            longest = maxOf(longest, runLength)
        }

        val lastDate = sorted.last()
        val current = if (lastDate.isBefore(today.minusDays(1))) 0 else runLength
        return StreakResult(current, longest)
    }
}

data class StreakResult(val current: Int, val longest: Int)
