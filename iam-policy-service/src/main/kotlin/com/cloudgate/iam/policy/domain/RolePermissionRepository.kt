package com.cloudgate.iam.policy.domain

import org.springframework.data.jpa.repository.JpaRepository

interface RolePermissionRepository : JpaRepository<RolePermission, Long> {
    fun findByRoleId(roleId: Long): List<RolePermission>
    fun existsByRoleIdAndPermissionId(roleId: Long, permissionId: Long): Boolean
}
