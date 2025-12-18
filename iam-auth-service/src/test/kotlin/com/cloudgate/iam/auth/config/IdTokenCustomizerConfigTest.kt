package com.cloudgate.iam.auth.config

import com.cloudgate.iam.account.domain.Tenant
import com.cloudgate.iam.account.domain.TenantRepository
import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.AuthApplication
import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationToken
import com.cloudgate.iam.auth.service.CredentialAuthenticationService
import com.cloudgate.iam.common.domain.UserAccountStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest(
    classes = [AuthApplication::class],
    properties = ["spring.session.store-type=none"]
)
@ActiveProfiles("test")
class IdTokenCustomizerConfigTest @Autowired constructor(
    private val tenantRepository: TenantRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val credentialAuthenticationService: CredentialAuthenticationService,
    private val authServerProperties: AuthServerProperties,
    private val registeredClientRepository: RegisteredClientRepository,
    private val idTokenCustomizer: OAuth2TokenCustomizer<JwtEncodingContext>
) {

    private lateinit var tenant: Tenant
    private lateinit var user: UserAccount

    @BeforeEach
    fun setUp() {
        userAccountRepository.deleteAllInBatch()
        tenantRepository.deleteAllInBatch()

        tenant = tenantRepository.save(
            Tenant(
                code = "TENANT-CODE",
                name = "테스트 테넌트",
                region = "KR"
            )
        )

        user = userAccountRepository.save(
            UserAccount(
                tenant = tenant,
                username = "oidc-user",
                email = "oidc@example.com",
                passwordHash = passwordEncoder.encode("OidcPass123!"),
                status = UserAccountStatus.ACTIVE,
                mfaEnabled = true,
                department = "ENGINEERING",
                roleLevel = "ADMIN"
            )
        )
    }

    @Test
    fun `ID Token 커스텀 클레임을 추가한다`() {
        val registeredClient = registeredClientRepository.findByClientId(authServerProperties.clientId)
            ?: throw IllegalStateException("등록된 클라이언트를 찾을 수 없습니다.")

        val principal = credentialAuthenticationService.authenticate(
            tenantId = tenant.id ?: throw IllegalStateException("테넌트 ID가 없습니다."),
            username = user.username,
            rawPassword = "OidcPass123!"
        )
        val authenticationToken = TenantUsernamePasswordAuthenticationToken(
            tenantId = tenant.id!!,
            principal = principal,
            credentials = null,
            authorities = principal.authorities
        )

        val jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256)
        val claimsBuilder = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuer(authServerProperties.issuer)
            .subject(principal.username)

        val context = JwtEncodingContext.with(jwsHeader, claimsBuilder)
            .registeredClient(registeredClient)
            .principal(authenticationToken)
            .authorizedScopes(setOf("openid", "profile"))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrant(authenticationToken)
            .tokenType(ID_TOKEN_TYPE)
            .build()

        idTokenCustomizer.customize(context)

        val claims = context.claims.build().claims

        assertThat(claims["tenantId"]).isEqualTo(tenant.id)
        assertThat(claims["tenantCode"]).isEqualTo(tenant.code)
        assertThat(claims["tenantRegion"]).isEqualTo(tenant.region)
        assertThat(claims["userId"]).isEqualTo(user.id)
        assertThat(claims["preferred_username"]).isEqualTo(user.username)

        val roles = claims["roles"] as? Collection<*>
        assertThat(roles).contains("TENANT_ADMIN")

        @Suppress("UNCHECKED_CAST")
        val attributes = claims["attributes"] as? Map<String, Any>
        assertThat(attributes?.get("department")).isEqualTo("ENGINEERING")
        assertThat(attributes?.get("roleLevel")).isEqualTo("ADMIN")
        assertThat(attributes?.get("mfaEnabled")).isEqualTo(true)
        assertThat(attributes?.get("email")).isEqualTo(user.email)
    }

    @Test
    fun `Access Token에도 사용자 컨텍스트 클레임을 추가한다`() {
        val registeredClient = registeredClientRepository.findByClientId(authServerProperties.clientId)
            ?: throw IllegalStateException("등록된 클라이언트를 찾을 수 없습니다.")

        val principal = credentialAuthenticationService.authenticate(
            tenantId = tenant.id ?: throw IllegalStateException("테넌트 ID가 없습니다."),
            username = user.username,
            rawPassword = "OidcPass123!"
        )
        val authenticationToken = TenantUsernamePasswordAuthenticationToken(
            tenantId = tenant.id!!,
            principal = principal,
            credentials = null,
            authorities = principal.authorities
        )

        val jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256)
        val claimsBuilder = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuer(authServerProperties.issuer)
            .subject(principal.username)

        val context = JwtEncodingContext.with(jwsHeader, claimsBuilder)
            .registeredClient(registeredClient)
            .principal(authenticationToken)
            .authorizedScopes(setOf("openid", "profile"))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrant(authenticationToken)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build()

        idTokenCustomizer.customize(context)

        val claims = context.claims.build().claims

        assertThat(claims["tenantId"]).isEqualTo(tenant.id)
        assertThat(claims["userId"]).isEqualTo(user.id)
        assertThat(claims["tenantCode"]).isEqualTo(tenant.code)
        assertThat(claims["tenantRegion"]).isEqualTo(tenant.region)
        assertThat(claims["roles"]).isNotNull
    }

    companion object {
        private val ID_TOKEN_TYPE = OAuth2TokenType("id_token")
    }
}
