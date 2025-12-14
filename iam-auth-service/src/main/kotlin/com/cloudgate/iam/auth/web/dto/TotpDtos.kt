package com.cloudgate.iam.auth.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * TOTP 등록을 위한 정보(시크릿, otpauth URI)를 담아 응답
 */
data class TotpSetupResponse(
    val secret: String,
    val provisioningUri: String,
    val issuer: String,
    val accountLabel: String,
    val mfaAlreadyEnabled: Boolean
)

/**
 * OTP 코드 입력 요청 DTO
 */
data class TotpVerifyRequest(
    @field:NotBlank
    val code: String
)

/**
 * MFA 활성화 및 세션 검증 상태를 반환
 */
data class MfaVerificationResponse(
    val mfaEnabled: Boolean,
    val mfaVerified: Boolean
)
