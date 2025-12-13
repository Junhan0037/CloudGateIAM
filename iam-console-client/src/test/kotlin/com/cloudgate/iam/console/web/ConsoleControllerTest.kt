package com.cloudgate.iam.console.web

import com.cloudgate.iam.console.ConsoleApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model

@SpringBootTest(
    classes = [ConsoleApplication::class, OAuth2TestConfig::class],
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsoleControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val clientRegistration: ClientRegistration
) {

    @Test
    fun `미인증 사용자는 OIDC 로그인 페이지로 리다이렉트된다`() {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrlPattern("**/oauth2/authorization/minicloud"))
    }

    @Test
    fun `OIDC 로그인 사용자는 프로필 페이지를 볼 수 있다`() {
        mockMvc.perform(
            get("/")
                .with(
                    oidcLogin()
                        .clientRegistration(clientRegistration)
                        .idToken { token ->
                            token.claim("sub", "user-1")
                                .claim("preferred_username", "console-user")
                                .claim("tenantId", 1L)
                                .claim("tenantCode", "TENANT-1")
                                .claim("roles", listOf("TENANT_ADMIN"))
                                .claim("attributes", mapOf("department" to "ENG", "mfaEnabled" to true))
                        }
                )
        )
            .andExpect(status().isOk)
            .andExpect(view().name("index"))
            .andExpect(model().attributeExists("profile", "claims"))
    }
}

/**
 * 테스트 전용 OAuth2 Client 설정을 등록해 외부 메타데이터 의존성을 제거한다.
 */
@Configuration
@EnableWebSecurity
class OAuth2TestConfig {

    @Bean
    fun clientRegistration(): ClientRegistration =
        ClientRegistration.withRegistrationId("minicloud")
            .clientId("minicloud-console")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .scope("openid", "profile", "offline_access")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .jwkSetUri("http://localhost/jwks")
            .userNameAttributeName("sub")
            .clientName("MiniCloud Console")
            .build()

    @Bean
    fun clientRegistrationRepository(clientRegistration: ClientRegistration): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(clientRegistration)

    @Bean
    fun authorizedClientService(
        clientRegistrationRepository: ClientRegistrationRepository
    ): OAuth2AuthorizedClientService =
        InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)
}
