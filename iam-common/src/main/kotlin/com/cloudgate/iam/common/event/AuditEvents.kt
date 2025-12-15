package com.cloudgate.iam.common.event

import java.time.Instant

/**
 * 감사 이벤트 공통 인터페이스로, 이벤트 타입·버전·발생 시각·테넌트 식별자와 발행한 서비스를 포함
 */
sealed interface AuditEvent {
    val eventId: String
    val eventType: String
    val version: Int
    val occurredAt: Instant
    val tenantId: Long
    val source: String
}

/**
 * 로그인 성공 시 발행되는 감사 이벤트 페이로드
 */
data class LoginAuditEvent(
    override val eventId: String,
    override val occurredAt: Instant,
    override val tenantId: Long,
    override val source: String,
    val userId: Long,
    val username: String,
    val tenantCode: String,
    val sessionId: String,
    val mfaVerified: Boolean,
    val clientIp: String?,
    val userAgent: String?
) : AuditEvent {
    override val eventType: String = "LOGIN_SUCCESS"
    override val version: Int = 1
}

/**
 * 정책 생성/수정/삭제 시 발행되는 감사 이벤트 페이로드
 */
data class PolicyChangeAuditEvent(
    override val eventId: String,
    override val occurredAt: Instant,
    override val tenantId: Long,
    override val source: String,
    val policyId: Long,
    val policyName: String,
    val resource: String,
    val actions: Set<String>,
    val effect: String,
    val priority: Int,
    val active: Boolean,
    val actorId: Long?,
    val actorName: String?,
    val changeType: PolicyChangeType,
    val changeSummary: String?
) : AuditEvent {
    override val eventType: String = "POLICY_CHANGED"
    override val version: Int = 1
}

/**
 * 정책 변경 작업의 성격을 나타내는 유형
 */
enum class PolicyChangeType {
    CREATED,
    UPDATED,
    DELETED
}