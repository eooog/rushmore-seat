package com.eooog.rushseat.adapter.outbound.admission

import com.eooog.rushseat.application.queue.ValidateAdmissionCommand
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.reservation.required.ValidateReservationAdmissionCommand
import com.eooog.rushseat.application.reservation.required.ValidateReservationAdmissionPort
import org.springframework.stereotype.Component

@Component
class QueueAdmissionValidatorAdapter(
    private val validateAdmissionUseCase: ValidateAdmissionUseCase,
) : ValidateReservationAdmissionPort {
    override fun validate(command: ValidateReservationAdmissionCommand) {
        validateAdmissionUseCase.requireAdmitted(
            ValidateAdmissionCommand(
                performanceId = command.performanceId,
                memberId = command.memberId,
                admissionToken = command.admissionToken,
            ),
        )
    }
}
