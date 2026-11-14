// 커서 페이징된 피드 목록 응답을 표현한다.
package com.ganzithon.team08.feed.dto

data class FeedResponse(
    val items: List<FeedItemResponse>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)
