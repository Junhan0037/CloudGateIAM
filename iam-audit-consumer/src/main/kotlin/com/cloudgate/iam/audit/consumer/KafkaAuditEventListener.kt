package com.cloudgate.iam.audit.consumer

import com.cloudgate.iam.audit.service.AuditEventPersistenceService
import com.cloudgate.iam.common.event.LoginAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka에서 감사 이벤트를 구독해 영속화 서비스로 위임
 * - 비즈니스 예외를 그대로 던져 컨테이너의 재시도 정책을 따른다.
 */
@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class KafkaAuditEventListener(
    private val auditEventPersistenceService: AuditEventPersistenceService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 감사 이벤트를 소비해 DB에 적재
     */
    @KafkaListener(
        topics = ["\${audit.kafka.login-topic}"],
        groupId = "\${spring.kafka.consumer.group-id:iam-audit-consumer}"
    )
    fun consumeLoginAudit(event: LoginAuditEvent) {
        try {
            auditEventPersistenceService.saveLoginEvent(event)
        } catch (ex: Exception) {
            logger.error(
                "로그인 감사 이벤트 처리 실패 eventId={} tenantId={} reason={}",
                event.eventId,
                event.tenantId,
                ex.message,
                ex
            )
            throw ex
        }
    }

    /**
     * 정책 변경 감사 이벤트를 소비해 DB에 적재
     */
    @KafkaListener(
        topics = ["\${audit.kafka.policy-topic}"],
        groupId = "\${spring.kafka.consumer.group-id:iam-audit-consumer}"
    )
    fun consumePolicyChangeAudit(event: PolicyChangeAuditEvent) {
        try {
            auditEventPersistenceService.savePolicyChangeEvent(event)
        } catch (ex: Exception) {
            logger.error(
                "정책 감사 이벤트 처리 실패 eventId={} tenantId={} policyId={} reason={}",
                event.eventId,
                event.tenantId,
                event.policyId,
                ex.message,
                ex
            )
            throw ex
        }
    }
}
