package com.eooog.rushseat.application.queue

import com.eooog.rushseat.application.performance.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.queue.provided.LeaveQueueUseCase
import com.eooog.rushseat.application.queue.provided.RefillAdmissionsUseCase
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.queue.required.QueueEventPort
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
    private val loadPerformanceSalesStatusPort: LoadPerformanceSalesStatusPort,
    private val queueEventPort: QueueEventPort,
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
        val snapshot =
            loadPerformanceSalesStatusPort.load(command.performanceId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Performance not found")

        if (!snapshot.isOnSale()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Performance is not on sale")
        }

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
        val rank = queueStatePort.getWaitingRank(query.performanceId, query.memberId)
        if (rank != null) {
            return QueueStatusResult(
                status = QueueStatus.WAITING,
                rank = rank + 1,
                estimatedWaitSeconds = estimateWaitSeconds(rank + 1),
                admissionToken = null,
                expiresAt = null,
            )
        }

        val admission =
            queueStatePort.findAdmissionByMember(query.performanceId, query.memberId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No queue entry found for this member")

        return QueueStatusResult(
            status = QueueStatus.ADMITTED,
            rank = null,
            estimatedWaitSeconds = null,
            admissionToken = admission.admissionToken,
            expiresAt = admission.expiresAt,
        )
    }

    override fun admit(command: AdmitQueueCommand): AdmitQueueResult {
        val limit = command.limit.coerceIn(1, 1_000)
        val expiresAt = command.requestedAt.plus(admissionTokenTtl)
        val admissions = mutableListOf<AdmissionResult>()

        for (i in 0 until limit) {
            val admissionToken = "at_${UUID.randomUUID()}"
            val memberId =
                queueStatePort.admitNextWaitingMember(
                    performanceId = command.performanceId,
                    admissionToken = admissionToken,
                    expiresAt = expiresAt,
                    ttl = admissionTokenTtl,
                ) ?: break

            admissions +=
                AdmissionResult(
                    memberId = memberId,
                    admissionToken = admissionToken,
                    expiresAt = expiresAt,
                )
        }

        admissions.forEach {
            queueEventPort.notifyAdmitted(
                performanceId = command.performanceId,
                memberId = it.memberId,
                admissionToken = it.admissionToken,
                expiresAt = it.expiresAt,
            )
        }
        if (admissions.isNotEmpty()) {
            queueEventPort.broadcastProgress(command.performanceId, admissions.size)
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
