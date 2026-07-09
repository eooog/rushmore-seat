package com.eooog.rushseat.adapter.inbound.web.queue

import com.eooog.rushseat.application.queue.AdmitQueueCommand
import com.eooog.rushseat.application.queue.AdmitQueueResult
import com.eooog.rushseat.application.queue.EnterQueueCommand
import com.eooog.rushseat.application.queue.GetQueueStatusQuery
import com.eooog.rushseat.application.queue.QueueEnterResult
import com.eooog.rushseat.application.queue.QueueStatusResult
import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
class QueueController(
    private val enterQueueUseCase: EnterQueueUseCase,
    private val getQueueStatusUseCase: GetQueueStatusUseCase,
    private val admitQueueUseCase: AdmitQueueUseCase,
    private val clock: Clock,
) {
    @PostMapping("/performances/{performanceId}/queue")
    fun enter(
        @PathVariable performanceId: Long,
        @Valid @RequestBody request: QueueEnterRequest,
    ): QueueEnterResult =
        enterQueueUseCase.enter(
            EnterQueueCommand(
                performanceId = performanceId,
                memberId = request.memberId,
                requestedAt = clock.instant(),
            ),
        )

    @GetMapping("/performances/{performanceId}/queue/me")
    fun me(
        @PathVariable performanceId: Long,
        @RequestParam queueToken: String,
    ): QueueStatusResult =
        getQueueStatusUseCase.getStatus(
            GetQueueStatusQuery(
                performanceId = performanceId,
                queueToken = queueToken,
            ),
        )

    @PostMapping("/internal/performances/{performanceId}/admissions")
    fun admit(
        @PathVariable performanceId: Long,
        @RequestParam(defaultValue = "100") limit: Int,
    ): AdmitQueueResult =
        admitQueueUseCase.admit(
            AdmitQueueCommand(
                performanceId = performanceId,
                limit = limit,
                requestedAt = clock.instant(),
            ),
        )
}

data class QueueEnterRequest(
    @field:Positive
    val memberId: Long,
)
