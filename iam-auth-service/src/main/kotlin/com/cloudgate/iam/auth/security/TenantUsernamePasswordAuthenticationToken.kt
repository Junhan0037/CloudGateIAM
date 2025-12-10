package com.cloudgate.iam.auth.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * 멀티테넌트 환경에서 테넌트 식별자를 함께 전달하기 위한 커스텀 인증 토큰
 */
class TenantUsernamePasswordAuthenticationToken : UsernamePasswordAuthenticationToken {

    val tenantId: Long

    constructor(tenantId: Long, principal: Any, credentials: Any?) : super(principal, credentials) {
        this.tenantId = tenantId
    }

    constructor(
        tenantId: Long,
        principal: Any,
        credentials: Any?,
        authorities: Collection<GrantedAuthority>
    ) : super(principal, credentials, authorities) {
        this.tenantId = tenantId
    }
}
