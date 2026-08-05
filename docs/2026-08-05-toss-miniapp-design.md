# 토스 앱인토스(미니앱) 퍼블리싱 — 설계

> 🧭 세션 메타: model=claude-fable-5 · effort=high
>
> 작성일 2026-08-05. 사용자 결정: **"DB(=기존 서버·도메인 로직)만 공유하고, 프론트는 토스에 맞게 새로 만든다."**
> **채널 전략: 토스 이전이 아니라 병행이다.** 웹(booktimer.app)은 PC 사용자를 위해 계속 유지·운영하고, 토스 미니앱은 모바일 채널이다. **같은 사람이 PC에선 웹, 모바일에선 토스로 같은 기록을 봐야 한다** — 그래서 DB 공유가 요구사항이고, **기존 계정 ↔ 토스 신원 연결이 MVP 필수다**(§2.2).
> 이 문서는 PR-3(미니앱 프론트)을 다른 머신에서 구현하기 위한 핸드오프로 repo에 커밋했다(원래 gitignored 임시 산출물이었음). PR-3 완료 후 이 문서는 삭제하거나 아카이브한다.

---

## ⚡ 구현 현황 (2026-08-05 기준 — 서버 완료, 프론트 대기)

| 단계 | 상태 |
|---|---|
| PR-1 서버: 토스 로그인 + Bearer 인증 기반 | ✅ 머지 (#695) — 테스트 39건 추가, 전체 1377 GREEN |
| PR-2 웹 연결 코드 UI + 미니앱 목표 API | ✅ 머지 (#696) — 테스트 11건 추가, 전체 1388 GREEN |
| PR-0 콘솔 앱 등록·mTLS 인증서·샌드박스 실측 | ⬜ **사용자 직접 작업 — PR-3 실검증의 전제** |
| PR-3 미니앱 프론트 (`miniapp/`) | 🔜 이 문서 §2.5·§3 PR-3 절대로 구현 |

**구현 과정에서 설계가 보정·확정된 지점** (아래 본문은 원 설계 그대로이므로, 충돌 시 이 표가 우선):

1. **미니앱 인증 체인 매처** = `/api/toss/**` 전부 + 그 외 `/api/**`는 Bearer 헤더 또는 CORS 프리플라이트일 때 (원 설계의 "Bearer 헤더 있는 `/api/**`"만으로는 로그인 요청이 막힘 — 로그인은 아직 토큰이 없다).
2. **연결 코드 저장·검증은 PR-1에 포함**됐다(원 설계는 PR-2) — `/api/toss/link`가 의존해서. PR-2는 발급 UI 배선 + 목표 API만.
3. `User.ofToss` 팩토리는 만들지 않았다 — 계정 생성은 `registerOAuth` 재사용 + `linkTossUserKey(userKey)`(once-set 불변).
4. 합성 이메일은 userKey를 8자로 자르지 않는다(충돌 방지) — 형식을 깨는 문자만 제거.
5. `POST /api/toss/register`는 멱등(이미 등록된 userKey면 기존 계정 반환).
6. 목표 설정은 `OnboardingService.setDailyGoal(User, long)` — `complete()`에서 login_id·onboarded를 뺀 메서드.
7. 계정 삭제 purge가 새 FK 자식(`api_token`·`toss_link_code`)을 먼저 정리한다.

**PR-3가 소비할 서버 API 계약** (전부 main에 머지됨):

- `POST /api/toss/login {authorizationCode, referrer}` → 등록됨: `{registered: true, token, ...}` / 미등록: `{registered: false}` (계정 미생성). 인증 불필요.
- `POST /api/toss/register {authorizationCode, referrer}` → 신규 TOSS 계정 생성 + 토큰. 멱등.
- `POST /api/toss/link {authorizationCode, referrer, linkCode}` → 웹에서 발급한 코드로 기존 계정에 연결 + 토큰. 만료·소비·부재 코드는 거부, 이미 연결 409.
- `POST /api/toss/logout` (Bearer) → 토큰 폐기.
- `POST /api/miniapp/goal {dailyIncrementSeconds}` (Bearer) → 하루 목표 설정.
- 기존 API 재사용: `GET /api/dashboard`, `POST /api/sessions/start|stop`, `POST /api/sessions/{id}/tag-book` 등 — 전부 Bearer 헤더로 호출(401 = 토큰 만료 → 재로그인 플로우).
- 응답 필드 상세는 `web/api/TossAuthApiController.java` · `DashboardApiController.java` 의 record DTO가 단일 출처.
- CORS 허용 오리진: 프로퍼티 `booktimer.miniapp.allowed-origins` (현재 빈 값 — **PR-0 샌드박스에서 WebView 오리진 실측 후 운영 환경변수로 설정해야 미니앱→서버 호출이 열린다**).
- ⚠️ 토스 API 응답 필드명은 공식 문서에 정확한 스키마가 없어 유연 파싱(`JsonNode.findValue`)으로 구현돼 있다 — PR-0 실측에서 다르면 `auth/TossLoginClient.java` 한 곳만 수정.

---

## 0. 결론 요약

```
토스 미니앱 프론트 (신규: miniapp/ — Vite + React + TDS + @apps-in-toss/web-framework)
        │  Bearer 토큰으로 기존 /api/** JSON API 호출 (CORS)
        ▼
기존 Spring Boot 서버 (booktimer.app EC2)
  + POST /api/toss/login  (토스 인가코드 → mTLS 토큰교환 → 자체 Bearer 토큰 발급)
  + 기존 계정 연결: 웹 설정에서 일회용 연결 코드 발급 → 미니앱에 입력 → 같은 User로 합류
  + Bearer 인증 필터체인 (stateless, CSRF 없음 — 기존 세션 체인과 별도)
        ▼
기존 MySQL (users.toss_user_key 컬럼 + api_token·연결코드 테이블만 추가)
```

**핵심 실측**: 기존 서버에 이미 광범위한 JSON API(`web/api/` 14개 컨트롤러 — dashboard·sessions start/stop/tag·history·books·search·garden·profile·follow…)가 있다. 웹 대시보드가 Vue 섬 + `/api/**` 구조라서, **미니앱이 재사용할 API는 대부분 이미 존재한다.** 새로 만드는 것은 ① 토스 로그인 → 자체 토큰 발급 경로 ② 기존 계정 연결(코드 방식) ③ Bearer 인증 체인 ④ 미니앱 경량 온보딩 API ⑤ 미니앱 프론트뿐이다.

**웹은 그대로 산다**: 이 설계는 웹 코드(SSR·Vue 섬·세션 인증)를 한 줄도 바꾸지 않는 것을 목표 제약으로 삼는다. 미니앱은 추가 채널이지 대체가 아니다. 유일한 웹 쪽 신규 UI는 설정 페이지의 "토스 앱 연결" 절(연결 코드 발급) 하나다.

---

## 1. 현황 (실측 — 추측 아님)

### 1.1 이미 있는 것

| 자산 | 위치 | 미니앱 관점 |
|---|---|---|
| JSON API 전 표면 | `src/main/java/com/booktimer/web/api/` (DashboardApiController 등 14개) | **그대로 재사용.** GET /api/dashboard 하나로 타이머·잔디·서재·격언까지 단일 응답. POST /api/sessions/start·stop·{id}/tag-book 뮤테이션 완비. 에러 계약(404 IDOR 마스킹 / 409 중복) 확립 |
| OAuth 프로비저닝 | `user/OAuthUserProvisioningService` — find-or-create, `UserRegistrationService.registerOAuth`(User+ReadingTimer 한 트랜잭션) | 패턴 재사용. 단 **토스는 이메일 신원이 아니라 userKey 신원**이라 find 키가 다름(§2.2) |
| principal 해석 | `security/CurrentUserService` — loginId → email 브리지 폴백 | Bearer 필터가 principal name만 맞춰주면 **API 컨트롤러 무수정** |
| 인증 체인 | `config/SecurityConfig` — 폼로그인+세션+CSRF, default-deny | 그대로 유지. 미니앱용 체인을 **별도로 추가**(§2.3) |
| 일회용 토큰 인프라 | `email/EmailTokenService`(발급·소비·만료·일회성) | **연결 코드의 참조 패턴**(같은 성질: 추측불가·일회용·만료) |
| 레이트리밋 | `security/RateLimitService`, `LoginAttemptService` | /api/toss/login·연결 코드 검증 보호에 재사용 |
| Vue 섬 프론트 | `frontend/src/` (dashboard·books·history·garden…) | **재사용 안 함**(사용자 결정 — 토스 UX로 신규). 단 API 호출 계약·DTO 형태의 참고 자료로 가치 큼 |

### 1.2 토스 쪽 요건 (공식 문서 실측)

- 기존 웹 기술로 미니앱 개발: `@apps-in-toss/web-framework`(WebView) + Vite. UI는 `@toss/tds-mobile`(React). 권장 MVP 조합 = WebView/Vite + TDS + 토스 로그인.
- 토스 로그인 서버 연동: 클라 `appLogin()` → `authorizationCode`·`referrer` 획득 → 서버가 `POST https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/user/oauth2/generate-token` (**mTLS 필수**, 인가코드 10분·일회성) → `accessToken`(1h)·`refreshToken` → `GET .../oauth2/login-me`로 사용자 조회.
- 조회 필드: `userKey`(scope `user_key` — 앱 단위 식별자), `email`(콘솔 동의항목 선택 시 — **토스 계정에 이메일 없으면 null**).
- mTLS 클라이언트 인증서는 앱인토스 콘솔 "서버 mTLS 인증서 발급받기"에서 발급.
- 입점: 계약 없이 출시 가능, 수익 정산 시에만 사업자 등록. 심사(사행성·선정성 등) 있음.

### 1.3 제약 (도메인 불변식 — 설계가 지켜야 함)

- `onboarded ⟹ login_id IS NOT NULL` (DB CHECK V15). login_id는 **한 번 정하면 불변**(공개 @핸들).
- `users.email` NOT NULL + 유니크. OAuth 프로비저닝은 "검증된 이메일"만 자동 연결(pre-hijacking 차단, N-026/N-053).
- 모든 User는 ReadingTimer를 가진다(`registerOAuth`가 한 트랜잭션 보장 — 재사용하면 자동 충족).
- null-state(login_id 없는) 유저는 발견/목록에서 제외된다(N-055 — 이미 테스트로 고정).

---

## 2. 설계 결정 (옵션 비교 + 추천)

### 2.1 인증 방식: 자체 Bearer 토큰 (추천) vs 세션 쿠키

| | ⓐ Bearer 토큰 (추천·구현됨) | ⓑ 세션 쿠키 재사용 |
|---|---|---|
| 동작 | 토스 로그인 성공 시 서버가 불투명 토큰 발급, 미니앱이 `Authorization: Bearer` 헤더로 호출 | WebView가 booktimer.app 쿠키 보유, SameSite=None 필요 |
| 리스크 | CORS 설정만 하면 끝. CSRF 불필요(쿠키 자체가 없음) | 미니앱 번들은 토스 인프라 오리진에서 서빙 → **크로스사이트 쿠키 = WebView 서드파티 쿠키 차단에 취약.** 실측 전까지 동작 보장 불가 |
| 구현량 | 토큰 테이블 + 필터 (작음) | Security 설정 변경 + SameSite 조정 (작아 보이나 발밑 불안) |

**채택 ⓐ.** 미니앱 표준 패턴이고, 실패 모드(서드파티 쿠키 차단)가 우리 통제 밖인 ⓑ를 피한다.

- 토큰: **불투명 랜덤(32바이트) + DB에 SHA-256 해시 저장**. JWT 안 쓴다 — 새 의존성 없이 되고, 즉시 폐기(revoke) 가능하고, 유저 수 규모에서 DB 조회 1회는 비용 아님.
- `api_token` 테이블: TTL 90일, 사용 시 `last_used_at` 갱신(슬라이딩 연장은 v2 — 만료 시 재로그인. 토스 `appLogin()`이 무마찰이라 재로그인 비용이 거의 0).
- 토스의 accessToken/refreshToken은 **로그인 시점 신원 확인에만 쓰고 버린다**(저장 안 함).

### 2.2 계정 정책: userKey 신원 + **명시적 연결 코드(MVP 필수)** — 이메일 자동 연결은 금지

**요구사항**: 같은 사람이 PC(웹)와 모바일(토스)에서 **같은 User 행**을 써야 한다(채널 병행 전략). 그런데 토스 email은 null일 수 있고 검증 보증이 없어, Google처럼 이메일 자동 연결을 하면 계정 탈취 벡터가 된다(§1.3). → **자동 연결은 금지하고, 본인이 증명하는 명시적 연결을 MVP에 넣는다.**

**연결 방식 비교**:

| | ⓐ 연결 코드 (채택) | ⓑ 미니앱에서 ID/비번 입력 | ⓒ 이메일 자동 매칭 |
|---|---|---|---|
| 흐름 | 웹 설정(로그인 상태)에서 일회용 코드 발급 → 미니앱에 입력 | 미니앱에서 login_id+비밀번호 | 토스 email == users.email이면 연결 |
| GOOGLE 소셜 계정 | ✅ 됨(웹 세션이 신원 증명) | ❌ 비밀번호가 없음 | — |
| 보안 | 코드 = 짧은 TTL·일회용·추측불가 | 미니앱에 비밀번호 입력면 노출 | ❌ 탈취 벡터(위 참조) |

**전체 신원 흐름** (미니앱 첫 진입):

1. `appLogin()` → `POST /api/toss/login {authorizationCode, referrer}` → 서버가 mTLS로 userKey 확인.
2. `users.toss_user_key == userKey`인 행이 **있으면** → 그 User로 Bearer 토큰 발급. 끝(재방문 경로 — 연결이든 신규든 동일).
3. **없으면** → **계정을 만들지 않고** `{registered: false}` 반환. 미니앱이 선택 화면을 띄운다:
   - **"새로 시작"** → `appLogin()` 재호출(인가코드는 일회성이라 새로 받음 — 무마찰) → `POST /api/toss/register` → TOSS 신규 User+Timer 생성, 토큰 발급.
   - **"기존 계정 연결"** → 안내: 웹 설정에서 코드 발급 → `appLogin()` 재호출 → `POST /api/toss/link {…, linkCode}` → 코드 검증 통과 시 **그 코드를 발급한 기존 User에 `toss_user_key`를 붙이고** 토큰 발급. 이후 미니앱의 모든 기록이 기존 계정으로 쌓인다.
   - 서버에 pending 상태를 두지 않는다 — 각 단계가 fresh 인가코드로 신원을 다시 증명하므로 상태 없는 3-엔드포인트로 끝난다.
4. 웹 쪽: 설정 페이지 "토스 앱 연결" 절 — 코드 발급 버튼(TTL 5분·일회용, 이미 연결된 계정이면 연결됨 표시). 발급은 로그인 세션 필수. (PR-2로 구현 완료.)

**신규 TOSS 계정의 email 처리** ("새로 시작" 경로): 토스가 email을 주면 그대로 저장하되 **`emailVerified=false`**(검증 보증 없음 — 넛지 메일 대상 제외가 자동으로 따라옴). null이거나 기존 계정과 충돌하면 합성 주소(`toss-…@noreply.booktimer.app`) 폴백 — 자동 연결 금지 불변식 유지. **연결된 계정의 authProvider는 원래 값(LOCAL/GOOGLE) 유지** — toss_user_key 보유 여부가 토스 로그인 가능을 뜻한다.

**역방향(토스에서 시작한 유저가 나중에 PC 웹을 쓰고 싶다)은 v2** — MVP 우회로는 "웹에서 가입 후 연결".

### 2.3 Security: 별도 필터체인 (채택)

- `@Order(0)` 별도 SecurityFilterChain — 매처는 **구현 현황 표 1번**(원 설계에서 보정됨) 참조.
- STATELESS + CSRF 비활성 + BearerTokenFilter + 401 엔트리포인트. `/api/toss/login·register·link`만 permitAll.
- **기존 세션 체인 무수정** — 웹 Vue 섬의 `/api/**` 호출(쿠키·CSRF, Bearer 없음)은 기존 체인으로 그대로.
- BearerTokenFilter의 principal = `loginId ?: email` — CurrentUserService 브리지가 받아줘 **API 컨트롤러 전부 무수정 재사용**.
- CORS: 프로퍼티 `booktimer.miniapp.allowed-origins`(기본 빈 값 = 교차 출처 안 열림). PR-0 실측 후 운영 환경변수로 설정.

### 2.4 온보딩: login_id 미강제 (채택)

- 미니앱 MVP는 login_id를 만들지 않는다(불변 핸들 작명을 첫 진입에 강요하지 않음). 연결 계정은 기존 login_id를 이미 가짐.
- 미니앱 온보딩 = 하루 목표만: `POST /api/miniapp/goal` → `OnboardingService.setDailyGoal`. `completeOnboarding()` 미호출 → CHECK 불변식 안전.
- login_id null 유저는 발견/검색에서 자동 제외(N-055) — 소셜 없는 미니앱 MVP에선 프라이버시 기본값으로 알맞다.
- 신규 계정 기본 목표는 1시간(3600s) 시드 — 온보딩 화면을 건너뛰어도 동작한다.

### 2.5 미니앱 프론트 스택 (PR-3 — 이 절이 구현 대상)

- **`miniapp/` 디렉터리 신설** (기존 `frontend/`와 완전 분리 — frontend는 우리 서버 정적 번들, miniapp은 토스 인프라로 배포). 코드 공유 금지(불필요한 결합).
- 스택: **Vite + React + `@toss/tds-mobile` + `@apps-in-toss/web-framework`** — 토스 공식 권장 조합 그대로.
- MVP 화면 5개:
  1. **로그인 브릿지** — 진입 시 `appLogin()` 자동 호출 → `/api/toss/login`. 등록된 userKey면 토큰 저장 후 바로 홈. 미등록이면 **선택 화면**: "새로 시작" / "기존 booktimer 계정 연결".
  2. **계정 연결** — 연결 코드 입력 폼 + "웹 설정에서 코드를 받으세요" 안내. 성공 시 홈으로.
  3. **타이머 홈** — GET /api/dashboard 축약 렌더: 오늘 남은 시간·시작/정지·책 선택(readingBooks)·미태깅 세션 태깅 시트. POST /api/sessions/start·stop·tag-book.
  4. **기록** — 잔디(ContributionGraphDto)·연속일·총 시간. stop 응답에 graph 동봉 → 즉시 갱신.
  5. **목표 설정** — 신규 계정 첫 실행 유도 + 이후 변경. POST /api/miniapp/goal.
- API 클라이언트: fetch + Bearer, 401 → 재로그인 플로우, `registered:false` → 선택 화면 분기.
- 서재 전체 관리·검색 추가·정원·책BTI·팔로우는 **전부 v2** — **웹이 풀 기능 본진**이므로 미니앱은 기능 동등성을 쫓지 않는다(측정·확인 동반 채널).
- 배포는 앱인토스 CLI/콘솔(토스 인프라) — 우리 CI에 안 얹는다(MVP). 빌드 재현 커맨드만 README에 기록.
- 테스트: 순수 로직이 얇다 — API 클라이언트의 401 분기·registered:false 분기 정도만 vitest. **실검증 게이트 = 토스 샌드박스 실기기**: appLogin→(연결 또는 신규)→타이머 시작/정지→잔디 갱신 1사이클 + **웹 PC 교차 확인**(연결 계정으로 양쪽에서 같은 기록 — 채널 병행의 acceptance).

### 2.6 토스 API 클라이언트 (서버 — 구현 완료)

- `auth/TossLoginClient.java` — RestClient + SSL Bundle mTLS, **지연 초기화**(인증서 없이도 앱 기동, 토스 엔드포인트만 401). 인증서·키는 EC2 파일 배치 + 환경변수 경로. **레포에 절대 커밋 금지.**

---

## 3. 남은 작업

### PR-0 — 스파이크·셋업 (사용자 직접, 코드 머지 없음)

1. 앱인토스 콘솔 앱 등록(개인) + 동의항목(email)·scope(user_key) 설정.
2. mTLS 인증서 발급 → EC2 배치(환경변수로 경로 지정).
3. `miniapp/` hello-world를 샌드박스에 올려 실측: **WebView 오리진 값 확정** → 운영 `booktimer.miniapp.allowed-origins` 설정, `appLogin()` 동작, booktimer.app으로 fetch 프리플라이트 통과 여부, **토스 API 실응답 필드명 확인**(다르면 `TossLoginClient` 수정).
4. (게이트) 여기서 막히면(예: CORS 불성립) 설계 재소집.

### PR-3 — 프론트: miniapp/ 신설

- §2.5 그대로. PR-0 실측값이 나오기 전에도 화면·클라이언트 구현은 진행 가능(로컬 dev 서버 + 로컬 백엔드로 개발, 샌드박스 실검증만 PR-0 이후).

### 출시 — 심사 제출 + 운영 확인

- 심사 제출, 승인 후 실기기 확인(웹 PC ↔ 토스 모바일 교차 시나리오). 서버 부하 관찰(EC2 단일 인스턴스 — /api/dashboard가 첫 병목 후보, 대응은 별도 작업).

---

## 4. 리스크 · 하지 않기로 한 것

**리스크 상위 3**: ① WebView 오리진/CORS 실측 불확실(PR-0 게이트) ② mTLS 인증서 만료·갱신 운영(배포 문서에 만료일 기록) ③ 트래픽 급증 시 EC2 단일 인스턴스 병목(관찰 후 별도 대응).

**하지 않기로 한 것(명시)**: 이메일 자동 계정 연결(탈취 벡터 — 명시적 코드 연결로 대체) · 연결 해제 UX(v2 — 문의 처리) · 역방향 연결: 토스 시작 유저의 웹 로그인 수단(v2) · 미니앱 소셜 기능(v2) · JWT(불투명 토큰으로 충분) · 세션 쿠키 재사용(서드파티 쿠키 리스크) · frontend/ 코드 공유(결합 금지) · 토스 refreshToken 저장(자체 토큰이 대체) · 미니앱-웹 기능 동등성(웹이 본진).

---

## 5. PR-3 구현 세션 지시

- 브랜치 → PR → 사용자 머지 확인 절차(프로젝트 Git 워크플로) 준수. changelog.md 한 줄 + plan.md 토스 절 PR-3 진행 표시 갱신을 같은 브랜치에 포함.
- 구현 중 이 설계와 어긋나는 발견(특히 토스 SDK 동작·오리진)이 나오면 임의로 벗어나지 말고 멈추고 보고(드리프트 규칙).
- 커밋 trailer: `Session-Model` / `Session-Effort`(구현 세션 값으로).
