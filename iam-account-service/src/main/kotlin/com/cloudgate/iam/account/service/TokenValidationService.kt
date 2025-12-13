package com.cloudgate.iam.account.service

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service

/**
 * 외부로부터 전달된 Access/ID 토큰의 무결성과 만료 여부를 검증
 */
@Service
class TokenValidationService(
    private val jwtDecoder: JwtDecoder
) {

    /**
     * JWT 디코더를 사용해 토큰을 검증하고, 실패 시 사용자 친화 메시지로 래핑
     */
    fun decode(rawToken: String): Jwt =
        try {
            jwtDecoder.decode(rawToken)
        } catch (ex: JwtException) {
            throw InvalidTokenException("유효하지 않은 토큰이거나 만료된 토큰입니다.", ex)
        }
}
