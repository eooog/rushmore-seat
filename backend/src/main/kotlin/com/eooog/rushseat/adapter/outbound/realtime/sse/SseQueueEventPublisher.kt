package com.eooog.rushseat.adapter.outbound.realtime.sse

import com.eooog.rushseat.application.queue.required.QueueEventPort
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class SseQueueEventPublisher : QueueEventPort {
    private val connections = ConcurrentHashMap<Long, ConcurrentHashMap<Long, SseEmitter>>()

    fun register(
        performanceId: Long,
        memberId: Long,
    ): SseEmitter {
        val emitter = SseEmitter(0L)
        connections.computeIfAbsent(performanceId) { ConcurrentHashMap() }[memberId] = emitter

        val cleanup: () -> Unit = { connections[performanceId]?.remove(memberId, emitter); Unit }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup() }

        // Spring/Tomcat buffer the response and don't flush headers until the first write, so a
        // client connecting to a quiet queue (no admits yet) gets no bytes at all — not even
        // headers — until something is actually broadcast. That looks like a hung/failed
        // connection to the client. Sending an SSE comment immediately forces the headers to
        // commit right away; ResponseBodyEmitter explicitly supports sending before the
        // container has called initialize() by queueing into earlySendAttempts.
        runCatching {
            emitter.send(SseEmitter.event().comment("connected"))
        }

        return emitter
    }

    override fun broadcastProgress(
        performanceId: Long,
        admittedCount: Int,
    ) {
        if (admittedCount <= 0) return

        connections[performanceId]?.values?.forEach { emitter ->
            runCatching {
                emitter.send(SseEmitter.event().name("progress").data(mapOf("admitted" to admittedCount)))
            }
        }
    }

    override fun notifyAdmitted(
        performanceId: Long,
        memberId: Long,
        admissionToken: String,
        expiresAt: Instant,
    ) {
        val emitter = connections[performanceId]?.remove(memberId) ?: return

        runCatching {
            emitter.send(
                SseEmitter
                    .event()
                    .name("admitted")
                    .data(mapOf("admissionToken" to admissionToken, "expiresAt" to expiresAt)),
            )
            emitter.complete()
        }
    }

    fun connectionCount(performanceId: Long): Int = connections[performanceId]?.size ?: 0
}
