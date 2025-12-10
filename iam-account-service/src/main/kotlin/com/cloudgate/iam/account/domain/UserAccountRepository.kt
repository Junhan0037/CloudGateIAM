package com.cloudgate.iam.account.domain

import org.springframework.data.jpa.repository.JpaRepository

interface UserAccountRepository : JpaRepository<UserAccount, Long> {
    fun findByTenantIdAndUsername(tenantId: Long, username: String): UserAccount?
    fun existsByTenantIdAndEmail(tenantId: Long, email: String): Boolean
    fun existsByTenantIdAndUsername(tenantId: Long, username: String): Boolean
}
