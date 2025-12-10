package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 리소스-액션 단위의 최소 권한 단위를 표현하며, 역할에 매핑해 RBAC을 구성
 */
@Entity
@Table(
    name = "permissions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_permissions_resource_action", columnNames = ["resource", "action"])
    ],
    indexes = [
        Index(name = "idx_permissions_resource", columnList = "resource")
    ]
)
class Permission(
    @Column(name = "resource", nullable = false, length = 100)
    val resource: String,

    @Column(name = "action", nullable = false, length = 50)
    val action: String,

    @Column(name = "description", length = 255)
    var description: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(resource.isNotBlank()) { "권한 리소스는 비어 있을 수 없습니다." }
        require(action.isNotBlank()) { "권한 액션은 비어 있을 수 없습니다." }
    }
}
