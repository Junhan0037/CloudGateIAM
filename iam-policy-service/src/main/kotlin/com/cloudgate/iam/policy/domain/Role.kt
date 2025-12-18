package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.BaseEntity
import com.cloudgate.iam.common.domain.RoleScope
import com.cloudgate.iam.common.tenant.TENANT_FILTER_NAME
import com.cloudgate.iam.common.tenant.TENANT_FILTER_PARAM
import jakarta.persistence.*
import org.hibernate.annotations.Filter

/**
 * RBAC 역할 정의 엔티티로, 테넌트 단위 혹은 시스템 전역 역할을 표현
 */
@Entity
@Filter(name = TENANT_FILTER_NAME, condition = "tenant_id = :$TENANT_FILTER_PARAM")
@Table(
    name = "roles",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_roles_name_scope", columnNames = ["tenant_id", "name", "scope"])
    ],
    indexes = [
        Index(name = "idx_roles_tenant_scope", columnList = "tenant_id, scope")
    ]
)
class Role(
    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30)
    val scope: RoleScope,

    @Column(name = "description", length = 255)
    var description: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
        require(name.isNotBlank()) { "역할 이름은 비어 있을 수 없습니다." }
    }
}
