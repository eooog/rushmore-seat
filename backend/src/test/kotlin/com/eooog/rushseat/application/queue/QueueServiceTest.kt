package com.eooog.rushseat.application.queue

import com.eooog.rushseat.application.performance.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.performance.required.PerformanceSalesStatusSnapshot
import com.eooog.rushseat.application.queue.required.AdmissionRecord
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueEventPort
import com.eooog.rushseat.application.queue.required.QueueStatePort
import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceStatus
import com.eooog.rushseat.support.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant

class QueueServiceTest {
    private lateinit var queueStatePort: FakeQueueStatePort
    private lateinit var loadPerformanceSalesStatusPort: FakeLoadPerformanceSalesStatusPort
    private lateinit var queueEventPort: FakeQueueEventPort
    private lateinit var clock: TestClock
    private lateinit var queueService: QueueService

    private val performanceId = 1L
    private val memberId = 259L
    private val otherMemberId = 999L
    private val admissionTokenTtlSeconds = 180L

    @BeforeEach
    fun setUp() {
        clock = TestClock(Instant.parse("2026-01-01T00:00:00Z"))
        queueStatePort = FakeQueueStatePort(clock)
        loadPerformanceSalesStatusPort =
            FakeLoadPerformanceSalesStatusPort().apply {
                setSalesStatus(performanceId, PerformanceStatus.SCHEDULED, PerformanceSalesStatus.ON_SALE)
            }
        queueEventPort = FakeQueueEventPort()
        queueService =
            QueueService(queueStatePort, loadPerformanceSalesStatusPort, queueEventPort, clock, admissionTokenTtlSeconds)
    }

    @Test
    fun `enter() should place member in the waiting queue and return the join time`() {
        val result = queueService.enter(enterCommand(memberId))

        assertThat(result.status).isEqualTo(QueueStatus.WAITING)
        assertThat(result.joinedAt).isEqualTo(clock.instant())
    }

    @Test
    fun `enter() should throw 404 when performanceId does not exist`() {
        val exception =
            catchThrowableOfType(
                ResponseStatusException::class.java,
            ) { queueService.enter(EnterQueueCommand(performanceId = 404L, memberId = memberId)) }

        assertThat(exception.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `enter() should throw 409 when performance has not opened for sale yet`() {
        loadPerformanceSalesStatusPort.setSalesStatus(performanceId, PerformanceStatus.SCHEDULED, PerformanceSalesStatus.BEFORE_SALE)

        val exception =
            catchThrowableOfType(
                ResponseStatusException::class.java,
            ) { queueService.enter(enterCommand(memberId)) }

        assertThat(exception.statusCode.value()).isEqualTo(409)
    }

    @Test
    fun `enter() should throw 409 when performance sales are closed`() {
        loadPerformanceSalesStatusPort.setSalesStatus(performanceId, PerformanceStatus.SCHEDULED, PerformanceSalesStatus.CLOSED)

        val exception =
            catchThrowableOfType(
                ResponseStatusException::class.java,
            ) { queueService.enter(enterCommand(memberId)) }

        assertThat(exception.statusCode.value()).isEqualTo(409)
    }

    @Test
    fun `enter() should not create a duplicate slot when called twice for the same member`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(memberId))

        val admitted = queueService.admit(admitCommand(limit = 10))

        assertThat(admitted.admittedCount).isEqualTo(1)
    }

    @Test
    fun `getStatus() should return WAITING with rank while member has not been admitted`() {
        queueService.enter(enterCommand(memberId))

        val status = queueService.getStatus(GetQueueStatusQuery(performanceId, memberId))

        assertThat(status.status).isEqualTo(QueueStatus.WAITING)
        assertThat(status.rank).isEqualTo(1)
        assertThat(status.admissionToken).isNull()
    }

    @Test
    fun `getStatus() should return ADMITTED with admissionToken after admit()`() {
        queueService.enter(enterCommand(memberId))
        queueService.admit(admitCommand(limit = 10))

        val status = queueService.getStatus(GetQueueStatusQuery(performanceId, memberId))

        assertThat(status.status).isEqualTo(QueueStatus.ADMITTED)
        assertThat(status.admissionToken).isNotNull
        assertThat(status.expiresAt).isNotNull
        assertThat(status.rank).isNull()
    }

