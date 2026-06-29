package com.eooog.rushseat.seat

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SeatController(
    private val seatService: SeatService,
) {
    @GetMapping("/events/{eventId}/sectors/summary")
    fun sectorSummary(
        @PathVariable eventId: Long,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): SectorSummaryResponse {
        return seatService.getSectorSummary(eventId, authorization)
    }

    @GetMapping("/events/{eventId}/tiles/{tileId}/assets")
    fun tileSnapshot(
        @PathVariable eventId: Long,
        @PathVariable tileId: String,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): TileSnapshotResponse {
        return seatService.getTileSnapshot(eventId, tileId, authorization)
    }
}
