// 인증 응원 등록 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.cheer

import com.ganzithon.team08.cheer.dto.CheerResponse
import com.ganzithon.team08.global.auth.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CheerController(
    private val cheerService: CheerService,
) {
    @PostMapping("/api/certifications/{certificationId}/cheers")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @CurrentUserId userId: Long,
        @PathVariable certificationId: Long,
    ): CheerResponse = cheerService.register(userId, certificationId)
}
