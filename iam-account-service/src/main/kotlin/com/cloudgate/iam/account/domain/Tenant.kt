package com.cloudgate.iam.account.domain

import com.cloudgate.iam.common.domain.BaseEntity
import com.cloudgate.iam.common.domain.RegionCode
import com.cloudgate.iam.common.domain.TenantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 멀티테넌트 환경에서 조직을 나타내는 엔티티로 외부 노출용 코드, 상태, 기본 리전 정보를 보유
 */
@Entity
@Table(
    name = "tenants",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tenants_code", columnNames = ["code"])
    ],
    indexes = [
        Index(name = "idx_tenants_region", columnList = "region"),
        Index(name = "idx_tenants_status", columnList = "status")
    ]
)
class Tenant(
    @Column(name = "code", nullable = false, length = 64)
    val code: String,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: TenantStatus = TenantStatus.ACTIVE,

    region: String,

    @Column(name = "description", length = 255)
    var description: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "region", nullable = false, length = 30)
    var region: String = RegionCode.normalize(region)
        private set(value) {
            field = RegionCode.normalize(value)
        }

    init {
        require(code.isNotBlank()) { "테넌트 코드는 비어 있을 수 없습니다." }
        require(name.isNotBlank()) { "테넌트 이름은 비어 있을 수 없습니다." }
    }

    /**
     * 리전 변경 시에도 일관되게 정규화하도록 setter를 커스터마이징
     */
    fun updateRegion(newRegion: String) {
        region = RegionCode.normalize(newRegion)
    }
}
