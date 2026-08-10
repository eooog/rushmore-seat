package com.eooog.rushseat.adapter.outbound.realtime.sse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SseQueueEventPublisherTest {
    private val publisher = SseQueueEventPublisher()
    private val performanceId = 1L
    private val memberId = 259L
    private val otherMemberId = 999L

    @Test
    fun `register() should track the connection immediately`() {
        publisher.register(performanceId, memberId)

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `register() should track connections per performance independently`() {
        publisher.register(performanceId, memberId)
        publisher.register(performanceId = 2L, memberId = memberId)

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(1)
        assertThat(publisher.connectionCount(2L)).isEqualTo(1)
    }

    @Test
    fun `register() should replace an existing connection for the same member`() {
        publisher.register(performanceId, memberId)
        publisher.register(performanceId, memberId)

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `broadcastProgress() should not fail when there are no connections for the performance`() {
        publisher.broadcastProgress(performanceId, admittedCount = 3)
    }

    @Test
    fun `broadcastProgress() should send to every connection without closing them`() {
        publisher.register(performanceId, memberId)
        publisher.register(performanceId, otherMemberId)

        publisher.broadcastProgress(performanceId, admittedCount = 2)

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(2)
    }

    @Test
    fun `broadcastProgress() should not error when admittedCount is zero or negative`() {
        publisher.register(performanceId, memberId)

        publisher.broadcastProgress(performanceId, admittedCount = 0)
        publisher.broadcastProgress(performanceId, admittedCount = -1)

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `notifyAdmitted() should close and remove only the admitted member's connection`() {
        publisher.register(performanceId, memberId)
        publisher.register(performanceId, otherMemberId)

        publisher.notifyAdmitted(performanceId, memberId, admissionToken = "at_abc", expiresAt = Instant.now())

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `notifyAdmitted() should be a no-op when the member has no connection`() {
        publisher.notifyAdmitted(performanceId, memberId, admissionToken = "at_abc", expiresAt = Instant.now())

        assertThat(publisher.connectionCount(performanceId)).isEqualTo(0)
    }
}
