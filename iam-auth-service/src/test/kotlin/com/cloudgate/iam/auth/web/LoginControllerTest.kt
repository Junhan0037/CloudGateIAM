package com.cloudgate.iam.auth.web

import com.cloudgate.iam.account.domain.Tenant
import com.cloudgate.iam.account.domain.TenantRepository
import com.cloudgate.iam.account.domain.UserAccount
import com.cloudgate.iam.account.domain.UserAccountRepository
import com.cloudgate.iam.auth.AuthApplication
import com.cloudgate.iam.common.domain.TenantStatus
import com.cloudgate.iam.common.domain.UserAccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest(
    classes = [AuthApplication::class],
    properties = ["spring.session.store-type=none"]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tenantRepository: TenantRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper
) {

    private lateinit var activeTenant: Tenant
    private lateinit var activeUser: UserAccount

    @BeforeEach
    fun setUp() {
        userAccountRepository.deleteAllInBatch()
        tenantRepository.deleteAllInBatch()

        activeTenant = tenantRepository.save(
            Tenant(
                code = "TENANT-1",
                name = "테스트 테넌트"
            )
        )

        activeUser = userAccountRepository.save(
            UserAccount(
                tenant = activeTenant,
                username = "alice",
                email = "alice@example.com",
                passwordHash = passwordEncoder.encode("P@ssw0rd!"),
                status = UserAccountStatus.ACTIVE,
                mfaEnabled = false,
                department = "DEV",
                roleLevel = "USER",
                lastLoginAt = Instant.now()
            )
        )
    }

    @Test
    fun `정상 로그인 시 세션이 생성되고 메타데이터가 반환된다`() {
        val payload = mapOf(
            "tenantId" to activeTenant.id,
            "username" to activeUser.username,
            "password" to "P@ssw0rd!"
        )

        val result = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.userId").value(activeUser.id!!.toInt()))
            .andExpect(jsonPath("$.tenantId").value(activeTenant.id!!.toInt()))
            .andExpect(jsonPath("$.mfaEnabled").value(false))
            .andExpect(jsonPath("$.mfaVerified").value(true))
            .andExpect(cookie().exists("CGIAMSESSION"))
            .andReturn()

        val responseBody = result.response.contentAsString
        assertThat(responseBody).contains("sessionExpiresInSeconds")
    }

    @Test
    fun `잠금된 계정은 로그인할 수 없다`() {
        val lockedUser = userAccountRepository.save(
            UserAccount(
                tenant = activeTenant,
                username = "locked",
                email = "locked@example.com",
                passwordHash = passwordEncoder.encode("Secret123!"),
                status = UserAccountStatus.LOCKED
            )
        )

        val payload = mapOf(
            "tenantId" to activeTenant.id,
            "username" to lockedUser.username,
            "password" to "Secret123!"
        )

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isLocked)
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
    }

    @Test
    fun `비활성 테넌트는 로그인할 수 없다`() {
        val inactiveTenant = tenantRepository.save(
            Tenant(
                code = "TENANT-2",
                name = "비활성 테넌트",
                status = TenantStatus.SUSPENDED
            )
        )

        userAccountRepository.save(
            UserAccount(
                tenant = inactiveTenant,
                username = "bob",
                email = "bob@example.com",
                passwordHash = passwordEncoder.encode("Another123!")
            )
        )

        val payload = mapOf(
            "tenantId" to inactiveTenant.id,
            "username" to "bob",
            "password" to "Another123!"
        )

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
    }

    @Test
    fun `로그아웃 시 세션이 무효화된다`() {
        val payload = mapOf(
            "tenantId" to activeTenant.id,
            "username" to activeUser.username,
            "password" to "P@ssw0rd!"
        )

        val loginResult = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andReturn()

        val sessionId = objectMapper.readTree(loginResult.response.contentAsByteArray)["sessionId"].asText()
        val sessionCookie = loginResult.response.getCookie("CGIAMSESSION")

        assertThat(sessionId).isNotBlank
        assertThat(sessionCookie).isNotNull

        mockMvc.perform(
            get("/auth/me")
                .cookie(sessionCookie!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(activeUser.username))
            .andExpect(jsonPath("$.mfaVerified").value(true))

        val logoutResult = mockMvc.perform(
            post("/auth/logout")
                .cookie(sessionCookie)
        )
            .andExpect(status().isOk)
            .andReturn()

        val expiredCookie = logoutResult.response.getCookie("CGIAMSESSION")
        assertThat(expiredCookie).isNotNull
        assertThat(expiredCookie!!.maxAge).isZero

        val meResult = mockMvc.perform(get("/auth/me"))
            .andReturn()

        assertThat(meResult.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }
}
