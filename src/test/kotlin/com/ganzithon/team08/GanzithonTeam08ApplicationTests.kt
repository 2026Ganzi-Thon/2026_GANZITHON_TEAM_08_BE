// 애플리케이션 구동과 HTTP 상태 확인 API의 응답을 검증한다.
package com.ganzithon.team08

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class GanzithonTeam08ApplicationTests @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun healthEndpointReturnsUp() {
        mockMvc.get("/actuator/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components") { doesNotExist() }
            }
    }
}
