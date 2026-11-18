// springdoc-openapi가 전체 컨텍스트에서 정상적으로 API 문서를 생성하는지 확인한다.
package com.ganzithon.team08

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsSmokeTests @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @Test
    fun openApiDocumentIsGenerated() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("/api/feed")) }
            content { string(org.hamcrest.Matchers.containsString("/api/certifications/{certificationId}/cheers")) }
        }
    }
}
