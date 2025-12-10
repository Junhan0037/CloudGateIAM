package com.cloudgate.iam.auth.web.dto

/**
 * 활성 세션 상태를 조회할 때 반환하는 DTO
 */
data class SessionInfoResponse(
    val userId: Long,
    val tenantId: Long,
    val username: String,
    val mfaEnabled: Boolean
)
