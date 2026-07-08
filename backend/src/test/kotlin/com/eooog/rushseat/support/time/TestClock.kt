package com.eooog.rushseat.support.time

import java.time.*
import java.util.concurrent.atomic.AtomicReference

class TestClock private constructor(
    private val initialInstant: Instant,
    private val currentInstant: AtomicReference<Instant>,
    private val zoneId: ZoneId,
) : Clock() {

    constructor(
        initialInstant: Instant,
        zoneId: ZoneId = ZoneOffset.UTC,
    ) : this(
        initialInstant = initialInstant,
        currentInstant = AtomicReference(initialInstant),
        zoneId = zoneId,
    )

    override fun getZone(): ZoneId {
        return zoneId
    }

    override fun withZone(zone: ZoneId): Clock {
        return TestClock(
            initialInstant = initialInstant,
            currentInstant = currentInstant,
            zoneId = zone,
        )
    }

    override fun instant(): Instant {
        return currentInstant.get()
    }

    fun reset() {
        currentInstant.set(initialInstant)
    }

    fun set(instant: Instant) {
        currentInstant.set(instant)
    }

    fun advance(duration: Duration) {
        require(!duration.isNegative) { "duration must not be negative" }

        currentInstant.updateAndGet { current ->
            current.plus(duration)
        }
    }
}