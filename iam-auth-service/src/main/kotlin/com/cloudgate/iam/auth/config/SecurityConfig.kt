package com.cloudgate.iam.auth.config

import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * Spring Security 설정으로 세션 기반 인증과 로그인 엔드포인트를 보호한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(
        tenantAuthenticationProvider: TenantUsernamePasswordAuthenticationProvider
    ): AuthenticationManager =
        ProviderManager(listOf(tenantAuthenticationProvider))

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tenantAuthenticationProvider: TenantUsernamePasswordAuthenticationProvider,
        securityContextRepository: SecurityContextRepository
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authenticationProvider(tenantAuthenticationProvider)
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
            }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/auth/login").permitAll()
                    .anyRequest().authenticated()
            }
            .securityContext { it.securityContextRepository(securityContextRepository()) }
            .logout(Customizer.withDefaults())

        return http.build()
    }
}
