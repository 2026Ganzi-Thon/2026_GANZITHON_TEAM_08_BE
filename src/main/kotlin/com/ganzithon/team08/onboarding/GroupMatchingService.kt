package com.ganzithon.team08.onboarding

import com.ganzithon.team08.domain.group.CareGroup
import com.ganzithon.team08.domain.user.LifeStage
import com.ganzithon.team08.domain.user.User
import com.ganzithon.team08.domain.user.UserType

/** 온보딩에서 그룹 매칭 담당 기능을 호출하기 위한 계약이다. */
interface GroupMatchingService {
    fun match(user: User, lifeStage: LifeStage, userType: UserType): CareGroup
}
