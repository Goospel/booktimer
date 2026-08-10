# 토스 미니앱 v2 설계 — 홈 리치화 · 서재 탭 · 소셜 (2026-08-10)

> 🧭 세션 메타: model=claude-fable-5 · effort=high
>
> MVP(로그인·타이머·잔디, PR-1~3 + #708·#709)로 실기기 1사이클이 통과한 뒤, 사용자 판정
> "미니앱 UI가 빈약하고 웹 기능이 반영 안 됨"에 따라 **출시 심사 전에** 범위를 확장한다.
> 사용자 선택(2026-08-10): 출시 타이밍=**확장 후 출시** / 범위=**홈 리치화+서재 탭, 소셜(책방·팔로우·스토리)**.
> 통계·책BTI 결과 뷰·서재 마을은 이번 범위 밖(마을은 Phaser 성능·TDS 이질감 별도 검증 필요).

## 1. 현황 (실측)

- 미니앱(`miniapp/`)은 화면 5개(LoginBridge·LinkAccount·Home·Goal·History)를 라우터 없이
  `App.tsx`의 `view` 상태 하나로 전환한다. 데이터는 `GET /api/dashboard` 단일 응답을 App이 들고
  화면에 내려준다.
- **서버 API는 이번 범위 전부에 대해 이미 존재한다.** 미니앱 보안 체인(`SecurityConfig.isMiniappApiRequest`)이
  Bearer 토큰이 붙은 `/api/**` 전체를 받아주고, principal 브리지(`BearerTokenFilter` → `CurrentUserService`)로
  기존 컨트롤러를 수정 없이 재사용하는 구조가 PR-1에서 이미 깔렸다. 이번 확장의 서버 변경은 **0**(리스크 §5-1 제외).
- `api.ts`의 `request()`는 GET/POST만 지원(body 유무로 판정)하고, 쿼리 파라미터 조립이 없다.
- 서버 대시보드 응답에는 격언(`quotes`, 최대 로테이션 개수만큼)이 이미 실려 있는데 미니앱
  `DashboardResponse` 타입이 받지 않아 버려지고 있다.

### 이번 범위가 쓰는 기존 API (전부 존재 확인, 2026-08-10)

| 기능 | 엔드포인트 |
|---|---|
| 서재 | `GET /api/books` · `GET /api/books/search` · `POST /api/books` · `POST /api/books/{id}/status` · `POST /api/books/{id}/visibility` · `POST /api/books/{id}/delete` |
| 프로필·책방 | `GET /api/profile` · `GET /api/profile/books` · `GET /api/profile/personality-tag` |
| 팔로우 | `POST /api/follow` · `POST /api/unfollow` · `GET /api/follow-list` · `GET /api/book-readers` |
| 스토리 | `GET /api/stories/feed` · `GET /api/stories/of/{loginId}` · `POST /api/stories` · `DELETE /api/stories/{id}` · `POST /api/stories/{id}/view` · `GET /api/stories/{id}/viewers` |
| 차단·신고 (소셜 동반) | `GET /api/blocks` · `POST /api/block` · `POST /api/unblock` · `POST /api/report` |
| 유저 검색 | `GET /api/search` |

## 2. 방향

- **탭 4개 구조로 재편**: 홈 / 서재 / 소셜 / 기록. TDS 탭바 컴포넌트 사용. 라우터는 여전히 두지
  않는다(YAGNI) — `App.tsx`를 `{ tab, ... }` 상태로 확장하고, 탭 내부 서브뷰(검색·상세 등)는 각 탭
  컴포넌트의 로컬 상태로 관리한다. 화면이 15개를 넘거나 딥링크가 필요해지면 그때 라우터를 들인다.
- **디자인은 TDS 유지**: 토스 앱 안에서 네이티브처럼 보이는 게 이 채널의 강점. 웹 CSS 이식은 하지
  않고 브랜드 요소(잔디 초록, 성장 단계 이모지)만 얹는다.
- **웹 = 본진 원칙은 유지**: 미니앱은 조회·측정·가벼운 소셜 소비 중심. 무거운 관리(공개 범위 일괄
  변경, 프로필 편집, 계정 설정)는 웹 안내로 남긴다.

## 3. 공통 선행 작업 (PR-4에 포함)

`api.ts`의 `request()` 확장 — 시그니처:

```ts
async function request<T>(path: string, init?: { method?: 'GET'|'POST'|'DELETE'; body?: unknown; query?: Record<string, string | number | undefined> }): Promise<T>
```

- 기존 호출부는 형태 유지 래퍼로 무변경 통과(기존 `request(path, body)` 오버로드 보존 또는 전 호출부 일괄 치환 — 구현 세션 재량, 단 diff 최소 쪽).
- `query`는 `URLSearchParams`로 조립, `undefined` 값은 생략.
- DELETE는 스토리 삭제(PR-8)가 쓴다.

## 4. PR 분할

> 각 PR은 독립 머지·배포 가능. 구현 세션은 PR마다 `npm --prefix miniapp run build` 후
> `ait deploy`(API 키: 메인 repo `.claude/.secrets/ait-api-key.txt`, `--api-key "$(cat ...)"` 주입)로
> 샌드박스 재배포하고 **실기기 1회 확인**을 게이트로 둔다(웹 프론트의 실 브라우저 게이트와 같은 정신 —
> 헤드리스로 안 잡히는 WebView 사각).

### PR-4 — 홈 리치화 (+ request() 확장)

- **내용**: ① 오늘 진행률 게이지(TDS ProgressBar — `todayGoalSeconds`·`remainingSeconds`로 계산)
  ② 작가 격언 카드(`quotes` 랜덤 1개 표시, 탭하면 다음 격언) ③ 잔디 미리보기(최근 5주 축약 —
  History의 렌더 로직을 공용 컴포넌트로 추출) ④ 읽는 중 책 리스트(표지 없음·제목만, 탭하면 그 책으로
  타이머 시작 흐름 연결).
- **변경 파일**: `miniapp/src/api.ts`(request 확장 + `quotes: QuoteDto[]` 타입 추가),
  `miniapp/src/screens/Home.tsx`, `miniapp/src/ui.tsx`(잔디 미니 컴포넌트 추출), `App.tsx`(무변경 목표).
- **TDD**: `request()` 확장은 vitest로 RED→GREEN — ① query 조립(undefined 생략·인코딩)
  ② DELETE 메서드 전달 ③ 기존 GET/POST 호출부 하위호환 ④ 401 → UnauthorizedError 유지.
  UI는 순수 시각 변경이라 단위테스트 대신 실기기 확인을 게이트로(생략 사유 명시 — TDD 절 규칙 준수).
- **규모**: 소.

### PR-5 — 탭 구조 + 서재 탭

- **내용**: ① `App.tsx`를 탭 구조로 재편(`tab: 'home'|'library'|'social'|'history'`, TDS 탭바.
  기존 `view` 오케스트레이션 중 auth/link/goal/error는 탭 밖 전역 상태로 유지) ② 서재 탭:
  읽는 중/다 읽음/읽고 싶어요 섹션 리스트(`GET /api/books`), 책 상태 변경·삭제(액션시트),
  공개 설정 토글 ③ 책 추가: 검색(`GET /api/books/search`, 알라딘) → 결과에서 추가(`POST /api/books`).
- **변경 파일**: `App.tsx`(구조 재편 — 이 PR의 핵심 리스크), `screens/Library.tsx`(신설),
  `api.ts`(books 계열 타입·함수 — 서버 `BookApiController` record가 타입 단일 출처), History를 탭으로 흡수.
- **TDD**: api 클라이언트 함수는 vitest(응답 매핑·에러 분기). 탭 전환은 컴포넌트 테스트 1건
  (탭 클릭 → 해당 화면 렌더). 서버 API에 미니앱발 첫 뮤테이션(책 추가·삭제)이 생기므로 실기기에서
  추가→웹 교차 확인 1회(채널 병행 acceptance).
- **엣지**: 검색 0건 / 알라딘 API 실패(서버 5xx 메시지 노출) / 중복 추가(서버 409 메시지 그대로 표시).
- **규모**: 중. 이 PR이 구조 변경이라 **PR-4보다 먼저 하지 않는다** — 홈 리치화를 기존 구조에서 먼저
  내보내 실기기 피드백을 받고, 탭 재편은 그 다음(작은 것 먼저 출하).

### PR-6 — 소셜 탭 1: 유저 검색 + 책방(프로필) 구경 + 팔로우

- **내용**: ① 소셜 탭 신설 — 상단 유저 검색(`GET /api/search`), 팔로우 목록(`GET /api/follow-list`)
  ② 책방 뷰: 닉네임·프로필 캐릭터·책BTI 태그(`/api/profile*` 3종) + 공개 책 리스트
  ③ 팔로우/언팔 버튼(`POST /api/follow`·`/api/unfollow`) ④ 차단·신고 진입(액션시트 —
  `POST /api/block`·`/api/report`. 소셜 노출이 생기는 순간 신고·차단이 짝으로 있어야 스토어 심사·운영
  안전 — 웹과 동일 원칙).
- **변경 파일**: `screens/Social.tsx`·`screens/Profile.tsx`(신설), `api.ts`(profile·follow·search·block·report 계열).
- **TDD**: api 함수 vitest + **login_id=null 경계**(§5-1)를 이 PR에서 실측·처리.
- **규모**: 중.

### PR-7 — 소셜 탭 2: 독서 스토리

- **내용**: ① 소셜 탭 상단 스토리 피드(`GET /api/stories/feed` — 팔로우 기반 24h) ② 열람 뷰
  (전체화면 카드, `POST /api/stories/{id}/view` 기록) ③ 작성(오늘 읽은 책 문장 공유 —
  `POST /api/stories`) ④ 내 스토리 삭제(`DELETE` — PR-4의 request 확장 사용)·뷰어 목록.
- **변경 파일**: `screens/Story*.tsx`(신설), `api.ts`(stories 계열), Social 탭에 피드 스트립 삽입.
- **TDD**: api 함수 vitest(특히 DELETE 경로·빈 피드). 24h 만료·뷰 기록은 서버 로직이라 재검증 안 함
  (웹에서 이미 커버) — 미니앱은 표시·액션 배선만.
- **엣지**: 피드 0건(팔로우 없음 → 유저 검색 유도) / 스토리 작성 자격(웹 규칙과 동일한 서버 검증
  메시지 노출).
- **규모**: 중.

### PR-8 — 출시 준비

- **내용**: ① 스토어 스크린샷을 실기기 캡처로 교체(현재는 playwright 재현본 — 타이포 차이 有)
  ② 콘솔에서 출시 심사 제출(사용자 직접 — 콘솔은 Claude 접근 차단이라 스크린샷 안내 방식)
  ③ 심사 리젝 대응 루프.
- **규모**: 소(작업량) + 외부 게이트(심사 기간).

## 5. 리스크 · 실측 필요 지점

1. **미니앱 신규 계정은 `login_id=null`** — 책방 URL·`/api/stories/of/{loginId}` 등 loginId 기반 경로에서
   이 유저의 노출·참조가 어떻게 되는지 PR-6에서 **null-state 엔티티 경계 테스트**(N-055 계열)로 실측한다.
   자기 책방 공유·스토리 작성이 loginId를 요구하면: v2 처리 = "웹에서 아이디 설정" 안내 화면(마찰 명시),
   미니앱 내 loginId 설정은 범위 밖.
   - **실측 완료(PR-6)**: 소셜 API는 전부 대상을 `findByLoginId`로 찾는다. 발견 표면은 이미 온보딩 전
     계정을 제외한다 — 검색은 `login_id` LIKE(NULL 미매칭), `FollowListService`는 명시적 null 필터,
     `recommend`도 제외(N-055 서버측 기완료, 서버 테스트 존재). **막히는 경로는 자기 책방 하나**
     (`@RequestParam String loginId` 필수 → 핸들 없는 계정은 요청이 성립 불가) → 소셜 탭에서 버튼 대신
     웹 안내를 그린다. 남을 팔로우·차단·신고하고 남의 책방을 보는 건 핸들 없이도 된다.
     스토리(PR-7)의 `/api/stories/of/{loginId}`는 그 PR에서 다시 확인한다.
   - **실측 완료(PR-7) — 스토리는 핸들 없이도 전부 성립한다**: `GET /api/stories/feed`가 **내 활성
     스토리를 `mine` 필드로 따로** 실어 준다(`StoryService.feed`는 viewer 엔티티로 바로 조회 —
     loginId를 안 거친다). 그 카드에 `id`가 실리므로 삭제(`DELETE /api/stories/{id}`)·열람자
     (`GET /api/stories/{id}/viewers`)까지 id 기반으로 이어지고, 작성(`POST /api/stories`)도
     `StoryService.create`가 loginId를 안 본다. 따라서 **미니앱은 `/api/stories/of/{loginId}`를
     쓰지 않는다**(핸들 있는 계정에도 `mine`이 상위집합) — PR-6의 "자기 책방" 같은 막힘은 없다.
     ⚠️ `mine.loginId`는 null일 수 있으니 표시·식별에 쓰지 않는다. **남는 비대칭**: `canView`가
     `author.getLoginId() == null`을 미노출로 치므로 핸들 없는 계정의 스토리는 **작성·자기 열람은 되지만
     팔로워에게는 안 보인다**(N-055 서버측 정책, 의도된 것). 서버는 고치지 않고 그대로 둔다 — 소셜 탭의
     기존 웹 안내("웹에서 아이디를 정하면…")가 같은 원인을 이미 설명한다.
2. **principal 브리지 실측**: 각 컨트롤러가 Bearer principal(이메일 브리지)로 동작하는 계약은 PR-1에서
   확립됐지만, 뮤테이션 계열(books·follow·stories)은 미니앱발 호출이 처음이다 — PR마다 실기기 1회가 게이트.
3. **App.tsx 재편(PR-5) 회귀**: 타이머·인증 흐름이 전부 이 파일을 지난다. 기존 흐름(로그인→홈→시작/정지→
   태깅→기록)의 컴포넌트 테스트를 재편 전에 스냅샷으로 박아 리팩터 안전망으로 쓴다.
4. **WebView 오리진**: 출시 채널 전환 시 `private-web`과 다른 오리진이 나올 수 있다 — 증상은 「Load failed」.
   그땐 Caddy 로그 실측 → SSM `/booktimer/MINIAPP_ALLOWED_ORIGINS`에 콤마로 추가 → 재배포(코드 변경 불필요).
5. **스토어 심사**: 소셜(UGC) 노출이 생기므로 신고·차단이 심사 요건일 수 있다 — PR-6에 포함해 선제 대응.

## 6. 규모 총평

전부 미니앱 프론트 작업(서버 0)이라 PR당 구현 세션 1회 분량. 순서 고정: 4 → 5 → 6 → 7 → 8.
드리프트 규칙: 구현 중 설계와 다른 사실(API 계약 불일치 등)이 보이면 멈추고 보고 — 특히 §5-1·2의
실측 결과가 설계 가정과 다르면 이 문서를 갱신하고 진행한다.
