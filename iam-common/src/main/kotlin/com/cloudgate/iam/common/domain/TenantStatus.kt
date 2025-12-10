package com.cloudgate.iam.common.domain

/**
 * 테넌트의 라이프사이클 상태를 정의
 */
enum class TenantStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
