// 내 미션 인증을 월별 캘린더(완료 여부+스트릭)와 날짜별 목록으로 조회한다.
package com.ganzithon.team08.record

import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.mission.MissionType
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.record.dto.DayRecordResponse
import com.ganzithon.team08.record.dto.MonthlyRecordResponse
import com.ganzithon.team08.record.dto.RecordItemResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

@Service
class RecordService(
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
) {
    @Transactional(readOnly = true)
    fun getMonthlyRecord(userId: Long, month: String): MonthlyRecordResponse {
        val yearMonth = parseMonth(month)
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        val personalDatesInMonth = certificationRepository
            .findMissionDates(userId, MissionType.PERSONAL, firstDay, lastDay)
            .toSet()
        val groupWeekStarts = certificationRepository
            .findMissionDates(userId, MissionType.GROUP, mondayOf(firstDay), mondayOf(lastDay))
            .toSet()

        val days = (0 until yearMonth.lengthOfMonth()).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            DayRecordResponse(
                date = date,
                personalCompleted = personalDatesInMonth.contains(date),
                groupCompleted = groupWeekStarts.contains(mondayOf(date)),
            )
        }

        val allPersonalDates = certificationRepository.findMissionDates(userId, MissionType.PERSONAL, null, null)
        val streak = StreakCalculator.calculate(allPersonalDates, LocalDate.now(SEOUL))

        return MonthlyRecordResponse(streak.current, streak.longest, days)
    }

    @Transactional(readOnly = true)
    fun getDateRecords(userId: Long, date: String): List<RecordItemResponse> {
        val missionDate = parseDate(date)
        val certifications = certificationRepository.findByUserAndMissionDate(userId, missionDate)
        if (certifications.isEmpty()) return emptyList()

        val certificationIds = certifications.map { requireNotNull(it.certificationId) }
        val cheerCounts = cheerRepository.countByCertificationIds(certificationIds)
            .associate { it.getCertificationId() to it.getCheerCount() }

        return certifications.map { certification ->
            RecordItemResponse.from(certification, cheerCounts[certification.certificationId] ?: 0L)
        }
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun parseMonth(month: String): YearMonth =
        try {
            YearMonth.parse(month)
        } catch (ex: DateTimeParseException) {
            throw BusinessException(RecordErrorCode.INVALID_MONTH)
        }

    private fun parseDate(date: String): LocalDate =
        try {
            LocalDate.parse(date)
        } catch (ex: DateTimeParseException) {
            throw BusinessException(RecordErrorCode.INVALID_DATE)
        }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
