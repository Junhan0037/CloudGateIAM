package com.cloudgate.iam.account.web

import com.cloudgate.iam.account.support.TestJwtConfig
import com.cloudgate.iam.account.support.TestJwtFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig::class)
class ResourceServerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtFactory: TestJwtFactory
) {

    @Test
    fun `Bearer 토큰 없이 보호 자원 호출 시 401을 반환한다`() {
        mockMvc.perform(get("/api/accounts/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `유효한 액세스 토큰으로 현재 사용자 정보를 조회한다`() {
        val accessToken = jwtFactory.createAccessToken(
            tenantId = 101L,
            tenantCode = "TENANT-101",
            userId = 501L,
            roles = listOf("TENANT_ADMIN"),
            scopes = setOf("profile")
        )

        mockMvc.perform(
            get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tenantId").value(101))
            .andExpect(jsonPath("$.tenantCode").value("TENANT-101"))
            .andExpect(jsonPath("$.userId").value(501))
            .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"))
            .andExpect(jsonPath("$.scopes[0]").value("profile"))
    }

    @Test
    fun `ID Token 검증 엔드포인트는 서명된 토큰만 허용한다`() {
        val accessToken = jwtFactory.createAccessToken(scopes = setOf("profile"))
        val idToken = jwtFactory.createIdToken(
            tenantId = 202L,
            tenantCode = "TENANT-202",
            userId = 808L,
            roles = listOf("TENANT_USER")
        )

        val requestBody = """
            {
              "token": "$idToken",
              "tokenType": "ID_TOKEN"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tenantId").value(202))
            .andExpect(jsonPath("$.tenantCode").value("TENANT-202"))
            .andExpect(jsonPath("$.roles[0]").value("TENANT_USER"))
    }

    @Test
    fun `검증되지 않는 토큰이면 401 에러를 반환한다`() {
        val malformedToken = "invalid-token"

        mockMvc.perform(
            get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $malformedToken")
        )
            .andExpect(status().isUnauthorized)
    }
}
