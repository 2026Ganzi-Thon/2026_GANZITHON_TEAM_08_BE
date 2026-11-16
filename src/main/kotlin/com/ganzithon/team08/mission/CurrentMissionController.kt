// 로그인한 사용자의 오늘 개인 미션과 현재 그룹의 주간 미션을 조회한다.
package com.ganzithon.team08.mission

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CurrentMissionController(private val missions: CurrentMissionService) {
    @GetMapping("/api/missions/personal/today")
    fun personal(authentication: Authentication): PersonalMissionResponse =
        missions.personal(authentication.name.toLong())

    @GetMapping("/api/groups/me/missions/current")
    fun group(authentication: Authentication): GroupMissionResponse =
        missions.group(authentication.name.toLong())
}
