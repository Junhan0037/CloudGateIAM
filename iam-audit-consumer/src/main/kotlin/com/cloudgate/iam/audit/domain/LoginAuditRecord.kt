package com.cloudgate.iam.audit.domain

import com.cloudgate.iam.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 로그인 성공 이벤트를 영속화하기 위한 엔티티
 * - Kafka에서 수신한 이벤트의 원본 정보를 최대한 보존
 */
@Entity
@Table(
    name = "login_audit_events",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_login_audit_event_id", columnNames = ["event_id"])
    ],
    indexes = [
        Index(name = "idx_login_audit_tenant_time", columnList = "tenant_id, occurred_at"),
        Index(name = "idx_login_audit_user_time", columnList = "user_id, occurred_at"),
        Index(name = "idx_login_audit_region_time", columnList = "tenant_region, occurred_at")
    ]
)
class LoginAuditRecord(
    @Column(name = "event_id", nullable = false, length = 64)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,

    @Column(name = "event_version", nullable = false)
    val eventVersion: Int,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Column(name = "source", nullable = false, length = 120)
    val source: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "username", nullable = false, length = 150)
    val username: String,

    @Column(name = "tenant_code", nullable = false, length = 100)
    val tenantCode: String,

    @Column(name = "tenant_region", nullable = false, length = 30)
    val tenantRegion: String,

    @Column(name = "session_id", nullable = false, length = 120)
    val sessionId: String,

    @Column(name = "mfa_verified", nullable = false)
    val mfaVerified: Boolean,

    @Column(name = "client_ip", length = 45)
    val clientIp: String? = null,

    @Column(name = "user_agent", length = 512)
    val userAgent: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(eventId.isNotBlank()) { "이벤트 ID는 비어 있을 수 없습니다." }
        require(eventType.isNotBlank()) { "이벤트 타입은 비어 있을 수 없습니다." }
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
        require(userId >= 0) { "사용자 ID는 음수가 될 수 없습니다." }
        require(username.isNotBlank()) { "사용자 이름은 비어 있을 수 없습니다." }
        require(sessionId.isNotBlank()) { "세션 ID는 비어 있을 수 없습니다." }
        require(tenantRegion.isNotBlank()) { "테넌트 리전은 비어 있을 수 없습니다." }
    }
}
