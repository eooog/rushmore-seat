package com.eooog.rushseat.adapter.outbound.queue.redis

import com.eooog.rushseat.application.queue.required.AdmissionRecord
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueStatePort
import org.springframework.data.redis.connection.StringRedisConnection
import org.springframework.data.redis.core.StringRedisTemplate
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

    override fun popWaitingMembers(
        performanceId: Long,
        limit: Int,
    ): List<Long> =
        redis
            .opsForZSet()
            .popMin(waitingKey(performanceId), limit.toLong())
            .orEmpty()
            .mapNotNull { it.value?.toLongOrNull() }

    override fun admit(
        performanceId: Long,
        memberId: Long,
        admissionToken: String,
        expiresAt: Instant,
        ttl: Duration,
    ) {
        redis.executePipelined { connection ->
            val stringConn = connection as StringRedisConnection
            val tokenKey = admissionTokenKey(admissionToken)
            val memberKey = admissionByMemberKey(performanceId, memberId)

            stringConn.hSet(tokenKey, "performanceId", performanceId.toString())
            stringConn.hSet(tokenKey, "memberId", memberId.toString())
            stringConn.expire(tokenKey, ttl.seconds)

            stringConn.hSet(memberKey, "admissionToken", admissionToken)
            stringConn.hSet(memberKey, "expiresAt", expiresAt.toString())
            stringConn.expire(memberKey, ttl.seconds)

            null
        }
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

    private fun waitingKey(performanceId: Long): String = "queue:waiting:$performanceId"

    private fun admissionByMemberKey(
        performanceId: Long,
        memberId: Long,
    ): String = "admission:member:$performanceId:$memberId"

    private fun sequenceKey(performanceId: Long): String = "queue:seq:$performanceId"

    private fun admissionTokenKey(admissionToken: String): String = "admission:token:$admissionToken"
}
