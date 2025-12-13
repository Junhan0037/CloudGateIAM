package com.cloudgate.iam.account.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * JWT roles 클레임을 Spring Security 권한으로 변환해 RBAC 검사 지원
 */
@Component
class RoleBasedJwtConverter(
    private val roleClaimName: String = "roles",
    private val authorityPrefix: String = "ROLE_"
) : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val roles = jwt.claims[roleClaimName] as? Collection<*>
            ?: return emptyList()

        return roles.mapNotNull { role ->
            val normalized = role?.toString()?.trim()?.uppercase().orEmpty()
            if (normalized.isBlank()) {
                null
            } else {
                SimpleGrantedAuthority("$authorityPrefix$normalized")
            }
        }
    }
}
