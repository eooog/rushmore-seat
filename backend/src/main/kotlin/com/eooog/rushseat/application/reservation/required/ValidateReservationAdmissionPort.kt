package com.eooog.rushseat.application.reservation.required

interface ValidateReservationAdmissionPort {
    fun validate(command: ValidateReservationAdmissionCommand)
}

data class ValidateReservationAdmissionCommand(
    val performanceId: Long,
    val memberId: Long,
    val admissionToken: String,
)
