package com.eooog.rushseat.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.HandlerMapping

@RestControllerAdvice
class GlobalErrorHandler {
    @ExceptionHandler(ApiError::class)
    fun handleApiError(
        error: ApiError,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ErrorResponse>? {
        if (isAlreadyCommitted(response, error)) return null
        clearProducibleMediaTypes(request)
        return ResponseEntity
            .status(error.status)
            .body(ErrorResponse(code = error.code, message = error.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        error: MethodArgumentNotValidException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ErrorResponse>? {
        if (isAlreadyCommitted(response, error)) return null
        clearProducibleMediaTypes(request)
        val message =
            error.bindingResult.fieldErrors
                .firstOrNull()
                ?.defaultMessage ?: "Invalid request"
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        error: Exception,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ErrorResponse>? {
        if (isAlreadyCommitted(response, error)) return null
        clearProducibleMediaTypes(request)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "INTERNAL_SERVER_ERROR", message = "Unexpected server error"))
    }

    // SSE 같은 스트리밍 응답은 헤더/데이터가 이미 클라이언트로 나간 뒤 async 재디스패치 중에
    // 예외가 날 수 있다. 이 시점엔 response.isCommitted()가 true라서 Content-Type을
    // text/event-stream에서 다른 값으로 바꿀 방법이 없다 — 시도하면 이 어드바이스 자체가
    // HttpMessageNotWritableException("No converter ... with preset Content-Type")으로 다시
    // 터진다. null을 반환하면 Spring MVC가 바디 렌더링 자체를 스킵하므로 그 실패를 피한다.
    private fun isAlreadyCommitted(
        response: HttpServletResponse,
        error: Throwable,
    ): Boolean {
        if (!response.isCommitted) return false
        log.warn("Response already committed; cannot render error body for {}", error.toString())
        return true
    }

    // 응답이 아직 커밋 전이더라도, produces가 특정 미디어 타입(예: text/event-stream)으로
    // 고정된 매핑에서 예외가 나면 그 매핑 정보가 요청 속성에 남아있어서 이 어드바이스가
    // JSON으로 응답하려 할 때 같은 종류의 에러로 실패할 수 있다. 원래 매핑의 producible
    // media type 제약을 지워서 이 어드바이스가 자유롭게 JSON을 쓸 수 있게 한다.
    private fun clearProducibleMediaTypes(request: HttpServletRequest) {
        request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE)
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalErrorHandler::class.java)
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
)
