package com.cloudgate.iam.auth.web

import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationToken
import com.cloudgate.iam.auth.web.dto.LoginRequest
import com.cloudgate.iam.auth.web.dto.LoginResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
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
    private val securityContextRepository: SecurityContextRepository
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

        return LoginResponse(
            sessionId = session.id,
            userId = principal.userId,
            tenantId = principal.tenantId,
            username = principal.username,
            mfaEnabled = principal.mfaEnabled,
            sessionExpiresInSeconds = session.maxInactiveInterval
        )
    }
}
