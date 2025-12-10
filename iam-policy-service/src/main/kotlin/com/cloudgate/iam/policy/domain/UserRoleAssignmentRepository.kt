package com.cloudgate.iam.policy.domain

import org.springframework.data.jpa.repository.JpaRepository

interface UserRoleAssignmentRepository : JpaRepository<UserRoleAssignment, Long> {
    fun findByTenantIdAndUserId(tenantId: Long, userId: Long): List<UserRoleAssignment>
    fun existsByTenantIdAndUserIdAndRoleId(tenantId: Long, userId: Long, roleId: Long): Boolean
}
