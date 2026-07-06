# 코드 지도 — 기능별 파일 안내 (code-map)

> **목적**: 특정 기능 작업을 시작하기 전에 여기서 **진입점(URL→컨트롤러)·소속 패키지·배선 주의**를 먼저 잡고 해당 파일로 직행한다. 작업 전 이 문서부터 훑는다.
>
> **⚠️ 이건 "린 지도"다 — 전 클래스 카탈로그가 아니다.** 도메인 패키지(`book/`·`garden/`·`session/`·`follow/` …)는 이미 기능 응집형이라 디렉터리만 봐도 경계가 보인다. 그래서 여기선 **트리가 안 보여주는 것**만 집는다 → ① `web/` 컨트롤러 50개는 기능이 아니라 기술 계층으로 묶여 있어 진입점 매핑이 사각, ② 한 기능이 여러 패키지에 흩어지는 배선.
>
> **⚠️ stale 주의**: 파일이 옮겨지거나 이름이 바뀌면 이 지도는 낡는다. **최종 진실은 코드다** — 의심되면 `Glob`/`Grep`으로 확인하고, 구조를 바꿨으면 이 파일도 같은 PR에서 갱신한다. 그래서 여기엔 요약을 최소화하고 **파일 경로만** 적어 유지비를 낮췄다.
>
> **관련**: 도메인 규칙·부채 계산은 [../README.md](../README.md) §6·§7, 세부 설계는 이 폴더의 `*-design.md`.

## 패키지 배치 한눈에

```
com.booktimer/
  web/            ← 컨트롤러 (기술 계층 — 기능별로 안 나뉨. 이 지도의 핵심 사각)
    api/          ←   JSON API 컨트롤러 (Vue SPA용, /api/**. default-deny + CSRF 자동보호)
  <feature>/      ← 도메인 패키지 (기능 응집: user, book, session, timer, garden, follow,
                     block, report, search, popularity, profile, story, personality,
                     email, push, retention, quote, feedback, admin)
  security/       ← 인증·인가 (UserDetails, OIDC, 로그인시도 방어)
  config/ common/ dev/  ← 횡단 관심사 (설정, 공통 엔티티, 로컬 시드)
```

- 프론트: `frontend/src/<feature>/` (Vue 3 SPA) — 마을 번들 산출물은 `src/main/resources/static/garden/garden.js`.
- 템플릿(SSR): `src/main/resources/templates/*.html`.
- DB 스키마 단일 소스: `src/main/resources/db/migration/V*.sql` (Flyway).

---

## 🔑 1. 인증 · 식별 (로그인 / 소셜 / 계정보안)

- **한 줄**: `login_id` 기반 자체 로그인 + Google OIDC. login_id(공개핸들·불변)/nickname(표시)/email(비공개) 3분리, 로그인 무차별대입 방어.
- **진입점**: `/login`(`web/LoginController`) · `/signup` GET·POST(`web/SignupController` + `web/SignupForm`) · `/settings/password`·`/settings/delete`(`web/SettingsController`) · Google OAuth 콜백(`config/SecurityConfig`)
- **소속 패키지**: `user/`(엔티티·등록·계정) · `security/`(UserDetails·OIDC·로그인시도) · `config/SecurityConfig`
- **핵심**: `user/UserRegistrationService` · `user/AccountService` · `user/OAuthUserProvisioningService` · `user/User`·`user/Role`·`user/AuthProvider` · `security/BookTimerUserDetailsService` · `security/BookTimerOidcUserService`·`security/BookTimerOidcUser` · `security/CurrentUserService` · 무차별대입 `security/LoginAttemptFilter`·`security/LoginAttemptService`·`security/RateLimitService`
- **⚠️ 배선 주의**: **로그인 식별자 = `login_id`**(이메일 아님). `security/BookTimerUserDetailsService`는 입력을 소문자화하지 않는다(시드계정 `testid`가 소문자인 이유). OAuth는 `email_verified`만 신뢰하는 find-or-create(`user/OAuthUserProvisioningService`). **현재 유저는 어디서나 `security/CurrentUserService.resolve(principal)`로** 조회.
- **템플릿**: `login.html`·`signup.html`·`settings.html`
- **DB**: `V1`(init) · `V13`·`V14`·`V15`(login_id 도입·유니크 조정)
- **설계**: [login-id-design.md](login-id-design.md) · README §2.2

