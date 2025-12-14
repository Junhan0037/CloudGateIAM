package com.cloudgate.iam.policy.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
class PolicyRepositoryTest @Autowired constructor(
    private val policyRepository: PolicyRepository
) {

    @Test
    fun `활성 정책을 리소스 기준으로 우선순위 순서로 조회한다`() {
        val highPriority = policyRepository.save(
            Policy(
                tenantId = 1L,
                name = "compute-read",
                resource = "compute.instance",
                actionSet = setOf("compute.instance:read"),
                effect = PolicyEffect.ALLOW,
                conditionPayload = """{"user.department":"DEV"}""",
                priority = 10,
                active = true
            )
        )
        policyRepository.save(
            Policy(
                tenantId = 1L,
                name = "compute-deny",
                resource = "compute.instance",
                actionSet = setOf("compute.instance:write"),
                effect = PolicyEffect.DENY,
                conditionPayload = """{"user.roleLevel":"L3"}""",
                priority = 20,
                active = true
            )
        )
        policyRepository.save(
            Policy(
                tenantId = 1L,
                name = "compute-inactive",
                resource = "compute.instance",
                actionSet = setOf("compute.instance:*"),
                effect = PolicyEffect.ALLOW,
                conditionPayload = """{"env.ipRange":"10.0.0.0/8"}""",
                priority = 5,
                active = false
            )
        )

        val policies = policyRepository.findByTenantIdAndResourceAndActiveTrueOrderByPriorityAscIdAsc(
            tenantId = 1L,
            resource = "compute.instance"
        )

        assertThat(policies).hasSize(2)
        assertThat(policies.first().id).isEqualTo(highPriority.id)
        assertThat(policies.first().actions).containsExactly("compute.instance:read")
    }

    @Test
    fun `정책 이름 중복 여부를 테넌트 단위로 확인한다`() {
        policyRepository.save(
            Policy(
                tenantId = 42L,
                name = "storage-read",
                resource = "storage.bucket",
                actionSet = setOf("storage.bucket:read"),
                effect = PolicyEffect.ALLOW,
                conditionPayload = """{"user.riskLevel":"LOW"}"""
            )
        )

        assertThat(policyRepository.existsByTenantIdAndName(42L, "storage-read")).isTrue()
        assertThat(policyRepository.existsByTenantIdAndName(99L, "storage-read")).isFalse()
    }

    @Test
    fun `액션이 비어있는 정책은 생성할 수 없다`() {
        assertThatThrownBy {
            Policy(
                tenantId = 1L,
                name = "invalid-policy",
                resource = "compute.instance",
                actionSet = emptySet(),
                effect = PolicyEffect.ALLOW,
                conditionPayload = """{"user.department":"DEV"}"""
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("액션")
    }
}
