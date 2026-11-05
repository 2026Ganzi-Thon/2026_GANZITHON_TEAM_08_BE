// 외부 정리 작업과 재시도 상태를 저장하고 조회한다.
package com.ganzithon.team08.domain.cleanup

import org.springframework.data.jpa.repository.JpaRepository

interface CleanupTaskRepository : JpaRepository<CleanupTask, Long>
