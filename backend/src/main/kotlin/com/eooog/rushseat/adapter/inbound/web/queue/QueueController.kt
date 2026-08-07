package com.eooog.rushseat.adapter.inbound.web.queue

import com.eooog.rushseat.application.queue.AdmitQueueCommand
import com.eooog.rushseat.application.queue.AdmitQueueResult
import com.eooog.rushseat.application.queue.AdmittedMember
import com.eooog.rushseat.application.queue.EnterQueueCommand
import com.eooog.rushseat.application.queue.GetQueueStatusQuery
import com.eooog.rushseat.application.queue.LeaveQueueCommand
import com.eooog.rushseat.application.queue.QueueEnterResult
import com.eooog.rushseat.application.queue.QueueStatusResult
import com.eooog.rushseat.application.queue.ValidateAdmissionCommand
import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.queue.provided.LeaveQueueUseCase
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.shared.auth.MemberPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class QueueController(
    private val enterQueueUseCase: EnterQueueUseCase,
    private val getQueueStatusUseCase: GetQueueStatusUseCase,
    private val admitQueueUseCase: AdmitQueueUseCase,
    private val leaveQueueUseCase: LeaveQueueUseCase,
    private val validateAdmissionUseCase: ValidateAdmissionUseCase,
) {
    @PostMapping("/performances/{performanceId}/queue")
    fun enter(
        @PathVariable performanceId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): QueueEnterResult =
        enterQueueUseCase.enter(
            EnterQueueCommand(
                performanceId = performanceId,
                memberId = principal.memberId,
            ),
        )

    @GetMapping("/performances/{performanceId}/queue/me")
    fun me(
        @PathVariable performanceId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): QueueStatusResult =
        getQueueStatusUseCase.getStatus(
            GetQueueStatusQuery(
                performanceId = performanceId,
                memberId = principal.memberId,
            ),
        )

    @PostMapping("/performances/{performanceId}/queue/leave")
    fun leave(
        @PathVariable performanceId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ) {
        leaveQueueUseCase.leave(
            LeaveQueueCommand(
                performanceId = performanceId,
                memberId = principal.memberId,
            ),
        )
    }

    @PostMapping("/performances/{performanceId}/queue/goal")
    fun goal(
        @PathVariable performanceId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestHeader(name = "X-Admission-Token", required = false, defaultValue = "") admissionToken: String,
    ): AdmittedMember =
        validateAdmissionUseCase.requireAdmitted(
            ValidateAdmissionCommand(
                performanceId = performanceId,
                memberId = principal.memberId,
                admissionToken = admissionToken,
            ),
        )

    // TODO: verify manager authentication
    @PostMapping("/internal/performances/{performanceId}/admissions")
    fun admit(
        @PathVariable performanceId: Long,
        @RequestParam(defaultValue = "100") limit: Int,
    ): AdmitQueueResult =
        admitQueueUseCase.admit(
            AdmitQueueCommand(
                performanceId = performanceId,
                limit = limit,
                requestedAt = Instant.now(),
            ),
        )
}
