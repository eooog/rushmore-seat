package com.eooog.rushseat.adapter.inbound.web.reservation

import com.eooog.rushseat.application.reservation.ConfirmReservationCommand
import com.eooog.rushseat.application.reservation.ConfirmReservationResult
import com.eooog.rushseat.application.reservation.ConfirmReservationResultStatus
import com.eooog.rushseat.application.reservation.HoldSeatCommand
import com.eooog.rushseat.application.reservation.HoldSeatResult
import com.eooog.rushseat.application.reservation.HoldSeatResultStatus
import com.eooog.rushseat.application.reservation.provided.ConfirmReservationUseCase
import com.eooog.rushseat.application.reservation.provided.HoldSeatUseCase
import com.eooog.rushseat.application.shared.auth.MemberPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

@RestController
class ReservationController(
    private val holdSeatUseCase: HoldSeatUseCase,
    private val confirmReservationUseCase: ConfirmReservationUseCase,
    private val clock: Clock,
) {
    @PostMapping("/performances/{performanceId}/seats/{performanceSeatId}/hold")
    fun hold(
        @PathVariable performanceId: Long,
        @PathVariable performanceSeatId: Long,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String,
        @RequestHeader("X-Admission-Token") admissionToken: String,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): HoldSeatResponse {
        val result =
            holdSeatUseCase.hold(
                HoldSeatCommand(
                    performanceId = performanceId,
                    performanceSeatId = performanceSeatId,
                    memberId = principal.memberId,
                    admissionToken = admissionToken,
                    idempotencyKey = idempotencyKey,
                    requestedAt = clock.instant(),
                ),
            )

        return result.toResponse()
    }

    @PostMapping("/performances/{performanceId}/reservations/confirm")
    fun confirm(
        @PathVariable performanceId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ConfirmReservationResponse {
        val result =
            confirmReservationUseCase.confirm(
                ConfirmReservationCommand(
                    performanceId = performanceId,
                    memberId = principal.memberId,
                    requestedAt = clock.instant(),
                ),
            )

        return result.toResponse()
    }

    private fun HoldSeatResult.toResponse(): HoldSeatResponse =
        when (status) {
            HoldSeatResultStatus.HELD,
            HoldSeatResultStatus.ALREADY_PROCESSED,
            -> {
                HoldSeatResponse(
                    status = status.name,
                    reservationId = reservationId,
                    performanceSeatId = performanceSeatId,
                    holdToken = holdToken,
                    expiresAt = expiresAt,
                )
            }

            HoldSeatResultStatus.NOT_ON_SALE -> {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Performance is not on sale",
                )
            }

            HoldSeatResultStatus.UNAVAILABLE -> {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat is not available",
                )
            }
        }

    private fun ConfirmReservationResult.toResponse(): ConfirmReservationResponse =
        when (status) {
            ConfirmReservationResultStatus.CONFIRMED -> {
                ConfirmReservationResponse(
                    status = status.name,
                    reservationId = reservationId,
                    performanceSeatId = performanceSeatId,
                )
            }

            ConfirmReservationResultStatus.NOT_CONFIRMABLE -> {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation is not confirmable",
                )
            }
        }
}

data class HoldSeatResponse(
    val status: String,
    val reservationId: Long?,
    val performanceSeatId: Long,
    val holdToken: String?,
    val expiresAt: Instant?,
)

data class ConfirmReservationResponse(
    val status: String,
    val reservationId: Long?,
    val performanceSeatId: Long?,
)
