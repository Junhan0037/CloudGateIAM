package com.cloudgate.iam.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 감사 이벤트 Kafka 발행 설정을 캡슐화
 */
@ConfigurationProperties(prefix = "audit.kafka")
data class AuditKafkaProperties(
    val enabled: Boolean = true,
    val loginTopic: String = "iam.audit.login",
    val policyTopic: String = "iam.audit.policy",
    val clientId: String? = null
)
