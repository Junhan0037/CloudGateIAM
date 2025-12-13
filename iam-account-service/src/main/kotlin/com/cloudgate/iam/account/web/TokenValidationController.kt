package com.cloudgate.iam.account.web

import com.cloudgate.iam.account.security.ResourceUserMapper
import com.cloudgate.iam.account.service.TokenValidationService
import com.cloudgate.iam.account.web.dto.TokenValidationRequest
import com.cloudgate.iam.account.web.dto.TokenValidationResponse
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Access Token/ID Token을 검증하고 주요 클레임을 요약해 반환
 */
@RestController
@RequestMapping("/api/tokens")
@Validated
class TokenValidationController(
    private val tokenValidationService: TokenValidationService,
    private val resourceUserMapper: ResourceUserMapper
) {

    /**
     * 전달된 토큰을 검증하고, 리소스 서버가 신뢰할 수 있는 형태로 요약 정보를 반환
     */
    @PostMapping("/validate")
    fun validate(
        @Valid @RequestBody request: TokenValidationRequest
    ): TokenValidationResponse {
        val jwt = tokenValidationService.decode(request.token)
        val resourceUser = resourceUserMapper.from(jwt)

        return TokenValidationResponse(
            tokenType = request.tokenType,
            subject = resourceUser.subject,
            tenantId = resourceUser.tenantId,
            tenantCode = resourceUser.tenantCode,
            roles = resourceUser.roles,
            attributes = resourceUser.attributes,
            issuer = jwt.issuer?.toString(),
            issuedAt = resourceUser.issuedAt,
            expiresAt = resourceUser.expiresAt
        )
    }
}
