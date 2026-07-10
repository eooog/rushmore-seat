package com.eooog.rushseat.domain.performance

import com.eooog.rushseat.domain.seatmap.Seat
import com.eooog.rushseat.domain.seatmap.SeatMap
import com.eooog.rushseat.domain.show.Show
import com.eooog.rushseat.domain.venue.Hall
import java.time.Instant
import java.time.temporal.ChronoUnit

object PerformanceFixture {
    fun performance(
        show: Show,
        hall: Hall,
        seatMap: SeatMap,
        startsAt: Instant,
        endsAt: Instant = startsAt.plus(show.runningMinutes?.toLong() ?: 0L, ChronoUnit.MINUTES),
        salesOpenAt: Instant,
        salesCloseAt: Instant,
        status: PerformanceStatus = PerformanceStatus.SCHEDULED,
        salesStatus: PerformanceSalesStatus = PerformanceSalesStatus.ON_SALE,
    ): Performance =
        Performance.create(
            show = show,
            hall = hall,
            seatMap = seatMap,
            startsAt = startsAt,
            endsAt = endsAt,
            salesOpenAt = salesOpenAt,
            salesCloseAt = salesCloseAt,
            status = status,
            salesStatus = salesStatus,
        )

    fun performanceSeat(
        performance: Performance,
        seat: Seat,
        status: PerformanceSeatStatus = PerformanceSeatStatus.AVAILABLE,
    ) = PerformanceSeat.create(
        performance = performance,
        seat = seat,
        status = status,
    )
}
