# booktimer 미니앱 (앱인토스)

토스 미니앱 채널의 프론트엔드. **기존 `frontend/`(우리 서버 static 번들)와 완전 분리**돼 있고 코드를 공유하지
않는다 — 이쪽은 토스 인프라로 배포되고, 서버의 `/api/**` JSON API만 Bearer 토큰으로 호출한다.

스택: Vite + React 18 + [`@toss/tds-mobile`](https://www.npmjs.com/package/@toss/tds-mobile) +
[`@apps-in-toss/web-framework`](https://www.npmjs.com/package/@apps-in-toss/web-framework).

> React는 **18**로 핀돼 있다 — TDS 2.5.1의 peer가 `^16.8.3 || ^17 || ^18`이라 19를 받지 않는다.

## 화면 (MVP 5개)

| 화면 | 파일 | 하는 일 |
|---|---|---|
| 로그인 브릿지 | `src/screens/LoginBridge.tsx` | 진입 시 토스 로그인 → `/api/toss/login`. 미등록이면 "새로 시작 / 기존 계정 연결" |
| 계정 연결 | `src/screens/LinkAccount.tsx` | 웹 설정에서 받은 일회용 코드로 `/api/toss/link` |
| 타이머 홈 | `src/screens/Home.tsx` | `/api/dashboard` 축약 렌더 · 시작/정지 · 책 선택 · 종료 후 태깅 |
| 기록 | `src/screens/History.tsx` | 잔디 · 연속일 · 총 시간 (stop 응답의 graph로 즉시 갱신) |
| 목표 설정 | `src/screens/Goal.tsx` | `/api/miniapp/goal` — 신규 계정 첫 실행 유도 + 이후 변경 |

`src/api.ts`가 서버 계약(타입·Bearer 헤더·401 재로그인 분기)의 단일 창구다. 응답 타입의 원본은
`src/main/java/com/booktimer/web/api/`의 record DTO(`TossAuthApiController` · `DashboardApiController`).

## 개발

```bash
npm install
cp .env.example .env.local     # VITE_API_BASE_URL — 개발은 로컬 백엔드(:8080), 운영은 https://booktimer.app
npm run dev                    # http://localhost:5174
npm test                       # vitest — API 클라이언트 분기 로직
npm run build                  # tsc -b && vite build → dist/
```

서버는 별도로 `./gradlew bootRun`으로 띄운다. 브라우저에서 열면 토스 SDK(`TossAuth.login`)가 없어
로그인 단계에서 멈춘다 — **로그인 이후 화면의 실검증은 토스 샌드박스 실기기가 필요하다.**

## 배포 (앱인토스 CLI — 우리 CI에 얹지 않는다)

```bash
npm run build                # dist/ 생성 — apps-in-toss.config.ts의 webBundleDir가 여기를 가리킨다
npx ait build                # 배포용 .ait 아티팩트
npx ait deploy --api-key "$(cat ../.claude/.secrets/ait-api-key.txt)"   # 앱인토스 업로드
```

> ⚠️ `npx ait token add`는 **존재하지 않는다** — 동봉 CLI(`@apps-in-toss/web-framework` 3.0.2의 `ait`)의
> 서브커맨드는 `build` / `deploy` / `init`뿐(실측 2026-08-07). 인증은 사전 등록이 아니라 **배포 시점에
> `--api-key`로 전달**한다. 키는 메인 repo `.claude/.secrets/ait-api-key.txt`에 보관(git 미추적).

## 남은 전제 (PR-0 — 사용자 직접 작업)

1. 앱인토스 콘솔 앱 등록 + 동의항목(email)·scope(`user_key`).
2. 서버 mTLS 인증서 발급 → EC2 배치.
3. 샌드박스에서 **WebView 오리진 실측** → 서버 `booktimer.miniapp.allowed-origins`에 설정.
   이게 비어 있으면 미니앱→서버 호출이 CORS로 막힌다.
4. 토스 API 실응답 필드명 확인 — 다르면 서버 `auth/TossLoginClient.java` 한 곳만 수정.
