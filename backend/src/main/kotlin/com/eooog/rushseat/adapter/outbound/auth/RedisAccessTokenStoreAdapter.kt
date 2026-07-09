package com.eooog.rushseat.adapter.outbound.auth

import com.eooog.rushseat.application.shared.auth.AccessToken
import com.eooog.rushseat.application.shared.auth.required.AccessTokenStorePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisAccessTokenStoreAdapter(
    private val redis: StringRedisTemplate,
) : AccessTokenStorePort {
    override fun save(
        accessToken: AccessToken,
        memberId: Long,
        ttl: Duration,
    ) {
        redis.opsForValue().set(
            accessTokenKey(accessToken),
            memberId.toString(),
            ttl,
        )
    }

    override fun findMemberId(accessToken: AccessToken): Long? =
        redis
            .opsForValue()
            .get(accessTokenKey(accessToken))
            ?.toLongOrNull()

    private fun accessTokenKey(accessToken: AccessToken): String = "auth:access:${accessToken.value}"
}
