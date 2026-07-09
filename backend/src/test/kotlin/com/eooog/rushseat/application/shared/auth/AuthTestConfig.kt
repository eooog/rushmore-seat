package com.eooog.rushseat.application.shared.auth

import com.eooog.rushseat.application.shared.auth.required.AccessTokenStorePort
import com.eooog.rushseat.support.time.TestClock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val TEST_INITIAL_INSTANT = Instant.parse("2026-01-01T00:00:00Z")
private val TEST_ACCESS_TOKEN_TTL = Duration.ofSeconds(600)

@TestConfiguration(proxyBeanMethods = false)
class AuthTestConfig {
    @Bean
    @Primary
    fun testClock(): TestClock = TestClock(TEST_INITIAL_INSTANT)

    @Bean
    fun fakeAccessTokenStorePort(clock: Clock): AccessTokenStorePort = FakeAccessTokenStorePort(clock)

    @Bean
    fun accessTokenGenerator(): AccessTokenGenerator = AccessTokenGenerator()

    @Bean
    fun authService(
        accessTokenGenerator: AccessTokenGenerator,
        accessTokenStorePort: AccessTokenStorePort,
    ): AuthService =
        AuthService(
            accessTokenGenerator = accessTokenGenerator,
            accessTokenStorePort = accessTokenStorePort,
            accessTokenTtl = TEST_ACCESS_TOKEN_TTL,
        )

    class FakeAccessTokenStorePort(
        private val clock: Clock,
    ) : AccessTokenStorePort {
        private val store = mutableMapOf<AccessToken, Entry>()

        override fun save(
            accessToken: AccessToken,
            memberId: Long,
            ttl: Duration,
        ) {
            store[accessToken] =
                Entry(
                    memberId = memberId,
                    expiredAt = clock.instant().plus(ttl),
                )
        }

        override fun findMemberId(accessToken: AccessToken): Long? {
            val entry = store[accessToken] ?: return null

            if (!entry.expiredAt.isAfter(clock.instant())) {
                store.remove(accessToken)
                return null
            }

            return entry.memberId
        }

        fun clear() {
            store.clear()
        }

        private data class Entry(
            val memberId: Long,
            val expiredAt: Instant,
        )
    }
}
