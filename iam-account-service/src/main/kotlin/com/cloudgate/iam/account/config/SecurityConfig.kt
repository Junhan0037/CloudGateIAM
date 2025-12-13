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
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/accounts/**", "/api/tokens/**").hasAuthority("SCOPE_profile")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwtConfigurer ->
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
        val decoder: NimbusJwtDecoder = properties.jwkSetUri
            ?.takeIf { it.isNotBlank() }
            ?.let { NimbusJwtDecoder.withJwkSetUri(it).build() }
            ?: JwtDecoders.fromIssuerLocation(properties.issuer) as NimbusJwtDecoder

        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>()
        validators += JwtValidators.createDefaultWithIssuer(properties.issuer)

        if (properties.audiences.isNotEmpty()) {
            validators += AudienceValidator(properties.audiences)
        }

        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    /**
     * Scope 기반 권한과 roles 클레임 기반 RBAC 권한을 결합한 Authentication 변환기를 설정
     */
    @Bean
    fun jwtAuthenticationConverter(roleBasedJwtConverter: RoleBasedJwtConverter): JwtAuthenticationConverter {
        val grantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("SCOPE_")
        }

        return JwtAuthenticationConverter().apply {
            setPrincipalClaimName("preferred_username")
            setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
                val scopeAuthorities = grantedAuthoritiesConverter.convert(jwt).orEmpty()
                val roleAuthorities = roleBasedJwtConverter.convert(jwt)

                (scopeAuthorities + roleAuthorities)
            }
        }
    }

    /**
     * audience 클레임 검증기. 설정된 audience 중 하나라도 포함되어야 유효 판정
     */
    private class AudienceValidator(
        private val audiences: Set<String>
    ) : OAuth2TokenValidator<Jwt> {

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
