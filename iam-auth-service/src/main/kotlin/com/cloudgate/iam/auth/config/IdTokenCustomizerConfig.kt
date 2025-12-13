package com.cloudgate.iam.auth.config

import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer

/**
 * OIDC ID Token에 멀티테넌시·RBAC/ABAC 컨텍스트를 담기 위한 커스텀 클레임 설정
 */
@Configuration
class IdTokenCustomizerConfig {

    /**
     * 인증된 사용자 정보를 기반으로 ID Token 커스텀 클레임을 추가
     */
    @Bean
    fun idTokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            val authentication = context.getPrincipal<Authentication>()
            val principal = authentication.principal as? AuthenticatedUserPrincipal
                ?: return@OAuth2TokenCustomizer

            when (context.tokenType) {
                ID_TOKEN_TYPE, OAuth2TokenType.ACCESS_TOKEN -> applyUserClaims(context, principal)
                else -> return@OAuth2TokenCustomizer
            }
        }

    /**
     * Access Token과 ID Token에 공통 사용자 컨텍스트를 주입해 리소스 서버가 일관된 RBAC/ABAC 정보를 활용
     */
    private fun applyUserClaims(
        context: JwtEncodingContext,
        principal: AuthenticatedUserPrincipal
    ) {
        val claims = context.claims
        claims.claim("tenantId", principal.tenantId)
        claims.claim("tenantCode", principal.tenantCode)
        claims.claim("userId", principal.userId)
        claims.claim("preferred_username", principal.username)

        if (principal.roles.isNotEmpty()) {
            claims.claim("roles", principal.roles)
        }

        val attributes = buildAttributeClaims(principal)
        if (attributes.isNotEmpty()) {
            claims.claim("attributes", attributes)
        }
    }

    /**
     * ID Token에서 리소스 서버가 활용할 수 있는 사용자 속성을 정리
     */
    private fun buildAttributeClaims(principal: AuthenticatedUserPrincipal): Map<String, Any> =
        buildMap {
            principal.department?.takeIf { it.isNotBlank() }?.let { put("department", it) }
            principal.roleLevel?.takeIf { it.isNotBlank() }?.let { put("roleLevel", it) }
            principal.email.takeIf { it.isNotBlank() }?.let { put("email", it) }
            put("mfaEnabled", principal.mfaEnabled)
        }

    companion object {
        // Authorization Server에서는 ID Token 타입 상수가 제공되지 않아 명시적으로 정의
        private val ID_TOKEN_TYPE = OAuth2TokenType("id_token")
    }
}
