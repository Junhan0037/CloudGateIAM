# 로그인 부하 테스트 리포트 (초안)

## 1. 테스트 목표
- `/auth/login` 세션 기반 로그인 처리량·지연·성공률을 검증하여 SLO(p95<300ms, p99<500ms, 오류율<1%) 충족 여부 확인
- Redis 세션·DB 인증 경로 병목 여부 사전 탐지

## 2. 시나리오 요약
- 스크립트: `infra/perf/k6-login.js`
- 부하 모델: `ramping-arrival-rate`
- 단계: 5 → 20 → 40 → 60 RPS(9분), 이후 쿨다운 1분
- 입력 변수: `BASE_URL`, `TENANT_ID`, `USERNAME`, `PASSWORD`, `MFA_CODE(옵션)`

## 3. 환경
- 대상: (예) 로컬 Docker Compose / K8s dev 네임스페이스
- 데이터베이스: MySQL 8.4 (단일 노드)
- 세션: Redis 7 (AOF)
- 어플리케이션 이미지: latest 태그(테스트 시점 빌드)

## 4. 임계값 및 SLO
- p95 < 300ms, p99 < 500ms
- 로그인 성공률 > 99%
- 로그인 실패율 < 1% (`login_errors` 지표)

## 5. 결과 요약 (실행 후 갱신)
| 구간 | 평균 지연(ms) | p95(ms) | p99(ms) | 에러율(%) | 비고 |
| --- | --- | --- | --- | --- | --- |
| 5→20 RPS 램프 | TBD | TBD | TBD | TBD |  |
| 20→40 RPS 램프 | TBD | TBD | TBD | TBD |  |
| 40→60 RPS 램프 | TBD | TBD | TBD | TBD |  |

> 실행 시 `k6 run ... --summary-export infra/perf/reports/login-summary.json`으로 결과를 저장하고 표를 채워 넣으세요.

## 6. 관찰 및 액션 아이템(예시)
- [ ] p95가 300ms 초과 시 DB 커넥션 풀/Hikari 대기 지표 점검
- [ ] 에러율 상승 시 로그인 실패 사유별 로그 샘플 수집(인증 실패 vs 5xx)
- [ ] Redis 세션 RTT/대기 지표 추가 수집 필요 여부 검토

## 7. 추후 계획
- Authorization Code 교환 포함 E2E 부하 시나리오 추가
- K8s HPA 기준 메트릭(p99 latency, CPU) 연동 후 부하 자동 확장 검증
