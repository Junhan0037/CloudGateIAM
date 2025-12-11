package com.cloudgate.iam.auth.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

/**
 * Spring Authorization Server 기본 설정을 구성하여 OAuth2/OIDC 메타데이터와 토큰 엔드포인트를 활성화
 */
@Configuration
class AuthorizationServerConfig(
    private val authServerProperties: AuthServerProperties
) {

    /**
     * Authorization Server 전용 시큐리티 필터 체인 (우선순위 1)
     */
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Authorization Server 전용 컨피규러를 명시 생성
        val authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer()
        // OIDC 메타데이터/엔드포인트를 활성화
        authorizationServerConfigurer.oidc(Customizer.withDefaults())

        // Authorization Server가 노출하는 엔드포인트 패턴 추출
        val endpointsMatcher = authorizationServerConfigurer.endpointsMatcher

        // AS 엔드포인트에만 시큐리티 체인을 적용해 다른 체인과 충돌을 방지
        http
            .securityMatcher(endpointsMatcher)
            // 모든 AS 엔드포인트 접근 시 인증 필요
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            // 토큰/메타데이터 호출은 CSRF 대상에서 제외
            .csrf { it.ignoringRequestMatchers(endpointsMatcher) }
            // 인증되지 않은 요청은 로그인 페이지로 이동하도록 처리
            .exceptionHandling { it.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/login")) }
            // Authorization Server 표준 설정 적용
            .with(authorizationServerConfigurer, Customizer.withDefaults())
            // JWT 검증 기반 리소스 서버 설정을 추가해 토큰 엔드포인트 보호
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }

        return http.build()
    }

    /**
     * 테스트용 인메모리 클라이언트를 등록 (Authorization Code + PKCE + Refresh Token)
     * - 데모용 단일 클라이언트를 인메모리에 등록
     */
    @Bean
    fun registeredClientRepository(passwordEncoder: PasswordEncoder): RegisteredClientRepository {
        val registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
            // 클라이언트 인증 정보는 프로퍼티에서 주입받아 해시 처리
            .clientId(authServerProperties.clientId)
            .clientSecret(passwordEncoder.encode(authServerProperties.clientSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri(authServerProperties.redirectUri)
            .scope("openid")
            .scope("profile")
            .scope("offline_access")
            .build()

        return InMemoryRegisteredClientRepository(registeredClient)
    }

    /**
     * 샘플 환경에서는 인메모리 저장소 사용 (운영 시 외부 저장소로 대체)
     */
    @Bean
    fun authorizationService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationService =
        InMemoryOAuth2AuthorizationService()

    /**
     * OAuth2 동의 정보를 관리하는 인메모리 Consent 서비스 빈을 등록
     */
    @Bean
    fun authorizationConsentService(): OAuth2AuthorizationConsentService =
        InMemoryOAuth2AuthorizationConsentService() // 동의 정보 역시 인메모리로 관리

    /**
     * Authorization Server 메타데이터(issuer 등) 설정 빈을 등록
     */
    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer(authServerProperties.issuer) // 외부 설정으로 주입된 issuer를 사용해 메타데이터 고정
            .build()

    /**
     * JWT 서명/검증에 사용할 JWK 소스를 생성
     */
    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        // RSA 키를 단일 인스턴스로 생성하여 JWK 세트로 노출
        val rsaKey = generateRsa()
        val jwkSet = JWKSet(rsaKey)

        return ImmutableJWKSet(jwkSet)
    }

    /**
     * Authorization Server용 JWT 디코더를 구성
     */
    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource) // Authorization Server 설정과 동일한 JWK 소스를 사용해 JWT 디코더 구성

    /**
     * RSA 키 쌍을 생성하여 JWK로 변환
     */
    private fun generateRsa(): RSAKey {
        // AS 서명/검증을 위한 2048비트 RSA 키를 생성
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey

        return RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build()
    }
}
