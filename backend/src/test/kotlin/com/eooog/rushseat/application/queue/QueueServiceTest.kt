package com.eooog.rushseat.application.queue

import com.eooog.rushseat.application.queue.required.AdmissionRecord
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueStatePort
import com.eooog.rushseat.support.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant

class QueueServiceTest {
    private lateinit var queueStatePort: FakeQueueStatePort
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
        queueService = QueueService(queueStatePort, clock, admissionTokenTtlSeconds)
    }

    @Test
    fun `enter() should place member in the waiting queue and return the join time`() {
        val result = queueService.enter(enterCommand(memberId))

        assertThat(result.status).isEqualTo(QueueStatus.WAITING)
        assertThat(result.joinedAt).isEqualTo(clock.instant())
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

        override fun popWaitingMembers(
            performanceId: Long,
            limit: Int,
        ): List<Long> {
            val members = waiting[performanceId] ?: return emptyList()
            val popped = members.take(limit)
            repeat(popped.size) { members.removeAt(0) }
            return popped
        }

        override fun admit(
            performanceId: Long,
            memberId: Long,
            admissionToken: String,
            expiresAt: Instant,
            ttl: Duration,
        ) {
            admissionsByMember[performanceId to memberId] = AdmissionRecord(admissionToken, expiresAt)
            admissionTokens[admissionToken] = AdmissionTokenRecord(admissionToken, performanceId, memberId)
            tokenExpiresAt[admissionToken] = expiresAt
            occupancy.getOrPut(performanceId) { mutableMapOf() }[memberId] = expiresAt
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
}
