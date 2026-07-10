package com.eooog.rushseat.domain.seatmap

import com.eooog.rushseat.domain.venue.Hall

object SeatMapFixture {
    fun seatMap(
        hall: Hall,
        name: String,
        version: Int,
        status: SeatMapStatus = SeatMapStatus.ACTIVE,
    ): SeatMap =
        SeatMap.create(
            hall = hall,
            name = name,
            version = version,
            status = status,
        )

    fun tile(
        seatMap: SeatMap,
        sector: Sector,
        code: String = "Tile 1",
        name: String = "Tile 1",
        rowStartNo: Int = 1,
        rowEndNo: Int = 10,
        colStartNo: Int = 1,
        colEndNo: Int = 10,
        seatCount: Int = 100,
    ) = Tile.create(
        seatMap = seatMap,
        sector = sector,
        code = code,
        name = name,
        rowStartNo = rowStartNo,
        rowEndNo = rowEndNo,
        colStartNo = colStartNo,
        colEndNo = colEndNo,
        seatCount = seatCount,
    )

    fun sector(
        seatMap: SeatMap,
        code: String = "Sector A",
        name: String = "Sector A",
        sortOrder: Int = 0,
    ): Sector =
        Sector.create(
            seatMap = seatMap,
            code = code,
            name = name,
            sortOrder = sortOrder,
        )

    fun seat(
        seatMap: SeatMap,
        sector: Sector,
        tile: Tile,
        code: String = "1-1",
        rowLabel: String? = null,
        colLabel: String? = null,
        rowNo: Int = 1,
        colNo: Int = 1,
    ): Seat =
        Seat.create(
            seatMap = seatMap,
            sector = sector,
            tile = tile,
            code = code,
            rowLabel = rowLabel,
            colLabel = colLabel,
            rowNo = rowNo,
            colNo = colNo,
        )
}
