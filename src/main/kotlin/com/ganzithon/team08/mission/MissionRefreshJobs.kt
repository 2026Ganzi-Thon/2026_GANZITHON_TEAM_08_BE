// 현재 기간의 미배정 대상을 페이지로 읽고 대상별로 배정한다.
package com.ganzithon.team08.mission

import com.ganzithon.team08.domain.group.GroupAssignmentAccess
import com.ganzithon.team08.domain.mission.MissionAllocator
import com.ganzithon.team08.domain.mission.MissionErrorCode
import com.ganzithon.team08.domain.mission.MissionPeriod
import com.ganzithon.team08.domain.user.UserAssignmentAccess
import com.ganzithon.team08.global.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class MissionRefreshJobs(
    private val users: UserAssignmentAccess,
    private val groups: GroupAssignmentAccess,
    private val allocator: MissionAllocator,
    @Value("\${app.missions.refresh.batch-size:100}") private val batchSize: Int,
) {
    init {
        require(batchSize > 0) { "미션 배정 배치 크기는 양수여야 합니다." }
    }

    fun run(kind: MissionRefreshKind, reference: Instant, now: () -> Instant = Instant::now): MissionRefreshResult {
        val period = kind.period(reference)
        val result = MissionRefreshResult()
        var after = 0L
        while (kind.period(now()) == period) {
            val ids = when (kind) {
                MissionRefreshKind.PERSONAL -> users.findMissingPersonalAssignmentIds(after, period, batchSize)
                MissionRefreshKind.GROUP -> groups.findMissingWeeklyAssignmentIds(after, period, batchSize)
            }
            if (ids.isEmpty()) break
            for (id in ids) {
                when (allocate(kind, id, reference, now)) {
                    Outcome.CREATED -> result.created++
                    Outcome.EXISTING -> result.existing++
                    Outcome.SKIPPED -> result.skipped++
                    Outcome.FAILED -> result.failed++
                }
                if (kind.period(now()) != period) return result
            }
            after = ids.last()
        }
        return result
    }

    private fun allocate(kind: MissionRefreshKind, id: Long, reference: Instant, now: () -> Instant): Outcome {
        repeat(MAX_ATTEMPTS) { attempt ->
            if (kind.period(now()) != kind.period(reference)) return Outcome.SKIPPED
            try {
                val assignment = when (kind) {
                    MissionRefreshKind.PERSONAL -> allocator.ensurePersonalAssignment(id, reference, now)
                    MissionRefreshKind.GROUP -> allocator.ensureGroupAssignment(id, reference, now)
                }
                return if (assignment.created) Outcome.CREATED else Outcome.EXISTING
            } catch (ex: BusinessException) {
                if (ex.errorCode == MissionErrorCode.TARGET_INELIGIBLE ||
                    ex.errorCode == MissionErrorCode.ASSIGNMENT_PERIOD_ENDED) return Outcome.SKIPPED
                logger.warn("미션 배정 실패 kind={} code={}", kind, ex.errorCode.code)
                return Outcome.FAILED
            } catch (_: TransientDataAccessException) {
                // 배정 서비스의 트랜잭션이 종료된 뒤 다시 호출한다.
                if (attempt == MAX_ATTEMPTS - 1) logger.warn("미션 배정 재시도 소진 kind={} code=TRANSIENT_DB", kind)
            } catch (ex: DataAccessException) {
                logger.warn("미션 배정 실패 kind={} code={}", kind, ex.javaClass.simpleName)
                return Outcome.FAILED
            }
        }
        return Outcome.FAILED
    }

    private enum class Outcome { CREATED, EXISTING, SKIPPED, FAILED }

    private companion object {
        const val MAX_ATTEMPTS = 3
        val logger = LoggerFactory.getLogger(MissionRefreshJobs::class.java)
    }
}

enum class MissionRefreshKind {
    PERSONAL,
    GROUP;

    fun period(instant: Instant): LocalDate = MissionPeriod.dateAt(instant).let {
        if (this == PERSONAL) it else MissionPeriod.weekStart(it)
    }
}

data class MissionRefreshResult(var created: Int = 0, var existing: Int = 0, var skipped: Int = 0, var failed: Int = 0)
