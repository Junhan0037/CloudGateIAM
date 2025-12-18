package com.cloudgate.iam.account.security

import com.cloudgate.iam.common.domain.RegionCode
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * JWT 클레임에서 리소스 서버가 활용할 사용자 컨텍스트를 추출
 */
@Component
class ResourceUserMapper {

    /**
     * 액세스 토큰 혹은 ID 토큰에서 사용자와 테넌트 정보를 해석
     */
    fun from(jwt: Jwt): ResourceUser =
        ResourceUser(
            subject = jwt.subject,
            userId = (jwt.claims["userId"] as? Number)?.toLong(),
            tenantId = (jwt.claims["tenantId"] as? Number)?.toLong(),
            tenantRegion = extractTenantRegion(jwt),
            tenantCode = jwt.claims["tenantCode"] as? String,
            roles = extractRoles(jwt),
            attributes = extractAttributes(jwt),
            scopes = extractScopes(jwt),
            issuedAt = jwt.issuedAt,
            expiresAt = jwt.expiresAt
        )

    private fun extractTenantRegion(jwt: Jwt): String? =
        (jwt.claims["tenantRegion"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { RegionCode.normalize(it) }

    private fun extractRoles(jwt: Jwt): List<String> =
        (jwt.claims["roles"] as? Collection<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun extractAttributes(jwt: Jwt): Map<String, Any> =
        (jwt.claims["attributes"] as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                val normalizedKey = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                value?.let { normalizedKey to it }
            }
            ?.toMap()
            ?: emptyMap()

    private fun extractScopes(jwt: Jwt): List<String> {
        val rawScope = jwt.claims["scope"]
        val scopes = when (rawScope) {
            is String -> rawScope.split(" ")
            is Collection<*> -> rawScope.mapNotNull { it?.toString() }
            else -> emptyList()
        }

        return scopes.filter { it.isNotBlank() }.sorted()
    }
}

/**
 * API 응답과 비즈니스 로직에서 공통으로 사용하는 리소스 사용자 정보 모델
 */
data class ResourceUser(
    val subject: String,
    val userId: Long?,
    val tenantId: Long?,
    val tenantRegion: String?,
    val tenantCode: String?,
    val roles: List<String>,
    val attributes: Map<String, Any>,
    val scopes: List<String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?
)
