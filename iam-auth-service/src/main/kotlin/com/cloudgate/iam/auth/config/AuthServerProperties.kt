package com.cloudgate.iam.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Authorization Server 설정 값(issuer, 클라이언트 정보)을 프로퍼티로 관리
 */
@ConfigurationProperties(prefix = "auth.server")
data class AuthServerProperties(
    val issuer: String = "http://localhost:8080",
    val clientId: String = "minicloud-console",
    val clientSecret: String = "minicloud-secret",
    val redirectUri: String = "http://localhost:3000/login/oauth2/code/minicloud",
    val requireProofKey: Boolean = true // Authorization Code 플로우 시 PKCE 강제를 위한 플래그
)
