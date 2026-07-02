package com.eooog.rushseat.adapter.outbound.queue.redis

import com.eooog.rushseat.application.queue.QueueStatus
import com.eooog.rushseat.application.queue.required.AdmissionTokenRecord
import com.eooog.rushseat.application.queue.required.QueueStatePort
import com.eooog.rushseat.application.queue.required.QueueTokenRecord
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
        joinedAtMillis: Long,
    ) {
        redis.opsForZSet().add(
            waitingKey(performanceId),
            memberId.toString(),
            joinedAtMillis.toDouble(),
        )
    }

    override fun getWaitingRank(
        performanceId: Long,
        memberId: Long,
    ): Long? {
        return redis.opsForZSet().rank(waitingKey(performanceId), memberId.toString())
    }

    override fun popWaitingMembers(
        performanceId: Long,
        limit: Int,
    ): List<Long> {
        return redis.opsForZSet()
            .popMin(waitingKey(performanceId), limit.toLong())
            .orEmpty()
            .mapNotNull { it.value?.toLongOrNull() }
    }

    override fun saveQueueToken(
        token: QueueTokenRecord,
        ttl: Duration,
    ) {
        val key = queueTokenKey(token.token)
        redis.opsForHash<String, String>().putAll(
            key,
            mapOf(
                "performanceId" to token.performanceId.toString(),
                "memberId" to token.memberId.toString(),
                "status" to token.status.name,
            ) + optionalAdmissionFields(token),
        )
        redis.expire(key, ttl)
    }

    override fun loadQueueToken(queueToken: String): QueueTokenRecord? {
        val values = redis.opsForHash<String, String>().entries(queueTokenKey(queueToken))
        if (values.isEmpty()) return null

        return QueueTokenRecord(
            token = queueToken,
            performanceId = values["performanceId"]?.toLongOrNull() ?: return null,
            memberId = values["memberId"]?.toLongOrNull() ?: return null,
            status = values["status"]?.let(QueueStatus::valueOf) ?: QueueStatus.WAITING,
            admissionToken = values["admissionToken"],
            admissionExpiresAt = values["admissionExpiresAt"]?.let(Instant::parse),
        )
    }

    override fun saveMemberQueueToken(
        performanceId: Long,
        memberId: Long,
        queueToken: String,
        ttl: Duration,
    ) {
        redis.opsForValue().set(
            memberQueueTokenKey(performanceId, memberId),
            queueToken,
            ttl,
        )
    }

    override fun findMemberQueueToken(
        performanceId: Long,
        memberId: Long,
    ): String? {
        return redis.opsForValue().get(memberQueueTokenKey(performanceId, memberId))
    }

    override fun markQueueTokenAdmitted(
        queueToken: String,
        admissionToken: String,
        expiresAt: Instant,
    ) {
        redis.opsForHash<String, String>().putAll(
            queueTokenKey(queueToken),
            mapOf(
                "status" to QueueStatus.ADMITTED.name,
                "admissionToken" to admissionToken,
                "admissionExpiresAt" to expiresAt.toString(),
            ),
        )
    }

    override fun saveAdmissionToken(
        token: AdmissionTokenRecord,
        ttl: Duration,
    ) {
        val key = admissionTokenKey(token.token)
        redis.opsForHash<String, String>().putAll(
            key,
            mapOf(
                "performanceId" to token.performanceId.toString(),
                "memberId" to token.memberId.toString(),
            ),
        )
        redis.expire(key, ttl)
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

    private fun optionalAdmissionFields(token: QueueTokenRecord): Map<String, String> {
        val admissionToken = token.admissionToken
        val admissionExpiresAt = token.admissionExpiresAt
        if (admissionToken == null || admissionExpiresAt == null) {
            return emptyMap()
        }

        return mapOf(
            "admissionToken" to admissionToken,
            "admissionExpiresAt" to admissionExpiresAt.toString(),
        )
    }

    private fun waitingKey(performanceId: Long): String = "queue:waiting:$performanceId"
    private fun queueTokenKey(queueToken: String): String = "queue:token:$queueToken"
    private fun memberQueueTokenKey(performanceId: Long, memberId: Long): String = "queue:member-token:$performanceId:$memberId"
    private fun admissionTokenKey(admissionToken: String): String = "admission:token:$admissionToken"
}
