package com.cloudgate.iam.audit.service

import com.cloudgate.iam.audit.domain.LoginAuditRecord
import com.cloudgate.iam.audit.domain.LoginAuditRecordRepository
import com.cloudgate.iam.audit.domain.PolicyChangeAuditRecord
import com.cloudgate.iam.audit.domain.PolicyChangeAuditRecordRepository
import com.cloudgate.iam.common.event.LoginAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Kafka로부터 전달된 감사 이벤트를 DB에 적재하는 서비스
 * - 중복 이벤트 ID는 무시하여 멱등성을 보장
 * - 필드 길이를 제한해 스키마 제약으로 인한 실패를 방지
 */
@Service
class AuditEventPersistenceService(
    private val loginAuditRecordRepository: LoginAuditRecordRepository,
    private val policyChangeAuditRecordRepository: PolicyChangeAuditRecordRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 감사 이벤트를 저장
     */
    @Transactional
    fun saveLoginEvent(event: LoginAuditEvent) {
        if (loginAuditRecordRepository.existsByEventId(event.eventId)) {
            logger.debug("중복 로그인 감사 이벤트 수신으로 저장을 건너뜀 eventId={}", event.eventId)
            return
        }

        val record = LoginAuditRecord(
            eventId = event.eventId,
            eventType = event.eventType,
            eventVersion = event.version,
            occurredAt = event.occurredAt,
            tenantId = event.tenantId,
            source = truncate(event.source, 120) ?: event.source,
            userId = event.userId,
            username = truncate(event.username, 150) ?: event.username,
            tenantCode = truncate(event.tenantCode, 100) ?: event.tenantCode,
            tenantRegion = truncate(event.tenantRegion, 30) ?: event.tenantRegion,
            sessionId = truncate(event.sessionId, 120) ?: event.sessionId,
            mfaVerified = event.mfaVerified,
            clientIp = truncate(event.clientIp, 45),
            userAgent = truncate(event.userAgent, 512)
        )

        loginAuditRecordRepository.save(record)
        logger.debug(
            "로그인 감사 이벤트 저장 완료 eventId={} tenantId={} userId={}",
            event.eventId,
            event.tenantId,
            event.userId
        )
    }

    /**
     * 정책 변경 감사 이벤트를 저장
     */
    @Transactional
    fun savePolicyChangeEvent(event: PolicyChangeAuditEvent) {
        if (policyChangeAuditRecordRepository.existsByEventId(event.eventId)) {
            logger.debug("중복 정책 감사 이벤트 수신으로 저장을 건너뜀 eventId={}", event.eventId)
            return
        }

        val normalizedActions = normalizeActions(event.eventId, event.actions)
        val record = PolicyChangeAuditRecord(
            eventId = event.eventId,
            eventType = event.eventType,
            eventVersion = event.version,
            occurredAt = event.occurredAt,
            tenantId = event.tenantId,
            source = truncate(event.source, 120) ?: event.source,
            policyId = event.policyId,
            policyName = truncate(event.policyName, 150) ?: event.policyName,
            resource = truncate(event.resource, 150) ?: event.resource,
            changeType = event.changeType,
            priority = event.priority,
            active = event.active,
            effect = truncate(event.effect, 20) ?: event.effect,
            actorId = event.actorId,
            actorName = truncate(event.actorName, 150),
            changeSummary = truncate(event.changeSummary, 255)
        )
        record.actions.addAll(normalizedActions)

        policyChangeAuditRecordRepository.save(record)
        logger.debug(
            "정책 감사 이벤트 저장 완료 eventId={} tenantId={} policyId={}",
            event.eventId,
            event.tenantId,
            event.policyId
        )
    }

    private fun truncate(value: String?, maxLength: Int): String? {
        if (value == null) {
            return null
        }

        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return if (trimmed.length <= maxLength) trimmed else trimmed.substring(0, maxLength)
    }

    private fun normalizeActions(eventId: String, actions: Set<String>): Set<String> =
        actions.map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .ifEmpty { throw IllegalArgumentException("정책 액션은 비어 있을 수 없습니다. eventId=$eventId") }
}
