package com.eooog.rushseat.domain.reservation

import com.eooog.rushseat.domain.DomainTestSupport
import com.eooog.rushseat.domain.member.Member
import com.eooog.rushseat.domain.member.MemberFixture
import com.eooog.rushseat.domain.performance.Performance
import com.eooog.rushseat.domain.performance.PerformanceFixture
import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceSeat
import com.eooog.rushseat.domain.performance.PerformanceStatus
import com.eooog.rushseat.domain.seatmap.Seat
import com.eooog.rushseat.domain.seatmap.SeatMap
import com.eooog.rushseat.domain.seatmap.SeatMapFixture
import com.eooog.rushseat.domain.seatmap.SeatMapStatus
import com.eooog.rushseat.domain.seatmap.Sector
import com.eooog.rushseat.domain.seatmap.Tile
import com.eooog.rushseat.domain.show.Show
import com.eooog.rushseat.domain.show.ShowFixture
import com.eooog.rushseat.domain.venue.Hall
import com.eooog.rushseat.domain.venue.Venue
import com.eooog.rushseat.domain.venue.VenueFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReservationTest : DomainTestSupport() {
    private val holdTimeout = Duration.ofMinutes(30)

    private val venue: Venue = VenueFixture.venue()
    private val hall: Hall =
        VenueFixture.hall(
            venue = venue,
            name = "hall 0",
            capacity = 1000,
        )
    private val show: Show = ShowFixture.show()
    private val seatMap: SeatMap =
        SeatMapFixture.seatMap(
            hall = hall,
            name = "type A",
            version = 1,
            status = SeatMapStatus.ACTIVE,
        )

    private val sector: Sector =
        SeatMapFixture.sector(
            seatMap = seatMap,
        )
    private val tile: Tile =
        SeatMapFixture.tile(
            seatMap = seatMap,
            sector = sector,
        )
    private val seat: Seat =
        SeatMapFixture.seat(
            seatMap = seatMap,
            sector = sector,
            tile = tile,
        )
    private val member: Member = MemberFixture.member()

    private lateinit var reservation: Reservation
    private lateinit var performance: Performance
    private lateinit var performanceSeat: PerformanceSeat

    @BeforeEach
    fun setUp() {
        performance =
            PerformanceFixture.performance(
                show = show,
                hall = hall,
                seatMap = seatMap,
                startsAt = clock.instant().plus(7, ChronoUnit.DAYS),
                salesOpenAt = clock.instant().plus(60, ChronoUnit.MINUTES),
                salesCloseAt = clock.instant().plus(180, ChronoUnit.MINUTES),
                status = PerformanceStatus.SCHEDULED,
                salesStatus = PerformanceSalesStatus.BEFORE_SALE,
            )

        performanceSeat =
            PerformanceFixture.performanceSeat(
                performance = performance,
                seat = seat,
            )

        reservation =
            ReservationFixture.reservation(
                performance = performance,
                performanceSeat = performanceSeat,
                member = member,
                idempotencyKey = UUID.randomUUID().toString(),
                expiresAt = clock.instant().plus(holdTimeout),
            )
    }

    @Test
    fun `createHeld should fail when performance seat belongs to different performance`() {
        val differentPerformance =
            PerformanceFixture.performance(
                show = show,
                hall = hall,
                seatMap = seatMap,
                startsAt = clock.instant().plus(7, ChronoUnit.DAYS),
                salesOpenAt = clock.instant().plus(60, ChronoUnit.MINUTES),
                salesCloseAt = clock.instant().plus(180, ChronoUnit.MINUTES),
                status = PerformanceStatus.SCHEDULED,
                salesStatus = PerformanceSalesStatus.BEFORE_SALE,
            )

        val idempotencyKey = UUID.randomUUID().toString()

        assertThatThrownBy {
            createHeldReservation(
                performance = differentPerformance,
                idempotencyKey = idempotencyKey,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("예약 좌석은 동일한 공연 회차에 속해야 합니다")
    }

    @Test
    fun `createHeld should fail when idempotency key is blank`() {
        assertThatThrownBy {
            createHeldReservation(
                performance = performance,
                idempotencyKey = "  ",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("멱등키는 비어 있을 수 없습니다")
    }

    @Test
    fun `createHeld should accept idempotency key when trimmed length is 120 characters`() {
        val normalizedIdempotencyKey = "A".repeat(120)
        val idempotencyKey = "  $normalizedIdempotencyKey  "

        val reservation =
            createHeldReservation(
                performance = performance,
                idempotencyKey = idempotencyKey,
            )

        assertThat(idempotencyKey.length).isGreaterThan(120)
        assertThat(reservation.idempotencyKey).isEqualTo(normalizedIdempotencyKey)
    }

    @Test
    fun `createHeld should fail when idempotency key exceeds 120 characters`() {
        val idempotencyKey = "A".repeat(121)

        assertThatThrownBy {
            createHeldReservation(
                performance = performance,
                idempotencyKey = idempotencyKey,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("멱등키는 120자를 초과할 수 없습니다")
    }

    @Test
    fun `createHeld should create reservation in held status with initial version`() {
        val idempotencyKey = UUID.randomUUID().toString()

        val reservation =
            Reservation.createHeld(
                performance = performance,
                performanceSeat = performanceSeat,
                member = member,
                idempotencyKey = idempotencyKey,
                expiresAt = clock.instant().plus(holdTimeout),
            )

        assertThat(reservation.status).isEqualTo(ReservationStatus.HELD)
        assertThat(reservation.version).isEqualTo(0)
    }

    @Test
    fun `expire should fail when reservation is not held`() {
        reservation.cancel()

        assertThatThrownBy {
            reservation.expire(clock.instant())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("임시 선점 상태의 예약이 아닙니다")
    }

    @Test
    fun `expire should fail before expiration time`() {
        assertThatThrownBy {
            reservation.expire(clock.instant())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("아직 예약 만료 시간이 지나지 않았습니다")

        assertThat(reservation.status).isEqualTo(ReservationStatus.HELD)
    }

    @Test
    fun `expire should expire reservation after expiration time`() {
        assertThat(reservation.isExpired(clock.instant())).isFalse

        clock.advance(holdTimeout)
        val now = clock.instant()

        reservation.expire(now)

        assertThat(reservation.status).isEqualTo(ReservationStatus.EXPIRED)
        assertThat(reservation.isExpired(now)).isTrue
    }

    @Test
    fun `confirm should fail when reservation is not held`() {
        reservation.cancel()

        assertThatThrownBy {
            reservation.confirm(clock.instant())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("임시 선점 상태의 예약이 아닙니다")
    }

    @Test
    fun `confirm should fail when reservation is expired`() {
        clock.advance(holdTimeout)
        val now = clock.instant()

        assertThat(reservation.isExpired(now)).isTrue

        assertThatThrownBy {
            reservation.confirm(now)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("만료된 예약은 확정할 수 없습니다")

        assertThat(reservation.status).isEqualTo(ReservationStatus.HELD)
        assertThat(reservation.confirmedAt).isNull()
    }

    @Test
    fun `confirm should change status to confirmed and set confirmedAt`() {
        val now = clock.instant()

        reservation.confirm(now)

        assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(reservation.confirmedAt).isEqualTo(now)
    }

    @Test
    fun `cancel should change status to cancelled`() {
        reservation.cancel()

        assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELLED)
    }

    @Test
    fun `cancel should fail when reservation is not held`() {
        reservation.confirm(clock.instant())

        assertThatThrownBy {
            reservation.cancel()
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("임시 선점 상태의 예약이 아닙니다")
    }

    private fun createHeldReservation(
        performance: Performance,
        idempotencyKey: String,
    ): Reservation =
        Reservation.createHeld(
            performance = performance,
            performanceSeat = performanceSeat,
            member = member,
            idempotencyKey = idempotencyKey,
            expiresAt = clock.instant().plus(holdTimeout),
        )
}