## 🚪 2. 온보딩

- **한 줄**: 첫 진입 시 login_id(소셜 가입자)·표시이름·타이머 초기값을 1회 설정. SSR 폼 + 실시간 미리보기·목표 프리셋 Vue 아일랜드.
- **진입점**: `/onboarding` GET·POST(`web/OnboardingController` + `web/OnboardingForm`)
- **소속 패키지**: `user/OnboardingService`
- **⚠️ 배선 주의**: 소셜 가입자는 **`login_id=null` 상태로 존재**하다가 온보딩에서 채운다 → 발견/검색 쿼리에서 이 미완성 엔티티가 새면 깨진 링크가 된다(null-state 누출 방지 — README·learning-notes N-055). 완료 플래그는 `user_onboarded`. 하루 목표는 **최소 1분**(`OnboardingForm @Min(1)` — 0분 방지). **온보딩 강화(아이디·닉네임 실시간 미리보기·목표 프리셋)는 Vue 아일랜드** — SSR 폼(th:field)을 값의 단일 소스로 두고 얹는다(TS 수정 후 `npm --prefix frontend run build`, 훅 강제). **아이디 클라 정제(`loginIdPreview.ts`)는 서버 `user/User.normalizeLoginId`의 거울**(소문자·`[a-z0-9_]`·20자) — 미리보기=제출값 일치를 위해 규칙 단일 출처.
- **템플릿**: `onboarding.html`
- **프론트**: `frontend/src/onboarding/`(`OnboardingPreview.vue`, `GoalPresets.vue`, `loginIdPreview.ts`, `main.ts`) · 번들 `static/onboarding/onboarding.js`
- **DB**: `V6`(user_onboarded) · `V15`(login_id-when-onboarded 체크제약)
- **설계**: README §2.2

## ⏱️ 3. 누적 타이머 · 7일 부채 모델 (핵심)

- **한 줄**: 하루 목표 대비 부족분을 7일 윈도우 부채로 lazy 누적/상환. 초과 읽기로 과거 빚 상환, 7일 초과 빚은 자동 용서.
- **진입점**: `/`·`/dashboard`(`web/DashboardController`) · `/api/dashboard`·`/api/sessions/start`·`/api/sessions/stop`·`/api/sessions/{id}/tag-book`(`web/api/DashboardApiController`) · `/sessions/start`·`/sessions/stop`·`/sessions/manual`(`web/ReadingSessionController`) · 목표 설정 `/settings`(`web/SettingsController` + `web/SettingsForm`)
- **소속 패키지**: `timer/`(목표·타이머 엔티티·목표변경 이력) · `session/`(세션·부채계산 — 잔디와 공유)
- **핵심**: 부채 `session/WeeklyDebtCalculator`·`session/ReadingDebtService`·`session/DayDebt`·`session/WeeklyDebt`·`session/DayDebtTrace` · 목표 `timer/GoalSchedule`·`timer/ReadingGoalService`·`timer/ReadingTimer`·`timer/ReadingGoalChange` · 세션 `session/ReadingSessionService`·`session/ReadingSession`·`session/ReadingSessionRepository`
- **⚠️ 배선 주의**: **부채 계산 로직은 `timer/`가 아니라 `session/`에 있다**(`WeeklyDebtCalculator`·`ReadingDebtService`). 목표 변경 시 과거 날짜 소급오염 방지는 `timer/GoalSchedule`(per-day 목표 스냅샷). 배치 스케줄러 없이 **접속 시 Lazy 계산**. 수동입력 경로는 `/sessions/manual`(README 서술의 표현과 실제 URL이 다르니 주의).
- **📌 책 없이 시작 + 종료 후 태깅(발견 1)**: `start`는 **책 선택**(bookId 없이 시작 허용) — 서비스·`DashboardApiController.start`·폼 컨트롤러 3층이 완화됐다. **IDOR 경계는 보존**: bookId가 '있는데' 남의 것/미존재면 여전히 404(API)·에러(폼). 종료 후 태깅 = `ReadingSession.tagBook` + `ReadingSessionService.tagBook`(세션 IDOR=`findByIdAndUser`) + `POST /api/sessions/{id}/tag-book`(책·세션 이중 IDOR, 재태깅 409). `StopResponse`가 `sessionId`·`untagged`를 노출해 프론트가 시트를 띄운다. **집계 무변경**: null-book 세션은 잔디·부채(시간 기반)엔 포함, 책별 통계(`sumSecondsByBook`=`where s.book is not null`)엔 제외 — 이미 그렇게 갈렸고 `ReadingSessionRepositoryTest`가 잠금.
- **프론트**: `frontend/src/dashboard/`(`TimerCard.vue`, `BookPickForm.vue`, `TagBookSheet.vue`, `DashboardApp.vue`, `useReadingTimer.ts`, `timerProgress.ts`, `ContributionGraph.vue`) — 시작 진입(책 없이 옵션)·종료 후 태깅 시트
- **템플릿**: `dashboard.html`·`manual-session.html`
- **DB**: `V4`(세션-책 연결) · `V20`(레거시 컬럼 제거) · `V21`(목표변경 이력) · `V22`(수동입력) · `V30`(부채 이월) · `V53`(세션 인덱스)
- **설계**: [domain-design.md](domain-design.md) · README §2.1·§6·§7

