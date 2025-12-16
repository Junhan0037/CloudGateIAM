package com.cloudgate.iam.account.web.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * 토큰 검증 및 현재 사용자 조회 API에 사용하는 DTO 모음
 */
data class TokenValidationRequest(
    @field:NotBlank(message = "검증할 토큰 값은 비어 있을 수 없습니다.")
    val token: String,
    val tokenType: TokenType = TokenType.ACCESS_TOKEN
)

data class TokenValidationResponse(
    val tokenType: TokenType,
    val subject: String,
    val tenantId: Long?,
    val tenantRegion: String?,
    val tenantCode: String?,
    val roles: List<String>,
    val attributes: Map<String, Any>,
    val issuer: String?,
    val issuedAt: Instant?,
    val expiresAt: Instant?
)

data class ResourceUserResponse(
    val subject: String,
    val userId: Long?,
    val tenantId: Long?,
    val tenantRegion: String?,
    val tenantCode: String?,
    val roles: List<String>,
    val scopes: List<String>,
    val attributes: Map<String, Any>,
    val issuedAt: Instant?,
    val expiresAt: Instant?
)

enum class TokenType {
    ACCESS_TOKEN,
    ID_TOKEN
}
