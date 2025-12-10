package com.cloudgate.iam.auth.security

import com.cloudgate.iam.common.domain.UserAccountStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 세션에 저장되는 인증 사용자 정보로, 향후 RBAC/ABAC 확장 시 속성을 확장
 */
data class AuthenticatedUserPrincipal(
    val userId: Long,
    val tenantId: Long,
    private val usernameValue: String,
    val mfaEnabled: Boolean,
    val status: UserAccountStatus,
    private val grantedAuthorities: Collection<GrantedAuthority> = emptyList()
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = grantedAuthorities

    override fun getPassword(): String? = null

    override fun getUsername(): String = usernameValue

    override fun isAccountNonExpired(): Boolean = status != UserAccountStatus.SUSPENDED

    override fun isAccountNonLocked(): Boolean = status != UserAccountStatus.LOCKED

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = status == UserAccountStatus.ACTIVE
}
