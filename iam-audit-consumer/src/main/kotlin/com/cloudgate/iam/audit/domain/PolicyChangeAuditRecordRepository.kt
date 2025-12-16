package com.cloudgate.iam.audit.domain

import org.springframework.data.jpa.repository.JpaRepository

interface PolicyChangeAuditRecordRepository : JpaRepository<PolicyChangeAuditRecord, Long> {
    fun existsByEventId(eventId: String): Boolean
}
