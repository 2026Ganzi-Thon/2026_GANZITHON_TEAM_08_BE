// 배정과 인증에 공통으로 사용하는 서울 날짜와 주 시작일을 계산한다.
package com.ganzithon.team08.domain.mission

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal object MissionPeriod {
    private val seoul = ZoneId.of("Asia/Seoul")

    fun dateAt(instant: Instant): LocalDate = instant.atZone(seoul).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
