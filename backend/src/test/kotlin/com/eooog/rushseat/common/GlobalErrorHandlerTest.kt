package com.eooog.rushseat.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class GlobalErrorHandlerTest {
    private val handler = GlobalErrorHandler()

    @Test
    fun `handleUnexpected() should render JSON when the response has not been committed yet`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val result = handler.handleUnexpected(RuntimeException("boom"), request, response)

        assertThat(result).isNotNull
        assertThat(result!!.statusCode.value()).isEqualTo(500)
        assertThat(result.body?.code).isEqualTo("INTERNAL_SERVER_ERROR")
    }

    @Test
    fun `handleUnexpected() should return null without touching the body once the response is committed`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        response.contentType = MediaType.TEXT_EVENT_STREAM_VALUE
        response.flushBuffer()

        val result = handler.handleUnexpected(RuntimeException("boom"), request, response)

        assertThat(result).isNull()
    }

    @Test
    fun `handleApiError() should return null once the response is committed`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        response.contentType = MediaType.TEXT_EVENT_STREAM_VALUE
        response.flushBuffer()

        val result =
            handler.handleApiError(
                ApiError(code = "NOT_FOUND", message = "performance not found", status = HttpStatus.NOT_FOUND),
                request,
                response,
            )

        assertThat(result).isNull()
    }

    // 아직 커밋 전인데 produces가 SSE로 고정된 매핑에서 예외가 난 경우의 회귀 테스트.
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(FailingSseController())
            .setControllerAdvice(GlobalErrorHandler())
            .build()

    @Test
    fun `an unexpected exception from a produces-locked SSE endpoint should still render as JSON before commit`() {
        mockMvc
            .perform(get("/test/sse-failure"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
    }

    @Test
    fun `an ApiError from a produces-locked SSE endpoint should still render as JSON before commit`() {
        mockMvc
            .perform(get("/test/sse-api-error"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("performance not found"))
    }

    @RestController
    private class FailingSseController {
        @GetMapping("/test/sse-failure", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun sseFailure(): Nothing = throw IllegalStateException("boom")

        @GetMapping("/test/sse-api-error", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun sseApiError(): Nothing =
            throw ApiError(code = "NOT_FOUND", message = "performance not found", status = HttpStatus.NOT_FOUND)
    }
}