## 📚 4. 책 · 책장 · 도서검색 · 제휴 구매링크

- **한 줄**: 책 등록(알라딘 검색)·책별 누적시간·책장 상태/공개여부 + 서점별 제휴 구매링크(클릭 추적).
- **진입점**: `/books`(`web/BookController`) · `/books/{id}` 상세 · `/books/{id}/buy/{coupang,yes24,kyobo}` + `/u/{loginId}/books/{bookId}/buy/*`(제휴 클릭 리다이렉트) · `/api/books`·`/api/books/search`·`/api/books/{id}/status`·`/api/books/{id}/visibility`(`web/api/BookApiController`) · `/books/readers`·`/api/book-readers`(같이 읽는 사람)
- **소속 패키지**: `book/`
- **핵심**: 엔티티 `book/Book`·`book/BookStatus`·`book/BookVisibility`·`book/Isbn` · 검색 `book/BookSearchClient`(iface)←`book/AladinBookSearchClient` · 제휴 `book/CoupangLinkBuilder`·`book/CoupangDeeplinkClient`·`book/CoupangDeeplinkSigner`·`book/Yes24LinkBuilder`·`book/KyoboLinkBuilder` · 백필 `book/BookCatalogBackfillService`(↔`web/AdminBackfillController`) · 서비스 `book/BookService`·`book/BookRepository`
- **⚠️ 배선 주의**: 제휴 링크는 **서점별 빌더로 분리**(`book/*LinkBuilder`). 쿠팡만 딥링크 API + HMAC 서명(`CoupangDeeplinkClient`+`CoupangDeeplinkSigner`, `config/CoupangDeeplinkProperties`). 클릭수는 **서점별 컬럼으로 분리 집계**(`V5`·`V34`·`V55`·`V59`). 제휴 추적 결함 이력은 memory(coupang/aladin/kyobo affiliate) 참조.
- **프론트**: `frontend/src/books/`·`frontend/src/book-readers/`
- **템플릿**: `books.html`·`book-detail.html`·`book-readers.html`
- **DB**: `V3`·`V4`·`V8`(visibility)·`V12`·`V16`·`V18`·`V58`(finished_at) · 클릭수 `V5`·`V34`·`V55`·`V59`
- **설계**: README §2.3

## 🌱 5. 독서기록 · 잔디(기여그래프) · 히스토리

