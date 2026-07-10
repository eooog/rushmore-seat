package com.eooog.rushseat.domain.reservation

import com.eooog.rushseat.domain.member.Member
import com.eooog.rushseat.domain.performance.Performance
import com.eooog.rushseat.domain.performance.PerformanceSeat
import java.time.Instant

object ReservationFixture {
    fun reservation(
        performance: Performance,
        performanceSeat: PerformanceSeat,
        member: Member,
        idempotencyKey: String,
        expiresAt: Instant,
    ): Reservation =
        Reservation.createHeld(
            performance = performance,
            performanceSeat = performanceSeat,
            member = member,
            idempotencyKey = idempotencyKey,
            expiresAt = expiresAt,
        )
}
