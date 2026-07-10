package com.eooog.rushseat.domain

import com.eooog.rushseat.support.time.TestClock
import org.junit.jupiter.api.BeforeEach
import java.time.Instant

private val TEST_INITIAL_INSTANT = Instant.parse("2026-01-01T00:00:00Z")

abstract class DomainTestSupport {
    lateinit var clock: TestClock

    @BeforeEach
    fun initializeClock() {
        clock = TestClock(TEST_INITIAL_INSTANT)
    }
}
