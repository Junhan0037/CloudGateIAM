# CloudGate IAM 로그인 부하 테스트(k6)

본 디렉터리는 `/auth/login` 세션 로그인 플로우에 대한 부하 시나리오와 실행 가이드를 포함합니다.

## 사전 준비
- k6 CLI 또는 Docker 이미지(`grafana/k6`) 사용
- 테스트 대상 엔드포인트: `BASE_URL` 환경 변수로 지정(기본 `http://localhost:8080`)
- 로그인 자격 정보: `TENANT_ID`, `USERNAME`, `PASSWORD`(필요 시 `MFA_CODE`)

## 실행 예시
```bash
# 로컬 k6 사용
k6 run infra/perf/k6-login.js \
  -e BASE_URL=http://localhost:8080 \
  -e TENANT_ID=1 \
  -e USERNAME=demo-user \
  -e PASSWORD=demo-password

# Docker 사용
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e TENANT_ID=1 \
  -e USERNAME=demo-user \
  -e PASSWORD=demo-password \
  -v $(pwd)/infra/perf:/scripts \
  grafana/k6 run /scripts/k6-login.js
```

## 시나리오 개요
- 부하 모델: `ramping-arrival-rate` (초당 요청 건수 기준)
- 단계: 5 → 20 → 40 → 60 RPS로 상승 후 종료(총 9분)
- 임계값(Threshold)
  - `http_req_duration` p95 < 300ms, p99 < 500ms
  - 로그인 체크 성공률 > 99%
  - 로그인 실패율 < 1%

## 결과 수집 팁
- `--out json=login-results.json` 옵션으로 원시 결과를 저장 후 Grafana k6 플러그인이나 자체 스크립트로 집계
- 실행 요약을 `tee infra/perf/reports/login-summary.txt` 형태로 저장해 추세를 관리

## 확장 아이디어
- VU 기반 `constant-vus` 시나리오를 추가해 세션 유지/로그아웃 플로우까지 검증
- OAuth Authorization Code 교환 단계까지 포함하는 E2E 시나리오(콘솔 클라이언트 포함) 추가