- **한 줄**: 일자별 독서시간 기록·누적, 잔디 시각화, 월별 기록·주간 부족분.
- **진입점**: `/history`(`web/HistoryController`) · `/api/history`(`web/api/HistoryApiController`)
- **소속 패키지**: `session/`(타이머·부채와 같은 패키지)
- **핵심**: 잔디 `session/ContributionGraph`·`session/ContributionGraphBuilder`·`session/ContributionDay`·`session/ActiveDayCount` · 기록 `session/DailyReadingRecord`·`session/ReadingHistoryService`·`session/MonthlyReadingSection`·`session/BookReadingDetail` · 통계 `session/BookReadingStatsService`·`session/ReadingContributionService`·`session/BookSecondsRow` · 성장식물 `session/GrowthStage`
- **⚠️ 배선 주의**: 잔디·히스토리·부채가 전부 `session/` 한 패키지에 산다. **연속일 성장식물(`session/GrowthStage`)은 마을 정원(`garden/`)과 별개 기능**이니 혼동 금지.
- **프론트**: `frontend/src/history/`(`ContributionGraph.vue`, `MonthlyRecords.vue`, `WeeklyShortfall.vue`, `grassTooltip.ts`)
- **템플릿**: `history.html`
- **DB**: 세션 관련 `V4`·`V22`·`V53`
- **설계**: README §2.4

## 🏘️ 6. 독서 마을 (수집형 게임화 — 정원)

- **한 줄**: 잔디 실적으로 식물·작가캐릭터·출판사건물을 해금해 마을을 채우는 게임화 레이어(보기 전용).
- **진입점**: `/village`·`/garden`(`web/GardenController`) · `/api/garden` GET · `/api/garden/feed`(`web/api/GardenApiController` + `web/api/GardenApiResponse`)
- **소속 패키지**: `garden/`
- **핵심**: 뷰 `garden/GardenService`·`garden/GardenView`·`garden/GardenWorld` · 해금 `garden/AuthorCharacterUnlockCalculator`·`garden/DailyQuotaCalculator` · 먹이/애정 `garden/FeedingService`·`garden/AuthorAffection`·`garden/AffectionLevel`·`garden/FeedRequest`·`garden/FeedResult` · 캐릭터 `garden/AuthorCharacter`·`garden/AuthorCharacterState`·`garden/OwnedCharacter`·`garden/ProfileCharacterService`
- **⚠️ 배선 주의**: **마을 프론트는 Vue 3 SPA** — TS 소스(`frontend/`) 수정 후 `npm --prefix frontend run build`로 `static/garden/garden.js`를 재생성하고 **커밋까지** 해야 반영(훅 `require-bundle-build.ps1`이 강제). 배치/편집 엔진은 은퇴(좌표 저장 없음, 보기 전용). 식물·캐릭터·건물 카탈로그는 Flyway 시드.
- **프론트**: `frontend/src/garden/`(`VillageApp.vue`, `PortraitVillage.vue`, `GardenDex.vue`, `DexCell.vue`) · `frontend/src/dashboard/GardenPanel.vue` · 번들 `src/main/resources/static/garden/garden.js`
- **템플릿**: `garden.html` · `fragments/garden-character-sprites.html`
- **DB**: `V35`~`V44`(식물·장르·레시피·다양성·배치·소품) · `V45`(작가캐릭터)·`V46`~`V49`(출판사건물) · `V52`(애정)·`V54`(프로필 캐릭터)
- **설계**: README §2.5 · memory: garden-spa-vue-migration / garden-vision-coc-zoo

## 👥 7. 소셜 (팔로우 · 차단 · 신고 · 검색 · 공개프로필 · 인기)

