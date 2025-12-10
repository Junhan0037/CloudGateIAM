package com.cloudgate.iam.auth.web

import com.cloudgate.iam.auth.web.dto.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 인증/입력 검증 오류를 API 친화적인 형태로 매핑
 */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.message, request)

    @ExceptionHandler(LockedException::class)
    fun handleLocked(ex: LockedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.LOCKED, "ACCOUNT_LOCKED", ex.message, request)

    @ExceptionHandler(DisabledException::class)
    fun handleDisabled(ex: DisabledException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", ex.message, request)

    @ExceptionHandler(MethodArgumentNotValidException::class, ConstraintViolationException::class, IllegalArgumentException::class)
    fun handleValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.message, request)

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_SERVER_ERROR", "예기치 않은 오류가 발생했습니다.", request)

    private fun buildResponse(
        status: HttpStatus,
        code: String,
        message: String?,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val safeMessage = message ?: status.reasonPhrase
        val body = ErrorResponse(
            path = request.requestURI,
            message = safeMessage,
            code = code
        )
        return ResponseEntity.status(status).body(body)
    }
}
