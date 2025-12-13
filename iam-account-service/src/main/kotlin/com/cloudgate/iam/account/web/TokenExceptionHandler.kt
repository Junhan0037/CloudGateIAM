package com.cloudgate.iam.account.web

import com.cloudgate.iam.account.service.InvalidTokenException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 토큰 검증 실패 시 일관된 401 응답을 내려 클라이언트가 재인증을 수행하도록 유도
 */
@RestControllerAdvice
class TokenExceptionHandler {

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(ex: InvalidTokenException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(
                mapOf(
                    "error" to "invalid_token",
                    "message" to (ex.message ?: "토큰 검증에 실패했습니다.")
                )
            )
}
