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
npm run dev                    # http://localhost:5300 — 실서버(:8080 또는 운영)에 붙는다
npm run dev:mock               # 목 모드: 서버·토스 SDK 없이 브라우저에서 전 화면 + HMR
npm test                       # vitest — API 클라이언트 분기 로직
npm run build                  # tsc -b && vite build → dist/
```

`npm run dev`는 서버를 별도로(`./gradlew bootRun`) 띄워야 하고, 브라우저에서는 토스 SDK(`TossAuth.login`)가
없어 로그인 단계에서 멈춘다.

**화면 작업은 `npm run dev:mock`으로 한다** — `.env.mock`의 `VITE_DEV_MOCK=1`이 켜지면 `api.ts`가 서버 대신
`src/dev-mock.ts`의 픽스처를 돌려주고 토큰을 더미로 둬, 로그인을 건너뛰고 홈·서재(검색·추가)·소셜(검색·팔로우·
책방)·여백(책별 글 목록·작성)·기록·목표가 브라우저에서 바로 뜬다. 픽스처 수정은 그 파일 한 곳이고, 상태는 모듈
메모리라 새로고침이 초기화다. 목에 없는 경로는 404로 던진다(빠진 목을 조용히 넘기지 않는다).
목 코드는 `import.meta.env.DEV` 게이트 + dynamic import로 프로드 번들에서 잘리고, `deploy.sh`가 `__DEV_MOCK__`
부재를 배포 전에 재확인한다.

> ⚠️ **실검증(실로그인·리워드 광고·알림 동의 등 SDK 연동)은 목 모드로 대체되지 않는다** — 아래 `deploy.sh` +
> 실기기가 유일한 경로다. 샌드박스 앱의 dev(핫 리로드) 연결은 granite(RN) 전용이라 웹 미니앱엔 없다(T-152).

## 배포 (앱인토스 CLI — 우리 CI에 얹지 않는다)

> 운영값(`VITE_API_BASE_URL` · `VITE_REWARD_AD_GROUP_ID` · `VITE_INTERSTITIAL_AD_GROUP_ID` · `VITE_PERSONALITY_AD_GROUP_ID`)은 **`.env.production`에 커밋돼 있어** 별도 env 주입 없이
> `npm run build`만으로 운영 번들이 나온다(비밀값이 아니라 커밋 — 없이 빌드하면 vite 기본값인 localhost API·광고 OFF로
> 조용히 구워진다, T-148). 로컬 개발은 `.env.local`이 이 파일을 덮는다.

**배포는 `deploy.sh` 하나로 한다** — 수동 3단계(`npm run build` → `npx ait build` → `npx ait deploy`)는
클린 빌드·산출물 검증 없이 이어져 **스테일 번들을 심사에 올린 적이 있다**(T-150 — 빌드가 완료 로그 없이
조용히 끝났는데 exit 0이라 체인이 계속됐고 옛 dist가 그대로 배포됐다).

```bash
# --expect = 이번 릴리스에서 **새로 들어간** 문구·값. 여러 개 줄 수 있다.
bash deploy.sh --expect "토스로 시작하기" --expect "borderRadius:28"
```

`deploy.sh`가 하는 일: `rm -rf dist` 클린 빌드 → **dist 검증**(index.html이 참조하는 js가 실존하는가 ·
`booktimer.app` 있음 · `localhost:8080` 없음 · 각 `--expect` 포함) → `npx ait build` → **.ait 검증**
(zip 안의 js가 방금 빌드한 dist의 js와 바이트 동일 — 패키징 단계 스테일 차단) → `npx ait deploy` →
deploymentId 출력. **어느 검증이든 실패하면 배포 전에 exit 1**.

> ⚠️ `--expect`는 **직전 번들과 구별되는 것**이어야 한다. 운영 env 값(`booktimer.app`·광고 ID)은 직전
> 번들에도 있어 신·구를 못 가른다 — 그게 T-150에서 검증을 통과시킨 원인이다.

게이트 자체의 스모크 테스트: `bash tests/test-deploy-gate.sh`(실 빌드·실 배포 없이 npm/npx 스텁으로 검증만).

> ⚠️ `npx ait token add`는 **존재하지 않는다** — 동봉 CLI(`@apps-in-toss/web-framework` 3.0.2의 `ait`)의
> 서브커맨드는 `build` / `deploy` / `init`뿐(실측 2026-08-07). 인증은 사전 등록이 아니라 **배포 시점에
> `--api-key`로 전달**한다. 키는 메인 repo `.claude/.secrets/ait-api-key.txt`에 보관(git 미추적).

### 업로드 이후 — 콘솔 단계 (심사 기간 · 롤백)

`deploy.sh`가 끝나면 나머지는 앱인토스 콘솔이고 **사용자 몫**이다([공식 가이드](https://developers-apps-in-toss.toss.im/guide/operation/deploy)).

- **심사 기간 = 영업일 기준 최대 3일**(일부 카테고리 7일+). 우리는 지금까지 계속 당일 승인이었지만
  그건 운이지 SLA가 아니다 — 출시일이 걸린 작업이면 3일을 전제로 잡는다. **한 번에 한 버전만 제출** 가능하고,
  심사 중에는 [요청 취소하기]로 물릴 수 있다(반려 사유는 [반려 사유 보기], 추가 문의는 채널톡).
- **롤백 = 콘솔 「앱 출시」 메뉴에서 이전 버전 선택 → [출시하기]**. 재배포·재심사 없이 즉시 되돌아간다 —
  운영 사고 시 첫 카드가 이것이다(코드 수정·재업로드는 그 다음). 출시는 **전체 사용자에게 즉시 반영**되니
  롤백 대상 버전을 고를 때도 신중히. 심각한 오류의 핫픽스 경로는 채널톡 즉시 문의.

## 남은 전제 (PR-0 — 사용자 직접 작업)

1. 앱인토스 콘솔 앱 등록 + 동의항목(email)·scope(`user_key`).
2. 서버 mTLS 인증서 발급 → EC2 배치.
3. 샌드박스에서 **WebView 오리진 실측** → 서버 `booktimer.miniapp.allowed-origins`에 설정.
   이게 비어 있으면 미니앱→서버 호출이 CORS로 막힌다.
4. 토스 API 실응답 필드명 확인 — 다르면 서버 `auth/TossLoginClient.java` 한 곳만 수정.
