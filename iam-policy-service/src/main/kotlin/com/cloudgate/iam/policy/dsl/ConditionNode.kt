package com.cloudgate.iam.policy.dsl

/**
 * ABAC 정책 DSL에서 조건을 표현하는 루트 타입으로, 조건 트리를 구성하는 노드들의 공통 인터페이스
 */
sealed interface ConditionNode

/**
 * 모든 하위 조건이 참이어야 하는 AND 조건 블록
 */
data class AllConditions(
    val conditions: List<ConditionNode>
) : ConditionNode {
    init {
        require(conditions.isNotEmpty()) { "AND 조건 블록에는 최소 한 개 이상의 하위 조건이 필요합니다." }
    }
}

/**
 * 하나의 하위 조건만 참이어도 통과되는 OR 조건 블록
 */
data class AnyConditions(
    val conditions: List<ConditionNode>
) : ConditionNode {
    init {
        require(conditions.isNotEmpty()) { "OR 조건 블록에는 최소 한 개 이상의 하위 조건이 필요합니다." }
    }
}

/**
 * 단일 조건의 부정
 */
data class NotCondition(
    val condition: ConditionNode
) : ConditionNode

/**
 * 특정 속성에 대한 비교 연산
 */
data class MatchCondition(
    val attribute: AttributeReference,
    val operator: AttributeOperator,
    val values: List<String>
) : ConditionNode {
    init {
        require(values.isNotEmpty()) { "비교 연산에는 최소 한 개 이상의 비교 값이 필요합니다." }
    }
}

/**
 * 속성 참조를 표현하며, prefix.path 형태의 DSL을 강제
 */
data class AttributeReference(
    val scope: AttributeScope,
    val path: String
) {
    init {
        require(path.isNotBlank()) { "속성 경로는 비어 있을 수 없습니다." }
    }
}

/**
 * user, resource, env 영역 중 어느 영역의 속성인지 표현
 */
enum class AttributeScope(val prefix: String) {
    USER("user"),
    RESOURCE("resource"),
    ENV("env");

    companion object {
        fun fromPrefix(raw: String): AttributeScope =
            entries.firstOrNull { it.prefix.equals(raw, ignoreCase = true) } ?: throw IllegalArgumentException("지원하지 않는 속성 스코프: $raw")
    }
}

/**
 * ABAC 정책에서 지원하는 비교 연산자 유형을 정의
 */
enum class AttributeOperator {
    EQ,          // 정확히 일치
    NEQ,         // 불일치
    IN,          // 다중 값 중 하나 일치
    NOT_IN,      // 다중 값 중 어느 것도 일치하지 않음
    CONTAINS,    // 포함 여부 (컬렉션/문자열)
    CIDR,        // CIDR 대역 일치
    REGEX,       // 정규식 매칭
    GT,          // 초과
    GTE,         // 이상
    LT,          // 미만
    LTE,         // 이하
    BETWEEN      // 범위 내 존재
}
