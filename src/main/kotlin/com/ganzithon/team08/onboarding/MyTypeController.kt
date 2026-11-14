package com.ganzithon.team08.onboarding

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/type")
class MyTypeController(private val myTypeService: MyTypeService) {
    @GetMapping
    fun getMyType(authentication: Authentication): MyTypeResponse =
        myTypeService.getMyType(authentication.name.toLong())
}
