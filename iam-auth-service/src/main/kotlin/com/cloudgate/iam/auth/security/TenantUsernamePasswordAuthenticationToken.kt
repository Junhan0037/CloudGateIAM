package com.cloudgate.iam.auth.security

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * 멀티테넌트 환경에서 테넌트 식별자를 함께 전달하기 위한 커스텀 인증 토큰
 * - 세션 직렬화/역직렬화를 위해 JsonCreator를 정의하고 인증 상태를 복원
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonCreator
    constructor(
        @JsonProperty("tenantId") tenantId: Long,
        @JsonProperty("principal") principal: Any?,
        @JsonProperty("credentials") credentials: Any?,
        @JsonProperty("authorities") authorities: Collection<GrantedAuthority> = emptyList(),
        @JsonProperty("details") details: Any? = null,
        @JsonProperty("authenticated") authenticated: Boolean = false
    ) : super(principal, credentials, authorities) {
        this.tenantId = tenantId
        this.details = details
        super.setAuthenticated(authenticated)
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
