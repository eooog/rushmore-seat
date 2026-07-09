package com.eooog.rushseat.adapter.outbound.reservation.persistence

import com.eooog.rushseat.adapter.outbound.persistence.performance.PerformanceJpaRepository
import com.eooog.rushseat.application.reservation.required.ConfirmReservationPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordCommand
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordResult
import com.eooog.rushseat.application.reservation.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.reservation.required.LoadReservationPort
import com.eooog.rushseat.application.reservation.required.LoadReservationReferencesCommand
import com.eooog.rushseat.application.reservation.required.LoadReservationReferencesPort
import com.eooog.rushseat.application.reservation.required.PerformanceSalesStatusSnapshot
import com.eooog.rushseat.application.reservation.required.ReservationReferences
import com.eooog.rushseat.application.reservation.required.ReservationSnapshot
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
    LoadReservationReferencesPort,
    SaveReservationPort,
    ConfirmReservationPort {
    override fun load(performanceId: Long): PerformanceSalesStatusSnapshot? {
        val projection =
            performanceRepository.findSalesStatus(performanceId)
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
    ): ReservationSnapshot? =
        reservationRepository
            .findByIdempotencyKey(
                performanceId = performanceId,
                memberId = memberId,
                idempotencyKey = idempotencyKey,
            )?.toSnapshot()

    override fun load(command: LoadReservationReferencesCommand): ReservationReferences? {
        val performance =
            entityManager.find(Performance::class.java, command.performanceId)
                ?: return null
        val performanceSeat =
            entityManager.find(PerformanceSeat::class.java, command.performanceSeatId)
                ?: return null
        val member =
            entityManager.find(Member::class.java, command.memberId)
                ?: return null

        return ReservationReferences(
            performance = performance,
            performanceSeat = performanceSeat,
            member = member,
        )
    }

    override fun save(reservation: Reservation): SavedReservationResult {
        val savedReservation = reservationRepository.saveAndFlush(reservation)
        return SavedReservationResult(
            reservationId = savedReservation.id ?: error("Reservation id was not generated"),
        )
    }

    override fun confirm(command: ConfirmReservationRecordCommand): ConfirmReservationRecordResult {
        val reservation =
            reservationRepository.findForConfirm(
                reservationId = command.reservationId,
                performanceId = command.performanceId,
                memberId = command.memberId,
            )
                ?: return ConfirmReservationRecordResult(
                    reservationId = null,
                )

        if (reservation.isExpired(command.confirmedAt)) {
            error("Reservation is already expired")
        }

        reservation.confirm(command.confirmedAt)

        return ConfirmReservationRecordResult(
            reservationId =
                reservation.id
                    ?: error("Reservation id is null"),
        )
    }

    private fun Reservation.toSnapshot(): ReservationSnapshot =
        ReservationSnapshot(
            reservationId = id ?: error("Reservation id is null"),
            performanceId = performance.id ?: error("Performance id is null"),
            performanceSeatId = performanceSeat.id ?: error("Performance seat id is null"),
            memberId = member.id ?: error("Member id is null"),
            status = status,
            expiresAt = expiresAt,
        )
}
