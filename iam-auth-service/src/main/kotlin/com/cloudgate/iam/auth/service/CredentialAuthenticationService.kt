package com.cloudgate.iam.auth.service

import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.common.domain.TenantStatus
import com.cloudgate.iam.common.domain.UserAccountStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
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
    private val passwordEncoder: PasswordEncoder
) {

    /**
     * 사용자 인증을 실행하고 성공 시 인증 주체 정보를 반환
     */
    @Transactional
    fun authenticate(tenantId: Long, username: String, rawPassword: String): AuthenticatedUserPrincipal {
        val account = userAccountRepository.findByTenantIdAndUsername(tenantId, username)
            ?: throw BadCredentialsException("잘못된 자격 증명입니다. tenantId=$tenantId")

        ensureTenantIsActive(account)
        ensureAccountIsUsable(account)

        if (!passwordEncoder.matches(rawPassword, account.passwordHash)) {
            throw BadCredentialsException("잘못된 자격 증명입니다. tenantId=$tenantId")
        }

        account.lastLoginAt = Instant.now()

        return AuthenticatedUserPrincipal(
            userId = account.id ?: throw IllegalStateException("사용자 ID가 누락되었습니다. tenantId=$tenantId"),
            tenantId = tenantId,
            usernameValue = account.username,
            mfaEnabled = account.mfaEnabled,
            status = account.status,
            grantedAuthorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
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
