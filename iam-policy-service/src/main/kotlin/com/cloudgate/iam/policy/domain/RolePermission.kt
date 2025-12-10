package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 역할과 권한 간 다대다 매핑 엔티티로, 역할이 보유한 권한을 정의
 */
@Entity
@Table(
    name = "role_permissions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_role_permissions_role_permission", columnNames = ["role_id", "permission_id"])
    ],
    indexes = [
        Index(name = "idx_role_permissions_role", columnList = "role_id")
    ]
)
class RolePermission(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    val permission: Permission,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
    }
}
