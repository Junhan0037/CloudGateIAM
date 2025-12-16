package com.cloudgate.iam.common.tenant

import jakarta.persistence.EntityManager
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * tenant_id 컬럼 기반 Hibernate 필터를 전역으로 적용하기 위한 설정
 * - @Transactional 경계 진입 시 현재 TenantContext가 있으면 필터를 활성화하고, 종료 시 해제
 */
@Configuration
@ComponentScan("com.cloudgate.iam.common.tenant")
class TenantFilterConfiguration {

    @Bean
    fun tenantFilterAspect(entityManager: EntityManager): TenantFilterAspect =
        TenantFilterAspect(entityManager)
}

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class TenantFilterAspect(
    private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    fun applyTenantFilter(joinPoint: ProceedingJoinPoint): Any? {
        val tenantId = TenantContextHolder.currentTenantId()
        if (tenantId == null) {
            logger.debug("테넌트 컨텍스트가 없어 멀티테넌시 필터를 적용하지 않습니다. method={}", joinPoint.signature.toShortString())
            return joinPoint.proceed()
        }

        val session: Session = entityManager.unwrap(Session::class.java)
        val alreadyEnabled = session.getEnabledFilter(TENANT_FILTER_NAME) != null
        if (!alreadyEnabled) {
            session.enableFilter(TENANT_FILTER_NAME)
                .setParameter(TENANT_FILTER_PARAM, tenantId)
        }

        return try {
            joinPoint.proceed()
        } finally {
            if (!alreadyEnabled) {
                session.disableFilter(TENANT_FILTER_NAME)
            }
        }
    }
}
