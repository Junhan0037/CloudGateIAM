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
 * 사용자와 역할의 매핑 정보를 저장해 멀티테넌시 RBAC을 구성
 */
@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_roles_assignment", columnNames = ["tenant_id", "user_id", "role_id", "project_id"])
    ],
    indexes = [
        Index(name = "idx_user_roles_user", columnList = "tenant_id, user_id")
    ]
)
class UserRoleAssignment(
    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @Column(name = "project_id", nullable = false)
    val projectId: Long = NO_PROJECT
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(tenantId >= 0) { "테넌트 ID는 음수가 될 수 없습니다." }
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다." }
        require(projectId >= 0) { "프로젝트 ID는 음수가 될 수 없습니다." }
    }

    companion object {
        const val NO_PROJECT: Long = 0
    }
}
