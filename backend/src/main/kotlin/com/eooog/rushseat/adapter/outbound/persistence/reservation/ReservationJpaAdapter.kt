package com.eooog.rushseat.adapter.outbound.persistence.reservation

import com.eooog.rushseat.adapter.outbound.persistence.performance.PerformanceJpaRepository
import com.eooog.rushseat.application.reservation.required.ConfirmReservationPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordCommand
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordResult
import com.eooog.rushseat.application.reservation.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.reservation.required.LoadReservationPort
import com.eooog.rushseat.application.reservation.required.PerformanceSalesStatusSnapshot
import com.eooog.rushseat.application.reservation.required.ReservationSnapshot
import com.eooog.rushseat.application.reservation.required.SaveHeldReservationCommand
import com.eooog.rushseat.application.reservation.required.SaveReservationPort
import com.eooog.rushseat.application.reservation.required.SavedReservationResult
import com.eooog.rushseat.domain.member.Member
import com.eooog.rushseat.domain.performance.Performance
import com.eooog.rushseat.domain.performance.PerformanceSeat
import com.eooog.rushseat.domain.reservation.Reservation
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class ReservationJpaAdapter(
    private val reservationRepository: ReservationJpaRepository,
    private val performanceRepository: PerformanceJpaRepository,
    private val entityManager: EntityManager,
) : LoadPerformanceSalesStatusPort,
    LoadReservationPort,
    SaveReservationPort,
    ConfirmReservationPort {

    override fun load(performanceId: Long): PerformanceSalesStatusSnapshot? {
        val projection = performanceRepository.findSalesStatus(performanceId)
            ?: return null

        return PerformanceSalesStatusSnapshot(
            performanceId = projection.performanceId,
            status = projection.status,
            salesStatus = projection.salesStatus,
        )
    }

    override fun findByIdempotencyKey(
        performanceId: Long,
        memberId: Long,
        idempotencyKey: String,
    ): ReservationSnapshot? {
        return reservationRepository.findByIdempotencyKey(
            performanceId = performanceId,
            memberId = memberId,
            idempotencyKey = idempotencyKey,
        )?.toSnapshot()
    }

    override fun findByHoldToken(
        performanceId: Long,
        memberId: Long,
        holdToken: String,
    ): ReservationSnapshot? {
        return reservationRepository.findByHoldToken(
            performanceId = performanceId,
            memberId = memberId,
            holdToken = holdToken,
        )?.toSnapshot()
    }

    override fun saveHeld(command: SaveHeldReservationCommand): SavedReservationResult {
        val performance = entityManager.getReference(Performance::class.java, command.performanceId)
        val performanceSeat = entityManager.find(PerformanceSeat::class.java, command.performanceSeatId)
            ?: error("Performance seat not found: ${command.performanceSeatId}")
        val member = entityManager.getReference(Member::class.java, command.memberId)

        val reservation = Reservation.createHeld(
            performance = performance,
            performanceSeat = performanceSeat,
            member = member,
            holdToken = command.holdToken,
            idempotencyKey = command.idempotencyKey,
            expiresAt = command.expiresAt,
        )

        val savedReservation = reservationRepository.saveAndFlush(reservation)
        return SavedReservationResult(
            reservationId = savedReservation.id ?: error("Reservation id was not generated"),
        )
    }

    override fun confirm(command: ConfirmReservationRecordCommand): ConfirmReservationRecordResult {
        val reservation = reservationRepository.findByHoldToken(
            performanceId = command.performanceId,
            memberId = command.memberId,
            holdToken = command.holdToken,
        ) ?: return ConfirmReservationRecordResult(reservationId = null)

        if (reservation.isExpired(command.confirmedAt)) {
            return ConfirmReservationRecordResult(reservationId = null)
        }

        reservation.confirm(command.confirmedAt)
        return ConfirmReservationRecordResult(
            reservationId = reservation.id,
        )
    }

    private fun Reservation.toSnapshot(): ReservationSnapshot {
        return ReservationSnapshot(
            reservationId = id ?: error("Reservation id is null"),
            performanceId = performance.id ?: error("Performance id is null"),
            performanceSeatId = performanceSeat.id ?: error("Performance seat id is null"),
            memberId = member.id ?: error("Member id is null"),
            status = status,
            holdToken = holdToken,
            expiresAt = expiresAt,
        )
    }
}
