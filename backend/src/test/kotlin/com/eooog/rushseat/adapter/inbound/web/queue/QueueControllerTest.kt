package com.eooog.rushseat.adapter.inbound.web.queue

import com.eooog.rushseat.application.queue.AdmitQueueCommand
import com.eooog.rushseat.application.queue.AdmitQueueResult
import com.eooog.rushseat.application.queue.EnterQueueCommand
import com.eooog.rushseat.application.queue.GetQueueStatusQuery
import com.eooog.rushseat.application.queue.QueueEnterResult
import com.eooog.rushseat.application.queue.QueueStatus
import com.eooog.rushseat.application.queue.QueueStatusResult
import com.eooog.rushseat.application.queue.provided.AdmitQueueUseCase
import com.eooog.rushseat.application.queue.provided.EnterQueueUseCase
import com.eooog.rushseat.application.queue.provided.GetQueueStatusUseCase
import com.eooog.rushseat.application.shared.auth.MemberPrincipal
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class QueueControllerTest {
    private val enterQueueUseCase = FakeEnterQueueUseCase()
    private val getQueueStatusUseCase = FakeGetQueueStatusUseCase()
    private val admitQueueUseCase = FakeAdmitQueueUseCase()

    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(QueueController(enterQueueUseCase, getQueueStatusUseCase, admitQueueUseCase))
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
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
}
