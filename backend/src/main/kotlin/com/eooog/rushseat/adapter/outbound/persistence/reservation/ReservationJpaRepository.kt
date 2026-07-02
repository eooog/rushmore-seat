package com.eooog.rushseat.adapter.outbound.persistence.reservation

import com.eooog.rushseat.domain.reservation.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReservationJpaRepository : JpaRepository<Reservation, Long> {

    @Query(
        """
        SELECT r
        FROM Reservation r
        JOIN FETCH r.performance
        JOIN FETCH r.performanceSeat
        JOIN FETCH r.member
        WHERE r.performance.id = :performanceId
          AND r.member.id = :memberId
          AND r.idempotencyKey = :idempotencyKey
        """
    )
    fun findByIdempotencyKey(
        @Param("performanceId") performanceId: Long,
        @Param("memberId") memberId: Long,
        @Param("idempotencyKey") idempotencyKey: String,
    ): Reservation?

    @Query(
        """
        SELECT r
        FROM Reservation r
        JOIN FETCH r.performance
        JOIN FETCH r.performanceSeat
        JOIN FETCH r.member
        WHERE r.performance.id = :performanceId
          AND r.member.id = :memberId
          AND r.holdToken = :holdToken
        """
    )
    fun findByHoldToken(
        @Param("performanceId") performanceId: Long,
        @Param("memberId") memberId: Long,
        @Param("holdToken") holdToken: String,
    ): Reservation?
}
