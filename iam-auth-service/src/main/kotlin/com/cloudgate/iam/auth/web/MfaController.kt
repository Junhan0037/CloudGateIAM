package com.cloudgate.iam.auth.web

import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationToken
import com.cloudgate.iam.auth.service.MfaState
import com.cloudgate.iam.auth.service.TotpMfaService
import com.cloudgate.iam.auth.web.dto.MfaVerificationResponse
import com.cloudgate.iam.auth.web.dto.TotpSetupResponse
import com.cloudgate.iam.auth.web.dto.TotpVerifyRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * MFA(TOTP) 등록/검증 API를 제공
 */
@RestController
@RequestMapping("/auth/mfa")
@Validated
class MfaController(
    private val totpMfaService: TotpMfaService,
    private val securityContextRepository: SecurityContextRepository
) {

    /**
     * TOTP 등록을 시작하고 시크릿 및 프로비저닝 URI를 반환
     */
    @PostMapping("/totp/setup")
    fun setup(authentication: Authentication): TotpSetupResponse {
        val principal = extractPrincipal(authentication)
        val result = totpMfaService.issueEnrollment(principal)

        return TotpSetupResponse(
            secret = result.secret,
            provisioningUri = result.provisioningUri,
            issuer = result.issuer,
            accountLabel = result.accountLabel,
            mfaAlreadyEnabled = result.mfaAlreadyEnabled
        )
    }

    /**
     * 발급된 TOTP 시크릿으로 생성된 OTP를 검증해 MFA를 활성화
     */
    @PostMapping("/totp/activate")
    fun activate(
        @Valid @RequestBody request: TotpVerifyRequest,
        authentication: Authentication,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): MfaVerificationResponse {
        val principal = extractPrincipal(authentication)
        val state = totpMfaService.activate(principal, request.code)
        val updatedPrincipal = refreshAuthentication(principal, state, httpRequest, httpResponse)

        return MfaVerificationResponse(
            mfaEnabled = updatedPrincipal.mfaEnabled,
            mfaVerified = updatedPrincipal.mfaVerified
        )
    }

    /**
     * 로그인 이후 추가 MFA 검증을 수행
     */
    @PostMapping("/totp/verify")
    fun verify(
        @Valid @RequestBody request: TotpVerifyRequest,
        authentication: Authentication,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): MfaVerificationResponse {
        val principal = extractPrincipal(authentication)
        val state = totpMfaService.verifyChallenge(principal, request.code)
        val updatedPrincipal = refreshAuthentication(principal, state, httpRequest, httpResponse)

        return MfaVerificationResponse(
            mfaEnabled = updatedPrincipal.mfaEnabled,
            mfaVerified = updatedPrincipal.mfaVerified
        )
    }

    /**
     * MFA 상태 변경을 세션 및 SecurityContext에 반영
     */
    private fun refreshAuthentication(
        principal: AuthenticatedUserPrincipal,
        state: MfaState,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): AuthenticatedUserPrincipal {
        val updatedPrincipal = principal.copy(
            mfaEnabled = state.mfaEnabled,
            mfaVerified = state.mfaVerified
        )

        val authentication = TenantUsernamePasswordAuthenticationToken(
            tenantId = updatedPrincipal.tenantId,
            principal = updatedPrincipal,
            credentials = null,
            authorities = updatedPrincipal.authorities
        )

        val context = SecurityContextHolder.createEmptyContext().apply {
            this.authentication = authentication
        }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)

        return updatedPrincipal
    }

    /**
     * 인증 객체에서 커스텀 Principal을 안전하게 추출
     */
    private fun extractPrincipal(authentication: Authentication): AuthenticatedUserPrincipal =
        authentication.principal as? AuthenticatedUserPrincipal
            ?: throw IllegalStateException("세션에 사용자 정보가 없습니다.")
}
