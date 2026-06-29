package com.eooog.rushseat.realtime

import com.eooog.rushseat.queue.QueueService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class SeatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val queueService: QueueService,
    private val sessions: WebSocketSessionRegistry,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val eventId = parseEventId(session)
        val token = parseToken(session)
        queueService.requireAdmitted("Bearer $token", eventId)
        session.attributes["eventId"] = eventId
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val eventId = session.attributes["eventId"] as? Long
            ?: throw IllegalStateException("eventId is missing from session")

        val node = objectMapper.readTree(message.payload)
        when (node.path("type").asText()) {
            "SUBSCRIBE_TILE" -> {
                val command = objectMapper.treeToValue(node, SubscribeTileCommand::class.java)
                if (command.eventId != eventId) {
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("eventId mismatch"))
                    return
                }
                sessions.subscribe(eventId, command.tileId, session)
                session.sendMessage(
                    TextMessage(
                        objectMapper.writeValueAsString(
                            TileSubscribedEvent(eventId = eventId, tileId = command.tileId),
                        ),
                    ),
                )
            }
            else -> {
                log.debug("Unknown WebSocket message: {}", message.payload)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        sessions.remove(session)
        if (session.isOpen) {
            session.close(CloseStatus.SERVER_ERROR)
        }
    }

    private fun parseEventId(session: WebSocketSession): Long {
        val path = session.uri?.path ?: throw IllegalArgumentException("WebSocket path is missing")
        return path.substringAfter("/ws/events/").substringBefore('/').toLong()
    }

    private fun parseToken(session: WebSocketSession): String {
        val query = session.uri?.query.orEmpty()
        return query.split('&')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .firstOrNull { it.first == "token" }
            ?.second
            ?: throw IllegalArgumentException("token query parameter is required")
    }
}
