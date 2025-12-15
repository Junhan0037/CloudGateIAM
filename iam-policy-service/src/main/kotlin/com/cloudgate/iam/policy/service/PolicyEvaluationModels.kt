package com.cloudgate.iam.policy.service

import com.cloudgate.iam.policy.dsl.AttributeScope

/**
 * RBAC·ABAC 통합 정책 평가를 위한 입력 컨텍스트 정의
 */
data class PolicyEvaluationRequest(
    val tenantId: Long,
    val userId: Long,
    val resource: String,
    val action: String,
    val projectId: Long? = null,
    val userAttributes: Map<String, List<String>> = emptyMap(),
    val resourceAttributes: Map<String, List<String>> = emptyMap(),
    val environmentAttributes: Map<String, List<String>> = emptyMap()
) {
    init {
        require(tenantId > 0) { "테넌트 ID는 0보다 커야 합니다." }
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다." }
        require(resource.isNotBlank()) { "리소스 식별자는 비어 있을 수 없습니다." }
        require(action.isNotBlank()) { "액션 식별자는 비어 있을 수 없습니다." }
    }

    /**
     * 속성 맵을 스코프별 컨텍스트로 정규화해 평가 엔진에 제공
     */
    fun attributeContext(): AttributeContext = AttributeContext(
        user = normalizeAttributes(userAttributes),
        resource = normalizeAttributes(resourceAttributes),
        environment = normalizeAttributes(environmentAttributes)
    )

    private fun normalizeAttributes(raw: Map<String, List<String>>): Map<String, List<String>> =
        raw.mapValues { (_, values) ->
            values.map { it.trim() }.filter { it.isNotEmpty() }
        }.filterValues { it.isNotEmpty() }
}

/**
 * 정책 조건 평가 시 사용되는 속성 컨텍스트로, 스코프와 경로 기반으로 값을 조회
 */
data class AttributeContext(
    val user: Map<String, List<String>>,
    val resource: Map<String, List<String>>,
    val environment: Map<String, List<String>>
) {
    /**
     * DSL에서 지정한 스코프·경로 조합으로 속성 값을 조회 (없으면 빈 리스트 반환)
     */
    fun values(scope: AttributeScope, path: String): List<String> = when (scope) {
        AttributeScope.USER -> user[path].orEmpty()
        AttributeScope.RESOURCE -> resource[path].orEmpty()
        AttributeScope.ENV -> environment[path].orEmpty()
    }
}

/**
 * 정책 평가 결과를 표현하는 DTO
 * - 허용 여부와 근거를 함께 전달
 */
data class PolicyDecision(
    val allowed: Boolean,
    val reason: DecisionReason,
    val matchedRoleNames: Set<String> = emptySet(),
    val matchedPolicyId: Long? = null
)

/**
 * 통합 정책 평가의 결과 사유 집합
 */
enum class DecisionReason {
    RBAC_DENY_NO_APPLICABLE_ROLE, // 요청 스코프에 적용 가능한 역할이 없음
    RBAC_DENY_NO_PERMISSION,      // 역할은 있으나 대상 리소스/액션 권한이 없음
    ABAC_DENY_EXPLICIT,           // 정책에서 명시적으로 DENY됨
    ABAC_ALLOW,                   // ABAC 조건을 만족하는 ALLOW 정책이 존재
    ABAC_SKIPPED_NO_POLICY        // 적용 가능한 ABAC 정책이 없어 RBAC 결과를 그대로 허용
}
