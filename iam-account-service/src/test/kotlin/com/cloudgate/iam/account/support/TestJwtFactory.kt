package com.cloudgate.iam.account.support

import com.cloudgate.iam.common.domain.RegionCode
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Duration
import java.time.Instant

/**
 * 테스트 시나리오에 필요한 Access/ID 토큰을 생성하는 헬퍼
 */
class TestJwtFactory(
    private val jwtEncoder: JwtEncoder
) {

    fun createAccessToken(
        subject: String = "resource-user",
        tenantId: Long = 1L,
        tenantCode: String = "TENANT-CODE",
        tenantRegion: String = "KR",
        userId: Long = 10L,
        roles: List<String> = listOf("TENANT_USER"),
        scopes: Set<String> = setOf("profile"),
        audience: String? = null,
        issuer: String = "http://localhost:8080",
        ttl: Duration = Duration.ofMinutes(10)
    ): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("tenantId", tenantId)
            .claim("tenantCode", tenantCode)
            .claim("tenantRegion", RegionCode.normalize(tenantRegion))
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("scope", scopes.joinToString(" "))

        audience?.let { claims.audience(listOf(it)) }

        return encode(claims.build())
    }

    fun createIdToken(
        subject: String = "resource-user",
        tenantId: Long = 1L,
        tenantCode: String = "TENANT-CODE",
        tenantRegion: String = "KR",
        userId: Long = 10L,
        roles: List<String> = listOf("TENANT_USER"),
        issuer: String = "http://localhost:8080",
        ttl: Duration = Duration.ofMinutes(10)
    ): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("tenantId", tenantId)
            .claim("tenantCode", tenantCode)
            .claim("tenantRegion", RegionCode.normalize(tenantRegion))
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("attributes", mapOf("department" to "ENGINEERING", "mfaEnabled" to true))

        return encode(claims.build())
    }

    private fun encode(claims: JwtClaimsSet): String {
        val headers = JwsHeader.with(SignatureAlgorithm.RS256).build()
        val parameters = JwtEncoderParameters.from(headers, claims)
        return jwtEncoder.encode(parameters).tokenValue
    }
}
