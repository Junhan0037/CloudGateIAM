package com.cloudgate.iam.auth.service

import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.common.domain.TenantStatus
import com.cloudgate.iam.common.domain.UserAccountStatus
import com.cloudgate.iam.common.tenant.TenantContextHolder
import com.cloudgate.iam.common.tenant.TenantFilterApplier
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 아이디/패스워드 인증을 수행하며 계정·테넌트 상태를 검증
 */
@Service
class CredentialAuthenticationService(
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tenantFilterApplier: TenantFilterApplier
) {

    /**
     * 사용자 인증을 실행하고 성공 시 인증 주체 정보를 반환
     */
    @Transactional
    fun authenticate(tenantId: Long, username: String, rawPassword: String): AuthenticatedUserPrincipal =
        TenantContextHolder.withTenant(tenantId) {
            tenantFilterApplier.enableForCurrentTenant()
            val account = userAccountRepository.findByTenantIdAndUsername(tenantId, username)
                ?: throw BadCredentialsException("잘못된 자격 증명입니다. tenantId=$tenantId")

            ensureTenantIsActive(account)
            ensureAccountIsUsable(account)

            if (!passwordEncoder.matches(rawPassword, account.passwordHash)) {
                throw BadCredentialsException("잘못된 자격 증명입니다. tenantId=$tenantId")
            }

            account.lastLoginAt = Instant.now()
            val mfaEnabled = account.mfaEnabled

            return@withTenant AuthenticatedUserPrincipal(
                userId = account.id ?: throw IllegalStateException("사용자 ID가 누락되었습니다. tenantId=$tenantId"),
                tenantId = tenantId,
                tenantCode = account.tenant.code,
                usernameValue = account.username,
                email = account.email,
                mfaEnabled = mfaEnabled,
                mfaVerified = !mfaEnabled,
                status = account.status,
                roles = resolveRoles(account),
                department = account.department,
                roleLevel = account.roleLevel
            )
        }

    /**
     * 간단한 역할 매핑을 수행해 기본 RBAC 컨텍스트를 구성
     * 향후 Policy 서비스 연동 시 실제 역할/프로젝트 범위를 반영하도록 확장
     */
    private fun resolveRoles(account: UserAccount): List<String> {
        val normalizedLevel = account.roleLevel?.trim()?.uppercase()

        if (normalizedLevel.isNullOrBlank()) {
            return listOf("TENANT_USER")
        }

        return when {
            normalizedLevel.contains("SYSTEM") -> listOf("SYSTEM_ADMIN")
            normalizedLevel.contains("ADMIN") -> listOf("TENANT_ADMIN")
            normalizedLevel.contains("VIEWER") -> listOf("PROJECT_VIEWER")
            else -> listOf("TENANT_USER")
        }
    }

    private fun ensureTenantIsActive(account: UserAccount) {
        if (account.tenant.status != TenantStatus.ACTIVE) {
            throw DisabledException("테넌트가 활성 상태가 아닙니다. tenantId=${account.tenant.id}")
        }
    }

    private fun ensureAccountIsUsable(account: UserAccount) {
        when (account.status) {
            UserAccountStatus.ACTIVE -> return
            UserAccountStatus.PENDING_VERIFICATION ->
                throw DisabledException("이메일 인증 전 계정입니다. tenantId=${account.tenant.id}")
            UserAccountStatus.LOCKED ->
                throw LockedException("계정이 잠금 상태입니다. tenantId=${account.tenant.id}")
            UserAccountStatus.SUSPENDED ->
                throw DisabledException("계정이 정지 상태입니다. tenantId=${account.tenant.id}")
            UserAccountStatus.DELETED ->
                throw DisabledException("탈퇴 처리된 계정입니다. tenantId=${account.tenant.id}")
        }
    }
}
