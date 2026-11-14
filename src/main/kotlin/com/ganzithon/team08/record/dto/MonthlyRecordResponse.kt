// 월별 기록 조회(날짜별 완료 여부, 현재/최장 스트릭) 응답을 표현한다.
package com.ganzithon.team08.record.dto

import java.time.LocalDate

data class MonthlyRecordResponse(
    val currentStreak: Int,
    val longestStreak: Int,
    val days: List<DayRecordResponse>,
)

data class DayRecordResponse(
    val date: LocalDate,
    val personalCompleted: Boolean,
    val groupCompleted: Boolean,
)
