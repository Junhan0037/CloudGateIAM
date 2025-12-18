package com.cloudgate.iam.common.domain

import com.cloudgate.iam.common.tenant.TENANT_FILTER_NAME
import com.cloudgate.iam.common.tenant.TENANT_FILTER_PARAM
import jakarta.persistence.*
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import java.time.Instant

/**
 * JPA 공통 엔티티 베이스로, 생성/수정 시각과 버전을 일관되게 관리
 */
@MappedSuperclass
@FilterDef(
    name = TENANT_FILTER_NAME,
    parameters = [ParamDef(name = TENANT_FILTER_PARAM, type = Long::class)]
)
abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
