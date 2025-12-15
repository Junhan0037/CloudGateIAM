package com.cloudgate.iam.auth.web

import com.cloudgate.iam.auth.audit.LoginAuditEventPublisher
import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationToken
import com.cloudgate.iam.auth.web.dto.LoginRequest
import com.cloudgate.iam.auth.web.dto.LoginResponse
import com.cloudgate.iam.auth.web.dto.SessionInfoResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ID/Password 로그인 엔드포인트로, 인증 성공 시 세션을 생성
 */
@RestController
@RequestMapping("/auth")
@Validated
class LoginController(
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
    private val loginAuditEventPublisher: LoginAuditEventPublisher
) {

    /**
     * 인증 매니저를 통해 로그인 후 세션을 생성하고 메타데이터를 반환
     */
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): LoginResponse {
        val tenantId = request.tenantId
            ?: throw IllegalArgumentException("테넌트 ID는 필수입니다.")

        val authentication = authenticationManager.authenticate(
            TenantUsernamePasswordAuthenticationToken(
                tenantId = tenantId,
                principal = request.username,
                credentials = request.password
            )
        )

        val context = SecurityContextHolder.createEmptyContext().apply {
            this.authentication = authentication
        }

        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)
        val session = httpRequest.session
            ?: throw IllegalStateException("세션 생성에 실패했습니다.")

        val principal = authentication.principal as AuthenticatedUserPrincipal

        loginAuditEventPublisher.publishLoginSuccess(
            principal = principal,
            sessionId = session.id,
            clientIp = resolveClientIp(httpRequest),
            userAgent = httpRequest.getHeader("User-Agent")
        )

        return LoginResponse(
            sessionId = session.id,
            userId = principal.userId,
            tenantId = principal.tenantId,
            username = principal.username,
            mfaEnabled = principal.mfaEnabled,
            mfaVerified = principal.mfaVerified,
            sessionExpiresInSeconds = session.maxInactiveInterval
        )
    }

    /**
     * 현재 세션의 사용자 정보를 반환
     */
    @GetMapping("/me")
    fun me(authentication: Authentication): SessionInfoResponse {
        val principal = authentication.principal as AuthenticatedUserPrincipal
        return SessionInfoResponse(
            userId = principal.userId,
            tenantId = principal.tenantId,
            username = principal.username,
            mfaEnabled = principal.mfaEnabled,
            mfaVerified = principal.mfaVerified
        )
    }

    /**
     * X-Forwarded-For를 우선 확인해 클라이언트 IP를 파악
     */
    private fun resolveClientIp(request: HttpServletRequest): String? {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").firstOrNull()?.trim()
        }
        return request.remoteAddr
    }
}
