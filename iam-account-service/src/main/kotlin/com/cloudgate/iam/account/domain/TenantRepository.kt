package com.cloudgate.iam.account.domain

import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findByCode(code: String): Tenant?
    fun existsByCode(code: String): Boolean
}
