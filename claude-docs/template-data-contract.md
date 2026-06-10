# 템플릿 데이터 계약 — 백엔드 → 디자인 핸드오프

> 각 화면이 컨트롤러에서 받는 모델 변수·nullable·**빈 상태**를 적는다.
> 디자인 세션은 이 문서를 읽고 "그려야 할 상태"를 빠짐없이 안다(존재하지 않는 데이터로 화면을 그리거나
> 빈 상태를 빠뜨리는 것 방지 — [N-055](learning-notes.md)의 디자인판).
> 워크플로: [frontend-design-workflow.md](frontend-design-workflow.md). (시작: 2026-06-09)

**채우는 규칙**: 입구 화면을 먼저 채우고, 그 화면 디자인에 들어갈 때 해당 컨트롤러를 읽어 정확히 기입한다.
한 번에 25개를 다 채우지 않는다(stale 방지) — `TODO: 도달 시 기입`으로 둔다.

범례: `()` = nullable / 빈 상태 = 컬렉션이 비었을 때 / 🔒 = 로그인 필요.

---

## landing — `/` (비로그인) · 공개

- **서빙**: `DashboardController.dashboard()` — `principal == null`이면 `return "landing"`.
- **모델 데이터**: **없음(완전 정적).** Thymeleaf는 URL 링크(`th:href`)에만 쓰임 — `/signup`, `/oauth2/authorization/google`, `/login`, `/privacy`.
- **빈 상태**: 해당 없음(데이터 없음).
- **디자인 자유도**: **최대.** 백엔드 결합 0 → 디자인 세션이 통째 소유. 마크업·CSS 무엇이든 바꿔도 컨트롤러 영향 없음.
- **현재 구조**(참고): `.brand` 헤더 / `.landing-hero`(태그라인·리드·CTA 2개: 무료 시작 + Google) / `.card`×2(기능 목록 `.landing-features`, 동작 설명 `.landing-steps`) / `.landing-bottom-cta` / 로그인·개인정보 링크.
- **주의**: CTA `href`(`/signup`, `/oauth2/authorization/google`)와 SEO `<title>`·`<meta description>`는 **의미라 보존**. 시각·레이아웃·카피 톤은 자유.

---

## signup — `/signup` · 공개

- **서빙**: `SignupController` — GET 빈 폼, POST `@Valid` 검증 → `register` → `redirect:/login?registered`. 검증 실패/`loginId` 중복·예약어/`timezone` IANA 무효는 `field-error`로 **재렌더(입력값 `th:field` 보존)**. 이메일 중복은 열거완화로 성공과 동일 리다이렉트(에러 안 뜸).
- **모델 데이터**: `signupForm`(`SignupForm`: `loginId`·`password`·`nickname`·`email`·`timezone`) · `timezones`(`List<String>`, `@ModelAttribute`라 GET·POST 재렌더 모두 자동 적재).
- **빈 상태**: 없음(폼). **에러 상태 = 각 필드 `#fields.hasErrors`** — 마감/검증 시 에러 렌더(빨강 메시지 + 입력 보존)를 반드시 그려봐야.
- **보존 불변식**: `th:object="${signupForm}"` · 각 `th:field`·`th:errors` · `timezones` `th:each` · action `@{/signup}` · `/login`·`/privacy` 링크 · `<title>`.
- **현재 구조**(참고): `.brand` 헤더 / `.entry-hero`(환영 헤드라인+서브카피) / `.card`(form 5필드 — 각 `.field`>label+input/select+`.field-hint`(loginId·password·timezone)+`.field-error`) / `.link-row`×2(로그인·개인정보). 디자인: PR #289에서 `.greeting` → `.entry-hero` + `.field-hint` 종이톤 정의.

## onboarding — `/onboarding` 🔒

- **서빙**: `OnboardingController` — 신규 가입 게이트(`DashboardController`가 `!isOnboarded()`면 리다이렉트). GET은 이미 온보딩했으면 `redirect:/`. POST `@Valid` → `complete` → `redirect:/`(PRG). loginId 필수(소셜)·중복·예약어는 `field-error`로 재렌더.
- **모델 데이터**: `onboardingForm`(`OnboardingForm`: `loginId`·`nickname`·`incrementMinutes`) · `needsLoginId`(`boolean`). 기본값 prefill: `nickname`=현재 닉, `incrementMinutes`=가입 시드 타이머(분).
- **분기**: `needsLoginId=true`(소셜 로그인만 `login_id` 미정) → loginId 입력칸 + greeting 서브카피 분기. `false`(로컬, 가입에서 받음) → loginId 칸 없음.
- **빈 상태**: 없음(폼). 에러 상태 = `#fields.hasErrors`(특히 loginId 필수·중복).
- **보존 불변식**: `th:object="${onboardingForm}"` · `needsLoginId` `th:if`(loginId 칸)·`th:text`(greeting 분기) · 각 `th:field`·`th:errors` · prefill 기본값 · action `@{/onboarding}`.
- **현재 구조**(참고): `.brand` / `.entry-hero`(환영 헤드라인+동적 서브카피) / `.card`(`.entry-note` 가치 안내 박스 + form: loginId(`th:if`)·nickname·incrementMinutes + 시작 버튼). 디자인: PR #290에서 `.greeting` → `.entry-hero` + `.status-line muted` 안내 → `.entry-note`(세이지 박스로 위계 강화).

## dashboard — `/` 🔒 (로그인 착지점)

- TODO: 도달 시 기입. **빈 상태가 핵심** — 책 0권/기록 0일 첫 진입 화면이 입구 인상을 좌우.
  라이브 영역(타이머·잔여)은 `DashboardModel` 위임 + htmx 무리로드 경로와 상태 공유, 잔디는 전체 렌더에서만.

---

## (내부 화면 — 입구 이후)

books · profile · history · personality · settings · book-detail 등은 입구 사이클 종료 후 필요 시 기입.
이미 데이터 계약 일부가 코드 주석·테스트에 있음(예: profile의 `personality`는 nullable, `shelfFilter` null=전체).
