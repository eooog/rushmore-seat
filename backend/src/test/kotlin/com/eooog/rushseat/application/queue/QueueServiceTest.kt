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
import java.time.Duration
import java.time.Instant

class QueueServiceTest {
    private lateinit var queueStatePort: FakeQueueStatePort
    private lateinit var clock: TestClock
    private lateinit var queueService: QueueService

    private val performanceId = 1L
    private val memberId = 259L
    private val admissionTokenTtlSeconds = 180L

    @BeforeEach
    fun setUp() {
        queueStatePort = FakeQueueStatePort()
        clock = TestClock(Instant.parse("2026-01-01T00:00:00Z"))
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
    fun `requireAdmitted() should validate admission token independent of memberId lookup`() {
        queueService.enter(enterCommand(memberId))
        val admitResult = queueService.admit(admitCommand(limit = 10))
        val admissionToken = admitResult.admissions.single().admissionToken

        val admittedMember = queueService.requireAdmitted(ValidateAdmissionCommand(performanceId, admissionToken))

        assertThat(admittedMember.memberId).isEqualTo(memberId)
        assertThat(admittedMember.performanceId).isEqualTo(performanceId)
    }

    private fun enterCommand(memberId: Long) = EnterQueueCommand(performanceId, memberId)

    private fun admitCommand(limit: Int) = AdmitQueueCommand(performanceId, limit, clock.instant())

    private class FakeQueueStatePort : QueueStatePort {
        private val waiting = mutableMapOf<Long, MutableList<Long>>()
        private val admissionsByMember = mutableMapOf<Pair<Long, Long>, AdmissionRecord>()
        private val admissionTokens = mutableMapOf<String, AdmissionTokenRecord>()

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
        }

        override fun findAdmissionByMember(
            performanceId: Long,
            memberId: Long,
        ): AdmissionRecord? = admissionsByMember[performanceId to memberId]

        override fun loadAdmissionToken(admissionToken: String): AdmissionTokenRecord? = admissionTokens[admissionToken]
    }
}
