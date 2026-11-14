// 전체 피드와 그룹 피드 조회 HTTP 엔드포인트를 노출한다.
package com.ganzithon.team08.feed

import com.ganzithon.team08.feed.dto.FeedResponse
import com.ganzithon.team08.global.auth.CurrentUserId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class FeedController(
    private val feedService: FeedService,
) {
    @GetMapping("/api/feed")
    fun feed(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): FeedResponse = feedService.getFeed(userId, cursor, size)

    @GetMapping("/api/groups/me/feed")
    fun groupFeed(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): FeedResponse = feedService.getGroupFeed(userId, cursor, size)
}
