package com.eooog.rushseat.adapter.inbound.scheduler.queue

import com.eooog.rushseat.application.queue.RefillAdmissionsCommand
import com.eooog.rushseat.application.queue.provided.RefillAdmissionsUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AdmissionScheduler(
    private val refillAdmissionsUseCase: RefillAdmissionsUseCase,
    private val clock: Clock,
    @Value("\${rushmore-seat.queue.admission-scheduler-performance-ids:}") performanceIdsCsv: String,
    @Value("\${rushmore-seat.queue.admission-scheduler-target-capacity}") private val targetCapacity: Int,
) {
    private val performanceIds: List<Long> = performanceIdsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }

    init {
        if (performanceIds.isEmpty()) {
            log.warn(
                "rushmore-seat.queue.admission-scheduler-performance-ids is empty; " +
                    "AdmissionScheduler will run on schedule but refill nothing",
            )
        }
    }

    @Scheduled(fixedDelayString = "\${rushmore-seat.queue.admission-scheduler-delay-ms}")
    fun refill() {
        val now = clock.instant()

        performanceIds.forEach { performanceId ->
            refillAdmissionsUseCase.refill(
                RefillAdmissionsCommand(
                    performanceId = performanceId,
                    targetCapacity = targetCapacity,
                    requestedAt = now,
                ),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdmissionScheduler::class.java)
    }
}
