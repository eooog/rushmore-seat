package com.eooog.rushseat.application.reservation.provided

import com.eooog.rushseat.application.reservation.command.ConfirmReservationCommand
import com.eooog.rushseat.application.reservation.command.HoldSeatCommand
import com.eooog.rushseat.application.reservation.result.ConfirmReservationResult
import com.eooog.rushseat.application.reservation.result.HoldSeatResult

interface HoldSeatUseCase {
    fun hold(command: HoldSeatCommand): HoldSeatResult
}

interface ConfirmReservationUseCase {
    fun confirm(command: ConfirmReservationCommand): ConfirmReservationResult
}
