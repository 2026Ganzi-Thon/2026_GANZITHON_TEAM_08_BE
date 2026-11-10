// 인증 공개 범위 변경 요청 본문을 표현한다.
package com.ganzithon.team08.certification.dto

import com.ganzithon.team08.domain.certification.Visibility

data class CertificationVisibilityChangeRequest(val visibility: Visibility)
