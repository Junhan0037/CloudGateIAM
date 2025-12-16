package com.cloudgate.iam.policy.service

import com.cloudgate.iam.common.domain.RoleScope
import com.cloudgate.iam.policy.domain.Permission
import com.cloudgate.iam.policy.domain.PermissionRepository
import com.cloudgate.iam.policy.domain.Policy
import com.cloudgate.iam.policy.domain.PolicyEffect
import com.cloudgate.iam.policy.domain.PolicyRepository
import com.cloudgate.iam.policy.domain.Role
import com.cloudgate.iam.policy.domain.RolePermission
import com.cloudgate.iam.policy.domain.RolePermissionRepository
import com.cloudgate.iam.policy.domain.RoleRepository
import com.cloudgate.iam.policy.domain.UserRoleAssignment
import com.cloudgate.iam.policy.domain.UserRoleAssignmentRepository
import com.cloudgate.iam.policy.dsl.PolicyDslParser
import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    PolicyEvaluationService::class,
    PolicyDslParser::class,
    TenantFilterConfiguration::class,
    PolicyEvaluationServiceTest.TestConfig::class
)
class PolicyEvaluationServiceTest @Autowired constructor(
    private val policyEvaluationService: PolicyEvaluationService,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleAssignmentRepository: UserRoleAssignmentRepository,
    private val policyRepository: PolicyRepository
) {
    private val defaultRegion: String = "KR"

    @Test
    fun `RBAC 권한이 없으면 즉시 거부한다`() {
        val role = createRoleWithPermission(
            tenantId = 1L,
            roleName = "TENANT_USER",
            scope = RoleScope.TENANT,
            resource = "compute.instance",
            action = "compute.instance:read"
        )
        assignRole(tenantId = 1L, userId = 1001L, role = role)

        val decision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 1L,
                userId = 1001L,
                resource = "compute.instance",
                action = "compute.instance:write",
                tenantRegion = defaultRegion,
                userAttributes = mapOf("department" to listOf("DEV"))
            )
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo(DecisionReason.RBAC_DENY_NO_PERMISSION)
        assertThat(decision.matchedPolicyId).isNull()
    }

    @Test
    fun `ABAC DENY 정책이 일치하면 거부한다`() {
        val role = createRoleWithPermission(
            tenantId = 1L,
            roleName = "TENANT_ADMIN",
            scope = RoleScope.TENANT,
            resource = "storage.bucket",
            action = "storage.bucket:write"
        )
        assignRole(tenantId = 1L, userId = 2001L, role = role)
        savePolicy(
            tenantId = 1L,
            name = "deny-high-risk-writes",
            resource = "storage.bucket",
            actions = setOf("storage.bucket:*"),
            effect = PolicyEffect.DENY,
            conditionPayload = """{"user.riskLevel":"HIGH"}""",
            priority = 1
        )

        val decision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 1L,
                userId = 2001L,
                resource = "storage.bucket",
                action = "storage.bucket:write",
                tenantRegion = defaultRegion,
                userAttributes = mapOf("riskLevel" to listOf("HIGH"))
            )
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo(DecisionReason.ABAC_DENY_EXPLICIT)
        assertThat(decision.matchedPolicyId).isNotNull()
    }

    @Test
    fun `RBAC 통과 후 적용 가능한 정책이 없으면 허용한다`() {
        val role = createRoleWithPermission(
            tenantId = 2L,
            roleName = "SYSTEM_ADMIN",
            scope = RoleScope.SYSTEM,
            resource = "iam.session",
            action = "iam.session:issue"
        )
        assignRole(tenantId = 2L, userId = 3001L, role = role)

        val decision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 2L,
                userId = 3001L,
                resource = "iam.session",
                action = "iam.session:issue",
                tenantRegion = defaultRegion,
                environmentAttributes = mapOf("ip" to listOf("10.10.0.1"))
            )
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.reason).isEqualTo(DecisionReason.ABAC_SKIPPED_NO_POLICY)
        assertThat(decision.matchedPolicyId).isNull()
        assertThat(decision.matchedRoleNames).contains("SYSTEM_ADMIN")
    }

    @Test
    fun `ABAC ALLOW 정책이 조건을 만족하면 허용한다`() {
        val role = createRoleWithPermission(
            tenantId = 3L,
            roleName = "PROJECT_VIEWER",
            scope = RoleScope.PROJECT,
            resource = "compute.instance",
            action = "compute.instance:*"
        )
        assignRole(tenantId = 3L, userId = 4001L, role = role, projectId = 77L)
        savePolicy(
            tenantId = 3L,
            name = "allow-platform-read",
            resource = "compute.instance",
            actions = setOf("compute.instance:*"),
            effect = PolicyEffect.ALLOW,
            conditionPayload = """
                {
                  "all": [
                    { "match": { "attribute": "user.department", "op": "EQ", "value": "PLATFORM" } },
                    { "match": { "attribute": "env.ip", "op": "CIDR", "value": "10.0.0.0/8" } }
                  ]
                }
            """.trimIndent(),
            priority = 5
        )

        val decision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 3L,
                userId = 4001L,
                resource = "compute.instance",
                action = "compute.instance:describe",
                projectId = 77L,
                tenantRegion = defaultRegion,
                userAttributes = mapOf("department" to listOf("PLATFORM")),
                environmentAttributes = mapOf("ip" to listOf("10.1.1.10"))
            )
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.reason).isEqualTo(DecisionReason.ABAC_ALLOW)
        assertThat(decision.matchedRoleNames).contains("PROJECT_VIEWER")
        assertThat(decision.matchedPolicyId).isNotNull()
    }

    @Test
    fun `리소스 리전이 자동 주입되어 교차 리전 접근을 차단한다`() {
        val role = createRoleWithPermission(
            tenantId = 4L,
            roleName = "TENANT_ADMIN",
            scope = RoleScope.TENANT,
            resource = "compute.instance",
            action = "compute.instance:*"
        )
        assignRole(tenantId = 4L, userId = 5001L, role = role)

        val denyPolicy = savePolicy(
            tenantId = 4L,
            name = "deny-cn-region",
            resource = "compute.instance",
            actions = setOf("compute.instance:*"),
            effect = PolicyEffect.DENY,
            conditionPayload = """{"resource.region": "CN"}""",
            priority = 1
        )

        val denyDecision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 4L,
                userId = 5001L,
                resource = "compute.instance",
                action = "compute.instance:read",
                tenantRegion = defaultRegion.lowercase(),
                resourceRegion = "cn"
            )
        )

        assertThat(denyDecision.allowed).isFalse()
        assertThat(denyDecision.reason).isEqualTo(DecisionReason.ABAC_DENY_EXPLICIT)
        assertThat(denyDecision.matchedPolicyId).isEqualTo(denyPolicy.id)

        val allowDecision = policyEvaluationService.evaluate(
            PolicyEvaluationRequest(
                tenantId = 4L,
                userId = 5001L,
                resource = "compute.instance",
                action = "compute.instance:describe",
                tenantRegion = defaultRegion,
                resourceRegion = defaultRegion.lowercase()
            )
        )

        assertThat(allowDecision.allowed).isTrue()
        assertThat(allowDecision.reason).isEqualTo(DecisionReason.ABAC_SKIPPED_NO_POLICY)
    }

    private fun createRoleWithPermission(
        tenantId: Long,
        roleName: String,
        scope: RoleScope,
        resource: String,
        action: String
    ): Role {
        val role = roleRepository.save(
            Role(
                tenantId = tenantId,
                name = roleName,
                scope = scope
            )
        )
        val permission = permissionRepository.save(
            Permission(
                resource = resource,
                action = action
            )
        )
        rolePermissionRepository.save(RolePermission(role = role, permission = permission, tenantId = tenantId))
        return role
    }

    private fun assignRole(tenantId: Long, userId: Long, role: Role, projectId: Long = UserRoleAssignment.NO_PROJECT) {
        userRoleAssignmentRepository.save(
            UserRoleAssignment(
                tenantId = tenantId,
                userId = userId,
                role = role,
                projectId = projectId
            )
        )
    }

    private fun savePolicy(
        tenantId: Long,
        name: String,
        resource: String,
        actions: Set<String>,
        effect: PolicyEffect,
        conditionPayload: String,
        priority: Int = Policy.DEFAULT_PRIORITY,
        active: Boolean = true
    ): Policy {
        return policyRepository.save(
            Policy(
                tenantId = tenantId,
                name = name,
                resource = resource,
                actionSet = actions,
                effect = effect,
                conditionPayload = conditionPayload,
                priority = priority,
                active = active
            )
        )
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }
}
