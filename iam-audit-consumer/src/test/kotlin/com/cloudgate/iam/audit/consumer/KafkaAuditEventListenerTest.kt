package com.cloudgate.iam.audit.consumer

import com.cloudgate.iam.audit.domain.LoginAuditRecordRepository
import com.cloudgate.iam.audit.domain.PolicyChangeAuditRecordRepository
import com.cloudgate.iam.common.event.LoginAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeAuditEvent
import com.cloudgate.iam.common.event.PolicyChangeType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.Duration
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["iam.audit.login", "iam.audit.policy"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:0", "port=0"]
)
class KafkaAuditEventListenerTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val loginAuditRecordRepository: LoginAuditRecordRepository,
    private val policyChangeAuditRecordRepository: PolicyChangeAuditRecordRepository
) {

    @AfterEach
    fun tearDown() {
        loginAuditRecordRepository.deleteAllInBatch()
        policyChangeAuditRecordRepository.deleteAllInBatch()
    }

    @Test
    fun `로그인 감사 이벤트를 수신하면 저장된다`() {
        val event = LoginAuditEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = Instant.now(),
            tenantId = 1L,
            source = "iam-auth-service",
            userId = 10L,
            username = "tester",
            tenantCode = "tenant-A",
            tenantRegion = "KR",
            sessionId = "session-123",
            mfaVerified = true,
            clientIp = "127.0.0.1",
            userAgent = "JUnit"
        )

        kafkaTemplate.send("iam.audit.login", event.eventId, event).get()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val stored = loginAuditRecordRepository.findAll()
            assertThat(stored).hasSize(1)
            assertThat(stored.first().username).isEqualTo(event.username)
            assertThat(stored.first().tenantRegion).isEqualTo(event.tenantRegion)
        }
    }

    @Test
    fun `중복 이벤트 ID는 단일 레코드로 저장된다`() {
        val eventId = UUID.randomUUID().toString()
        val firstEvent = LoginAuditEvent(
            eventId = eventId,
            occurredAt = Instant.now(),
            tenantId = 2L,
            source = "iam-auth-service",
            userId = 20L,
            username = "dup-user",
            tenantCode = "tenant-B",
            tenantRegion = "KR",
            sessionId = "session-dup",
            mfaVerified = false,
            clientIp = null,
            userAgent = null
        )
        val duplicateEvent = firstEvent.copy(userAgent = "Changed-Agent")

        kafkaTemplate.send("iam.audit.login", eventId, firstEvent).get()
        kafkaTemplate.send("iam.audit.login", eventId, duplicateEvent).get()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val stored = loginAuditRecordRepository.findAll()
            assertThat(stored).hasSize(1)
            assertThat(stored.first().userAgent).isEqualTo(firstEvent.userAgent)
        }
    }

    @Test
    fun `정책 감사 이벤트를 수신하면 액션까지 함께 저장된다`() {
        val event = PolicyChangeAuditEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = Instant.now(),
            tenantId = 3L,
            source = "iam-policy-service",
            policyId = 99L,
            policyName = "StorageReadOnly",
            resource = "storage:bucket",
            actions = setOf("read", "list"),
            effect = "ALLOW",
            priority = 10,
            active = true,
            actorId = 300L,
            actorName = "policy-admin",
            changeType = PolicyChangeType.CREATED,
            changeSummary = "created"
        )

        kafkaTemplate.send("iam.audit.policy", event.eventId, event).get()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val stored = policyChangeAuditRecordRepository.findAll()
            assertThat(stored).hasSize(1)
            val saved = stored.first()
            assertThat(saved.policyName).isEqualTo(event.policyName)
            assertThat(saved.actions).containsExactlyInAnyOrderElementsOf(event.actions)
        }
    }
}
