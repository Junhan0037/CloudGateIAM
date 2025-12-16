package com.cloudgate.iam.auth.web

import com.cloudgate.iam.account.domain.Tenant
import com.cloudgate.iam.account.domain.TenantRepository
import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.AuthApplication
import com.cloudgate.iam.auth.service.TotpTokenService
import com.cloudgate.iam.common.domain.UserAccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest(
    classes = [AuthApplication::class],
    properties = ["spring.session.store-type=none"]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val tenantRepository: TenantRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpTokenService: TotpTokenService
) {

    private lateinit var tenant: Tenant
    private lateinit var user: UserAccount

    @BeforeEach
    fun setUp() {
        userAccountRepository.deleteAllInBatch()
        tenantRepository.deleteAllInBatch()

        tenant = tenantRepository.save(
            Tenant(
                code = "MFA-TENANT",
                name = "MFA 테넌트",
                region = "KR"
            )
        )

        user = userAccountRepository.save(
            UserAccount(
                tenant = tenant,
                username = "mfa-user",
                email = "mfa@example.com",
                passwordHash = passwordEncoder.encode("Secret123!"),
                status = UserAccountStatus.ACTIVE,
                mfaEnabled = false
            )
        )
    }

    @Test
    fun `TOTP 시크릿을 발급하면 pending 컬럼에 저장된다`() {
        val sessionCookie = loginAndGetSessionCookie()

        val result = mockMvc.perform(
            post("/auth/mfa/totp/setup")
                .cookie(sessionCookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.secret").isNotEmpty)
            .andExpect(jsonPath("$.provisioningUri").isNotEmpty)
            .andExpect(jsonPath("$.mfaAlreadyEnabled").value(false))
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsByteArray)
        val issuedSecret = body["secret"].asText()

        val refreshedUser = userAccountRepository.findById(user.id!!).orElseThrow()
        assertThat(refreshedUser.pendingMfaSecret).isEqualTo(issuedSecret)
        assertThat(refreshedUser.mfaEnabled).isFalse()
        assertThat(refreshedUser.mfaSecret).isNull()
    }

    @Test
    fun `TOTP 검증 후 MFA가 활성화되고 세션이 검증된다`() {
        val sessionCookie = loginAndGetSessionCookie()

        val setupResponse = mockMvc.perform(
            post("/auth/mfa/totp/setup")
                .cookie(sessionCookie)
        )
            .andExpect(status().isOk)
            .andReturn()

        val secret = objectMapper.readTree(setupResponse.response.contentAsByteArray)["secret"].asText()
        val otp = totpTokenService.generateCode(secret, Instant.now())

        mockMvc.perform(
            post("/auth/mfa/totp/activate")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("code" to otp)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mfaEnabled").value(true))
            .andExpect(jsonPath("$.mfaVerified").value(true))

        val refreshedUser = userAccountRepository.findById(user.id!!).orElseThrow()
        assertThat(refreshedUser.pendingMfaSecret).isNull()
        assertThat(refreshedUser.mfaEnabled).isTrue()
        assertThat(refreshedUser.mfaSecret).isNotBlank

        mockMvc.perform(
            get("/auth/me")
                .cookie(sessionCookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tenantRegion").value(tenant.region))
            .andExpect(jsonPath("$.mfaEnabled").value(true))
            .andExpect(jsonPath("$.mfaVerified").value(true))
    }

    @Test
    fun `MFA 활성 사용자는 검증 전까지 보호 자원 접근이 차단된다`() {
        val activeSecret = "JBSWY3DPEHPK3PXP"
        val mfaUser = userAccountRepository.save(
            UserAccount(
                tenant = tenant,
                username = "mfa-active",
                email = "mfa-active@example.com",
                passwordHash = passwordEncoder.encode("Secret123!"),
                status = UserAccountStatus.ACTIVE,
                mfaEnabled = true,
                mfaSecret = activeSecret,
                pendingMfaSecret = null,
                mfaEnrolledAt = Instant.now()
            )
        )

        val payload = mapOf(
            "tenantId" to tenant.id,
            "username" to mfaUser.username,
            "password" to "Secret123!"
        )

        val loginResult = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mfaEnabled").value(true))
            .andExpect(jsonPath("$.mfaVerified").value(false))
            .andReturn()

        val sessionCookie = loginResult.response.getCookie("CGIAMSESSION")
        requireNotNull(sessionCookie)

        mockMvc.perform(
            get("/auth/me")
                .cookie(sessionCookie)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("MFA_REQUIRED"))

        val otp = totpTokenService.generateCode(activeSecret, Instant.now())

        mockMvc.perform(
            post("/auth/mfa/totp/verify")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("code" to otp)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mfaVerified").value(true))

        mockMvc.perform(
            get("/auth/me")
                .cookie(sessionCookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tenantRegion").value(tenant.region))
            .andExpect(jsonPath("$.mfaVerified").value(true))
    }

    private fun loginAndGetSessionCookie(): jakarta.servlet.http.Cookie {
        val payload = mapOf(
            "tenantId" to tenant.id,
            "username" to user.username,
            "password" to "Secret123!"
        )

        val result = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andReturn()

        return result.response.getCookie("CGIAMSESSION")
            ?: throw IllegalStateException("세션 쿠키가 설정되지 않았습니다.")
    }
}
