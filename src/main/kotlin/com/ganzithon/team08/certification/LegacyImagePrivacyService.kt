// 기존 사진을 찾고 탈퇴와 직렬화해 정리된 사진으로 교체한다.
package com.ganzithon.team08.certification

import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.LegacyImage
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserStatus
import com.ganzithon.team08.global.storage.ImageStorage
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LegacyImagePrivacyService(
    private val certifications: CertificationRepository,
    private val users: UserRepository,
    private val storage: ImageStorage,
) {
    @Transactional(readOnly = true)
    fun pending(now: Instant): List<LegacyImage> = certifications.findLegacyImages(now, PageRequest.of(0, 10))

    @Transactional
    fun replace(image: LegacyImage, cleaned: ByteArray) {
        if (users.findForUpdate(image.userId)?.status != UserStatus.ACTIVE) return
        val certification = certifications.findById(image.id).orElse(null) ?: return
        if (certification.imageSanitized == true) return
        storage.replace(image.key, cleaned)
        certification.markImageSanitized()
    }

    @Transactional
    fun retry(image: LegacyImage, now: Instant) {
        if (users.findForUpdate(image.userId)?.status != UserStatus.ACTIVE) return
        val certification = certifications.findById(image.id).orElse(null) ?: return
        if (certification.imageSanitized != true) certification.delayImageSanitization(now.plusSeconds(3600))
    }
}
