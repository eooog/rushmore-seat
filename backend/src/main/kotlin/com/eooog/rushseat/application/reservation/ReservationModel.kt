package com.eooog.rushseat.application.reservation

import java.time.Instant

data class HoldSeatCommand(
    val performanceId: Long,
    val performanceSeatId: Long,
    val memberId: Long,
    val idempotencyKey: String,
    val requestedAt: Instant,
)

data class ConfirmReservationCommand(
    val performanceId: Long,
    val memberId: Long,
    val holdToken: String,
    val requestedAt: Instant,
)

enum class HoldSeatResultStatus {
    HELD,
    ALREADY_PROCESSED,
    NOT_ON_SALE,
    UNAVAILABLE,
}

data class HoldSeatResult(
    val status: HoldSeatResultStatus,
    val reservationId: Long? = null,
    val performanceSeatId: Long,
    val holdToken: String? = null,
    val expiresAt: Instant? = null,
)

enum class ConfirmReservationResultStatus {
    CONFIRMED,
    NOT_CONFIRMABLE,
}

data class ConfirmReservationResult(
    val status: ConfirmReservationResultStatus,
    val reservationId: Long? = null,
    val performanceSeatId: Long? = null,
)
