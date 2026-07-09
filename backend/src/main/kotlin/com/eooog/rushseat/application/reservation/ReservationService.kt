package com.eooog.rushseat.application.reservation

import com.eooog.rushseat.application.reservation.provided.ConfirmReservationUseCase
import com.eooog.rushseat.application.reservation.provided.HoldSeatUseCase
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.reservation.required.LoadReservationPort
import com.eooog.rushseat.application.reservation.required.LoadReservationReferencesCommand
import com.eooog.rushseat.application.reservation.required.LoadReservationReferencesPort
import com.eooog.rushseat.application.reservation.required.PublishSeatChangePort
import com.eooog.rushseat.application.reservation.required.SaveReservationPort
import com.eooog.rushseat.application.reservation.required.SeatHeldEvent
import com.eooog.rushseat.application.reservation.required.SeatReservedEvent
import com.eooog.rushseat.application.reservation.required.ValidateReservationAdmissionCommand
import com.eooog.rushseat.application.reservation.required.ValidateReservationAdmissionPort
import com.eooog.rushseat.domain.reservation.Reservation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class ReservationService(
    private val loadPerformanceSalesStatusPort: LoadPerformanceSalesStatusPort,
    private val loadReservationPort: LoadReservationPort,
    private val loadReservationReferencesPort: LoadReservationReferencesPort,
    private val holdPerformanceSeatPort: HoldPerformanceSeatPort,
    private val saveReservationPort: SaveReservationPort,
    private val confirmPerformanceSeatPort: ConfirmPerformanceSeatPort,
    private val confirmReservationPort: ConfirmReservationPort,
    private val publishSeatChangePort: PublishSeatChangePort,
    private val validateReservationAdmissionPort: ValidateReservationAdmissionPort,
    @Value("\${rushmore-seat.hold.ttl-seconds}") holdTtlSeconds: Long,
) : HoldSeatUseCase,
    ConfirmReservationUseCase {
    private val holdTtl: Duration = Duration.ofSeconds(holdTtlSeconds)

    // TODO: 좌석 선점 트랜잭션 범위를 축소한다.
    // 좌석 조건부 업데이트, 예약 저장, Outbox 저장만 하나의 트랜잭션으로 묶는다.
    // Admission Token 검증과 그 밖의 비트랜잭션 작업은 트랜잭션 외부로 분리한다.
    // 좌석 변경 이벤트는 Transactional Outbox를 통해 짧은 커밋 이후 발행하여
    // 좌석 잠금 시간을 최소화하고, 커밋되지 않은 상태가 외부에 노출되지 않도록 한다.
    @Transactional
    override fun hold(command: HoldSeatCommand): HoldSeatResult {
        // 1. admission token 검증
        validateReservationAdmissionPort.validate(
            ValidateReservationAdmissionCommand(
                performanceId = command.performanceId,
                memberId = command.memberId,
                admissionToken = command.admissionToken,
            ),
        )

        // 2. idempotency key 기준 기존 reservation 조회
        val existing =
            loadReservationPort.findByIdempotencyKey(
                performanceId = command.performanceId,
                memberId = command.memberId,
                idempotencyKey = command.idempotencyKey,
            )

        if (existing != null) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.ALREADY_PROCESSED,
                reservationId = existing.reservationId,
                performanceSeatId = existing.performanceSeatId,
                expiresAt = existing.expiresAt,
            )
        }

        // 3. performance 판매 상태 확인
        val performance =
            loadPerformanceSalesStatusPort.load(command.performanceId)
                ?: return HoldSeatResult(
                    status = HoldSeatResultStatus.PERFORMANCE_NOT_FOUND,
                    performanceSeatId = command.performanceSeatId,
                )

        if (!performance.isOnSale()) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.NOT_ON_SALE,
                performanceSeatId = performance.performanceId,
            )
        }

        // 4. seat hold 시도
        val expiresAt = command.requestedAt.plus(holdTtl)

        val holdResult =
            holdPerformanceSeatPort.hold(
                HoldPerformanceSeatCommand(
                    performanceId = command.performanceId,
                    performanceSeatId = command.performanceSeatId,
                    memberId = command.memberId,
                    expiresAt = expiresAt,
                ),
            )

        if (!holdResult.held) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.UNAVAILABLE,
                performanceSeatId = command.performanceSeatId,
            )
        }

        // 5. reservation 생성에 필요한 reference 조회
        val references =
            loadReservationReferencesPort.load(
                LoadReservationReferencesCommand(
                    performanceId = command.performanceId,
                    performanceSeatId = command.performanceSeatId,
                    memberId = command.memberId,
                ),
            ) ?: error("Reservation references were not found after seat hold")

        // 6. reservation 저장
        val reservation =
            Reservation.createHeld(
                performance = references.performance,
                performanceSeat = references.performanceSeat,
                member = references.member,
                idempotencyKey = command.idempotencyKey,
                expiresAt = expiresAt,
            )

        val savedReservation = saveReservationPort.save(reservation)

        // TODO: 트랜잭션 내부의 직접 이벤트 발행을 Transactional Outbox 방식으로 대체한다.
        // 좌석 변경 이벤트는 좌석 선점 트랜잭션이 정상적으로 커밋된 이후에만 발행되어야 한다.
        // 7. seat changed event publish
        publishSeatChangePort.publishSeatHeld(
            SeatHeldEvent(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
                holdExpiresAt = expiresAt,
            ),
        )

        return HoldSeatResult(
            status = HoldSeatResultStatus.HELD,
            reservationId = savedReservation.reservationId,
            performanceSeatId = command.performanceSeatId,
            expiresAt = expiresAt,
        )
    }

    @Transactional
    override fun confirm(command: ConfirmReservationCommand): ConfirmReservationResult {
        val seatResult =
            confirmPerformanceSeatPort.confirm(
                ConfirmPerformanceSeatCommand(
                    performanceId = command.performanceId,
                    memberId = command.memberId,
                    requestedAt = command.requestedAt,
                ),
            )

        if (!seatResult.confirmed || seatResult.performanceSeatId == null) {
            return ConfirmReservationResult(
                status = ConfirmReservationResultStatus.NOT_CONFIRMABLE,
            )
        }

        val reservationResult =
            confirmReservationPort.confirm(
                ConfirmReservationRecordCommand(
                    performanceId = command.performanceId,
                    memberId = command.memberId,
                    reservationId = command.reservationId,
                    confirmedAt = command.requestedAt,
                ),
            )

        check(reservationResult.reservationId != null) {
            "Reservation record was not confirmed after seat confirmation"
        }

        publishSeatChangePort.publishSeatReserved(
            SeatReservedEvent(
                performanceId = command.performanceId,
                performanceSeatId = seatResult.performanceSeatId,
            ),
        )

        return ConfirmReservationResult(
            status = ConfirmReservationResultStatus.CONFIRMED,
            reservationId = reservationResult.reservationId,
            performanceSeatId = seatResult.performanceSeatId,
        )
    }
}
