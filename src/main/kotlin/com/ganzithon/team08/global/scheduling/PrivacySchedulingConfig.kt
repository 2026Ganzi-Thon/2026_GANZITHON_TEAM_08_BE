// 탈퇴와 기존 사진 작업이 미션 스케줄러를 지연시키지 않도록 실행기를 분리한다.
package com.ganzithon.team08.global.scheduling

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration(proxyBeanMethods = false)
class PrivacySchedulingConfig {
    @Bean(defaultCandidate = false)
    fun privacyTaskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("privacy-")
    }
}
