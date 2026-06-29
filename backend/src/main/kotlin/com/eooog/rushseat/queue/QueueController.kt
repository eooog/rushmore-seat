package com.eooog.rushseat.queue

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class QueueController(
    private val queueService: QueueService,
) {
    @PostMapping("/events/{eventId}/queue")
    fun enter(
        @PathVariable eventId: Long,
        @Valid @RequestBody request: QueueEnterRequest,
    ): QueueEnterResponse {
        return queueService.enter(eventId, request)
    }

    @GetMapping("/events/{eventId}/queue/me")
    fun me(
        @PathVariable eventId: Long,
        @RequestParam queueToken: String,
    ): QueueStatusResponse {
        return queueService.getStatus(eventId, queueToken)
    }

    @PostMapping("/internal/events/{eventId}/admissions")
    fun admit(
        @PathVariable eventId: Long,
        @RequestParam(defaultValue = "100") limit: Int,
    ): AdmitResponse {
        return queueService.admitNext(eventId, limit)
    }
}
