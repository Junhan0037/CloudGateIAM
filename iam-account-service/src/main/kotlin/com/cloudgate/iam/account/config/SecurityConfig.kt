package com.cloudgate.iam.account.config

import com.cloudgate.iam.account.security.RoleBasedJwtConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * OAuth2 Resource Server 구성을 정의해 Bearer 토큰 기반 보호 API를 제공
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ResourceServerProperties::class)
class SecurityConfig(
    private val properties: ResourceServerProperties
) {

    /**
     * JWT 기반 액세스 토큰 검증을 적용한 시큐리티 필터 체인 구성
     */
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationConverter: JwtAuthenticationConverter
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // 서버에 세션 저장하지 않음
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/accounts/**", "/api/tokens/**").hasAuthority("SCOPE_profile")
                    .anyRequest().authenticated()
            }
            // OAuth2 Resource Server 모드로 동작
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwtConfigurer ->
                    // JWT 서명/클레임 검증 후 Authentication 만들 때, 기본 매핑 대신 커스텀 converter 주입
                    jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }

        return http.build()
    }

    /**
     * JWT 디코더를 issuer 혹은 명시적 JWK URI 기반으로 구성
     * Audience 조건이 설정된 경우 검증기에 함께 연결
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(NimbusJwtDecoder::class)
    @Profile("!test")
    fun jwtDecoder(): NimbusJwtDecoder {
        // jwkSetUri 가 명시되면 그걸로 공개키를 가져와 검증, 없다면 issuer 에서 OIDC discovery 를 따라가서 JWK 위치를 자동으로 찾음.
        val decoder: NimbusJwtDecoder = properties.jwkSetUri
            ?.takeIf { it.isNotBlank() }
            ?.let { NimbusJwtDecoder.withJwkSetUri(it).build() }
            ?: JwtDecoders.fromIssuerLocation(properties.issuer) as NimbusJwtDecoder

        // 검증기 체인 구성
        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>()
        validators += JwtValidators.createDefaultWithIssuer(properties.issuer) // 만료(exp), nbf 등 기본 검증 포함 (프레임워크 기본 validator 조합)

        if (properties.audiences.isNotEmpty()) {
            validators += AudienceValidator(properties.audiences) // audiences 클레임이 허용 목록 중 하나를 포함하는지 추가 검증
        }

        // 여러 검증기를 체인으로 묶음
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    /**
     * Scope 기반 권한과 roles 클레임 기반 RBAC 권한을 결합한 Authentication 변환기를 설정
     * - JWT -> Authentication 권한 변환
     */
    @Bean
    fun jwtAuthenticationConverter(roleBasedJwtConverter: RoleBasedJwtConverter): JwtAuthenticationConverter {
        // JWT의 scope 또는 scp 등을 읽어서 권한으로 변환
        val grantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("SCOPE_")
        }

        return JwtAuthenticationConverter().apply {
            // Authentication.getName()에 들어갈 값(대표 사용자 식별자)을 어떤 클레임으로 할지 지정
            setPrincipalClaimName("preferred_username")

            // scope 기반 권한(인가의 기본 단위) + roles 기반 권한(RBAC)을 합침
            setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
                val scopeAuthorities = grantedAuthoritiesConverter.convert(jwt).orEmpty()
                val roleAuthorities = roleBasedJwtConverter.convert(jwt)

                (scopeAuthorities + roleAuthorities)
            }
        }
    }

    /**
     * audience 클레임 검증기
     * - 설정된 audience 중 하나라도 포함되어야 유효 판정
     */
    private class AudienceValidator(private val audiences: Set<String>) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult {
            val tokenAudiences = token.audience.toSet()
            val matched = tokenAudiences.any { it in audiences }

            if (!matched) {
                val error = OAuth2Error(
                    "invalid_token",
                    "허용된 audience(${audiences.joinToString(",")})가 아닙니다.",
                    null
                )
                return OAuth2TokenValidatorResult.failure(error)
            }

            return OAuth2TokenValidatorResult.success()
        }
    }
}
