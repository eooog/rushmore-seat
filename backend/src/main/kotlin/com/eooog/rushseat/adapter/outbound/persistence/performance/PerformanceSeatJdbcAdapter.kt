package com.eooog.rushseat.adapter.outbound.persistence.performance

import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatResult
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatResult
import com.eooog.rushseat.domain.performance.PerformanceSeatStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
class PerformanceSeatJdbcAdapter(
    private val jdbc: JdbcClient,
) : HoldPerformanceSeatPort,
    ConfirmPerformanceSeatPort {

    override fun hold(command: HoldPerformanceSeatCommand): HoldPerformanceSeatResult {
        val updated = jdbc.sql(
            """
            UPDATE performance_seat
            SET status = 'HELD',
                hold_member_id = :memberId,
                hold_token = :holdToken,
                hold_expires_at = :expiresAt,
                version = version + 1,
                updated_at = now()
            WHERE id = :performanceSeatId
              AND performance_id = :performanceId
              AND status = 'AVAILABLE'
            """.trimIndent()
        )
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("expiresAt", command.expiresAt)
            .param("performanceSeatId", command.performanceSeatId)
            .param("performanceId", command.performanceId)
            .update()

        if (updated == 1) {
            return HoldPerformanceSeatResult(
                held = true,
                performanceSeatId = command.performanceSeatId,
                latestStatus = PerformanceSeatStatus.HELD,
            )
        }

        return HoldPerformanceSeatResult(
            held = false,
            performanceSeatId = command.performanceSeatId,
            latestStatus = loadPerformanceSeatStatus(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
            ),
        )
    }

    override fun confirm(command: ConfirmPerformanceSeatCommand): ConfirmPerformanceSeatResult {
        val performanceSeatId = jdbc.sql(
            """
            UPDATE performance_seat
            SET status = 'RESERVED',
                hold_member_id = NULL,
                hold_token = NULL,
                hold_expires_at = NULL,
                version = version + 1,
                updated_at = now()
            WHERE performance_id = :performanceId
              AND hold_member_id = :memberId
              AND hold_token = :holdToken
              AND status = 'HELD'
              AND hold_expires_at > :requestedAt
            RETURNING id
            """.trimIndent()
        )
            .param("performanceId", command.performanceId)
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("requestedAt", command.requestedAt)
            .query(Long::class.java)
            .optional()
            .orElse(null)

        return ConfirmPerformanceSeatResult(
            confirmed = performanceSeatId != null,
            performanceSeatId = performanceSeatId,
        )
    }

    private fun loadPerformanceSeatStatus(
        performanceId: Long,
        performanceSeatId: Long,
    ): PerformanceSeatStatus? {
        return jdbc.sql(
            """
            SELECT status
            FROM performance_seat
            WHERE performance_id = :performanceId
              AND id = :performanceSeatId
            """.trimIndent()
        )
            .param("performanceId", performanceId)
            .param("performanceSeatId", performanceSeatId)
            .query { rs, _ -> PerformanceSeatStatus.valueOf(rs.getString("status")) }
            .optional()
            .orElse(null)
    }
}
