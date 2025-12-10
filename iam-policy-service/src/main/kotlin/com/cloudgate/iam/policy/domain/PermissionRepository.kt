package com.cloudgate.iam.policy.domain

import org.springframework.data.jpa.repository.JpaRepository

interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByResourceAndAction(resource: String, action: String): Permission?
    fun existsByResourceAndAction(resource: String, action: String): Boolean
}
