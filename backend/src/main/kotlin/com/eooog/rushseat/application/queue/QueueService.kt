package com.eooog.rushseat.application.queue

import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.queue.provided.LeaveQueueUseCase
import com.eooog.rushseat.application.queue.provided.RefillAdmissionsUseCase
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.queue.required.QueueStatePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class QueueService(
    private val queueStatePort: QueueStatePort,
    private val clock: Clock,
    @Value("\${rushmore-seat.queue.admission-token-ttl-seconds}") admissionTokenTtlSeconds: Long,
) : EnterQueueUseCase,
    GetQueueStatusUseCase,
    AdmitQueueUseCase,
    ValidateAdmissionUseCase,
    LeaveQueueUseCase,
    RefillAdmissionsUseCase {
    private val admissionTokenTtl = Duration.ofSeconds(admissionTokenTtlSeconds)

    override fun enter(command: EnterQueueCommand): QueueEnterResult {
        val requestedAt = clock.instant()

        queueStatePort.addWaitingMember(
            performanceId = command.performanceId,
            memberId = command.memberId,
        )

        return QueueEnterResult(
            status = QueueStatus.WAITING,
            joinedAt = requestedAt,
        )
    }

    override fun getStatus(query: GetQueueStatusQuery): QueueStatusResult {
        val admission = queueStatePort.findAdmissionByMember(query.performanceId, query.memberId)
        if (admission != null) {
            return QueueStatusResult(
                status = QueueStatus.ADMITTED,
                rank = null,
                estimatedWaitSeconds = null,
                admissionToken = admission.admissionToken,
                expiresAt = admission.expiresAt,
            )
        }

        val rank =
            queueStatePort.getWaitingRank(query.performanceId, query.memberId)?.plus(1)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No queue entry found for this member")

        return QueueStatusResult(
            status = QueueStatus.WAITING,
            rank = rank,
            estimatedWaitSeconds = estimateWaitSeconds(rank),
            admissionToken = null,
            expiresAt = null,
        )
    }

    override fun admit(command: AdmitQueueCommand): AdmitQueueResult {
        val limit = command.limit.coerceIn(1, 1_000)
        val admittedMembers =
            queueStatePort.popWaitingMembers(
                performanceId = command.performanceId,
                limit = limit,
            )

        val expiresAt = command.requestedAt.plus(admissionTokenTtl)
        val admissions =
            admittedMembers.map { memberId ->
                val admissionToken = "at_${UUID.randomUUID()}"

                queueStatePort.admit(
                    performanceId = command.performanceId,
                    memberId = memberId,
                    admissionToken = admissionToken,
                    expiresAt = expiresAt,
                    ttl = admissionTokenTtl,
                )

                AdmissionResult(
                    memberId = memberId,
                    admissionToken = admissionToken,
                    expiresAt = expiresAt,
                )
            }

        return AdmitQueueResult(
            admittedCount = admissions.size,
            admissions = admissions,
        )
    }

    override fun requireAdmitted(command: ValidateAdmissionCommand): AdmittedMember {
        val token =
            queueStatePort.loadAdmissionToken(command.admissionToken)
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admission token is invalid or expired")

        if (token.performanceId != command.performanceId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admission token does not belong to this performance")
        }

        if (token.memberId != command.memberId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admission token does not belong to this member")
        }

        return AdmittedMember(
            performanceId = token.performanceId,
            memberId = token.memberId,
            admissionToken = token.token,
        )
    }

    override fun leave(command: LeaveQueueCommand) {
        queueStatePort.release(
            performanceId = command.performanceId,
            memberId = command.memberId,
        )
    }

    override fun refill(command: RefillAdmissionsCommand): RefillAdmissionsResult {
        val occupied = queueStatePort.countOccupancy(command.performanceId, clock.instant())
        val room = (command.targetCapacity - occupied).coerceAtLeast(0)
        if (room == 0L) {
            return RefillAdmissionsResult(admittedCount = 0)
        }

        val result =
            admit(
                AdmitQueueCommand(
                    performanceId = command.performanceId,
                    limit = room.toInt(),
                    requestedAt = command.requestedAt,
                ),
            )

        return RefillAdmissionsResult(admittedCount = result.admittedCount)
    }

    private fun estimateWaitSeconds(rank: Long?): Long? {
        if (rank == null) return null
        val assumedAdmissionPerSecond = 100L
        return rank / assumedAdmissionPerSecond
    }
}
