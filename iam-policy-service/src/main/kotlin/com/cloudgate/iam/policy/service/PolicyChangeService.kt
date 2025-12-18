package com.cloudgate.iam.policy.service

import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeType
import com.cloudgate.iam.common.tenant.TenantContextHolder
import com.cloudgate.iam.common.tenant.TenantFilterApplier
import com.cloudgate.iam.policy.audit.PolicyAuditEventPublisher
import com.cloudgate.iam.policy.domain.Policy
import com.cloudgate.iam.policy.domain.PolicyEffect
import com.cloudgate.iam.policy.domain.PolicyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.*

/**
 * 정책 생성/수정 작업을 처리하고 감사 이벤트를 Kafka로 발행하는 서비스
 */
@Service
class PolicyChangeService(
    private val policyRepository: PolicyRepository,
    private val policyAuditEventPublisher: PolicyAuditEventPublisher,
    private val tenantFilterApplier: TenantFilterApplier,
    private val clock: Clock,
    @Value("\${spring.application.name:iam-policy-service}")
    private val serviceName: String
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 신규 정책을 저장하고 감사 이벤트를 발행
     */
    @Transactional
    fun createPolicy(command: CreatePolicyCommand): Policy =
        TenantContextHolder.withTenant(command.tenantId) {
            tenantFilterApplier.enableForCurrentTenant()
            if (policyRepository.existsByTenantIdAndName(command.tenantId, command.name)) {
                throw IllegalArgumentException("동일한 이름의 정책이 이미 존재합니다. tenantId=${command.tenantId}, name=${command.name}")
            }

            val normalizedActions = normalizeActions(command.actions)
            val policy = Policy(
                tenantId = command.tenantId,
                name = command.name.trim(),
                resource = command.resource.trim(),
                actionSet = normalizedActions,
                effect = command.effect,
                conditionPayload = command.conditionJson.trim(),
                priority = command.priority,
                active = command.active,
                description = command.description?.trim()
            )

            val savedPolicy = policyRepository.save(policy)
            publishAuditEvent(savedPolicy, PolicyChangeType.CREATED, command.actorId, command.actorName, "created")
            logger.info("정책 생성 완료 tenantId={} policyId={} name={}", savedPolicy.tenantId, savedPolicy.id, savedPolicy.name)

            return@withTenant savedPolicy
        }

    /**
     * 정책 속성을 업데이트하고 변경 사항을 감사 로그로 남김
     */
    @Transactional
    fun updatePolicy(command: UpdatePolicyCommand): Policy =
        TenantContextHolder.withTenant(command.tenantId) {
            tenantFilterApplier.enableForCurrentTenant()
            val policy = policyRepository.findById(command.policyId)
                .orElseThrow { IllegalArgumentException("정책을 찾을 수 없습니다. policyId=${command.policyId}") }

            if (policy.tenantId != command.tenantId) {
                throw IllegalArgumentException("다른 테넌트의 정책은 수정할 수 없습니다. tenantId=${command.tenantId}")
            }

            val changedFields = mutableListOf<String>()

            command.effect?.let {
                if (policy.effect != it) {
                    policy.effect = it
                    changedFields += "effect"
                }
            }

            command.priority?.let {
                require(it >= 0) { "정책 우선순위는 0 이상이어야 합니다." }
                if (policy.priority != it) {
                    policy.priority = it
                    changedFields += "priority"
                }
            }

            command.active?.let {
                if (policy.active != it) {
                    policy.active = it
                    changedFields += if (it) "activated" else "deactivated"
                }
            }

            command.description?.let {
                val trimmed = it.trim()
                if (policy.description != trimmed) {
                    policy.description = trimmed
                    changedFields += "description"
                }
            }

            command.conditionJson?.let {
                val normalized = it.trim()
                if (normalized.isBlank()) {
                    throw IllegalArgumentException("정책 조건은 비어 있을 수 없습니다.")
                }
                if (policy.conditionJson != normalized) {
                    policy.conditionJson = normalized
                    changedFields += "condition"
                }
            }

            command.actions?.let {
                val normalizedActions = normalizeActions(it)
                if (normalizedActions != policy.actions.toSet()) {
                    policy.actions.clear()
                    policy.actions.addAll(normalizedActions)
                    changedFields += "actions"
                }
            }

            val savedPolicy = policyRepository.save(policy)
            val summary = if (changedFields.isEmpty()) "no-op" else changedFields.joinToString(",")
            publishAuditEvent(savedPolicy, PolicyChangeType.UPDATED, command.actorId, command.actorName, summary)
            logger.info("정책 수정 완료 tenantId={} policyId={} changed={}", savedPolicy.tenantId, savedPolicy.id, summary)

            return@withTenant savedPolicy
        }

    private fun normalizeActions(actions: Set<String>): Set<String> =
        actions.map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .ifEmpty { throw IllegalArgumentException("정책 액션은 비어 있을 수 없습니다.") }

    private fun publishAuditEvent(
        policy: Policy,
        changeType: PolicyChangeType,
        actorId: Long?,
        actorName: String?,
        changeSummary: String?
    ) {
        val policyId = policy.id ?: throw IllegalStateException("정책 ID가 확정되지 않았습니다.")

        val event = PolicyChangeAuditEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = Instant.now(clock),
            tenantId = policy.tenantId,
            source = serviceName,
            policyId = policyId,
            policyName = policy.name,
            resource = policy.resource,
            actions = policy.actions.toSet(),
            effect = policy.effect.name,
            priority = policy.priority,
            active = policy.active,
            actorId = actorId,
            actorName = actorName,
            changeType = changeType,
            changeSummary = changeSummary
        )

        policyAuditEventPublisher.publish(event)
    }
}

/**
 * 정책 생성 요청 DTO
 */
data class CreatePolicyCommand(
    val tenantId: Long,
    val name: String,
    val resource: String,
    val actions: Set<String>,
    val effect: PolicyEffect,
    val conditionJson: String,
    val priority: Int = Policy.DEFAULT_PRIORITY,
    val active: Boolean = true,
    val description: String? = null,
    val actorId: Long?,
    val actorName: String?
)

/**
 * 정책 수정 요청 DTO
 */
data class UpdatePolicyCommand(
    val policyId: Long,
    val tenantId: Long,
    val actions: Set<String>? = null,
    val effect: PolicyEffect? = null,
    val conditionJson: String? = null,
    val priority: Int? = null,
    val active: Boolean? = null,
    val description: String? = null,
    val actorId: Long?,
    val actorName: String?
)
