// JWT와 CSRF를 포함한 내 그룹 조회 및 탈퇴 API 계약을 검증한다.
package com.ganzithon.team08.group

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.global.security.JwtProvider
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyGroupApiTests @Autowired constructor(
    private val entities: EntityManager,
    private val mvc: MockMvc,
    private val tokens: JwtProvider,
) {
    @Test
    fun activeMembersAreReturnedInOrderAndLeaveCanBeRetried() {
        val (user, group) = fixture()
        val member = persist(User("group-api-2", "다른 멤버"))
        member.completeOnboarding(group.lifeStage, group.userType, AnswerChoice.B, group)
        val withdrawn = persist(User("group-api-3", "숨김"))
        withdrawn.completeOnboarding(group.lifeStage, group.userType, AnswerChoice.A, group)
        withdrawn.withdraw()

        mvc.perform(get("/api/groups/me").cookie(auth(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groupId").value(group.groupId))
            .andExpect(jsonPath("$.groupName").value("취업 준비생 · 기본 유형 · ${group.groupId}번 그룹"))
            .andExpect(jsonPath("$.capacity").value(4))
            .andExpect(jsonPath("$.memberCount").value(2))
            .andExpect(jsonPath("$.members[0].nickname").value(user.nickname))
            .andExpect(jsonPath("$.members[0].isMe").value(true))
            .andExpect(jsonPath("$.members[1].userId").value(member.userId))
            .andExpect(jsonPath("$.members[1].isMe").value(false))

        repeat(2) {
            mvc.perform(leave(user, """{"expectedGroupId":${group.groupId}}"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.leftGroupId").value(group.groupId))
                .andExpect(jsonPath("$.groupId").value(null as Any?))
                .andExpect(jsonPath("$.left").value(true))
        }
        mvc.perform(get("/api/groups/me").cookie(auth(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groupId").value(null as Any?))
            .andExpect(jsonPath("$.groupName").value(null as Any?))
            .andExpect(jsonPath("$.lifeStage").value("JOB_SEEKER"))
            .andExpect(jsonPath("$.typeCode").value("TYPE_01"))
            .andExpect(jsonPath("$.memberCount").value(0))
            .andExpect(jsonPath("$.members").isEmpty)
    }

    @Test
    fun missingCsrfAndInvalidGroupIdsAreRejected() {
        val (user, group) = fixture()
        mvc.perform(delete("/api/groups/me").cookie(auth(user))
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedGroupId":${group.groupId}}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
        listOf("{}", """{"expectedGroupId":0}""", """{"expectedGroupId":1.5}""", """{"expectedGroupId":"1"}""")
            .forEach { body ->
                mvc.perform(leave(user, body))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("INVALID_GROUP_ID"))
            }
    }

    @Test
    fun staleGroupIdAndIneligibleUsersAreRejected() {
        val (user, group) = fixture()
        val incomplete = persist(User("group-api-incomplete", "온보딩 전"))
        val withdrawn = persist(User("group-api-withdrawn", "탈퇴 계정")).also { it.withdraw() }
        mvc.perform(get("/api/groups/me").cookie(auth(incomplete)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"))
        mvc.perform(get("/api/groups/me").cookie(auth(withdrawn)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"))
        mvc.perform(leave(user, """{"expectedGroupId":${group.groupId!! + 1}}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GROUP_CHANGED"))
    }

    private fun fixture(): Pair<User, CareGroup> {
        val type = persist(UserType("TYPE_01", "기본 유형", "설명"))
        val group = persist(CareGroup(LifeStage.JOB_SEEKER, type))
        val user = persist(User("group-api-1", "본인"))
        user.completeOnboarding(group.lifeStage, type, AnswerChoice.A, group)
        return user to group
    }

    private fun auth(user: User) = Cookie("ACCESS_TOKEN", tokens.createAccessToken(checkNotNull(user.userId)))

    private fun leave(user: User, body: String) = delete("/api/groups/me")
        .cookie(auth(user), Cookie("XSRF-TOKEN", "group-csrf-test"))
        .header("X-XSRF-TOKEN", "group-csrf-test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun <T : Any> persist(entity: T): T = entity.also(entities::persist)
}
