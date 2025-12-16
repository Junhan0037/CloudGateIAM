package com.cloudgate.iam.audit.domain

import org.springframework.data.jpa.repository.JpaRepository

interface LoginAuditRecordRepository : JpaRepository<LoginAuditRecord, Long> {
    fun existsByEventId(eventId: String): Boolean
}
