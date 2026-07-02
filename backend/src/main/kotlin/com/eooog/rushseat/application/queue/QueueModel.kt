package com.eooog.rushseat.application.queue

import java.time.Instant

enum class QueueStatus {
    WAITING,
    ADMITTED,
}

data class EnterQueueCommand(
    val performanceId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)

data class QueueEnterResult(
    val status: QueueStatus,
    val queueToken: String,
    val rank: Long?,
    val estimatedWaitSeconds: Long?,
)

data class GetQueueStatusQuery(
    val performanceId: Long,
    val queueToken: String,
)

data class QueueStatusResult(
    val status: QueueStatus,
    val rank: Long?,
    val estimatedWaitSeconds: Long?,
    val admissionToken: String?,
    val expiresAt: Instant?,
)

data class AdmitQueueCommand(
    val performanceId: Long,
    val limit: Int,
    val requestedAt: Instant,
)

data class AdmitQueueResult(
    val admittedCount: Int,
    val admissions: List<AdmissionResult>,
)

data class AdmissionResult(
    val memberId: Long,
    val admissionToken: String,
    val expiresAt: Instant,
)

data class ValidateAdmissionCommand(
    val performanceId: Long,
    val admissionToken: String,
)

data class AdmittedMember(
    val performanceId: Long,
    val memberId: Long,
    val admissionToken: String,
)