- **한 줄**: 공개 프로필·아이디 검색·팔로우·차단·신고 + 팔로우 범위 인기 drill-down.
- **진입점**: `/u/{loginId}` 공개프로필(`web/ProfileController`) · `/search`(`web/SearchController`)·`/api/search`(`web/api/SearchApiController`) · `/api/follow`·`/api/unfollow`(`web/api/FollowApiController`)·`/me/followers`·`/me/following`(`web/FollowListController`)·`/api/follow-list`(`web/api/FollowListApiController`) · `/me/blocks`(`web/BlockController`)·`/api/block`·`/api/unblock`(`web/api/BlockApiController`) · `/api/report`(`web/api/ReportApiController`) · `/api/profile`·`/api/profile/books`·`/api/profile/personality-tag`(`web/api/ProfileApiController`)
- **소속 패키지**: **6개에 흩어짐** — `follow/`·`block/`·`report/`·`search/`·`popularity/`·`profile/`
- **핵심**: 팔로우 `follow/FollowService`·`follow/FollowListService`·`follow/Follow` · 차단 `block/BlockService`·`block/Block` · 신고 `report/ReportService`·`report/Report`·`report/ReportReason`(쓰기 rate limit) · 검색 `search/UserSearchService`·`search/UserRowAssembler`·`search/RecommendedUser`·`search/UserSearchResult` · 인기 `popularity/FollowScopePopularityService`·`popularity/FollowScopeReadersService` + `book/FollowScopeCount`·`book/CoReadCount`·`follow/FriendOfFriendCount` · 프로필 `profile/ProfileService`·`profile/ProfileView`·`profile/ProfileTag`
- **⚠️ 배선 주의**: **소셜은 단일 패키지가 아니다** — 위 6개 도메인 패키지 + `web/` 컨트롤러가 분산. 핵심은 **공개경계·IDOR·차단 게이트**: 공개 책장·인기 drill-down이 모두 같은 게이트(팔로우·PUBLIC·distinct)를 통과해야 새 노출·IDOR이 없다. 검색은 `login_id=null`(온보딩 전) 누출 금지(N-055).
- **프론트**: `frontend/src/search/`·`frontend/src/profile/`·`frontend/src/follow-list/`·`frontend/src/block-list/` · 공용 `frontend/src/shared/`(`FollowAction.vue`, `ReportModal.vue`, `UserRow.vue`, `UserSearchPanel.vue`)
- **템플릿**: `profile.html`·`search.html`·`follow-list.html`·`block-list.html`
- **DB**: `V7`·`V14`(nickname 유니크 조정) · `V9`(follow)·`V10`(block)·`V11`(report)
- **설계**: [sns-design.md](sns-design.md) · README §2.6

## 📸 8. 스토리 (스토리 피드)

- **한 줄**: 프로필별 스토리 카드·피드·조회자 — 순수 API + Vue(SSR 뷰 없음).
- **진입점**: `/api/stories/feed`·`/api/stories/of/{loginId}`·`POST /api/stories`·`DELETE /api/stories/{id}`·`/api/stories/{id}/view`·`/api/stories/{id}/viewers`(`web/api/StoryApiController`)
- **소속 패키지**: `story/`
- **핵심**: `story/StoryService`·`story/Story`·`story/StoryCard`·`story/StoryView`·`story/StoryViewerEntry`·`story/AuthorStories`·`story/StoryFeedResponse`·`story/StoryView`
- **⚠️ 배선 주의**: 스토리 프론트는 **`frontend/src/shared/story/`**에 있다(여러 페이지에서 `StoryStrip` 재사용 — 대시보드·프로필 등에 삽입). 전용 SSR 템플릿이 없다.
- **프론트**: `frontend/src/shared/story/`(`StoryStrip.vue`, `StoryViewer.vue`, `StoryComposer.vue`, `storyFeed.ts`, `storyApi.ts`)
- **DB**: `V56`(story)·`V57`(story_view)
- **설계**: README 로드맵

## 🧭 9. 독서 성향 분석 ("독서 MBTI")

