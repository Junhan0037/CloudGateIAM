package com.cloudgate.iam.common.domain

/**
 * 사용자 계정의 기본 상태를 정의
 */
enum class UserAccountStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    LOCKED,
    SUSPENDED,
    DELETED
}
