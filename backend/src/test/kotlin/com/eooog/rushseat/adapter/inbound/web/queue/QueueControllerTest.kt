package com.eooog.rushseat.adapter.inbound.web.queue

import com.eooog.rushseat.adapter.outbound.realtime.sse.SseQueueEventPublisher
import com.eooog.rushseat.application.queue.AdmitQueueCommand
import com.eooog.rushseat.application.queue.AdmitQueueResult
import com.eooog.rushseat.application.queue.AdmittedMember
import com.eooog.rushseat.application.queue.EnterQueueCommand
import com.eooog.rushseat.application.queue.GetQueueStatusQuery
import com.eooog.rushseat.application.queue.LeaveQueueCommand
import com.eooog.rushseat.application.queue.QueueEnterResult
import com.eooog.rushseat.application.queue.QueueStatus
import com.eooog.rushseat.application.queue.QueueStatusResult
import com.eooog.rushseat.application.queue.ValidateAdmissionCommand
import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.queue.provided.LeaveQueueUseCase
import com.eooog.rushseat.application.queue.provided.ValidateAdmissionUseCase
import com.eooog.rushseat.application.shared.auth.MemberPrincipal
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockAsyncContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class QueueControllerTest {
    private val enterQueueUseCase = FakeEnterQueueUseCase()
    private val getQueueStatusUseCase = FakeGetQueueStatusUseCase()
    private val admitQueueUseCase = FakeAdmitQueueUseCase()
    private val leaveQueueUseCase = FakeLeaveQueueUseCase()
    private val validateAdmissionUseCase = FakeValidateAdmissionUseCase()
    private val sseQueueEventPublisher = SseQueueEventPublisher()

    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                QueueController(
                    enterQueueUseCase,
                    getQueueStatusUseCase,
                    admitQueueUseCase,
                    leaveQueueUseCase,
                    validateAdmissionUseCase,
                    sseQueueEventPublisher,
                ),
            ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()

    private val performanceId = 1L
    private val memberId = 259L

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(MemberPrincipal(memberId), null, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `enter() should derive memberId from the authenticated principal without a request body`() {
        val joinedAt = Instant.parse("2026-01-01T00:00:00Z")
        enterQueueUseCase.result = QueueEnterResult(status = QueueStatus.WAITING, joinedAt = joinedAt)

        mockMvc
            .perform(post("/performances/{performanceId}/queue", performanceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.joinedAt").value(joinedAt.toString()))

        assertThat(enterQueueUseCase.lastCommand?.performanceId).isEqualTo(performanceId)
        assertThat(enterQueueUseCase.lastCommand?.memberId).isEqualTo(memberId)
    }

    @Test
    fun `me() should derive memberId from the authenticated principal without a queueToken parameter`() {
        getQueueStatusUseCase.result =
            QueueStatusResult(
                status = QueueStatus.ADMITTED,
                rank = null,
                estimatedWaitSeconds = null,
                admissionToken = "at_abc",
                expiresAt = Instant.parse("2026-01-01T00:03:00Z"),
            )

        mockMvc
            .perform(get("/performances/{performanceId}/queue/me", performanceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ADMITTED"))
            .andExpect(jsonPath("$.admissionToken").value("at_abc"))

        assertThat(getQueueStatusUseCase.lastQuery)
            .isEqualTo(GetQueueStatusQuery(performanceId = performanceId, memberId = memberId))
    }

    @Test
    fun `leave() should release the authenticated member's occupancy without an admissionToken`() {
        mockMvc
            .perform(post("/performances/{performanceId}/queue/leave", performanceId))
            .andExpect(status().isOk)

        assertThat(leaveQueueUseCase.lastCommand)
            .isEqualTo(LeaveQueueCommand(performanceId = performanceId, memberId = memberId))
    }

    @Test
    fun `stream() should register an SSE connection for the authenticated principal`() {
        mockMvc
            .perform(get("/performances/{performanceId}/queue/stream", performanceId))
            .andExpect(request().asyncStarted())

        assertThat(sseQueueEventPublisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `stream() connection should be cleaned up when the client disconnects`() {
        val mvcResult =
            mockMvc
                .perform(get("/performances/{performanceId}/queue/stream", performanceId))
                .andExpect(request().asyncStarted())
                .andReturn()

        assertThat(sseQueueEventPublisher.connectionCount(performanceId)).isEqualTo(1)

        (mvcResult.request.asyncContext as MockAsyncContext).complete()

        assertThat(sseQueueEventPublisher.connectionCount(performanceId)).isEqualTo(0)
    }

    @Test
    fun `a stale disconnect should not evict a newer connection registered for the same member`() {
        val staleResult =
            mockMvc
                .perform(get("/performances/{performanceId}/queue/stream", performanceId))
                .andExpect(request().asyncStarted())
                .andReturn()

        mockMvc
            .perform(get("/performances/{performanceId}/queue/stream", performanceId))
            .andExpect(request().asyncStarted())

        assertThat(sseQueueEventPublisher.connectionCount(performanceId)).isEqualTo(1)

        (staleResult.request.asyncContext as MockAsyncContext).complete()

        assertThat(sseQueueEventPublisher.connectionCount(performanceId)).isEqualTo(1)
    }

    @Test
    fun `goal() should pass the admission token header and principal through to requireAdmitted()`() {
        validateAdmissionUseCase.result =
            AdmittedMember(performanceId = performanceId, memberId = memberId, admissionToken = "at_abc")

        mockMvc
            .perform(post("/performances/{performanceId}/queue/goal", performanceId).header("X-Admission-Token", "at_abc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.admissionToken").value("at_abc"))

        assertThat(validateAdmissionUseCase.lastCommand)
            .isEqualTo(ValidateAdmissionCommand(performanceId = performanceId, memberId = memberId, admissionToken = "at_abc"))
    }

    @Test
    fun `goal() should return 401 when called without an admission token`() {
        validateAdmissionUseCase.shouldReject = true

        mockMvc
            .perform(post("/performances/{performanceId}/queue/goal", performanceId))
            .andExpect(status().isUnauthorized)

        assertThat(validateAdmissionUseCase.lastCommand?.admissionToken).isEmpty()
    }

    private class FakeEnterQueueUseCase : EnterQueueUseCase {
        var result = QueueEnterResult(status = QueueStatus.WAITING, joinedAt = Instant.EPOCH)
        var lastCommand: EnterQueueCommand? = null

        override fun enter(command: EnterQueueCommand): QueueEnterResult {
            lastCommand = command
            return result
        }
    }

    private class FakeGetQueueStatusUseCase : GetQueueStatusUseCase {
        var result =
            QueueStatusResult(
                status = QueueStatus.WAITING,
                rank = null,
                estimatedWaitSeconds = null,
                admissionToken = null,
                expiresAt = null,
            )
        var lastQuery: GetQueueStatusQuery? = null

        override fun getStatus(query: GetQueueStatusQuery): QueueStatusResult {
            lastQuery = query
            return result
        }
    }

    private class FakeAdmitQueueUseCase : AdmitQueueUseCase {
        override fun admit(command: AdmitQueueCommand): AdmitQueueResult = AdmitQueueResult(admittedCount = 0, admissions = emptyList())
    }

    private class FakeLeaveQueueUseCase : LeaveQueueUseCase {
        var lastCommand: LeaveQueueCommand? = null

        override fun leave(command: LeaveQueueCommand) {
            lastCommand = command
        }
    }

    private class FakeValidateAdmissionUseCase : ValidateAdmissionUseCase {
        var result = AdmittedMember(performanceId = 0, memberId = 0, admissionToken = "")
        var shouldReject = false
        var lastCommand: ValidateAdmissionCommand? = null

        override fun requireAdmitted(command: ValidateAdmissionCommand): AdmittedMember {
            lastCommand = command
            if (shouldReject) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admission token is invalid or expired")
            }
            return result
        }
    }
}
