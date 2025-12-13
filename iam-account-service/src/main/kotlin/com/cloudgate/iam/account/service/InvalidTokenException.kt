package com.cloudgate.iam.account.service

/**
 * 토큰 검증 실패 시 클라이언트에 의미 있는 메시지를 전달하기 위한 예외
 */
class InvalidTokenException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
