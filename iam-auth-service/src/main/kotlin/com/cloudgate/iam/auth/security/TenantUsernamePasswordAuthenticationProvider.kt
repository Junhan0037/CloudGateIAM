package com.cloudgate.iam.auth.security

import com.cloudgate.iam.auth.service.CredentialAuthenticationService
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * 테넌트 정보를 포함한 커스텀 인증 토큰을 처리하는 AuthenticationProvider
 */
@Component
class TenantUsernamePasswordAuthenticationProvider(
    private val credentialAuthenticationService: CredentialAuthenticationService
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val token = authentication as? TenantUsernamePasswordAuthenticationToken
            ?: throw BadCredentialsException("지원하지 않는 인증 토큰 타입입니다.")

        val principal = credentialAuthenticationService.authenticate(
            tenantId = token.tenantId,
            username = token.name,
            rawPassword = token.credentials?.toString() ?: ""
        )

        return TenantUsernamePasswordAuthenticationToken(
            tenantId = token.tenantId,
            principal = principal,
            credentials = null,
            authorities = principal.authorities
        )
    }

    override fun supports(authentication: Class<*>): Boolean =
        TenantUsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
}