- **한 줄**: 책장 사실 집계는 코드, 해석·서술만 LLM(Gemini). 캐시 + 새로고침 제한.
- **진입점**: `/personality`(`web/PersonalityController` + `web/PersonalityView`) · `/api/personality` GET·`/api/personality/refresh`·`/api/personality/select/{id}`(`web/api/PersonalityApiController`)
- **소속 패키지**: `personality/`
- **핵심**: 집계 `personality/ReadingProfileAggregator`·`personality/ReadingProfileService`·`personality/ReadingProfile`·`personality/ReadingTagger`·`personality/WriterName` · 서술(LLM) `personality/ReadingPersonalityNarrator`(iface)←`personality/GeminiReadingPersonalityNarrator`·`personality/PersonalityNarration` · 캐시 `personality/ReadingPersonalityCache`·`personality/ReadingPersonalityCacheRepository` · 결과 `personality/ReadingPersonality`·`personality/ReadingPersonalityService`·`personality/ReadingTribe`·`personality/ProfileSignature`
- **⚠️ 배선 주의**: **사실 집계(코드) ↔ 서술(LLM) 분리**가 핵심 설계. LLM 실패·비용 대비 캐시(`ReadingPersonalityCache`) + refresh 횟수 제한(`V27`). **README §2.8은 "미구현"이라고 하지만 실제 구현체가 존재**(설계를 넘어 출하됨) — 설계문서/README 상태 표기와 실제가 어긋나니 작업 전 코드 확인.
- **프론트**: `frontend/src/personality/`(`PersonalityApp.vue`, `PersonalityCarousel.vue`) · `frontend/src/profile/BtiPanel.vue`
- **템플릿**: `personality.html`
- **DB**: `V19`·`V25`·`V26`·`V27`(refresh 제한)·`V28`·`V29`(history)
- **설계**: [reading-personality-design.md](reading-personality-design.md) · README §2.8

## 🛠️ 10. 운영자 대시보드 (admin)

- **한 줄**: ROLE_ADMIN 전용 통계 요약 + 사용자/피드백/신고/격언 조회·관리. PII 최소 노출.
- **진입점**: `/admin`(`web/AdminController`) · `/admin/users`·`/admin/users/{loginId}`·`/admin/users/{loginId}/debt`(`web/AdminUserController`) · `/admin/feedback*`(`web/AdminFeedbackController`) · `/admin/quotes*`(`web/AdminQuoteController`) · `/admin/reports*`(`web/AdminReportController`) · `/admin/books/backfill-*`(`web/AdminBackfillController`)
- **소속 패키지**: `admin/` + 각 도메인 패키지의 admin 뷰(`feedback/AdminFeedbackRow`·`report/AdminReportRow`)
- **핵심**: 통계 `admin/AdminStatsService`·`admin/AdminStats` · 유저조회 `admin/AdminUserService`·`admin/AdminUserRow`·`admin/AdminUserDetail`·`admin/AdminDebtView` · 승격 `admin/AdminAccountService`·`admin/AdminAccountSeeder` · PII마스킹 `admin/EmailMask`
- **⚠️ 배선 주의**: **admin 컨트롤러가 여러 기능을 걸친다** — feedback/quote/report/backfill 각각이 해당 도메인 패키지(`feedback/`·`quote/`·`report/`·`book/`)와 연결된다. ROLE_ADMIN은 ENV `BOOKTIMER_ADMIN_LOGIN_IDS`로 부트스트랩 승격(`admin/AdminAccountSeeder`). 비밀번호 해시 미전송·이메일 마스킹(`admin/EmailMask`).
- **템플릿**: `admin.html`·`admin-users.html`·`admin-user-detail.html`·`admin-user-debt.html`·`admin-feedback.html`·`admin-quotes.html`·`admin-reports.html`
- **설계**: [admin-data-lookup-design.md](admin-data-lookup-design.md) · README §2.7

## 💬 11. 피드백 (사용자 문의)

- **한 줄**: 사용자 문의 접수 + 운영자 읽음/해결/답변 처리.
- **진입점**: `/feedback` GET·POST·`/feedback/{id}/delete`(`web/FeedbackController`) · 운영 `/admin/feedback*`(`web/AdminFeedbackController`)
- **소속 패키지**: `feedback/`
- **핵심**: `feedback/FeedbackService`·`feedback/Feedback`·`feedback/FeedbackType`·`feedback/FeedbackStatus`·`feedback/AdminFeedbackRow`·`feedback/FeedbackRepository`
- **템플릿**: `feedback.html`·`admin-feedback.html`
- **DB**: `V23`(feedback)·`V24`(reply)

## ✉️ 12. 이메일 (인증 · 비밀번호 재설정 · 가입알림)

