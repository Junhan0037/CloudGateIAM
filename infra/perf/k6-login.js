import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 로그인 실패율 모니터링 지표
const loginErrorRate = new Rate('login_errors');

// 부하 옵션: 점진적 증가 후 유지, p95/p99 지연과 성공률을 임계값으로 설정
export const options = {
  scenarios: {
    login_ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: 20, duration: '2m' },
        { target: 40, duration: '3m' },
        { target: 60, duration: '3m' },
        { target: 0, duration: '1m' },
      ],
      exec: 'loginFlow',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<500'],
    'checks{type:login}': ['rate>0.99'],
    login_errors: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// 기본 설정값: 환경 변수로 대체 가능
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const tenantId = __ENV.TENANT_ID || '1';
const username = __ENV.USERNAME || 'demo-user';
const password = __ENV.PASSWORD || 'demo-password';
const mfaCode = __ENV.MFA_CODE || ''; // MFA 활성화 시 사용

/**
 * 로그인 API 호출 플로우를 수행하며 세션 기반 응답을 검증
 */
export function loginFlow() {
  const payload = JSON.stringify({
    tenantId,
    username,
    password,
    mfaCode: mfaCode || undefined,
  });

  const res = http.post(`${baseUrl}/auth/login`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { type: 'login' },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'session id exists': (r) => !!r.json('sessionId'),
  });

  if (!ok) {
    loginErrorRate.add(1);
  }

  // 세션 유지 시나리오를 모사하기 위해 짧은 think time 부여
  sleep(1);
}
