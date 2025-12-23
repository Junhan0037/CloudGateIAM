# ABAC Policy JSON DSL 예제

`iam-policy-service`에서 사용하는 JSON DSL 형식의 샘플입니다. 실제 저장 시에는 이 조건 JSON이 `Policy` 메타데이터(tenantId, resource, actions, effect, priority, active)와 함께 저장·평가됩니다.

## 1) ALLOW: 내부망에서만 PLATFORM 부서 읽기 허용
```json
{
  "version": "2025-12-15",
  "all": [
    { "match": { "attribute": "user.department", "op": "EQ", "value": "PLATFORM" } },
    { "match": { "attribute": "env.ip", "op": "CIDR", "value": "10.0.0.0/8" } }
  ]
}
```

## 2) DENY: 리소스 리전이 CN이면 차단(우선순위 높게 설정 권장)
```json
{
  "version": "2025-12-15",
  "match": { "attribute": "resource.region", "op": "EQ", "value": "CN" }
}
```

## 3) ALLOW: 프로젝트 태그가 일치하고 업무 시간대(09-18시) 내 요청만 허용
```json
{
  "version": "2025-12-15",
  "all": [
    { "match": { "attribute": "resource.projectTag", "op": "EQ", "value": "PAYMENTS" } },
    { "match": { "attribute": "env.timeOfDay", "op": "BETWEEN", "values": ["09", "18"] } }
  ]
}
```

## 4) DENY: 사용자 위험도가 HIGH이면 모든 액션 거부
```json
{
  "version": "2025-12-15",
  "match": { "attribute": "user.riskLevel", "op": "EQ", "value": "HIGH" }
}
```

## 5) ALLOW: 국가가 KR/JP이고 이메일 도메인이 사내 도메인일 때만 허용
```json
{
  "version": "2025-12-15",
  "all": [
    { "match": { "attribute": "env.country", "op": "IN", "values": ["KR", "JP"] } },
    { "match": { "attribute": "user.email", "op": "REGEX", "value": "^[A-Za-z0-9._%+-]+@minicloud\\.corp$" } }
  ]
}
```

## 6) ALLOW: 리소스 민감도가 LOW/MEDIUM이고 MFA 사용자가 API 호출 시 허용
```json
{
  "version": "2025-12-15",
  "all": [
    { "match": { "attribute": "resource.dataClassification", "op": "IN", "values": ["LOW", "MEDIUM"] } },
    { "match": { "attribute": "user.mfaEnabled", "op": "EQ", "value": "true" } }
  ]
}
```

### Notes
- 축약형(`{"user.department": "DEV"}`)도 지원하지만, 가독성과 유효성 검증을 위해 `match` 블록 형태를 권장합니다.
- CIDR/REGEX/BETWEEN 등 연산자는 실패 시 `false`로 평가되어 fail-closed 동작합니다.
