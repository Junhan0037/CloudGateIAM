package com.cloudgate.iam.policy.audit

import com.cloudgate.iam.common.config.AuditKafkaProperties
import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * 정책 변경 이벤트 발행 인터페이스
 */
interface PolicyAuditEventPublisher {
    fun publish(event: PolicyChangeAuditEvent)
}

/**
 * Kafka에 정책 변경 이벤트를 적재
 */
@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Primary
class KafkaPolicyAuditEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val auditKafkaProperties: AuditKafkaProperties
) : PolicyAuditEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(event: PolicyChangeAuditEvent) {
        kafkaTemplate.send(auditKafkaProperties.policyTopic, event.eventId, event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    logger.warn(
                        "정책 감사 이벤트 발행 실패 eventId={} tenantId={} policyId={} reason={}",
                        event.eventId,
                        event.tenantId,
                        event.policyId,
                        ex.message
                    )
                } else {
                    logger.debug(
                        "정책 감사 이벤트 발행 완료 eventId={} tenantId={} policyId={}",
                        event.eventId,
                        event.tenantId,
                        event.policyId
                    )
                }
            }
    }
}

/**
 * 감사 발행이 비활성화된 환경에서 사용할 No-Op 구현체
 */
@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = ["enabled"], havingValue = "false")
class NoOpPolicyAuditEventPublisher : PolicyAuditEventPublisher {
    override fun publish(event: PolicyChangeAuditEvent) {
        // Kafka 비활성화 시 감사 발행을 스킵
    }
}
