package com.cloudgate.iam.policy.domain

import org.springframework.data.jpa.repository.JpaRepository

interface PolicyRepository : JpaRepository<Policy, Long> {
    fun findByTenantIdAndResourceAndActiveTrueOrderByPriorityAscIdAsc(tenantId: Long, resource: String): List<Policy>
    fun findByTenantIdAndActiveTrue(tenantId: Long): List<Policy>
    fun findByTenantIdAndName(tenantId: Long, name: String): Policy?
    fun existsByTenantIdAndName(tenantId: Long, name: String): Boolean
}
