package com.cloudgate.iam.common.domain

/**
 * 역할이 적용되는 범위를 구분해 멀티스코프 RBAC을 표현
 */
enum class RoleScope {
    SYSTEM,
    TENANT,
    PROJECT
}