- **한 줄**: 이메일 검증·비밀번호 재설정·가입 알림. 발송은 인터페이스 뒤(운영 SMTP / 로컬 로깅).
- **진입점**: `/verify-email` GET·POST·`/verify-email/resend`(`web/EmailVerificationController`) · `/password/forgot`·`/password/reset` GET·POST(`web/PasswordResetController`)
- **소속 패키지**: `email/`
- **핵심**: 발송추상화 `email/EmailSender`(iface)←`email/SmtpEmailSender`·`email/LoggingEmailSender` + `email/EmailDispatcher`·`email/EmailSendException` · 토큰 `email/EmailTokenService`·`email/EmailToken`·`email/EmailTokenType`·`email/EmailTokenRepository` · 흐름 `email/EmailVerificationService`·`email/PasswordResetService`·`email/SignupNotificationService`
- **⚠️ 배선 주의**: 발송은 인터페이스 뒤 — 운영 `SmtpEmailSender`, 로컬/테스트 `LoggingEmailSender`. **토큰 서비스가 이메일검증·비번재설정 공용**(`EmailTokenType`로 분기). 익명 폼(비번찾기 등) 큰 GET 페이지는 **CSRF 세션 선확정** 필요 — SSR GET 핸들러가 렌더 전 `web/CsrfTokenUtil.precommit(request)` 호출(예: `web/PasswordResetController`). 배경 T-033/T-049, CLAUDE.md CSRF 절.
- **템플릿**: `verify-email-confirm.html`·`verify-email-result.html`·`password-forgot.html`·`password-forgot-sent.html`·`password-reset.html`·`password-reset-result.html`
- **DB**: `V31`(email verification & tokens)·`V33`(oauth email_verified backfill)
- **설계**: README §2.2

## 🔔 13. 푸시 알림 · 리텐션 넛지

- **한 줄**: Web Push 구독·리마인더 + 이메일/푸시 리텐션 넛지(스케줄러) + 구독 해지.
- **진입점**: `/api/push/public-key`·`/api/push/subscribe`·`/api/push/unsubscribe`·`/api/push/marketing-consent`(**`push/PushApiController` — `web/`가 아님**) · `/unsubscribe` GET·POST(`web/UnsubscribeController`)
- **소속 패키지**: `push/`(웹푸시 구독·발송) · `retention/`(넛지 스케줄러·구독해지)
- **핵심**: 푸시 `push/PushSenderService`·`push/PushReminderService`·`push/PushReminderScheduler`·`push/PushSubscription`·`push/PushSubscriptionRepository` · 리텐션 `retention/RetentionNudgeScheduler`(cron 10시 KST)·`retention/RetentionPushScheduler`(cron 19시 KST)·`retention/RetentionNudgeService`·`retention/RetentionPushService`·`retention/UnsubscribeService`
- **⚠️ 배선 주의**: **푸시 컨트롤러는 `web/`가 아니라 `push/PushApiController`**(파일 찾을 때 주의). 스케줄러는 `config/SchedulingConfig` 활성 전제(@Scheduled, Asia/Seoul). 이메일 넛지(`retention/`)와 웹푸시(`push/`)는 **별개 채널**.
- **템플릿**: `unsubscribe-confirm.html`·`unsubscribe-done.html`·`fragments/pwa-head.html`
- **DB**: `V32`(marketing nudge 컬럼)·`V50`(push subscriptions & reminder)·`V51`(push consent)
- **설계**: memory: pwa-adoption · README 로드맵

## 📜 14. 격언 (작가 명언)

- **한 줄**: 대시보드 랜덤 격언 노출 + 운영자 CRUD.
- **진입점**: 대시보드 랜덤 노출 — `/api/dashboard` 응답에 실림(`web/api/DashboardApiController`) · 운영 `/admin/quotes`·`/admin/quotes/{id}/delete`(`web/AdminQuoteController`)
- **소속 패키지**: `quote/`
- **핵심**: `quote/QuotePicker`(랜덤 선택)·`quote/QuoteService`·`quote/Quote`·`quote/QuoteRepository`
- **프론트**: `frontend/src/dashboard/BrandQuote.vue`
- **템플릿**: `admin-quotes.html`
- **DB**: `V17`(quote table)

## ⚙️ 15. 공통 · 설정 · 인프라 (횡단)

