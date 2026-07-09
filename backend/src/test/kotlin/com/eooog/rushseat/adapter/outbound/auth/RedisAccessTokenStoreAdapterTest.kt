package com.eooog.rushseat.adapter.outbound.auth

import com.eooog.rushseat.application.shared.auth.AccessToken
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

@SpringJUnitConfig(RedisAccessTokenStoreAdapterTest.RedisTestConfig::class)
class RedisAccessTokenStoreAdapterTest {
    @Autowired
    lateinit var adapter: RedisAccessTokenStoreAdapter

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    private val memberId = 259L
    private val accessToken = AccessToken.parse("acc_${"a".repeat(43)}")

    @BeforeEach
    fun setUp() {
        flushRedis()
    }

    @Test
    fun `save() should store access token member mapping`() {
        adapter.save(
            accessToken = accessToken,
            memberId = memberId,
            ttl = Duration.ofMinutes(10),
        )

        val foundMemberId = adapter.findMemberId(accessToken)

        assertThat(foundMemberId).isEqualTo(memberId)
    }

    @Test
    fun `findMemberId() should return null when token does not exist`() {
        val unknownToken = AccessToken.parse("acc_${"b".repeat(43)}")

        val foundMemberId = adapter.findMemberId(unknownToken)

        assertThat(foundMemberId).isNull()
    }

    @Test
    fun `findMemberId() should return null after ttl expires`() {
        adapter.save(
            accessToken = accessToken,
            memberId = memberId,
            ttl = Duration.ofMillis(100),
        )

        assertThat(adapter.findMemberId(accessToken)).isEqualTo(memberId)

        assertEventually {
            assertThat(adapter.findMemberId(accessToken)).isNull()
        }
    }

    @Test
    fun `save() should overwrite member mapping for same access token`() {
        adapter.save(
            accessToken = accessToken,
            memberId = 1L,
            ttl = Duration.ofMinutes(10),
        )

        adapter.save(
            accessToken = accessToken,
            memberId = 2L,
            ttl = Duration.ofMinutes(10),
        )

        val foundMemberId = adapter.findMemberId(accessToken)

        assertThat(foundMemberId).isEqualTo(2L)
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
        fun redisAccessTokenStoreAdapter(redisTemplate: StringRedisTemplate): RedisAccessTokenStoreAdapter =
            RedisAccessTokenStoreAdapter(redisTemplate)
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
