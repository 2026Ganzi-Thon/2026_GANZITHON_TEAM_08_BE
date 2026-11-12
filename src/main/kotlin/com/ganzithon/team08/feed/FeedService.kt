// 전체 피드(PUBLIC)와 그룹 피드(GROUP·PUBLIC)를 커서 기반으로 조회한다.
package com.ganzithon.team08.feed

import com.ganzithon.team08.domain.certification.Certification
import com.ganzithon.team08.domain.certification.CertificationRepository
import com.ganzithon.team08.domain.certification.Visibility
import com.ganzithon.team08.domain.cheer.CheerRepository
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.feed.dto.FeedItemResponse
import com.ganzithon.team08.feed.dto.FeedResponse
import com.ganzithon.team08.global.exception.BusinessException
import com.ganzithon.team08.global.exception.CommonErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedService(
    private val certificationRepository: CertificationRepository,
    private val cheerRepository: CheerRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getFeed(viewerId: Long, cursor: Long?, size: Int): FeedResponse {
        val pageSize = validatePageSize(size)
        val rows = certificationRepository.findFeed(PUBLIC_ONLY, cursor, PageRequest.of(0, pageSize + 1))
        return toFeedResponse(rows, pageSize, viewerId)
    }

    @Transactional(readOnly = true)
    fun getGroupFeed(viewerId: Long, cursor: Long?, size: Int): FeedResponse {
        val pageSize = validatePageSize(size)
        val groupId = getUser(viewerId).group?.groupId ?: return EMPTY_FEED
        val rows = certificationRepository.findGroupFeed(groupId, GROUP_AND_PUBLIC, cursor, PageRequest.of(0, pageSize + 1))
        return toFeedResponse(rows, pageSize, viewerId)
    }

    private fun toFeedResponse(rows: List<Certification>, pageSize: Int, viewerId: Long): FeedResponse {
        if (rows.isEmpty()) return EMPTY_FEED
        val hasNext = rows.size > pageSize
        val page = if (hasNext) rows.subList(0, pageSize) else rows
        val certificationIds = page.map { requireNotNull(it.certificationId) }
        val cheerCounts = cheerRepository.countByCertificationIds(certificationIds)
            .associate { it.getCertificationId() to it.getCheerCount() }
        val cheeredIds = cheerRepository.findCertificationIdsCheeredBy(viewerId, certificationIds).toSet()
        val items = page.map { certification ->
            val certificationId = requireNotNull(certification.certificationId)
            FeedItemResponse.from(
                certification = certification,
                cheerCount = cheerCounts[certificationId] ?: 0L,
                cheered = cheeredIds.contains(certificationId),
                mine = certification.user.userId == viewerId,
            )
        }
        return FeedResponse(items, certificationIds.last(), hasNext)
    }

    private fun getUser(userId: Long) =
        userRepository.findById(userId).orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }

    private fun validatePageSize(size: Int): Int {
        if (size < 1 || size > MAX_PAGE_SIZE) throw BusinessException(FeedErrorCode.INVALID_PAGE_SIZE)
        return size
    }

    companion object {
        private const val MAX_PAGE_SIZE = 50
        private val PUBLIC_ONLY = listOf(Visibility.PUBLIC)
        private val GROUP_AND_PUBLIC = listOf(Visibility.GROUP, Visibility.PUBLIC)
        private val EMPTY_FEED = FeedResponse(emptyList(), null, false)
    }
}
