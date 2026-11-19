// 탈퇴한 회원의 파일 삭제와 카카오 연결 해제를 백그라운드에서 실행한다.
package com.ganzithon.team08.auth

import com.ganzithon.team08.domain.cleanup.CleanupTaskType
import com.ganzithon.team08.global.storage.ImageStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.privacy.cleanup", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WithdrawalCleanupScheduler(
    private val cleanup: WithdrawalCleanupService,
    private val withdrawal: WithdrawService,
    private val storage: ImageStorage,
    private val kakao: KakaoApiClient,
    private val kakaoProperties: KakaoAuthProperties,
    @Value("\${app.privacy.legacy-enabled:false}") private val legacyEnabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${app.privacy.cleanup.delay-ms:30000}", scheduler = "privacyTaskScheduler")
    fun run() {
        try {
            if (legacyEnabled) withdrawal.enqueueLegacyWithdrawals()
            val due = cleanup.dueIds(Instant.now())
            if (due.isNotEmpty()) {
                val completed = due.count(::process)
                logger.info("탈퇴 정리 실행 batch={} completed={}", due.size, completed)
            }
        } catch (exception: RuntimeException) {
            logger.error("탈퇴 정리 실행 중단 error={}", exception.javaClass.simpleName)
        }
    }

    private fun process(id: Long): Boolean {
        val claim = cleanup.claim(id, Instant.now()) ?: return false
        return try {
            when (claim.type) {
                CleanupTaskType.DELETE_IMAGE -> storage.delete(claim.target)
                CleanupTaskType.UNLINK_KAKAO -> unlink(claim.target)
            }
            cleanup.finish(claim)
            true
        } catch (exception: Exception) {
            val reason = (exception as? KakaoUnlinkFailure)?.reason ?: "CLEANUP_${exception.javaClass.simpleName}"
            cleanup.retry(claim, reason, Instant.now())
            logger.warn("탈퇴 정리 재시도 type={} attempt={} reason={}", claim.type, claim.attempt + 1, reason)
            false
        }
    }

    private fun unlink(memberId: String) {
        // 시연 데이터는 서비스 내부에서 만들며 연결된 카카오 계정이 없다.
        if (memberId.startsWith("demo-user-")) return
        if (memberId.toLongOrNull()?.let { it > 0 } != true) throw KakaoUnlinkFailure("KAKAO_INVALID_MEMBER_ID")
        if (kakaoProperties.adminKey.isBlank()) throw KakaoUnlinkFailure("KAKAO_ADMIN_KEY_MISSING")
        kakao.unlink(memberId, kakaoProperties.adminKey)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(WithdrawalCleanupScheduler::class.java)
    }
}
