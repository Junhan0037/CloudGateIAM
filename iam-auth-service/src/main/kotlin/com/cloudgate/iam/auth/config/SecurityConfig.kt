package com.cloudgate.iam.auth.config

import com.cloudgate.iam.auth.security.TenantUsernamePasswordAuthenticationProvider
import com.cloudgate.iam.auth.web.dto.LogoutResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * Spring Security 설정으로 세션 기반 인증과 로그인 엔드포인트를 보호
 */
@Configuration
@EnableWebSecurity
@Order(2)
class SecurityConfig {

    @Bean
    fun logoutSuccessHandler(objectMapper: ObjectMapper) = { _: HttpServletRequest, response: HttpServletResponse, _: Authentication? ->
        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(LogoutResponse()))
    }

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
        securityContextRepository: SecurityContextRepository,
        logoutSuccessHandler: (HttpServletRequest, HttpServletResponse, Authentication?) -> Unit
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
                it.requestMatchers("/actuator/health", "/auth/login", "/auth/logout").permitAll()
                    .anyRequest().authenticated()
            }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .logout {
                it.logoutUrl("/auth/logout")
                    .deleteCookies("CGIAMSESSION")
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler(logoutSuccessHandler)
            }

        return http.build()
    }
}