- **부트/시드**: `BooktimerApplication` · 로컬 시드 `dev/LocalTestAccountSeeder`(@Profile("local"), `testid`/`1234qwer!!`, 멱등)
- **설정 `config/`**: `SecurityConfig`(인증·`/api/**` default-deny) · `WebConfig` · `JpaConfig` · `TimeConfig`(`Clock` 주입 — 테스트 시간 고정) · `AsyncConfig` · `SchedulingConfig`(@Scheduled 활성) · Properties `AdsProperties`·`AnalyticsProperties`·`CoupangDeeplinkProperties`
- **공통 엔티티**: `common/BaseTimeEntity`(created/updated 감사 컬럼)
- **웹 횡단**: `web/GlobalExceptionHandler` · `web/CsrfTokenUtil`(CSRF 세션 선확정 헬퍼) · `web/TimeZoneOptions` · 전 페이지 공통 모델 `web/AdsModelAdvice`·`web/AffiliateModelAdvice`·`web/AnalyticsModelAdvice`(@ControllerAdvice) · 정적 안내 `web/PrivacyController`(`/privacy`)
- **템플릿**: `landing.html`·`ads.html`·`analytics.html`·`error.html`·`privacy.html` · 조각 `fragments/nav-links.html`·`fragments/nav-icons.html`·`fragments/pwa-head.html`
- **프론트 공용**: `frontend/src/shared/`(`NavLinks.vue`, `NavIcon.vue`, `AuthorAvatar.vue` 등)
- **DB**: `V1`(init schema)·`V2`(spring_session)

---

## 🔁 역인덱스 — 컨트롤러(진입점) → 기능

> `web/` 컨트롤러는 기능이 아니라 기술 계층으로 묶여 있어 파일명만으론 소속 기능이 안 보인다. 파일에서 기능으로 역추적할 때 사용.

| 컨트롤러 | 기능(절) |
|---|---|
| `web/LoginController`, `web/SignupController` | 1 인증 |
| `web/OnboardingController` | 2 온보딩 |
| `web/DashboardController`, `web/api/DashboardApiController`, `web/ReadingSessionController` | 3 타이머·부채 (+14 격언 노출) |
| `web/BookController`, `web/api/BookApiController`, `web/api/BookReadersApiController` | 4 책·제휴 |
| `web/HistoryController`, `web/api/HistoryApiController` | 5 잔디·히스토리 |
| `web/GardenController`, `web/api/GardenApiController` | 6 마을 |
| `web/ProfileController`, `web/SearchController`, `web/api/SearchApiController`, `web/api/FollowApiController`, `web/FollowListController`, `web/api/FollowListApiController`, `web/BlockController`, `web/api/BlockApiController`, `web/api/ReportApiController`, `web/api/ProfileApiController` | 7 소셜 |
| `web/api/StoryApiController` | 8 스토리 |
| `web/PersonalityController`, `web/api/PersonalityApiController` | 9 성향분석 |
| `web/AdminController`, `web/AdminUserController`, `web/AdminBackfillController` | 10 운영자 |
| `web/FeedbackController`, `web/AdminFeedbackController` | 11 피드백 |
| `web/EmailVerificationController`, `web/PasswordResetController` | 12 이메일 |
| `push/PushApiController`, `web/UnsubscribeController` | 13 푸시·리텐션 |
| `web/AdminQuoteController` | 14 격언 |
| `web/SettingsController` | 1 인증(계정) + 3 타이머(목표) + 6 마을(프로필 캐릭터) |
| `web/PrivacyController` | 15 공통 |

---

## 갱신 정책

- 이 지도는 **파일 경로 앵커만** 담아 유지비를 낮췄다. 클래스 한두 개 추가는 굳이 반영하지 않아도 되지만, **패키지 신설·진입점(URL) 추가/변경·기능 간 배선 변화**가 생기면 같은 PR에서 해당 절을 갱신한다.
- 상세 설계는 여기에 복제하지 않는다 — `*-design.md`로 링크만 건다(단일 출처).
- 관련: [../README.md](../README.md) · [changelog.md](changelog.md) · [troubleshooting.md](troubleshooting.md) · [learning-notes.md](learning-notes.md)