    @Test
    fun `getStatus() should throw 404 when member has no queue entry`() {
        assertThatThrownBy {
            queueService.getStatus(GetQueueStatusQuery(performanceId, memberId))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `admit() should pop waiting members and issue admission tokens`() {
        queueService.enter(enterCommand(memberId))

        val result = queueService.admit(admitCommand(limit = 10))

        assertThat(result.admittedCount).isEqualTo(1)
        assertThat(result.admissions.single().memberId).isEqualTo(memberId)
        assertThat(result.admissions.single().admissionToken).startsWith("at_")
    }

    @Test
    fun `admit() should stop early and preserve join order when the waiting queue has fewer members than the limit`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(otherMemberId))

        val result = queueService.admit(admitCommand(limit = 10))

        assertThat(result.admittedCount).isEqualTo(2)
        assertThat(result.admissions.map { it.memberId }).containsExactly(memberId, otherMemberId)
    }

    @Test
    fun `admit() should notify each admitted member and broadcast progress once`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(otherMemberId))

        val result = queueService.admit(admitCommand(limit = 10))

        assertThat(queueEventPort.notifiedAdmissions.map { it.memberId }).containsExactly(memberId, otherMemberId)
        assertThat(queueEventPort.notifiedAdmissions.map { it.admissionToken })
            .isEqualTo(result.admissions.map { it.admissionToken })
        assertThat(queueEventPort.broadcasts)
            .containsExactly(FakeQueueEventPort.BroadcastProgress(performanceId, 2))
    }

    @Test
    fun `admit() should not notify or broadcast when nobody is admitted`() {
        val result = queueService.admit(admitCommand(limit = 10))

        assertThat(result.admittedCount).isEqualTo(0)
        assertThat(queueEventPort.notifiedAdmissions).isEmpty()
        assertThat(queueEventPort.broadcasts).isEmpty()
    }

    @Test
    fun `requireAdmitted() should return the member when memberId matches the token`() {
        queueService.enter(enterCommand(memberId))
        val admitResult = queueService.admit(admitCommand(limit = 10))
        val admissionToken = admitResult.admissions.single().admissionToken

        val admittedMember =
            queueService.requireAdmitted(ValidateAdmissionCommand(performanceId, memberId, admissionToken))

        assertThat(admittedMember.memberId).isEqualTo(memberId)
    }

    @Test
    fun `requireAdmitted() should reject a token that belongs to a different member`() {
        queueService.enter(enterCommand(memberId))
        val admitResult = queueService.admit(admitCommand(limit = 10))
        val admissionToken = admitResult.admissions.single().admissionToken

        assertThatThrownBy {
            queueService.requireAdmitted(ValidateAdmissionCommand(performanceId, memberId = otherMemberId, admissionToken))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `requireAdmitted() should reject a token after it has expired`() {
        queueService.enter(enterCommand(memberId))
        val admitResult = queueService.admit(admitCommand(limit = 10))
        val admissionToken = admitResult.admissions.single().admissionToken

        clock.advance(Duration.ofSeconds(admissionTokenTtlSeconds))

        assertThatThrownBy {
            queueService.requireAdmitted(ValidateAdmissionCommand(performanceId, memberId, admissionToken))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `leave() should free capacity so refill() admits the next waiting member`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(otherMemberId))
        queueService.admit(admitCommand(limit = 1))

        queueService.leave(LeaveQueueCommand(performanceId, memberId))
        val refillResult =
            queueService.refill(RefillAdmissionsCommand(performanceId, targetCapacity = 1, requestedAt = clock.instant()))

        assertThat(refillResult.admittedCount).isEqualTo(1)
        val status = queueService.getStatus(GetQueueStatusQuery(performanceId, otherMemberId))
        assertThat(status.status).isEqualTo(QueueStatus.ADMITTED)
    }

    @Test
    fun `refill() should not admit anyone when occupancy already meets target capacity`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(otherMemberId))
        queueService.admit(admitCommand(limit = 1))

        val refillResult =
            queueService.refill(RefillAdmissionsCommand(performanceId, targetCapacity = 1, requestedAt = clock.instant()))

        assertThat(refillResult.admittedCount).isEqualTo(0)
    }

    @Test
    fun `refill() should treat expired admissions as no longer occupying capacity`() {
        queueService.enter(enterCommand(memberId))
        queueService.enter(enterCommand(otherMemberId))
        queueService.admit(admitCommand(limit = 1))

        clock.advance(Duration.ofSeconds(admissionTokenTtlSeconds))

        val refillResult =
            queueService.refill(RefillAdmissionsCommand(performanceId, targetCapacity = 1, requestedAt = clock.instant()))

        assertThat(refillResult.admittedCount).isEqualTo(1)
    }

    private fun enterCommand(memberId: Long) = EnterQueueCommand(performanceId, memberId)

    private fun admitCommand(limit: Int) = AdmitQueueCommand(performanceId, limit, clock.instant())

    private class FakeQueueStatePort(
        private val clock: Clock,
    ) : QueueStatePort {
        private val waiting = mutableMapOf<Long, MutableList<Long>>()
        private val admissionsByMember = mutableMapOf<Pair<Long, Long>, AdmissionRecord>()
        private val admissionTokens = mutableMapOf<String, AdmissionTokenRecord>()
        private val tokenExpiresAt = mutableMapOf<String, Instant>()
        private val occupancy = mutableMapOf<Long, MutableMap<Long, Instant>>()

        override fun addWaitingMember(
            performanceId: Long,
            memberId: Long,
        ) {
            val members = waiting.getOrPut(performanceId) { mutableListOf() }
            if (!members.contains(memberId)) {
                members.add(memberId)
            }
        }

        override fun getWaitingRank(
            performanceId: Long,
            memberId: Long,
        ): Long? {
            val index = waiting[performanceId]?.indexOf(memberId) ?: -1
            return if (index >= 0) index.toLong() else null
        }

        override fun admitNextWaitingMember(
            performanceId: Long,
            admissionToken: String,
            expiresAt: Instant,
            ttl: Duration,
        ): Long? {
            val members = waiting[performanceId]
            if (members.isNullOrEmpty()) return null
            val memberId = members.removeAt(0)

            admissionsByMember[performanceId to memberId] = AdmissionRecord(admissionToken, expiresAt)
            admissionTokens[admissionToken] = AdmissionTokenRecord(admissionToken, performanceId, memberId)
            tokenExpiresAt[admissionToken] = expiresAt
            occupancy.getOrPut(performanceId) { mutableMapOf() }[memberId] = expiresAt

            return memberId
        }

        override fun findAdmissionByMember(
            performanceId: Long,
            memberId: Long,
        ): AdmissionRecord? {
            val record = admissionsByMember[performanceId to memberId] ?: return null
            if (isExpired(record.expiresAt)) {
                admissionsByMember.remove(performanceId to memberId)
                return null
            }
            return record
        }

        override fun loadAdmissionToken(admissionToken: String): AdmissionTokenRecord? {
            val expiresAt = tokenExpiresAt[admissionToken] ?: return null
            if (isExpired(expiresAt)) {
                admissionTokens.remove(admissionToken)
                tokenExpiresAt.remove(admissionToken)
                return null
            }
            return admissionTokens[admissionToken]
        }

        override fun countOccupancy(
            performanceId: Long,
            now: Instant,
        ): Long {
            val members = occupancy[performanceId] ?: return 0
            members.entries.removeAll { !it.value.isAfter(now) }
            return members.size.toLong()
        }

        override fun release(
            performanceId: Long,
            memberId: Long,
        ) {
            occupancy[performanceId]?.remove(memberId)
        }

        private fun isExpired(expiresAt: Instant): Boolean = !expiresAt.isAfter(clock.instant())
    }

    private class FakeLoadPerformanceSalesStatusPort : LoadPerformanceSalesStatusPort {
        private val snapshots = mutableMapOf<Long, PerformanceSalesStatusSnapshot>()

        fun setSalesStatus(
            performanceId: Long,
            status: PerformanceStatus,
            salesStatus: PerformanceSalesStatus,
        ) {
            snapshots[performanceId] = PerformanceSalesStatusSnapshot(performanceId, status, salesStatus)
        }

        override fun load(performanceId: Long): PerformanceSalesStatusSnapshot? = snapshots[performanceId]
    }

    private class FakeQueueEventPort : QueueEventPort {
        val notifiedAdmissions = mutableListOf<NotifiedAdmission>()
        val broadcasts = mutableListOf<BroadcastProgress>()

        override fun broadcastProgress(
            performanceId: Long,
            admittedCount: Int,
        ) {
            broadcasts += BroadcastProgress(performanceId, admittedCount)
        }

        override fun notifyAdmitted(
            performanceId: Long,
            memberId: Long,
            admissionToken: String,
            expiresAt: Instant,
        ) {
            notifiedAdmissions += NotifiedAdmission(performanceId, memberId, admissionToken, expiresAt)
        }

        data class BroadcastProgress(
            val performanceId: Long,
            val admittedCount: Int,
        )

        data class NotifiedAdmission(
            val performanceId: Long,
            val memberId: Long,
            val admissionToken: String,
            val expiresAt: Instant,
        )
    }
}
