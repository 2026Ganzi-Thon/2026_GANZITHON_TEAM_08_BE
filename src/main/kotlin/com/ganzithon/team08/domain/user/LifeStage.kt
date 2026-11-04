// 온보딩에서 선택하는 생활 단계를 정의한다.
package com.ganzithon.team08.domain.user

enum class LifeStage(val label: String) {
    COLLEGE_STUDENT("대학생"),
    JOB_SEEKER("취업 준비생"),
    EARLY_CAREER("사회초년생"),
}
