package com.cloudgate.iam.policy.service

import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeType
import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import com.cloudgate.iam.policy.audit.PolicyAuditEventPublisher
import com.cloudgate.iam.policy.config.ClockConfig
import com.cloudgate.iam.policy.domain.Policy
import com.cloudgate.iam.policy.domain.PolicyEffect
import com.cloudgate.iam.policy.domain.PolicyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(
    PolicyChangeService::class,
    ClockConfig::class,
    TenantFilterConfiguration::class,
    PolicyChangeServiceTest.TestConfig::class
)
class PolicyChangeServiceTest @Autowired constructor(
    private val policyChangeService: PolicyChangeService,
    private val policyRepository: PolicyRepository,
    private val recordingPublisher: RecordingPolicyAuditEventPublisher
) {

    @Test
    fun `정책 생성 시 감사 이벤트가 발행된다`() {
        recordingPublisher.events.clear()

        val command = CreatePolicyCommand(
            tenantId = 1L,
            name = "allow-read",
            resource = "compute.instance",
            actions = setOf("compute.instance:read"),
            effect = PolicyEffect.ALLOW,
            conditionJson = """{"user.department":"DEV"}""",
            actorId = 1001L,
            actorName = "tenant-admin"
        )

        val created = policyChangeService.createPolicy(command)

        assertThat(created.id).isNotNull()
        assertThat(policyRepository.count()).isEqualTo(1)

        assertThat(recordingPublisher.events).hasSize(1)
        val event = recordingPublisher.events.first()
        assertThat(event.changeType).isEqualTo(PolicyChangeType.CREATED)
        assertThat(event.policyId).isEqualTo(created.id)
        assertThat(event.actions).containsExactlyInAnyOrderElementsOf(command.actions)
    }

    @Test
    fun `정책 수정 시 변경 요약이 감사 이벤트에 포함된다`() {
        recordingPublisher.events.clear()

        val existing = policyRepository.save(
            Policy(
                tenantId = 2L,
                name = "deny-external",
                resource = "storage.bucket",
                actionSet = setOf("storage.bucket:write"),
                effect = PolicyEffect.DENY,
                conditionPayload = """{"env.ip":"10.0.0.0/8"}"""
            )
        )

        val command = UpdatePolicyCommand(
            policyId = existing.id!!,
            tenantId = existing.tenantId,
            actions = setOf("storage.bucket:*"),
            effect = PolicyEffect.ALLOW,
            conditionJson = """{"env.ip":"0.0.0.0/0"}""",
            priority = 5,
            active = true,
            actorId = 1002L,
            actorName = "system-admin"
        )

        val updated = policyChangeService.updatePolicy(command)

        assertThat(updated.actions).contains("storage.bucket:*")
        assertThat(updated.effect).isEqualTo(PolicyEffect.ALLOW)
        assertThat(updated.priority).isEqualTo(5)

        assertThat(recordingPublisher.events).hasSize(1)
        val event = recordingPublisher.events.first()
        assertThat(event.changeType).isEqualTo(PolicyChangeType.UPDATED)
        assertThat(event.changeSummary).contains("actions")
        assertThat(event.changeSummary).contains("effect")
        assertThat(event.policyId).isEqualTo(updated.id)
    }

    /**
     * 테스트에서 감사 발행 호출을 기록하기 위한 더미 구현체
     */
    class RecordingPolicyAuditEventPublisher : PolicyAuditEventPublisher {
        val events: MutableList<PolicyChangeAuditEvent> = mutableListOf()

        override fun publish(event: PolicyChangeAuditEvent) {
            events += event
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun recordingPolicyAuditEventPublisher(): RecordingPolicyAuditEventPublisher =
            RecordingPolicyAuditEventPublisher()
    }
}
