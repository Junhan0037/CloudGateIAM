package com.cloudgate.iam.account.web

import com.cloudgate.iam.account.security.ResourceUserMapper
import com.cloudgate.iam.account.web.dto.ResourceUserResponse
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Access Token을 검증한 뒤 사용자·테넌트 컨텍스트를 반환
 */
@RestController
@RequestMapping("/api/accounts")
@Validated
class AccountResourceController(
    private val resourceUserMapper: ResourceUserMapper
) {

    /**
     * Bearer 토큰에 포함된 클레임을 기반으로 현재 사용자 정보를 반환
     */
    @GetMapping("/me")
    fun me(authentication: JwtAuthenticationToken): ResourceUserResponse {
        val resourceUser = resourceUserMapper.from(authentication.token)

        return ResourceUserResponse(
            subject = resourceUser.subject,
            userId = resourceUser.userId,
            tenantId = resourceUser.tenantId,
            tenantRegion = resourceUser.tenantRegion,
            tenantCode = resourceUser.tenantCode,
            roles = resourceUser.roles,
            scopes = resourceUser.scopes,
            attributes = resourceUser.attributes,
            issuedAt = resourceUser.issuedAt,
            expiresAt = resourceUser.expiresAt
        )
    }
}
