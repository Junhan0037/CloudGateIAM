package com.cloudgate.iam.auth.audit

import com.cloudgate.iam.auth.security.AuthenticatedUserPrincipal
import com.cloudgate.iam.common.config.AuditKafkaProperties
import com.cloudgate.iam.common.event.LoginAuditEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 로그인 감사 이벤트 발행 인터페이스
 */
interface LoginAuditEventPublisher {
    fun publishLoginSuccess(
        principal: AuthenticatedUserPrincipal,
        sessionId: String,
        clientIp: String?,
        userAgent: String?
    )
}

/**
 * Kafka를 통해 로그인 감사 이벤트를 발행
 * - 실패 시 사용자 요청은 막지 않고 경고 로그만 남겨 운영자가 모니터링
 */
@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Primary
class KafkaLoginAuditEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val auditKafkaProperties: AuditKafkaProperties,
    private val clock: Clock
) : LoginAuditEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publishLoginSuccess(
        principal: AuthenticatedUserPrincipal,
        sessionId: String,
        clientIp: String?,
        userAgent: String?
    ) {
        val event = LoginAuditEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = Instant.now(clock),
            tenantId = principal.tenantId,
            source = auditKafkaProperties.clientId ?: "iam-auth-service",
            userId = principal.userId,
            username = principal.username,
            tenantCode = principal.tenantCode,
            tenantRegion = principal.tenantRegion,
            sessionId = sessionId,
            mfaVerified = principal.mfaVerified,
            clientIp = clientIp,
            userAgent = userAgent
        )

        kafkaTemplate.send(auditKafkaProperties.loginTopic, event.eventId, event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    logger.warn(
                        "로그인 감사 이벤트 발행 실패 eventId={} tenantId={} reason={}",
                        event.eventId,
                        event.tenantId,
                        ex.message
                    )
                } else {
                    logger.debug("로그인 감사 이벤트 발행 완료 eventId={} tenantId={}", event.eventId, event.tenantId)
                }
            }
    }
}

/**
 * 감사 발행을 끈 환경에서 사용되는 No-Op 구현체
 */
@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = ["enabled"], havingValue = "false")
class NoOpLoginAuditEventPublisher : LoginAuditEventPublisher {
    override fun publishLoginSuccess(
        principal: AuthenticatedUserPrincipal,
        sessionId: String,
        clientIp: String?,
        userAgent: String?
    ) {
        // Kafka 비활성화 환경에서는 감사 발행을 생략
    }
}
