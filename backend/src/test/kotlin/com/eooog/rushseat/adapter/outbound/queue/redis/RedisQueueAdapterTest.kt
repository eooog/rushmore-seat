package com.eooog.rushseat.adapter.outbound.queue.redis

import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

@SpringJUnitConfig(RedisQueueAdapterTest.RedisTestConfig::class)
class RedisQueueAdapterTest {
    @Autowired
    lateinit var adapter: RedisQueueAdapter

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    private val performanceId = 1L
    private val memberId = 259L

    @BeforeEach
    fun setUp() {
        flushRedis()
    }

    @Test
    fun `addWaitingMember() should rank members by join order`() {
        adapter.addWaitingMember(performanceId, memberId = 1L)
        adapter.addWaitingMember(performanceId, memberId = 2L)

        assertThat(adapter.getWaitingRank(performanceId, memberId = 1L)).isEqualTo(0L)
        assertThat(adapter.getWaitingRank(performanceId, memberId = 2L)).isEqualTo(1L)
    }

    @Test
    fun `addWaitingMember() should not create a duplicate slot for the same member`() {
        adapter.addWaitingMember(performanceId, memberId)
        adapter.addWaitingMember(performanceId, memberId)

        val popped = adapter.popWaitingMembers(performanceId, limit = 10)

        assertThat(popped).containsExactly(memberId)
    }

    @Test
    fun `popWaitingMembers() should remove members in join order`() {
        adapter.addWaitingMember(performanceId, memberId = 1L)
        adapter.addWaitingMember(performanceId, memberId = 2L)

        val popped = adapter.popWaitingMembers(performanceId, limit = 1)

        assertThat(popped).containsExactly(1L)
        assertThat(adapter.getWaitingRank(performanceId, memberId = 2L)).isEqualTo(0L)
    }

    @Test
    fun `findAdmissionByMember() should return null before admission is saved`() {
        assertThat(adapter.findAdmissionByMember(performanceId, memberId)).isNull()
    }

    @Test
    fun `admit() should populate both the member-keyed and token-keyed admission records`() {
        val expiresAt = Instant.parse("2026-01-01T00:03:00Z")

        adapter.admit(
            performanceId = performanceId,
            memberId = memberId,
            admissionToken = "at_abc",
            expiresAt = expiresAt,
            ttl = Duration.ofMinutes(3),
        )

        val admission = adapter.findAdmissionByMember(performanceId, memberId)
        assertThat(admission).isNotNull
        assertThat(admission!!.admissionToken).isEqualTo("at_abc")
        assertThat(admission.expiresAt).isEqualTo(expiresAt)

        val tokenRecord = adapter.loadAdmissionToken("at_abc")
        assertThat(tokenRecord).isEqualTo(AdmissionTokenRecord(token = "at_abc", performanceId = performanceId, memberId = memberId))
    }

    @Test
    fun `admit() records should expire after ttl`() {
        adapter.admit(
            performanceId = performanceId,
            memberId = memberId,
            admissionToken = "at_abc",
            expiresAt = Instant.now().plusSeconds(1),
            ttl = Duration.ofSeconds(1),
        )

        assertThat(adapter.findAdmissionByMember(performanceId, memberId)).isNotNull

        assertEventually(timeout = Duration.ofSeconds(3)) {
            assertThat(adapter.findAdmissionByMember(performanceId, memberId)).isNull()
            assertThat(adapter.loadAdmissionToken("at_abc")).isNull()
        }
    }

    @Test
    fun `admit() should add the member to occupancy while the admission is still valid`() {
        val expiresAt = Instant.parse("2026-01-01T00:03:00Z")

        adapter.admit(
            performanceId = performanceId,
            memberId = memberId,
            admissionToken = "at_abc",
            expiresAt = expiresAt,
            ttl = Duration.ofMinutes(3),
        )

        assertThat(adapter.countOccupancy(performanceId, expiresAt.minusSeconds(1))).isEqualTo(1L)
    }

    @Test
    fun `countOccupancy() should exclude admissions that have expired by the given instant`() {
        val expiresAt = Instant.parse("2026-01-01T00:03:00Z")

        adapter.admit(
            performanceId = performanceId,
            memberId = memberId,
            admissionToken = "at_abc",
            expiresAt = expiresAt,
            ttl = Duration.ofMinutes(3),
        )

        assertThat(adapter.countOccupancy(performanceId, expiresAt.plusSeconds(1))).isEqualTo(0L)
    }

    @Test
    fun `release() should remove the member from occupancy`() {
        val expiresAt = Instant.parse("2026-01-01T00:03:00Z")

        adapter.admit(
            performanceId = performanceId,
            memberId = memberId,
            admissionToken = "at_abc",
            expiresAt = expiresAt,
            ttl = Duration.ofMinutes(3),
        )
        adapter.release(performanceId, memberId)

        assertThat(adapter.countOccupancy(performanceId, expiresAt.minusSeconds(1))).isEqualTo(0L)
    }

    private fun flushRedis() {
        redisTemplate.execute { connection ->
            connection.serverCommands().flushDb()
            null
        }
    }

    private fun assertEventually(
        timeout: Duration = Duration.ofSeconds(2),
        interval: Duration = Duration.ofMillis(50),
        assertion: () -> Unit,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastFailure: AssertionError? = null

        while (System.nanoTime() < deadline) {
            try {
                assertion()
                return
            } catch (failure: AssertionError) {
                lastFailure = failure
                Thread.sleep(interval.toMillis())
            }
        }

        throw lastFailure ?: AssertionError("Condition was not satisfied within $timeout")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RedisTestConfig {
        @Bean(destroyMethod = "destroy")
        fun redisConnectionFactory(): LettuceConnectionFactory {
            val configuration =
                RedisStandaloneConfiguration(
                    redisContainer.host,
                    redisContainer.getMappedPort(REDIS_PORT),
                )

            return LettuceConnectionFactory(configuration).apply {
                afterPropertiesSet()
            }
        }

        @Bean
        fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): StringRedisTemplate =
            StringRedisTemplate(redisConnectionFactory).apply {
                afterPropertiesSet()
            }

        @Bean
        fun redisQueueAdapter(redisTemplate: StringRedisTemplate): RedisQueueAdapter = RedisQueueAdapter(redisTemplate)
    }

    private class RedisTestContainer :
        GenericContainer<RedisTestContainer>(DockerImageName.parse("redis:7.2-alpine"))

    companion object {
        private const val REDIS_PORT = 6379

        private val redisContainer =
            RedisTestContainer().apply {
                withExposedPorts(REDIS_PORT)
                start()
            }

        @JvmStatic
        @AfterAll
        fun tearDownContainer() {
            redisContainer.stop()
        }
    }
}
