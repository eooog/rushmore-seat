package com.eooog.rushseat.application.queue.required

import com.eooog.rushseat.application.queue.QueueStatus
import java.time.Duration
import java.time.Instant

interface QueueStatePort {
    fun addWaitingMember(
        performanceId: Long,
        memberId: Long,
        joinedAtMillis: Long,
    )

    fun getWaitingRank(
        performanceId: Long,
        memberId: Long,
    ): Long?

    fun popWaitingMembers(
        performanceId: Long,
        limit: Int,
    ): List<Long>

    fun saveQueueToken(
        token: QueueTokenRecord,
        ttl: Duration,
    )

    fun loadQueueToken(queueToken: String): QueueTokenRecord?

    fun saveMemberQueueToken(
        performanceId: Long,
        memberId: Long,
        queueToken: String,
        ttl: Duration,
    )

    fun findMemberQueueToken(
        performanceId: Long,
        memberId: Long,
    ): String?

    fun markQueueTokenAdmitted(
        queueToken: String,
        admissionToken: String,
        expiresAt: Instant,
    )

    fun saveAdmissionToken(
        token: AdmissionTokenRecord,
        ttl: Duration,
    )

    fun loadAdmissionToken(admissionToken: String): AdmissionTokenRecord?
}

data class QueueTokenRecord(
    val token: String,
    val performanceId: Long,
    val memberId: Long,
    val status: QueueStatus,
    val admissionToken: String? = null,
    val admissionExpiresAt: Instant? = null,
)

data class AdmissionTokenRecord(
    val token: String,
    val performanceId: Long,
    val memberId: Long,
)
