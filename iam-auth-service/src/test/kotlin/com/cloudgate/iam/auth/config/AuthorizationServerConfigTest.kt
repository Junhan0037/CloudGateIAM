package com.cloudgate.iam.auth.config

import com.cloudgate.iam.auth.AuthApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.hasItem
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

@SpringBootTest(classes = [AuthApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationServerConfigTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val registeredClientRepository: RegisteredClientRepository,
    private val authServerProperties: AuthServerProperties
) {

    @Test
    fun `OpenID Provider 메타데이터를 조회할 수 있다`() {
        mockMvc.perform(
            get("/.well-known/oauth-authorization-server")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("http://localhost:8080"))
            .andExpect(jsonPath("$.authorization_endpoint").exists())
            .andExpect(jsonPath("$.token_endpoint").exists())
    }

    @Test
    fun `Authorization Server 메타데이터에 PKCE 지원이 노출된다`() {
        mockMvc.perform(
            get("/.well-known/oauth-authorization-server")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code_challenge_methods_supported").isArray)
            .andExpect(jsonPath("$.code_challenge_methods_supported").value(hasItem("S256")))
    }

    @Test
    fun `JWK 세트 엔드포인트가 키를 반환한다`() {
        mockMvc.perform(
            get("/oauth2/jwks")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keys").isArray)
            .andExpect(jsonPath("$.keys[0].kty").exists())
    }

    @Test
    fun `등록된 클라이언트가 PKCE를 필수로 요구한다`() {
        val client = registeredClientRepository.findByClientId(authServerProperties.clientId)

        assertThat(client).isNotNull
        assertThat(client!!.clientSettings.isRequireProofKey).isTrue()
    }
}
