package com.eooog.rushseat.application.queue

import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueStatePort
import com.eooog.rushseat.application.queue.required.QueueTokenRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.UUID

@Service
class QueueService(
    private val queueStatePort: QueueStatePort,
    @Value("\${rushmore-seat.queue.token-ttl-seconds}") queueTokenTtlSeconds: Long,
    @Value("\${rushmore-seat.queue.admission-token-ttl-seconds}") admissionTokenTtlSeconds: Long,
) : EnterQueueUseCase,
    GetQueueStatusUseCase,
    AdmitQueueUseCase,
    ValidateAdmissionUseCase {
    private val queueTokenTtl = Duration.ofSeconds(queueTokenTtlSeconds)
    private val admissionTokenTtl = Duration.ofSeconds(admissionTokenTtlSeconds)

    override fun enter(command: EnterQueueCommand): QueueEnterResult {
        queueStatePort.addWaitingMember(
            performanceId = command.performanceId,
            memberId = command.memberId,
            joinedAtMillis = command.requestedAt.toEpochMilli(),
        )

        val queueToken = "qt_${UUID.randomUUID()}"
        queueStatePort.saveQueueToken(
            token =
                QueueTokenRecord(
                    token = queueToken,
                    performanceId = command.performanceId,
                    memberId = command.memberId,
                    status = QueueStatus.WAITING,
                ),
            ttl = queueTokenTtl,
        )
        queueStatePort.saveMemberQueueToken(
            performanceId = command.performanceId,
            memberId = command.memberId,
            queueToken = queueToken,
            ttl = queueTokenTtl,
        )

        val rank = queueStatePort.getWaitingRank(command.performanceId, command.memberId)?.plus(1)
        return QueueEnterResult(
            status = QueueStatus.WAITING,
            queueToken = queueToken,
            rank = rank,
            estimatedWaitSeconds = estimateWaitSeconds(rank),
        )
    }

    override fun getStatus(query: GetQueueStatusQuery): QueueStatusResult {
        val token =
            queueStatePort.loadQueueToken(query.queueToken)
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Queue token is invalid or expired")

        if (token.performanceId != query.performanceId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Queue token does not belong to this performance")
        }

        if (token.status == QueueStatus.ADMITTED) {
            return QueueStatusResult(
                status = QueueStatus.ADMITTED,
                rank = null,
                estimatedWaitSeconds = null,
                admissionToken = token.admissionToken,
                expiresAt = token.admissionExpiresAt,
            )
        }

        val rank = queueStatePort.getWaitingRank(query.performanceId, token.memberId)?.plus(1)
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

                queueStatePort.saveAdmissionToken(
                    token =
                        AdmissionTokenRecord(
                            token = admissionToken,
                            performanceId = command.performanceId,
                            memberId = memberId,
                        ),
                    ttl = admissionTokenTtl,
                )

                queueStatePort.findMemberQueueToken(command.performanceId, memberId)?.let { queueToken ->
                    queueStatePort.markQueueTokenAdmitted(
                        queueToken = queueToken,
                        admissionToken = admissionToken,
                        expiresAt = expiresAt,
                    )
                }

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

        if (token.memberId != command.memberId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admission token does not belong to this member")
        }

        if (token.performanceId != command.performanceId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admission token does not belong to this performance")
        }

        return AdmittedMember(
            performanceId = token.performanceId,
            memberId = token.memberId,
            admissionToken = token.token,
        )
    }

    private fun estimateWaitSeconds(rank: Long?): Long? {
        if (rank == null) return null
        val assumedAdmissionPerSecond = 100L
        return rank / assumedAdmissionPerSecond
    }
}
