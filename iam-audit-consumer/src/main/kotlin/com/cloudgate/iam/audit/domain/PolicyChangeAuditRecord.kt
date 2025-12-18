package com.cloudgate.iam.audit.domain

import com.cloudgate.iam.common.domain.BaseEntity
import com.cloudgate.iam.common.event.PolicyChangeType
import jakarta.persistence.*
import java.time.Instant

/**
 * 정책 변경 감사 이벤트를 저장하는 엔티티
 * - Kafka 소비 시점의 이벤트 스냅샷을 그대로 기록
 */
@Entity
@Table(
    name = "policy_audit_events",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_policy_audit_event_id", columnNames = ["event_id"])
    ],
    indexes = [
        Index(name = "idx_policy_audit_tenant_time", columnList = "tenant_id, occurred_at"),
        Index(name = "idx_policy_audit_policy", columnList = "policy_id")
    ]
)
class PolicyChangeAuditRecord(
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

    @Column(name = "policy_id", nullable = false)
    val policyId: Long,

    @Column(name = "policy_name", nullable = false, length = 150)
    val policyName: String,

    @Column(name = "resource", nullable = false, length = 150)
    val resource: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    val changeType: PolicyChangeType,

    @Column(name = "priority", nullable = false)
    val priority: Int,

    @Column(name = "is_active", nullable = false)
    val active: Boolean,

    @Column(name = "effect", nullable = false, length = 20)
    val effect: String,

    @Column(name = "actor_id")
    val actorId: Long? = null,

    @Column(name = "actor_name", length = 150)
    val actorName: String? = null,

    @Column(name = "change_summary", length = 255)
    val changeSummary: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    // 정책 변경 시 복수 액션을 기록하기 위한 별도 테이블
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "policy_audit_actions",
        joinColumns = [JoinColumn(name = "policy_audit_event_id")],
        foreignKey = ForeignKey(name = "fk_policy_audit_actions_event"),
        uniqueConstraints = [
            UniqueConstraint(name = "uk_policy_audit_event_action", columnNames = ["policy_audit_event_id", "action"])
        ]
    )
    @Column(name = "action", nullable = false, length = 150)
    val actions: MutableSet<String> = mutableSetOf()

    init {
        require(eventId.isNotBlank()) { "이벤트 ID는 비어 있을 수 없습니다." }
        require(eventType.isNotBlank()) { "이벤트 타입은 비어 있을 수 없습니다." }
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
        require(policyId >= 0) { "정책 ID는 음수가 될 수 없습니다." }
        require(policyName.isNotBlank()) { "정책 이름은 비어 있을 수 없습니다." }
        require(resource.isNotBlank()) { "리소스는 비어 있을 수 없습니다." }
    }
}
