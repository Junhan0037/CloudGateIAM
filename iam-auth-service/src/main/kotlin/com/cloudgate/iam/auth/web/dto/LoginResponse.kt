package com.cloudgate.iam.auth.web.dto

/**
 * 로그인 성공 시 세션 메타데이터를 포함해 반환하는 응답 DTO
 */
data class LoginResponse(
    val sessionId: String,
    val userId: Long,
    val tenantId: Long,
    val username: String,
    val mfaEnabled: Boolean,
    val mfaVerified: Boolean,
    val sessionExpiresInSeconds: Int
)
