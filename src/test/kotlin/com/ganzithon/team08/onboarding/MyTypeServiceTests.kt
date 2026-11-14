package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.group.CareGroupRepository
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserRepository
import com.ganzithon.team08.domain.user.UserType
import com.ganzithon.team08.domain.user.UserTypeRepository
import com.ganzithon.team08.domain.onboarding.AnswerChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class MyTypeServiceTests @Autowired constructor(
    private val myTypeService: MyTypeService,
    private val userRepository: UserRepository,
    private val userTypeRepository: UserTypeRepository,
    private val groupRepository: CareGroupRepository,
) {
    @Test
    fun returnsTypeAfterUserIsLoadedInANewTransaction() {
        val userType = userTypeRepository.save(UserType("TYPE_01", "부담정돈형", "설명"))
        val group = groupRepository.save(CareGroup(LifeStage.JOB_SEEKER, userType))
        val user = User("my-type-target", "유형확인양")
        user.completeOnboarding(LifeStage.JOB_SEEKER, userType, AnswerChoice.A, group)
        val savedUser = userRepository.save(user)

        val response = myTypeService.getMyType(requireNotNull(savedUser.userId))

        assertEquals("TYPE_01", response.typeCode)
        assertEquals("부담정돈형", response.typeName)
    }
}
