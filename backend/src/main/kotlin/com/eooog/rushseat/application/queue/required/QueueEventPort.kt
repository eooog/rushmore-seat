package com.eooog.rushseat.application.queue.required

import java.time.Instant

interface QueueEventPort {
    fun broadcastProgress(
        performanceId: Long,
        admittedCount: Int,
    )

    fun notifyAdmitted(
        performanceId: Long,
        memberId: Long,
        admissionToken: String,
        expiresAt: Instant,
    )
}
