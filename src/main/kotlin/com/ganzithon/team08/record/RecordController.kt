// 내 미션 인증 월별/날짜별 기록 조회 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.record

import com.ganzithon.team08.global.auth.CurrentUserId
import com.ganzithon.team08.record.dto.MonthlyRecordResponse
import com.ganzithon.team08.record.dto.RecordItemResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RecordController(
    private val recordService: RecordService,
) {
    @GetMapping("/api/me/records")
    fun monthly(
        @CurrentUserId userId: Long,
        @RequestParam month: String,
    ): MonthlyRecordResponse = recordService.getMonthlyRecord(userId, month)

    @GetMapping("/api/me/records/{date}")
    fun daily(
        @CurrentUserId userId: Long,
        @PathVariable date: String,
    ): List<RecordItemResponse> = recordService.getDateRecords(userId, date)
}
