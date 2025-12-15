package com.cloudgate.iam.policy.dsl

import com.cloudgate.iam.policy.domain.PolicyEffect

/**
 * 정책 JSON DSL을 역직렬화한 후 애플리케이션 내부에서 사용하는 정책 문서 표현
 * - 엔티티에 저장된 메타데이터(리소스, 액션, 이펙트)와 조건 트리를 함께 노출해 평가 로직에서 일관되게 활용
 */
data class PolicyDocument(
    val version: String,
    val resource: String,
    val actions: Set<String>,
    val effect: PolicyEffect,
    val condition: ConditionNode
)

/**
 * DSL 버전 기본값
 * - DSL 포맷을 확장할 때 버전을 올려 파서를 분기
 */
const val POLICY_DSL_VERSION: String = "2025-12-15"
