package com.cloudgate.iam.account.domain

import com.cloudgate.iam.common.domain.BaseEntity
import com.cloudgate.iam.common.domain.UserAccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 테넌트 소속 사용자를 나타내며, 인증·보안과 연계되는 최소 정보를 저장
 */
@Entity
@Table(
    name = "user_accounts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_accounts_username", columnNames = ["tenant_id", "username"]),
        UniqueConstraint(name = "uk_user_accounts_email", columnNames = ["tenant_id", "email"])
    ],
    indexes = [
        Index(name = "idx_user_accounts_tenant_status", columnList = "tenant_id, status"),
        Index(name = "idx_user_accounts_email", columnList = "email")
    ]
)
class UserAccount(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    val tenant: Tenant,

    @Column(name = "username", nullable = false, length = 80)
    val username: String,

    @Column(name = "email", nullable = false, length = 180)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: UserAccountStatus = UserAccountStatus.ACTIVE,

    @Column(name = "mfa_enabled", nullable = false)
    var mfaEnabled: Boolean = false,

    @Column(name = "mfa_secret", length = 120)
    var mfaSecret: String? = null,

    @Column(name = "pending_mfa_secret", length = 120)
    var pendingMfaSecret: String? = null,

    @Column(name = "mfa_enrolled_at")
    var mfaEnrolledAt: Instant? = null,

    @Column(name = "department", length = 120)
    var department: String? = null,

    @Column(name = "role_level", length = 60)
    var roleLevel: String? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    init {
        require(username.isNotBlank()) { "사용자 아이디는 비어 있을 수 없습니다." }
        require(email.isNotBlank()) { "사용자 이메일은 비어 있을 수 없습니다." }
        require(passwordHash.isNotBlank()) { "패스워드 해시가 누락되었습니다." }
    }
}
