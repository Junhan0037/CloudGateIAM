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
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
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
            // 클라이언트가 토큰 엔드포인트 등에 접근할 때 사용하는 인증 정보
            .clientId(authServerProperties.clientId)
            // 보통 bcrypt 해시로 저장. 인증 시에는 “비교”만 하고 원문은 복원 불가
            .clientSecret(passwordEncoder.encode(authServerProperties.clientSecret))
            // HTTP Authorization: Basic base64(clientId:clientSecret) 헤더로 인증하는 방식
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            // “로그인 후 Authorization Code를 받고 → 토큰으로 교환”하는 표준 플로우
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            // Access Token 만료 후, Refresh Token으로 재발급 받는 플로우
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            // Authorization Code 발급 후 사용자를 돌려보낼 주소. 등록된 URI와 정확히 일치해야 발급이 됨
            .redirectUri(authServerProperties.redirectUri)
            // OIDC를 쓰겠다는 신호(없으면 그냥 OAuth2로 취급)
            .scope("openid")
            // 사용자 기본 프로필 클레임을 원한다는 의미(이름, 닉네임 등)
            .scope("profile")
            // 보통 “Refresh Token 발급 받겠다”는 의미로 쓰는 OIDC 관례 스코프
            .scope("offline_access")
            // Authorization Code 요청 시 PKCE(code_challenge/code_verifier) 검증을 강제
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(authServerProperties.requireProofKey) // PKCE 필수. 즉 Authorization Code 요청에 code_challenge가 들어와야 하고, 토큰 교환 시 code_verifier로 검증함
                    .requireAuthorizationConsent(false) // 데모 클라이언트는 기본 동의 절차를 생략
                    .build()
            )
            .build()

        return InMemoryRegisteredClientRepository(registeredClient)
    }

    /**
     * 발급된 코드/토큰/상태를 저장하는 서비스
     */
    @Bean
    fun authorizationService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationService =
        InMemoryOAuth2AuthorizationService()

    /**
     * OAuth2 사용자가 동의한 기록 정보를 관리
     */
    @Bean
    fun authorizationConsentService(): OAuth2AuthorizationConsentService =
        InMemoryOAuth2AuthorizationConsentService()

    /**
     * Authorization Server 메타데이터(issuer 등) 고정
     */
    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer(authServerProperties.issuer) // 토큰의 iss 클레임, OIDC discovery 문서의 issuer로 사용됨. 프록시/도메인/외부 URL과 정확히 맞춰야 검증 성공
            .build()

    /**
     * JWT 서명/검증에 사용할 JWK 소스를 생성
     */
    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        // RSA 키를 단일 인스턴스로 생성하여 JWK 세트로 노출
        val rsaKey = generateRsa()
        val jwkSet = JWKSet(rsaKey)

        return ImmutableJWKSet(jwkSet) // 메모리에 고정된 JWK 세트 (동적 회전 없음)
    }

    /**
     * 같은 JWK로 JWT 검증기 구성
     */
    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource) // 위에서 만든 JWK 소스를 기준으로 AS와 동일한 키로 검증하도록 맞춤

    /**
     * RSA 키 쌍을 생성하여 JWK로 변환
     */
    private fun generateRsa(): RSAKey {
        // RSA 키쌍 생성기
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        // 키 길이 2048비트(일반적인 기본 권장선)
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        // 공개키/개인키 타입 캐스팅
        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey

        return RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString()) // JWT 헤더의 kid 로 쓰이는 값
            .build()
    }
}
