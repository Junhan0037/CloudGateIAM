package com.cloudgate.iam.account.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 리소스 서버가 신뢰할 수 있는 토큰 발급자 및 검증 파라미터를 정의하는 설정
 * - issuer: Authorization Server의 issuer 값
 * - jwkSetUri: 필요 시 명시적으로 설정할 JWK 세트 URL
 * - audiences: 허용된 audience 목록(비어 있으면 검사 생략)
 */
@ConfigurationProperties(prefix = "auth.resource")
data class ResourceServerProperties(
    val issuer: String = "http://localhost:8080",
    val jwkSetUri: String? = null,
    val audiences: Set<String> = emptySet()
)
