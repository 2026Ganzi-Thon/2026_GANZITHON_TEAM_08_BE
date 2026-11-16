// 그룹 조회와 탈퇴 결과에 필요한 정보만 응답으로 반환한다.
package com.ganzithon.team08.group

import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User

data class MyGroupResponse(
    val groupId: Long?,
    val groupName: String?,
    val lifeStage: LifeStage,
    val typeCode: String,
    val typeName: String,
    val memberCount: Int,
    val capacity: Int,
    val members: List<MyGroupMember>,
) {
    companion object {
        fun from(user: User, activeMembers: List<User>): MyGroupResponse {
            val group = user.group
            val lifeStage = checkNotNull(group?.lifeStage ?: user.lifeStage)
            val type = checkNotNull(group?.userType ?: user.userType)
            val members = activeMembers.map {
                val id = checkNotNull(it.userId)
                MyGroupMember(id, it.nickname, id == user.userId)
            }
            return MyGroupResponse(
                groupId = group?.groupId,
                groupName = group?.let { "${lifeStage.label} · ${type.typeName} · ${it.groupId}번 그룹" },
                lifeStage = lifeStage,
                typeCode = type.typeCode,
                typeName = type.typeName,
                memberCount = members.size,
                capacity = 4,
                members = members,
            )
        }
    }
}

data class MyGroupMember(val userId: Long, val nickname: String, val isMe: Boolean)

data class LeaveGroupResponse(val leftGroupId: Long, val groupId: Long? = null, val left: Boolean = true)

data class GroupMatchResponse(
    val groupId: Long,
    val lifeStage: LifeStage,
    val typeCode: String,
    val groupCreated: Boolean,
    val memberCount: Long,
    val previousGroupId: Long? = null,
    val groupRematched: Boolean = false,
)
