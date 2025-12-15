package com.cloudgate.iam.policy.dsl

import com.cloudgate.iam.policy.domain.Policy
import com.cloudgate.iam.policy.domain.PolicyEffect
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component

/**
 * 정책 JSON DSL을 안전하게 역직렬화하여 내부 도메인 모델로 변환하는 파서
 * - 잘못된 스키마나 불완전한 조건은 즉시 예외를 발생시켜 정책 저장·평가 시점의 오류를 조기에 발견
 */
@Component
class PolicyDslParser(
    private val objectMapper: ObjectMapper
) {
    /**
     * 정책 엔티티에 저장된 조건 JSON을 파싱해 평가용 문서 모델로 변환
     */
    fun parse(policy: Policy): PolicyDocument = parse(
        resource = policy.resource,
        actions = policy.actions.toSet(),
        effect = policy.effect,
        conditionJson = policy.conditionJson
    )

    /**
     * 정책 메타데이터와 조건 JSON을 결합해 문서 모델을 생성
     */
    fun parse(resource: String, actions: Set<String>, effect: PolicyEffect, conditionJson: String): PolicyDocument {
        require(resource.isNotBlank()) { "리소스 식별자는 비어 있을 수 없습니다." }
        val normalizedActions = actions.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        require(normalizedActions.isNotEmpty()) { "정책 액션 목록은 비어 있을 수 없습니다." }

        val rootNode = objectMapper.readTree(conditionJson)
        val (version, conditionNode) = extractDocument(rootNode)

        return PolicyDocument(
            version = version,
            resource = resource.trim(),
            actions = normalizedActions,
            effect = effect,
            condition = conditionNode
        )
    }

    /**
     * DSL 문서의 버전과 조건 블록을 추출
     * - condition/conditions 키가 없을 경우 루트 전체를 조건으로 취급
     */
    private fun extractDocument(rootNode: JsonNode): Pair<String, ConditionNode> {
        val version = rootNode.get("version")?.asText()?.takeIf { it.isNotBlank() } ?: POLICY_DSL_VERSION
        val conditionNode = rootNode.get("condition")
            ?.takeIf { !it.isNull }?.let { parseCondition(it) }
            ?: rootNode.get("conditions")?.takeIf { !it.isNull }?.let { parseCondition(it) }
            ?: parseCondition(
                rootNode.deepCopy<ObjectNode>().also { it.remove("version") }
            )

        return version to conditionNode
    }

    /**
     * all 배열을 AND 조건으로 변환
     */
    private fun parseAll(node: JsonNode): ConditionNode {
        require(node.isArray) { "all 키는 배열이어야 합니다." }
        val children = node.map { parseCondition(it) }
        return AllConditions(children)
    }

    /**
     * any 배열을 OR 조건으로 변환
     */
    private fun parseAny(node: JsonNode): ConditionNode {
        require(node.isArray) { "any 키는 배열이어야 합니다." }
        val children = node.map { parseCondition(it) }
        return AnyConditions(children)
    }

    /**
     * 단일 not 블록을 부정 조건으로 변환
     */
    private fun parseNot(node: JsonNode): ConditionNode {
        return NotCondition(parseCondition(node))
    }

    /**
     * 조건 노드 하나를 파싱
     * - all/any/not/match 키를 우선적으로 해석
     * - 그렇지 않은 경우에는 축약형(속성: 값) 맵을 AND 조건으로 해석
     */
    private fun parseCondition(node: JsonNode): ConditionNode {
        require(node.isObject) { "정책 조건은 JSON 객체여야 합니다." }

        node.get("all")?.let { return parseAll(it) }
        node.get("any")?.let { return parseAny(it) }
        node.get("not")?.let { return parseNot(it) }
        node.get("match")?.let { return parseMatch(it) }

        return parseImplicitMatches(node)
    }

    /**
     * match 블록을 비교 조건으로 변환
     */
    private fun parseMatch(matchNode: JsonNode): ConditionNode {
        require(matchNode.isObject) { "match 블록은 JSON 객체여야 합니다." }

        val attribute = parseAttribute(matchNode.get("attribute")?.asText()?.trim().orEmpty())
        val operator = parseOperator(matchNode.get("op")?.asText()?.trim().orEmpty())
        val values = parseMatchValues(matchNode)

        validateOperator(operator, values)
        return MatchCondition(attribute, operator, values)
    }

    /**
     * 축약형 조건( { "user.department": "DEV" } )을 모두 AND 묶음으로 파싱
     */
    private fun parseImplicitMatches(node: JsonNode): ConditionNode {
        val matches = mutableListOf<MatchCondition>()

        val fieldNames = node.fieldNames()
        while (fieldNames.hasNext()) {
            val fieldName = fieldNames.next()
            val valueNode = node.get(fieldName)
            val attribute = parseAttribute(fieldName)
            val (operator, values) = when {
                valueNode.isArray -> AttributeOperator.IN to parseValueArray(valueNode)
                valueNode.isObject -> throw IllegalArgumentException("객체 값은 축약형에서 지원하지 않습니다. match 블록을 사용하세요.")
                else -> AttributeOperator.EQ to listOf(parseScalar(valueNode))
            }
            validateOperator(operator, values)
            matches += MatchCondition(attribute, operator, values)
        }

        require(matches.isNotEmpty()) { "조건 맵이 비어 있습니다." }
        return if (matches.size == 1) matches.first() else AllConditions(matches)
    }

    /**
     * match 블록의 값(value/values/range)을 추출
     */
    private fun parseMatchValues(matchNode: JsonNode): List<String> = when {
        matchNode.has("range") -> parseRange(matchNode.get("range"))
        matchNode.has("values") -> parseValueArray(matchNode.get("values"))
        matchNode.has("value") -> listOf(parseScalar(matchNode.get("value")))
        else -> throw IllegalArgumentException("match 블록에는 value, values, range 중 하나가 필요합니다.")
    }

    /**
     * attribute 문자열을 scope.path 형태로 파싱
     */
    private fun parseAttribute(raw: String): AttributeReference {
        require(raw.isNotBlank()) { "속성 이름은 비어 있을 수 없습니다." }

        val tokens = raw.split('.', limit = 2)
        require(tokens.size == 2) { "속성 이름은 prefix.path 형태여야 합니다: $raw" }

        val scope = AttributeScope.fromPrefix(tokens[0])
        val path = tokens[1].trim()
        return AttributeReference(scope, path)
    }

    /**
     * 연산자 문자열을 검증하고 열거형으로 변환
     */
    private fun parseOperator(raw: String): AttributeOperator {
        require(raw.isNotBlank()) { "연산자(op)는 비어 있을 수 없습니다." }
        return runCatching { AttributeOperator.valueOf(raw.uppercase()) }
            .getOrElse { throw IllegalArgumentException("지원하지 않는 연산자입니다: $raw") }
    }

    /**
     * 범위(range) 값을 좌/우 값 2개로 변환
     */
    private fun parseRange(node: JsonNode): List<String> {
        val values = parseValueArray(node)
        require(values.size == 2) { "range 필드는 정확히 두 개의 값을 가져야 합니다." }
        return values
    }

    /**
     * values 배열을 문자열 리스트로 변환
     */
    private fun parseValueArray(node: JsonNode): List<String> {
        require(node.isArray) { "values 필드는 배열이어야 합니다." }
        val values = node.map { parseScalar(it) }.filter { it.isNotBlank() }
        require(values.isNotEmpty()) { "values 배열은 비어 있을 수 없습니다." }
        return values
    }

    /**
     * 스칼라 값을 문자열로 변환 (문자열/숫자/불리언만 허용)
     */
    private fun parseScalar(node: JsonNode): String {
        return when {
            node.isTextual -> node.asText().trim()
            node.isNumber -> node.numberValue().toString()
            node.isBoolean -> node.booleanValue().toString()
            else -> throw IllegalArgumentException("조건 값은 문자열, 숫자, 불리언만 허용됩니다.")
        }
    }

    /**
     * 연산자별 값 개수/형식을 검증
     */
    private fun validateOperator(operator: AttributeOperator, values: List<String>) {
        when (operator) {
            AttributeOperator.BETWEEN -> require(values.size == 2) { "BETWEEN 연산자는 시작·종료 2개 값이 필요합니다." }
            AttributeOperator.IN, AttributeOperator.NOT_IN -> require(values.isNotEmpty()) { "${operator.name} 연산자는 한 개 이상의 값이 필요합니다." }
            AttributeOperator.CIDR -> {
                require(values.size == 1) { "CIDR 연산자는 단일 값만 허용합니다." }
                require(values.first().contains('/')) { "CIDR 연산자는 CIDR 표기(x.x.x.x/yy)만 허용합니다." }
            }
            AttributeOperator.REGEX -> {
                require(values.size == 1) { "REGEX 연산자는 단일 패턴만 허용합니다." }
                runCatching { Regex(values.first()) }
                    .getOrElse { throw IllegalArgumentException("유효하지 않은 정규식입니다: ${it.message}") }
            }
            AttributeOperator.EQ, AttributeOperator.NEQ, AttributeOperator.CONTAINS,
            AttributeOperator.GT, AttributeOperator.GTE, AttributeOperator.LT, AttributeOperator.LTE -> {
                require(values.size == 1) { "${operator.name} 연산자는 단일 값만 허용합니다." }
            }
        }
    }
}
