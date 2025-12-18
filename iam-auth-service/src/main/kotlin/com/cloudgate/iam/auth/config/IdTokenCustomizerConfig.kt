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
     * - OAuth2TokenCustomizer<JwtEncodingContext>: 토큰을 발급할 때(JWT로 인코딩할 때) 호출되는 “훅(hook)” 인터페이스
     */
    @Bean
    fun idTokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            val authentication = context.getPrincipal<Authentication>()
            val principal = authentication.principal as? AuthenticatedUserPrincipal
                ?: return@OAuth2TokenCustomizer

            when (context.tokenType) {
                ID_TOKEN_TYPE, OAuth2TokenType.ACCESS_TOKEN -> applyUserClaims(context, principal) // ID Token과 Access Token 둘 다에 동일한 사용자 컨텍스트를 넣는다.
                else -> return@OAuth2TokenCustomizer
            }
        }

    /**
     * Access Token과 ID Token에 공통 사용자 컨텍스트를 주입해 리소스 서버가 일관된 RBAC/ABAC 정보를 활용
     */
    private fun applyUserClaims(context: JwtEncodingContext, principal: AuthenticatedUserPrincipal) {
        // 커스텀 클레임 추가
        val claims = context.claims
        claims.claim("tenantId", principal.tenantId) // 멀티테넌시 컨텍스트. 리소스 서버가 tenant scope 체크(테넌트 격리)를 토큰만으로 수행 가능.
        claims.claim("tenantCode", principal.tenantCode)
        claims.claim("tenantRegion", principal.tenantRegion)
        claims.claim("userId", principal.userId) // // 내부 사용자 식별자.
        claims.claim("preferred_username", principal.username) // OIDC에서 자주 쓰는 표준 클레임 이름 중 하나. UI 표시나 감사로그 등에 유용.

        if (principal.roles.isNotEmpty()) {
            claims.claim("roles", principal.roles) // RBAC(Role Based Access Control) 핵심 정보.
        }

        val attributes = buildAttributeClaims(principal)
        if (attributes.isNotEmpty()) {
            claims.claim("attributes", attributes) // ABAC(Attribute Based Access Control)용.
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
        private val ID_TOKEN_TYPE = OAuth2TokenType("id_token") // Authorization Server에서는 ID Token 타입 상수가 제공되지 않아 명시적으로 정의
    }
}
