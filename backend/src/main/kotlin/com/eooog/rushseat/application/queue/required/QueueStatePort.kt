package com.eooog.rushseat.application.queue.required

import java.time.Duration
import java.time.Instant

interface QueueStatePort {
    fun addWaitingMember(
        performanceId: Long,
        memberId: Long,
    )

    fun getWaitingRank(
        performanceId: Long,
        memberId: Long,
    ): Long?

    fun popWaitingMembers(
        performanceId: Long,
        limit: Int,
    ): List<Long>

    fun admit(
        performanceId: Long,
        memberId: Long,
        admissionToken: String,
        expiresAt: Instant,
        ttl: Duration,
    )

    fun findAdmissionByMember(
        performanceId: Long,
        memberId: Long,
    ): AdmissionRecord?

    fun loadAdmissionToken(admissionToken: String): AdmissionTokenRecord?
}

data class AdmissionRecord(
    val admissionToken: String,
    val expiresAt: Instant,
)

data class AdmissionTokenRecord(
    val token: String,
    val performanceId: Long,
    val memberId: Long,
)
