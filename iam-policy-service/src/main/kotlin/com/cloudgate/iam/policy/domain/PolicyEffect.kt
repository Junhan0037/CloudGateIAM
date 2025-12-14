package com.cloudgate.iam.policy.domain

/**
 * ABAC 정책 평가 시 허용·거부 결정을 명시하는 효과 타입
 */
enum class PolicyEffect {
    ALLOW,
    DENY
}
