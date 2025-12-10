package com.cloudgate.iam.auth.web.dto

import java.time.Instant

/**
 * 예외 발생 시 클라이언트로 전달할 표준 에러 응답
 */
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val path: String,
    val message: String,
    val code: String
)
