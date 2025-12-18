package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.tenant.TenantContextHolder
import com.cloudgate.iam.common.tenant.TenantFilterApplier
import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(TenantFilterConfiguration::class)
class TenantFilterIntegrationTest @Autowired constructor(
    private val policyRepository: PolicyRepository,
    private val tenantFilterApplier: TenantFilterApplier
) {

    @Test
    fun `필터가 활성화되면 다른 테넌트 정책은 조회되지 않는다`() {
        policyRepository.save(
            Policy(
                tenantId = 1L,
                name = "t1-allow",
                resource = "compute.instance",
                actionSet = setOf("compute.instance:read"),
                effect = PolicyEffect.ALLOW,
                conditionPayload = """{"user.department":"DEV"}"""
            )
        )
        policyRepository.save(
            Policy(
                tenantId = 2L,
                name = "t2-deny",
                resource = "compute.instance",
                actionSet = setOf("compute.instance:write"),
                effect = PolicyEffect.DENY,
                conditionPayload = """{"user.department":"QA"}"""
            )
        )

        val tenant1Policies = TenantContextHolder.withTenant(1L) {
            tenantFilterApplier.enableForCurrentTenant()
            policyRepository.findAll()
        }
        val tenant2Policies = TenantContextHolder.withTenant(2L) {
            tenantFilterApplier.enableForCurrentTenant()
            policyRepository.findAll()
        }

        assertThat(tenant1Policies).allMatch { it.tenantId == 1L }
        assertThat(tenant1Policies).hasSize(1)
        assertThat(tenant2Policies).allMatch { it.tenantId == 2L }
        assertThat(tenant2Policies).hasSize(1)
    }
}
