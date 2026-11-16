// 인증된 사용자의 현재 그룹을 조회하고 요청한 그룹에서 탈퇴한다.
package com.ganzithon.team08.group

import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.ErrorResponse
import org.springframework.http.ResponseEntity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.Authentication
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/groups/me")
class MyGroupController(private val groups: MyGroupService) {
    @GetMapping
    fun getMyGroup(authentication: Authentication): MyGroupResponse =
        groups.getMyGroup(authentication.name.toLong())

    @DeleteMapping
    fun leaveGroup(
        authentication: Authentication,
        @Valid @RequestBody(required = false) request: LeaveGroupRequest?,
    ): LeaveGroupResponse {
        val id = request?.expectedGroupId
        if (id == null || !id.isIntegralNumber || !id.canConvertToLong() || id.asLong() <= 0) {
            throw BusinessException(MyGroupErrorCode.INVALID_GROUP_ID)
        }
        return groups.leaveGroup(authentication.name.toLong(), id.asLong())
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidRequest(): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse.from(MyGroupErrorCode.INVALID_GROUP_ID))
}

data class LeaveGroupRequest(@field:NotNull val expectedGroupId: JsonNode? = null)
