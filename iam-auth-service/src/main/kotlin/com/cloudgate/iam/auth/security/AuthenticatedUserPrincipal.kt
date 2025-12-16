package com.cloudgate.iam.auth.security

import com.cloudgate.iam.common.domain.UserAccountStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.io.Serializable

/**
 * 세션에 저장되는 인증 사용자 정보로, 향후 RBAC/ABAC 확장 시 속성을 확장
 */
data class AuthenticatedUserPrincipal @JsonCreator constructor(
    @JsonProperty("userId")
    val userId: Long,
    @JsonProperty("tenantId")
    val tenantId: Long,
    @JsonProperty("tenantCode")
    val tenantCode: String = "",
    @JsonProperty("tenantRegion")
    val tenantRegion: String,
    @JsonProperty("username")
    private val usernameValue: String,
    @JsonProperty("email")
    val email: String = "",
    @JsonProperty("mfaEnabled")
    val mfaEnabled: Boolean,
    @JsonProperty("mfaVerified")
    val mfaVerified: Boolean = false,
    @JsonProperty("status")
    val status: UserAccountStatus,
    @JsonProperty("roles")
    val roles: List<String> = emptyList(),
    @JsonProperty("department")
    val department: String? = null,
    @JsonProperty("roleLevel")
    val roleLevel: String? = null
) : UserDetails, Serializable {

    init {
        require(tenantRegion.isNotBlank()) { "테넌트 리전은 비어 있을 수 없습니다." }
    }

    override fun getAuthorities(): Collection<GrantedAuthority> =
        roles.map { UserAuthority(it) }

    override fun getPassword(): String? = null

    override fun getUsername(): String = usernameValue

    override fun isAccountNonExpired(): Boolean = status != UserAccountStatus.SUSPENDED

    override fun isAccountNonLocked(): Boolean = status != UserAccountStatus.LOCKED

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = status == UserAccountStatus.ACTIVE
}
