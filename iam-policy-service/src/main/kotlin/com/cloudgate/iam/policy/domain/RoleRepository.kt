package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.RoleScope
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByTenantIdAndName(tenantId: Long, name: String): Role?
    fun findByTenantIdAndScope(tenantId: Long, scope: RoleScope): List<Role>
    fun existsByTenantIdAndNameAndScope(tenantId: Long, name: String, scope: RoleScope): Boolean
}
