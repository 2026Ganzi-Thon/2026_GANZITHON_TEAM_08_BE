// 배포 전에 저장한 인증 사진의 개인정보 메타데이터를 배치로 제거한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.global.storage.ImageFormat
import com.ganzithon.team08.global.storage.ImageSanitizer
import com.ganzithon.team08.global.storage.ImageStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.privacy", name = ["legacy-enabled"], havingValue = "true")
class LegacyImagePrivacyScheduler(
    private val legacy: LegacyImagePrivacyService,
    private val storage: ImageStorage,
    private val sanitizer: ImageSanitizer,
) {
    @Scheduled(fixedDelayString = "\${app.privacy.image-backfill-delay-ms:60000}", initialDelay = 60000, scheduler = "privacyTaskScheduler")
    fun run() {
        try {
            migrateBatch()
        } catch (exception: RuntimeException) {
            logger.error("기존 인증 사진 정리 실행 중단 error={}", exception.javaClass.simpleName)
        }
    }

    private fun migrateBatch() {
        val pending = legacy.pending(Instant.now())
        if (pending.isEmpty()) return
        var failed = 0
        for (image in pending) {
            try {
                val format = requireNotNull(ImageFormat.fromExtension(image.key.substringAfterLast('.')))
                legacy.replace(image, sanitizer.sanitize(storage.load(image.key), format))
            } catch (exception: Exception) {
                failed++
                legacy.retry(image, Instant.now())
                logger.warn("기존 인증 사진 정리 재시도 error={}", exception.javaClass.simpleName)
            }
        }
        logger.info("기존 인증 사진 정리 batch={} failed={}", pending.size, failed)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(LegacyImagePrivacyScheduler::class.java)
    }
}
