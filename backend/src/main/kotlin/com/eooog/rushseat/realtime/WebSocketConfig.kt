package com.eooog.rushseat.realtime

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
class WebSocketConfig(
    private val seatWebSocketHandler: SeatWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(seatWebSocketHandler, "/ws/events/{eventId}")
            .setAllowedOrigins("*")
    }
}
