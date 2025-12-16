package com.cloudgate.iam.policy.service

import com.cloudgate.iam.common.domain.RegionCode
import com.cloudgate.iam.policy.dsl.AttributeScope

/**
 * RBAC·ABAC 통합 정책 평가를 위한 입력 컨텍스트 정의
 * - tenantRegion/resourceRegion을 정규화해 정책 속성 컨텍스트에 자동 주입
 */
data class PolicyEvaluationRequest(
    val tenantId: Long,
    val userId: Long,
    val resource: String,
    val action: String,
    val tenantRegion: String,
    val resourceRegion: String? = null,
    val projectId: Long? = null,
    val userAttributes: Map<String, List<String>> = emptyMap(),
    val resourceAttributes: Map<String, List<String>> = emptyMap(),
    val environmentAttributes: Map<String, List<String>> = emptyMap()
) {
    private val normalizedTenantRegion: String = RegionCode.normalize(tenantRegion)
    private val normalizedResourceRegion: String? = resourceRegion?.let { RegionCode.normalize(it) }

    init {
        require(tenantId > 0) { "테넌트 ID는 0보다 커야 합니다." }
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다." }
        require(resource.isNotBlank()) { "리소스 식별자는 비어 있을 수 없습니다." }
        require(action.isNotBlank()) { "액션 식별자는 비어 있을 수 없습니다." }
        resourceRegion?.let {
            require(it.isNotBlank()) { "리소스 리전이 제공될 경우 비어 있을 수 없습니다." }
        }
    }

    /**
     * 속성 맵을 스코프별 컨텍스트로 정규화해 평가 엔진에 제공
     */
    fun attributeContext(): AttributeContext = AttributeContext(
        user = normalizeAttributes(userAttributes),
        resource = normalizeResourceAttributes(),
        environment = normalizeEnvironmentAttributes()
    )

    /**
     * 리소스 리전은 호출자가 임의로 덮어쓰지 못하도록 서버가 주입한 값을 우선
     */
    private fun normalizeResourceAttributes(): Map<String, List<String>> {
        val normalized = normalizeAttributes(resourceAttributes, reservedRegionKeys = setOf(RESOURCE_REGION_ATTR)).toMutableMap()
        normalizedResourceRegion?.let { region ->
            normalized[RESOURCE_REGION_ATTR] = listOf(region)
        }
        return normalized
    }

    /**
     * 테넌트 리전은 요청 컨텍스트로부터 강제 주입하여 정책에서 안전하게 참조
     */
    private fun normalizeEnvironmentAttributes(): Map<String, List<String>> {
        val normalized = normalizeAttributes(environmentAttributes, reservedRegionKeys = setOf(TENANT_REGION_ATTR)).toMutableMap()
        normalized[TENANT_REGION_ATTR] = listOf(normalizedTenantRegion)
        return normalized
    }

    private fun normalizeAttributes(raw: Map<String, List<String>>, reservedRegionKeys: Set<String> = emptySet()): Map<String, List<String>> =
        raw.mapNotNull { (key, values) ->
            val trimmedKey = key.trim()
            if (trimmedKey.isEmpty()) return@mapNotNull null

            val sanitizedValues = values.map { it.trim() }
                .filter { it.isNotEmpty() }
                .let { vals ->
                    if (reservedRegionKeys.contains(trimmedKey)) vals.map { RegionCode.normalize(it) } else vals
                }
            if (sanitizedValues.isEmpty()) return@mapNotNull null

            trimmedKey to sanitizedValues
        }.toMap()

    companion object {
        private const val RESOURCE_REGION_ATTR: String = "region"
        private const val TENANT_REGION_ATTR: String = "tenantRegion"
    }
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
