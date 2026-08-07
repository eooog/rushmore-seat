package com.eooog.rushseat.adapter.outbound.queue.redis

import com.eooog.rushseat.application.queue.required.AdmissionRecord
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueStatePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisQueueAdapter(
    private val redis: StringRedisTemplate,
) : QueueStatePort {
    override fun addWaitingMember(
        performanceId: Long,
        memberId: Long,
    ) {
        val sequence =
            redis.opsForValue().increment(sequenceKey(performanceId))
                ?: error("sequence increment failed")
        redis.opsForZSet().add(waitingKey(performanceId), memberId.toString(), sequence.toDouble())
    }

    override fun getWaitingRank(
        performanceId: Long,
        memberId: Long,
    ): Long? = redis.opsForZSet().rank(waitingKey(performanceId), memberId.toString())

    override fun admitNextWaitingMember(
        performanceId: Long,
        admissionToken: String,
        expiresAt: Instant,
        ttl: Duration,
    ): Long? {
        val memberId: String? =
            redis.execute(
                ADMIT_NEXT_WAITING_MEMBER_SCRIPT,
                listOf(waitingKey(performanceId)),
                performanceId.toString(),
                admissionToken,
                expiresAt.toString(),
                expiresAt.toEpochMilli().toString(),
                ttl.seconds.toString(),
            )
        return memberId?.toLongOrNull()
    }

    override fun findAdmissionByMember(
        performanceId: Long,
        memberId: Long,
    ): AdmissionRecord? {
        val values = redis.opsForHash<String, String>().entries(admissionByMemberKey(performanceId, memberId))
        if (values.isEmpty()) return null

        return AdmissionRecord(
            admissionToken = values["admissionToken"] ?: return null,
            expiresAt = values["expiresAt"]?.let(Instant::parse) ?: return null,
        )
    }

    override fun loadAdmissionToken(admissionToken: String): AdmissionTokenRecord? {
        val values = redis.opsForHash<String, String>().entries(admissionTokenKey(admissionToken))
        if (values.isEmpty()) return null

        return AdmissionTokenRecord(
            token = admissionToken,
            performanceId = values["performanceId"]?.toLongOrNull() ?: return null,
            memberId = values["memberId"]?.toLongOrNull() ?: return null,
        )
    }

    override fun countOccupancy(
        performanceId: Long,
        now: Instant,
    ): Long {
        val key = occupancyKey(performanceId)
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now.toEpochMilli().toDouble())
        return redis.opsForZSet().size(key) ?: 0L
    }

    override fun release(
        performanceId: Long,
        memberId: Long,
    ) {
        redis.opsForZSet().remove(occupancyKey(performanceId), memberId.toString())
    }

    private fun waitingKey(performanceId: Long): String = "queue:waiting:$performanceId"

    private fun admissionByMemberKey(
        performanceId: Long,
        memberId: Long,
    ): String = "admission:member:$performanceId:$memberId"

    private fun occupancyKey(performanceId: Long): String = "admission:occupancy:$performanceId"

    private fun sequenceKey(performanceId: Long): String = "queue:seq:$performanceId"

    private fun admissionTokenKey(admissionToken: String): String = "admission:token:$admissionToken"

    companion object {
        // KEYS[1] = queue:waiting:{performanceId}
        // ARGV[1] = performanceId
        // ARGV[2] = admissionToken
        // ARGV[3] = expiresAt (ISO-8601 string, admission record field)
        // ARGV[4] = expiresAt epoch millis (occupancy ZSET score)
        // ARGV[5] = ttlSeconds
        private val ADMIT_NEXT_WAITING_MEMBER_SCRIPT: RedisScript<String> =
            RedisScript.of(
                """
                local popped = redis.call('ZPOPMIN', KEYS[1], 1)
                if #popped == 0 then
                    return false
                end

                local memberId = popped[1]
                local performanceId = ARGV[1]
                local admissionToken = ARGV[2]
                local expiresAtIso = ARGV[3]
                local expiresAtEpochMillis = ARGV[4]
                local ttlSeconds = ARGV[5]

                local tokenKey = 'admission:token:' .. admissionToken
                local memberKey = 'admission:member:' .. performanceId .. ':' .. memberId
                local occupancyKey = 'admission:occupancy:' .. performanceId

                redis.call('HSET', tokenKey, 'performanceId', performanceId, 'memberId', memberId)
                redis.call('EXPIRE', tokenKey, ttlSeconds)

                redis.call('HSET', memberKey, 'admissionToken', admissionToken, 'expiresAt', expiresAtIso)
                redis.call('EXPIRE', memberKey, ttlSeconds)

                redis.call('ZADD', occupancyKey, expiresAtEpochMillis, memberId)

                return memberId
                """.trimIndent(),
                String::class.java,
            )
    }
}
