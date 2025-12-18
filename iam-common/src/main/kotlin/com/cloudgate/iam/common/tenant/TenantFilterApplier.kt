package com.cloudgate.iam.common.tenant

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 현재 쓰레드의 tenantId를 읽어 Hibernate 필터를 활성화
 * - 멱등 적용: 이미 활성화된 경우 파라미터만 갱신
 */
@Component
class TenantFilterApplier(
    @PersistenceContext private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun enableForCurrentTenant() {
        val tenantId = TenantContextHolder.currentTenantId()
            ?: throw IllegalStateException("TenantContext가 설정되지 않았습니다.")

        val session: Session = entityManager.unwrap(Session::class.java)
        val filter = session.getEnabledFilter(TENANT_FILTER_NAME)
            ?: session.enableFilter(TENANT_FILTER_NAME)

        filter.setParameter(TENANT_FILTER_PARAM, tenantId)
        logger.debug("테넌트 필터 활성화 tenantId={}", tenantId)
    }
}
