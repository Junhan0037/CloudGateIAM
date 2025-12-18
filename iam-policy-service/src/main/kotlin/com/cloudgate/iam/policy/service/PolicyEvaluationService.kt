package com.cloudgate.iam.policy.service

import com.cloudgate.iam.common.domain.RoleScope
import com.cloudgate.iam.common.tenant.TenantContextHolder
import com.cloudgate.iam.common.tenant.TenantFilterApplier
import com.cloudgate.iam.policy.domain.*
import com.cloudgate.iam.policy.dsl.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * RBAC 권한 체크 후 ABAC 정책 조건을 평가하는 통합 인가 서비스
 * - RBAC 미통과 시 ABAC 평가 전에 즉시 거부
 * - 정책 우선순위(ASC) 순회 중 DENY 매칭 시 즉시 거부, ALLOW 매칭은 첫 번째 정책 ID를 반환
 */
@Service
class PolicyEvaluationService(
    private val userRoleAssignmentRepository: UserRoleAssignmentRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val policyRepository: PolicyRepository,
    private val policyDslParser: PolicyDslParser,
    private val tenantFilterApplier: TenantFilterApplier
) {

    /**
     * RBAC → ABAC 순으로 평가하여 최종 허용 여부를 반환
     */
    @Transactional(readOnly = true)
    fun evaluate(request: PolicyEvaluationRequest): PolicyDecision =
        TenantContextHolder.withTenant(request.tenantId) {
            tenantFilterApplier.enableForCurrentTenant()
            val rbacResult = evaluateRbac(request)
            if (!rbacResult.allowed) {
                return@withTenant PolicyDecision(
                    allowed = false,
                    reason = rbacResult.reasonWhenDenied,
                    matchedRoleNames = rbacResult.matchedRoleNames
                )
            }

            return@withTenant evaluateAbac(request, rbacResult.matchedRoleNames)
        }

    /**
     * 요청 스코프에 맞는 역할/권한을 검사하여 RBAC 통과 여부를 계산
     */
    private fun evaluateRbac(request: PolicyEvaluationRequest): RbacEvaluationResult {
        val assignments = userRoleAssignmentRepository.findByTenantIdAndUserId(request.tenantId, request.userId)

        val applicableAssignments = assignments.filter { assignment ->
            assignment.role.tenantId == request.tenantId && isRoleApplicable(assignment, request.projectId)
        }
        if (applicableAssignments.isEmpty()) {
            return RbacEvaluationResult(
                allowed = false,
                matchedRoleNames = emptySet(),
                reasonWhenDenied = DecisionReason.RBAC_DENY_NO_APPLICABLE_ROLE
            )
        }

        val roleIds = applicableAssignments.mapNotNull { it.role.id }
        if (roleIds.isEmpty()) {
            return RbacEvaluationResult(
                allowed = false,
                matchedRoleNames = emptySet(),
                reasonWhenDenied = DecisionReason.RBAC_DENY_NO_APPLICABLE_ROLE
            )
        }

        val permissions = rolePermissionRepository.findByRoleIdIn(roleIds)
        val allowed = permissions.filter { mapping ->
            mapping.tenantId == request.tenantId &&
                mapping.permission.resource == request.resource &&
                matchesActionPattern(mapping.permission.action, request.action)
        }

        if (allowed.isEmpty()) {
            return RbacEvaluationResult(
                allowed = false,
                matchedRoleNames = applicableAssignments.map { it.role.name }.toSet(),
                reasonWhenDenied = DecisionReason.RBAC_DENY_NO_PERMISSION
            )
        }

        return RbacEvaluationResult(
            allowed = true,
            matchedRoleNames = allowed.map { it.role.name }.toSet(),
            reasonWhenDenied = DecisionReason.RBAC_DENY_NO_PERMISSION
        )
    }

    /**
     * ABAC 정책을 우선순위대로 평가해 최종 허용 여부를 결정
     */
    private fun evaluateAbac(request: PolicyEvaluationRequest, matchedRoleNames: Set<String>): PolicyDecision {
        val policies = policyRepository.findByTenantIdAndResourceAndActiveTrueOrderByPriorityAscIdAsc(
            tenantId = request.tenantId,
            resource = request.resource
        )
        if (policies.isEmpty()) {
            return PolicyDecision(
                allowed = true,
                reason = DecisionReason.ABAC_SKIPPED_NO_POLICY,
                matchedRoleNames = matchedRoleNames
            )
        }

        val attributeContext = request.attributeContext()
        var allowedPolicyId: Long? = null

        for (policy in policies) {
            if (!actionMatches(policy, request.action)) continue

            val document = policyDslParser.parse(policy)
            if (!evaluateCondition(document, attributeContext)) {
                continue
            }

            val policyId = policy.id
            if (policyId != null) {
                if (document.effect == PolicyEffect.DENY) {
                    return PolicyDecision(
                        allowed = false,
                        reason = DecisionReason.ABAC_DENY_EXPLICIT,
                        matchedRoleNames = matchedRoleNames,
                        matchedPolicyId = policyId
                    )
                }
                if (allowedPolicyId == null) {
                    allowedPolicyId = policyId
                }
            }
        }

        if (allowedPolicyId != null) {
            return PolicyDecision(
                allowed = true,
                reason = DecisionReason.ABAC_ALLOW,
                matchedRoleNames = matchedRoleNames,
                matchedPolicyId = allowedPolicyId
            )
        }

        return PolicyDecision(
            allowed = true,
            reason = DecisionReason.ABAC_SKIPPED_NO_POLICY,
            matchedRoleNames = matchedRoleNames
        )
    }

    /**
     * 프로젝트 스코프 역할은 projectId 일치 시에만 적용
     * - 시스템/테넌트 스코프는 항상 적용
     */
    private fun isRoleApplicable(assignment: UserRoleAssignment, projectId: Long?): Boolean {
        return when (assignment.role.scope) {
            RoleScope.SYSTEM, RoleScope.TENANT -> true
            RoleScope.PROJECT -> {
                val targetProject = projectId ?: UserRoleAssignment.NO_PROJECT
                assignment.projectId == targetProject || assignment.projectId == UserRoleAssignment.NO_PROJECT
            }
        }
    }

    /**
     * 정책 조건 트리를 재귀 평가
     */
    private fun evaluateCondition(document: PolicyDocument, attributeContext: AttributeContext): Boolean {
        return evaluateConditionNode(document.condition, attributeContext)
    }

    private fun evaluateConditionNode(node: ConditionNode, attributeContext: AttributeContext): Boolean {
        return when (node) {
            is AllConditions -> node.conditions.all { evaluateConditionNode(it, attributeContext) }
            is AnyConditions -> node.conditions.any { evaluateConditionNode(it, attributeContext) }
            is NotCondition -> !evaluateConditionNode(node.condition, attributeContext)
            is MatchCondition -> evaluateMatch(node, attributeContext)
        }
    }

    /**
     * 단일 match 조건을 평가
     */
    private fun evaluateMatch(condition: MatchCondition, attributeContext: AttributeContext): Boolean {
        val attributeValues = attributeContext.values(condition.attribute.scope, condition.attribute.path)
        if (attributeValues.isEmpty()) return false

        val values = condition.values
        return when (condition.operator) {
            AttributeOperator.EQ -> attributeValues.any { attr -> values.any { attr == it } }
            AttributeOperator.NEQ -> attributeValues.none { attr -> values.any { attr == it } }
            AttributeOperator.IN -> attributeValues.any { attr -> values.contains(attr) }
            AttributeOperator.NOT_IN -> attributeValues.none { attr -> values.contains(attr) }
            AttributeOperator.CONTAINS -> attributeValues.any { attr -> values.any { attr.contains(it) } }
            AttributeOperator.CIDR -> {
                val cidr = values.firstOrNull() ?: return false
                attributeValues.any { isInCidr(it, cidr) }
            }

            AttributeOperator.REGEX -> {
                val pattern = values.firstOrNull() ?: return false
                val regex = runCatching { Regex(pattern) }.getOrNull() ?: return false
                attributeValues.any { regex.matches(it) }
            }

            AttributeOperator.GT -> numericCompare(attributeValues, values.firstOrNull()) { attr, expected -> attr > expected }
            AttributeOperator.GTE -> numericCompare(attributeValues, values.firstOrNull()) { attr, expected -> attr >= expected }
            AttributeOperator.LT -> numericCompare(attributeValues, values.firstOrNull()) { attr, expected -> attr < expected }
            AttributeOperator.LTE -> numericCompare(attributeValues, values.firstOrNull()) { attr, expected -> attr <= expected }
            AttributeOperator.BETWEEN -> compareBetween(attributeValues, values)
        }
    }

    /**
     * 숫자 비교 연산 처리 (GT/GTE/LT/LTE)
     */
    private fun numericCompare(attributeValues: List<String>, expectedRaw: String?, comparator: (BigDecimal, BigDecimal) -> Boolean): Boolean {
        val expected = expectedRaw?.toBigDecimalOrNull() ?: return false
        return attributeValues.any { candidate ->
            candidate.toBigDecimalOrNull()?.let { comparator(it, expected) } ?: false
        }
    }

    /**
     * 범위 비교 연산 처리 (BETWEEN)
     */
    private fun compareBetween(attributeValues: List<String>, expectedValues: List<String>): Boolean {
        if (expectedValues.size < 2) return false
        val lower = expectedValues[0].toBigDecimalOrNull() ?: return false
        val upper = expectedValues[1].toBigDecimalOrNull() ?: return false
        return attributeValues.any { candidate ->
            candidate.toBigDecimalOrNull()?.let { it >= lower && it <= upper } ?: false
        }
    }

    /**
     * 액션 패턴(정확히 일치 또는 접두사 *) 매칭
     */
    private fun actionMatches(policy: Policy, requestedAction: String): Boolean {
        return policy.actions.any { matchesActionPattern(it, requestedAction) }
    }

    private fun matchesActionPattern(pattern: String, requestedAction: String): Boolean {
        val normalizedPattern = pattern.trim()
        if (normalizedPattern == "*") return true
        if (normalizedPattern.endsWith("*")) {
            val prefix = normalizedPattern.removeSuffix("*")
            return requestedAction.startsWith(prefix)
        }
        return normalizedPattern == requestedAction
    }

    /**
     * IPv4 CIDR 포함 여부 계산
     * - 잘못된 입력은 false로 처리해 fail-closed
     */
    private fun isInCidr(ip: String, cidr: String): Boolean {
        return try {
            val parts = cidr.split("/")
            if (parts.size != 2) return false

            val prefixLength = parts[1].toIntOrNull() ?: return false
            val target = InetAddress.getByName(ip).address
            val network = InetAddress.getByName(parts[0]).address

            if (target.size != 4 || network.size != 4 || prefixLength !in 0..32) {
                return false
            }

            val targetInt = ByteBuffer.wrap(target).int
            val networkInt = ByteBuffer.wrap(network).int
            val mask = if (prefixLength == 0) 0 else -0x1 shl (32 - prefixLength)
            targetInt and mask == networkInt and mask
        } catch (_: Exception) {
            false
        }
    }

    private data class RbacEvaluationResult(
        val allowed: Boolean,
        val matchedRoleNames: Set<String>,
        val reasonWhenDenied: DecisionReason
    )
}
