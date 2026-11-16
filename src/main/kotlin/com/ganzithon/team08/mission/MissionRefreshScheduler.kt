// 서울 시간의 일간·주간 경계와 누락 복구 시점에 미션 배정을 실행한다.
package com.ganzithon.team08.mission

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.missions.refresh", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class MissionRefreshScheduler(private val jobs: MissionRefreshJobs) {
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun refreshPersonal() = execute(MissionRefreshKind.PERSONAL, Instant.now(), "daily")

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    fun refreshGroup() = execute(MissionRefreshKind.GROUP, Instant.now(), "weekly")

    @EventListener(ApplicationReadyEvent::class)
    fun recoverOnStartup() = recover()

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    fun recover() {
        val reference = Instant.now()
        execute(MissionRefreshKind.PERSONAL, reference, "recovery")
        execute(MissionRefreshKind.GROUP, reference, "recovery")
    }

    private fun execute(kind: MissionRefreshKind, reference: Instant, trigger: String) {
        try {
            val result = jobs.run(kind, reference)
            logger.info(
                "미션 갱신 trigger={} kind={} period={} start={} end={} target={} created={} existing={} skipped={} failed={}",
                trigger, kind, kind.period(reference), reference, Instant.now(),
                result.created + result.existing + result.skipped + result.failed,
                result.created, result.existing, result.skipped, result.failed,
            )
        } catch (ex: RuntimeException) {
            logger.error("미션 갱신 중단 trigger={} kind={} period={} error={}", trigger, kind, kind.period(reference), ex.javaClass.simpleName)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MissionRefreshScheduler::class.java)
    }
}
