package com.eooog.rushseat.realtime

import com.eooog.rushseat.seat.AssetState
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class AssetEventPublisher(
    private val objectMapper: ObjectMapper,
    private val sessions: WebSocketSessionRegistry,
) {
    fun publishClaiming(eventId: Long, tileId: String, assetId: Long, observedVersion: Long?) {
        val event = AssetChangedBatchEvent(
            eventId = eventId,
            tileId = tileId,
            tileVersion = observedVersion ?: 0,
            changes = listOf(
                AssetChangeDto(
                    assetId = assetId,
                    status = "CLAIMING",
                    assetVersion = observedVersion ?: 0,
                ),
            ),
        )
        sessions.sendToTile(eventId, tileId, objectMapper.writeValueAsString(event))
    }

    fun publishState(state: AssetState) {
        val event = AssetChangedBatchEvent(
            eventId = state.eventId,
            tileId = state.tileId,
            tileVersion = state.tileVersion,
            changes = listOf(
                AssetChangeDto(
                    assetId = state.assetId,
                    status = state.status,
                    assetVersion = state.version,
                    holdExpiresAt = state.holdExpiresAt,
                ),
            ),
        )
        sessions.sendToTile(state.eventId, state.tileId, objectMapper.writeValueAsString(event))
    }
}
