// 팀 08 백엔드의 Spring Boot 애플리케이션을 시작한다.
package com.ganzithon.team08

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GanzithonTeam08Application

fun main(args: Array<String>) {
    runApplication<GanzithonTeam08Application>(*args)
}
