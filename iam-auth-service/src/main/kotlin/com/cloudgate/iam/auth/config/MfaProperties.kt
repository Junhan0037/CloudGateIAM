package com.cloudgate.iam.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * MFA(TOTP) 동작을 제어하는 설정 값 모음
 */
@ConfigurationProperties(prefix = "auth.mfa")
data class MfaProperties(
    val issuer: String = "CloudGate IAM",
    val digits: Int = 6,
    val periodSeconds: Long = 30,
    val allowedDriftWindows: Int = 1
) {
    init {
        require(digits in 6..8) { "TOTP 자릿수는 6~8 사이여야 합니다." }
        require(periodSeconds in 15..120) { "TOTP 주기는 15~120초 사이여야 합니다." }
        require(allowedDriftWindows >= 0) { "허용 드리프트 윈도우는 0 이상이어야 합니다." }
    }
}
