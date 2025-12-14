package com.cloudgate.iam.auth.security

import com.cloudgate.iam.auth.web.dto.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets

/**
 * MFA 활성 사용자에 대해 추가 검증 여부를 확인하고 미검증 시 접근을 차단
 */
@Component
class MfaVerificationFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val bypassPrefixes = listOf(
        "/auth/login",
        "/auth/logout",
        "/auth/mfa",
        "/actuator",
        "/oauth2",
        "/.well-known"
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        bypassPrefixes.any { prefix -> request.requestURI.startsWith(prefix) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? AuthenticatedUserPrincipal

        if (principal != null && principal.mfaEnabled && !principal.mfaVerified) {
            val error = ErrorResponse(
                path = request.requestURI,
                message = "추가 MFA 검증이 필요합니다.",
                code = "MFA_REQUIRED"
            )

            response.status = HttpStatus.UNAUTHORIZED.value()
            response.characterEncoding = StandardCharsets.UTF_8.name()
            response.contentType = "application/json"
            response.writer.write(objectMapper.writeValueAsString(error))
            return
        }

        filterChain.doFilter(request, response)
    }
}
