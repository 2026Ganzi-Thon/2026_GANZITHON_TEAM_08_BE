package com.ganzithon.team08.onboarding

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/type")
@ConditionalOnBean(MyTypeService::class)
class MyTypeController(private val myTypeService: MyTypeService) {
    @GetMapping
    fun getMyType(authentication: Authentication): MyTypeResponse =
        myTypeService.getMyType(authentication.name.toLong())
}
