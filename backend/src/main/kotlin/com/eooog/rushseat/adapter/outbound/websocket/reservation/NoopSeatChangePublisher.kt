package com.eooog.rushseat.adapter.outbound.websocket.reservation

import com.eooog.rushseat.application.reservation.required.PublishSeatChangePort
import com.eooog.rushseat.application.reservation.required.SeatHeldEvent
import com.eooog.rushseat.application.reservation.required.SeatReservedEvent
import org.springframework.stereotype.Component

@Component
class NoopSeatChangePublisher : PublishSeatChangePort {

    override fun publishSeatHeld(event: SeatHeldEvent) {
        // WebSocket publisher will be connected after the performance_seat API migration.
    }

    override fun publishSeatReserved(event: SeatReservedEvent) {
        // WebSocket publisher will be connected after the performance_seat API migration.
    }
}
