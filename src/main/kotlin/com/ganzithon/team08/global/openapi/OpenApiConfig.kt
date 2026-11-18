// springdoc-openapi가 생성하는 API 문서의 제목·설명을 정의한다.
package com.ganzithon.team08.global.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("MINDOT API")
                .description("2026 간지톤 8팀 백엔드 API 문서")
                .version("v1"),
        )
}
