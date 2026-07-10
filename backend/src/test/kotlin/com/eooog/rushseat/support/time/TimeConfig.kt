package com.eooog.rushseat.support.time

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Instant

private val TEST_INITIAL_INSTANT = Instant.parse("2026-01-01T00:00:00Z")

@TestConfiguration(proxyBeanMethods = false)
class TimeConfig {
    @Bean
    @Primary
    fun testClock(): TestClock = TestClock(TEST_INITIAL_INSTANT)
}
