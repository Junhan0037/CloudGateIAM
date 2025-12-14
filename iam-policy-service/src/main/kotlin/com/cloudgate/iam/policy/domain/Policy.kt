package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 멀티테넌트 ABAC 정책을 표현하는 엔티티로, 리소스·액션·조건과 효과를 저장
 * 조건은 JSON DSL 원문을 그대로 저장하여 향후 파서와 평가 엔진에서 해석
 */
@Entity
@Table(
    name = "policies",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_policies_tenant_name", columnNames = ["tenant_id", "name"])
    ],
    indexes = [
        Index(name = "idx_policies_tenant_resource_active", columnList = "tenant_id, resource, is_active"),
        Index(name = "idx_policies_active_priority", columnList = "is_active, priority")
    ]
)
class Policy(
    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Column(name = "name", nullable = false, length = 150)
    val name: String,

    @Column(name = "resource", nullable = false, length = 150)
    val resource: String,

    actionSet: Set<String>,

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 10)
    var effect: PolicyEffect,

    conditionPayload: String,

    @Column(name = "priority", nullable = false)
    var priority: Int = DEFAULT_PRIORITY,

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true,

    @Column(name = "description", length = 255)
    var description: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Lob
    @Column(name = "condition_json", nullable = false, columnDefinition = "text")
    var conditionJson: String = conditionPayload.trim()

    // 정책이 적용되는 액션 식별자 집합을 별도 테이블에 저장해 다중 액션 정책을 지원
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "policy_actions",
        joinColumns = [JoinColumn(name = "policy_id")],
        foreignKey = ForeignKey(name = "fk_policy_actions_policy"),
        uniqueConstraints = [
            UniqueConstraint(name = "uk_policy_actions_policy_action", columnNames = ["policy_id", "action"])
        ]
    )
    @Column(name = "action", nullable = false, length = 150)
    val actions: MutableSet<String> = actionSet
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableSet()

    init {
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
        require(name.isNotBlank()) { "정책 이름은 비어 있을 수 없습니다." }
        require(resource.isNotBlank()) { "정책 리소스는 비어 있을 수 없습니다." }
        require(conditionJson.isNotBlank()) { "정책 조건 JSON이 비어 있을 수 없습니다." }
        require(priority >= 0) { "정책 우선순위는 0 이상이어야 합니다." }
        require(actions.isNotEmpty()) { "정책에는 최소 한 개 이상의 액션이 필요합니다." }
    }

    companion object {
        const val DEFAULT_PRIORITY: Int = 100
    }
}
