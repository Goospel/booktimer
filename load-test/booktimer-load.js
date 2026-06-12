// BookTimer 부하 테스트 (k6) — 홍보 전 서버 용량 게이트의 "실측" 단계.
//
// 목적:
//   1) latency가 꺾이는 RPS/VU 지점을 숫자로 확보(추정 → 사실 전환, plan.md §홍보 전 선수과정).
//   2) ECS 오토스케일링(autoscaling-config.yml)이 실제로 발동하는지 검증
//      — 부하를 올리면 평균 CPU가 70%를 넘어 태스크가 2 → 3 → 4로 늘어야 한다.
//
// ⚠️ 운영 주의(스테이징이 없어 운영 대상으로 돌린다):
//   - 반드시 "저부하부터" 시작한다(아래 stages는 보수적 기본값 — 더 세게 가려면 단계적으로 올린다).
//   - 부하로 오토스케일이 발동하면 태스크가 최대 4개까지 늘어 "순간 최대 4배 컴퓨트 요금"이 든다(예산 $50 인지).
//   - 로그인은 BCrypt 검증이라 CPU가 비싸다(의도된 부하). 단 자격증명이 틀리면 IP별 5회 실패 → 15분 잠금
//     (LoginAttemptFilter)에 걸린다. 반드시 "유효한 실계정"을 넣어라. 성공 로그인은 실패 카운트를 리셋한다.
//   - 가입(POST /signup)은 매번 새 계정·DB 행을 만들어 운영 데이터를 오염시키므로 시나리오에서 제외했다.
//
// 실행:
//   k6 run -e BASE_URL=https://booktimer.app -e LOGIN_USER=<로그인 식별자> -e LOGIN_PASS=<비밀번호> load-test/booktimer-load.js
//   - LOGIN_USER = 폼의 username 필드값 = 로그인 식별자(BookTimerUserDetailsService 기준 이메일).
//   - 부하 강도/곡선은 아래 stages를 직접 조절한다(VU 수·duration).
//
// k6가 없으면(가장 단순한 읽기 부하만 보고 싶으면) hey로 공개 헬스/랜딩을 때려 볼 수 있다:
//   hey -z 30s -c 20 https://booktimer.app/actuator/health
//   (단 hey는 로그인·세션·CSRF를 못 타 인증 경로 부하는 못 본다 — 그게 필요하면 이 k6 스크립트.)

import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER = __ENV.LOGIN_USER;
const PASS = __ENV.LOGIN_PASS;

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 1,
      // 보수적 기본 곡선: 1 → 10 → 30 → 60 → 0. 운영 반응을 보며 target을 단계적으로 키운다.
      stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 30 },
        { duration: '2m', target: 60 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    // 게이트: 실패율 5% 미만 + p95 응답 1.5s 미만. 부하를 올려 이게 깨지는 지점이 곧 "꺾이는 RPS".
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
  },
};

// Spring Security가 th:action 폼에 자동 주입하는 CSRF hidden input에서 토큰을 추출한다.
function extractCsrf(html) {
  const m = html.match(/name="_csrf"[^>]*value="([^"]+)"/);
  return m ? m[1] : null;
}

// VU별 1회만 로그인하고(세션 쿠키는 k6 cookieJar가 VU별로 유지) 이후 읽기 루프를 돈다
// — "도구로 들어와 잠깐 머무는" 실사용 패턴 근사(매 요청 로그인은 비현실적·과도한 BCrypt 부하).
let loggedIn = false;

export default function () {
  if (!USER || !PASS) {
    fail('LOGIN_USER / LOGIN_PASS 환경변수가 필요합니다 (유효한 실계정).');
  }

  if (!loggedIn) {
    const page = http.get(`${BASE}/login`);
    const csrf = extractCsrf(page.body);
    if (!csrf) {
      fail('CSRF 토큰 추출 실패 — 로그인 폼 구조가 바뀌었는지 확인하세요.');
    }
    const res = http.post(
      `${BASE}/login`,
      { username: USER, password: PASS, _csrf: csrf },
      { redirects: 0 }, // 302를 직접 보고 성공/실패를 판정(성공="/" · 실패="/login?error")
    );
    const ok = check(res, {
      '로그인 302 리다이렉트': (r) => r.status === 302,
      '로그인 성공(error 아님)': (r) => !String(r.headers['Location'] || '').includes('error'),
    });
    if (!ok) {
      fail('로그인 실패 — 자격증명/IP 잠금(LoginAttemptFilter)을 확인하세요.');
    }
    loggedIn = true;
  }

  // 인증된 읽기 경로 — 대시보드(잔디·타이머 카드) + 독서 기록
  const dashboard = http.get(`${BASE}/`);
  check(dashboard, { '대시보드 200': (r) => r.status === 200 });

  const history = http.get(`${BASE}/history`);
  check(history, { '독서 기록 200': (r) => r.status === 200 });

  sleep(1); // think-time
}
