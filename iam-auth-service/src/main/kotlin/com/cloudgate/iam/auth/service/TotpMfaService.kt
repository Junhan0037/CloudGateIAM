package com.cloudgate.iam.auth.service

import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.config.MfaProperties
import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.auth.service.exception.MfaCodeInvalidException
import com.cloudgate.iam.auth.service.exception.MfaRegistrationNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 사용자별 TOTP 기반 MFA 등록과 검증을 처리하는 서비스
 */
@Service
class TotpMfaService(
    private val userAccountRepository: UserAccountRepository,
    private val totpTokenService: TotpTokenService,
    private val mfaProperties: MfaProperties,
    private val clock: Clock
) {

    /**
     * 사용자가 인증 앱을 등록할 수 있도록 새로운 TOTP 시크릿을 발급
     * 기존 MFA가 활성화된 경우에도 새 시크릿을 pending 영역에 저장해 회전 시나리오를 지원
     */
    @Transactional
    fun issueEnrollment(principal: AuthenticatedUserPrincipal): TotpEnrollmentResult {
        val account = findAccount(principal)
        val secret = totpTokenService.generateSecret()

        account.pendingMfaSecret = secret
        userAccountRepository.save(account)

        val label = buildAccountLabel(account)
        return TotpEnrollmentResult(
            secret = secret,
            provisioningUri = totpTokenService.buildProvisioningUri(secret, label),
            issuer = mfaProperties.issuer,
            accountLabel = label,
            mfaAlreadyEnabled = account.mfaEnabled
        )
    }

    /**
     * 발급된 시크릿으로 생성된 OTP가 유효하면 MFA를 활성화
     */
    @Transactional
    fun activate(principal: AuthenticatedUserPrincipal, code: String): MfaState {
        val account = findAccount(principal)
        val pendingSecret = account.pendingMfaSecret
            ?: throw MfaRegistrationNotFoundException("활성화할 MFA 시크릿이 없습니다.")

        if (!totpTokenService.verifyCode(pendingSecret, code)) {
            throw MfaCodeInvalidException("유효하지 않은 TOTP 코드입니다.")
        }

        account.mfaSecret = pendingSecret
        account.pendingMfaSecret = null
        account.mfaEnabled = true
        account.mfaEnrolledAt = Instant.now(clock)
        userAccountRepository.save(account)

        return MfaState(mfaEnabled = true, mfaVerified = true)
    }

    /**
     * 활성화된 TOTP 시크릿 기준으로 로그인 시점의 MFA 코드를 검증
     */
    @Transactional(readOnly = true)
    fun verifyChallenge(principal: AuthenticatedUserPrincipal, code: String): MfaState {
        val account = findAccount(principal)
        val activeSecret = account.mfaSecret ?: throw MfaRegistrationNotFoundException("MFA가 활성화되어 있지 않습니다.")

        if (!account.mfaEnabled) {
            throw MfaRegistrationNotFoundException("MFA가 활성화되어 있지 않습니다.")
        }

        if (!totpTokenService.verifyCode(activeSecret, code)) {
            throw MfaCodeInvalidException("유효하지 않은 TOTP 코드입니다.")
        }

        return MfaState(mfaEnabled = true, mfaVerified = true)
    }

    private fun findAccount(principal: AuthenticatedUserPrincipal): UserAccount =
        userAccountRepository.findById(principal.userId)
            .filter { it.tenant.id == principal.tenantId }
            .orElseThrow {
                IllegalArgumentException("계정을 찾을 수 없습니다. tenantId=${principal.tenantId}, userId=${principal.userId}")
            }

    private fun buildAccountLabel(account: UserAccount): String =
        "${account.username}@${account.tenant.code}"
}

/**
 * MFA 활성화 상태와 세션 검증 여부를 표현
 */
data class MfaState(
    val mfaEnabled: Boolean,
    val mfaVerified: Boolean
)

/**
 * MFA 등록을 위한 시크릿과 otpauth URI 정보
 */
data class TotpEnrollmentResult(
    val secret: String,
    val provisioningUri: String,
    val issuer: String,
    val accountLabel: String,
    val mfaAlreadyEnabled: Boolean
)
