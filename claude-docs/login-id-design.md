# 로그인 아이디(login_id) 도입 — 식별/인증 분리 설계 메모

> **상태**: 완료 ✅ (PR-1 ✅ #146, PR-2 ✅ #147, PR-3 ✅ #148, PR-4 ✅ #149, wipe ✅ 실행됨, **PR-5 ✅ #156**). **B안 + 기존 데이터 wipe(그린필드)** + **🔁 모델 전환(2026-06-05): login_id를 공개 @핸들로**. 5단계 전부 머지됨.
> **왜 설계 먼저**: 인증/식별 경계 변경이다 — `principal.getName()`으로 유저를 찾는 컨트롤러 14곳, `findByEmail` 21곳. auth는 깨지면 위험하다.
>
> 관련: [plan.md](../plan.md) §관리자 대시보드 · [sns-design.md](sns-design.md) §3.5(가시성 경계) · [learning-notes.md](learning-notes.md) **N-037**(식별=관계/속성 분리).

---

## 🔁 방향 전환 (2026-06-05) — login_id = 공개 @핸들 (인스타/X 모델)

최초 초안은 **login_id를 비공개**로, **nickname을 공개 핸들**(검색/프로필 URL)로 뒀다. 사용자 결정으로 **뒤집힌다**:
"검색을 닉네임이 아니라 **아이디로**(인스타·X처럼)" + "닉네임은 중복 OK·수정 자유" + "**아이디는 한번 정하면 영원히 불변**".

| | 최초 초안 | **현재 모델 (확정)** |
|---|---|---|
| **login_id** | 비공개 로그인 식별자 | **공개 @핸들** — 로그인 + 검색 + 프로필 URL, **불변**, 유니크 |
| **nickname** | 공개 핸들(유니크) | **표시 이름** — 중복 허용, 수정 자유, 핸들 아님 |
| **email** | 연락/복구 (비공개) | 연락/복구 (비공개 — 유지) |

**보안적으로 동일하게 안전**: 원래 목표("공개된 *이메일*이 로그인 표적")는 그대로 달성된다 — **이메일은 여전히 비공개**(복구용), 로그인 식별자는 email이 아니다. 공개되는 건 login_id뿐인데, 이는 X의 @핸들처럼 알려져도 인증(비밀번호)을 뚫지 못한다. 즉 "식별자를 연락 채널(email)에서 분리"라는 본질은 유지하고, 공개 핸들을 nickname→login_id로 옮겼을 뿐이다.

---

## 0. 한눈에 — 이 메모가 정한 방향

| 항목 | 결정 | 확정? | 근거 |
|---|---|---|---|
| 로그인 식별자 | **별도 `login_id`(아이디)** — email 아님 | ✅ | 공개 이메일 = 로그인 표적 노출 |
| login_id 공개성 | **공개 @핸들**(검색·프로필 URL) — 인스타/X | ✅ | 사용자 결정(🔁 전환). email은 계속 비공개 |
| login_id 불변성 | **한번 정하면 영원히 불변** | ✅ | 영구 식별자 — 표시 이름(nickname)이 가변 역할 담당 |
| principal(주체) | **`login_id`로 전면 전환 (B안)** | ✅ | 장기 일관성 — email은 속성으로 강등 |
| email 역할 | 연락/복구/OAuth 연결용 **속성**(비공개) | ✅ | 식별을 email에 묶지 않음 |
| nickname 역할 | **표시 이름** — 중복 허용·수정 자유, 핸들 아님 | ✅ | 공개 핸들이 login_id로 이동 |
| nickname 유니크 | **제거**(uk_users_nickname drop, V14) | ✅ | 단순 표시 이름이라 중복 무방 |
| **기존 사용자** | **전부 wipe (그린필드 리셋)** — 스냅샷 없이 | ✅ | 출시 전·친구뿐, 백필/전환 복잡성 제거 |
| login_id 캡처 | **온보딩 한 곳에서 전원**(local+OAuth) 직접 선택 | ✅ | 단일 경로·불변 가드와 무충돌(항상 null→확정) |
| 마이그레이션 | V13 `login_id unique`(nullable→NOT NULL) + V14 nickname unique drop | ✅ | wipe라 백필 없음, 증분 PR 안전 |
| 인증 컷오버 | `loadUserByUsername`=**findByLoginId만**(email 폴백 없음) | ✅ | wipe라 폴백 불필요 → 표적 약점 즉시 닫힘 |
| Admin 시드 | `BOOKTIMER_ADMIN_EMAILS` → `BOOKTIMER_ADMIN_LOGIN_IDS` | ✅ | 표적 식별자 비공개·일관 |
| login_id 형식/규칙 | `a-z0-9_`, 3~20자, 소문자 정규화, 예약어 차단 | ✅ | 표준(GitHub/X/Reddit 공통) |

> ✅ = 합의. 🟡 = 착수 전 확정. **§6 열린 질문**.

---

## 1. 문제 / 배경 + 보안 전제 정정

- 현재 로그인·식별 핸들이 **email**이다(`loadUserByUsername(email)`, 컨트롤러 `findByEmail(principal.getName())`). 운영자 이메일이 홈페이지에 **공개**돼 있다.

### 정정 (패닉 금지)
- **이메일 공개 ≠ admin 탈취.** 시드는 "그 이메일 계정을 *소유한 사람*"을 승격 — 악용하려면 배포 ENV 조작(외부인 불가) + 그 계정 로그인(비번/구글). 시드 자체는 안 뚫린다.
- **진짜 약점**은 email이 **로그인 식별자**라는 것: 공개 이메일 = 로그인 표적 공개 → 표적형 무차별 대입/비번 재사용에 약함(IP 잠금 있으나 식별자 노출은 약한 자세).
- 결론: 이메일을 로그인·식별 핸들에서 빼고 **비공개 아이디**로 로그인·식별한다.

---

## 2. 식별 모델 — login_id = 공개 @핸들 (B안 + 🔁 전환)

| 값 | 역할 | 공개? | 유니크 | 가변? |
|---|---|---|---|---|
| **login_id** | **로그인 + 내부 식별(principal) + 공개 @핸들**(검색·프로필 URL) | ✅ 공개 | ✅ uk_users_login_id | ❌ **불변** |
| email | 연락·복구·OAuth 계정 연결 | ❌ 비공개 | ✅ uk_users_email | (변경 정책 별도) |
| nickname | **표시 이름**(화면 표기) | ✅ 공개 | ❌ (V14에서 drop) | ✅ 자유 변경 |

- **principal = login_id (전원)**. local·OAuth 모두 보유, `principal.getName() = login_id`.
- **공개 핸들 = login_id** (인스타 `@handle` / X와 동일). 검색·프로필 URL·@멘션이 login_id 기준. nickname은 표시 텍스트일 뿐.
- **login_id 불변** — `User.assignLoginId`가 이미 설정됐으면 `IllegalStateException`. 한번 확정되면 변경 불가(영구 식별자).
- **email은 계속 타인 비공개** — 로그인 식별자도 아님. "식별자를 연락 채널에서 분리"라는 원목표 유지.

---

## 3. 기존 데이터 wipe (그린필드 리셋)

출시 전이고 친구/테스트 데이터뿐이라, 기존 사용자를 **전부 날린다**. 이게 백필·dual-lookup·임시값·전환 게이트를 통째로 제거한다(되돌릴 수 없음 — 합의됨, 스냅샷 생략).

- **무엇을**: 전 사용자 + 종속 데이터 — `users`, `reading_timer`, `reading_session`, `book`, `follow`, `block`, `report`, 그리고 활성 세션(`SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES`). FK 자식부터 역순 삭제(또는 일괄 truncate 후 재정합).
- **어떻게**: **일회성 ops 작업**(prod DB에 직접 DELETE) — **파괴적 SQL은 Flyway에 넣지 않는다**(마이그레이션은 스키마 전용 유지, 미래 신규 DB에 오발 방지).
- **언제**: 인증 컷오버(PR-3) 배포 **전**. 그 전까진 prod가 비어 있어도 무방. wipe 후 친구들은 재가입(기록 0부터).

---

## 4. 스키마 / 마이그레이션 (Flyway V13)

```sql
-- V13 — 로그인 아이디(login_id). wipe로 테이블이 비므로 백필 불필요.
alter table users add column login_id varchar(50);          -- 처음 nullable(증분 PR 안전)
alter table users add constraint uk_users_login_id unique (login_id);
-- 무결성 강화(PR-5): 단순 NOT NULL은 OAuth와 충돌(아래 §7 PR-5) → 조건부 CHECK로:
--   alter table users add constraint ck_users_login_id_when_onboarded
--     check (onboarded = false or login_id is not null);   -- V15
```

- 처음 **nullable**으로 두는 건 wipe 여부와 무관하게 **증분 PR 안전**을 위해서다(PR-1 배포 시점엔 가입 경로가 아직 login_id를 안 채울 수 있음). 모든 생성 경로가 채우면(PR-2) NOT NULL로 좁힌다(PR-4).
- unique는 처음부터. `varchar(50)` — 형식/길이는 §6.

---

## 5. 인증 컷오버 (PR-3, 가장 큰 단계)

- **`loadUserByUsername(input)` = `findByLoginId(input)`만**(email 폴백 없음 — wipe라 불필요). 반환 `UserDetails.username = login_id` → principal = login_id.
- **OAuth(OIDC)**: 온보딩에서 사용자가 login_id를 고른다(§아래). 프로비저닝 직후 온보딩 미완 상태로 만들고, 온보딩에서 login_id 확정. OIDC principal도 login_id로 통일.
- **14개 컨트롤러**: `findByEmail(principal.getName())` → `findByLoginId(principal.getName())`.
- **로그인 폼**: 라벨 "이메일"→"아이디"(폼 name은 `username` 유지).
- **Admin 시드**: `BOOKTIMER_ADMIN_LOGIN_IDS` 읽어 login_id 조회로 승격(`AdminAccountService`/`AdminAccountSeeder` 전환).
- **무차별 대입 방어**: `LoginAttemptService`는 IP 키 → 영향 없음.

### 온보딩에서 login_id 선택 (전원 단일 경로 — 🔁 전환)
- **LOCAL·OAuth 공통**: 첫 진입 온보딩(타임존/타이머 설정 화면)에서 **아이디를 직접 입력**한다. 로컬도 온보딩 게이트를 반드시 거치므로(`DashboardController`), 가입 폼이 아니라 온보딩 한 곳에서 캡처한다 → 단일 경로 + login_id가 모두에게 온보딩 전까지 null이라 **불변 가드와 무충돌**(항상 null→확정). (PR-2에서 구현 ✅)
- 자동생성·임시값 없음 → 전원이 직접 정한 login_id 보유. 유니크는 `existsByLoginId` 사전 확인 + DB 제약, 형식/예약어는 도메인(`User.normalizeLoginId`).

### 노출 정책 (🔁 전환)
- **login_id는 공개 핸들** — 검색·프로필 URL·@멘션에 노출된다(인스타/X의 @핸들과 동일). 알려져도 인증(비밀번호)을 뚫지 못한다.
- **email은 계속 타인 비노출**(현행 유지) — 로그인 식별자도 아님.

---

## 6. 열린 질문 (착수 전 확정)

- ~~**login_id 형식 규칙**~~ **확정 ✅** — 표준(GitHub/X/Reddit 공통): charset `a-z0-9_`, 길이 **3~20자**, **소문자 정규화 저장**(대소문자 구분 안 함 → `Goospel`=`goospel` 충돌 방지), **예약어 차단**(`admin`·`root`·`me`·`api` 등). `varchar(50)` 컬럼은 여유로 유지(검증은 20자).
- ~~**login_id NOT NULL 강화 시점**~~ **확정 ✅ (PR-5, #156)** — 단순 NOT NULL은 불가능했다. OAuth 사용자는 프로비저닝(INSERT) 시점엔 login_id가 없고 온보딩에서 정하므로(login_id는 불변 → 자동 핸들 박기도 불가), 그 전환 창의 null은 정상이다. 박을 수 있는 진짜 불변식은 **`onboarded = true ⟹ login_id IS NOT NULL`**(조건부 CHECK, V15). 로컬은 가입에서 채워 항상 만족, OAuth는 온보딩 완료와 동시에 채워진다.
- **wipe 실행 타이밍/방법**: 컷오버 배포 직전 수동 DELETE(역순) — 실행 스크립트·순서 확정.
- **login_id 변경 정책**: 변경 허용 여부·빈도·쿨다운(닉네임 §11-3과 동행).

---

## 7. PR 단계 / 선후

각 단계 독립 PR + TDD(Red→Green 가시화). auth/핸들 단계마다 "로그인·OAuth·조회 안 깨짐" 회귀.

- **PR-1 스키마 + 도메인** ✅ #146 — V13(`login_id` nullable unique). `User`에 login_id 필드·형식 검증(`assignLoginId`). **동작 변화 없음**(인증·핸들 아직 그대로). TDD: 도메인 규칙·FlywayMigrationTest.
- **PR-2 불변 + 닉네임 중복허용 + 온보딩 캡처** ✅ — ① `assignLoginId` 불변 가드(재설정 시 ISE). ② nickname 유니크 제거(V14 + register/onboarding/settings/oauth-provisioning 검사 제거, `NicknameAllocator`/`NicknameAlreadyExistsException`/`existsByNickname` 삭제). ③ 온보딩에서 전원 login_id 입력+유니크(`existsByLoginId`)/형식/예약어 강제(`OnboardingForm`·`OnboardingService`·`onboarding.html`). 로그인·검색은 아직 email/nickname. TDD: 도메인 불변·중복허용·온보딩 캡처/유니크.
- **PR-3 공개 핸들 컷오버** ✅ #148 — 검색을 **login_id 기준**으로(`UserSearchService`·`findTop20ByLoginIdContainingIgnoreCaseOrderByLoginIdAsc`), 프로필/팔로우/차단/신고 핸들을 **nickname→login_id**(`/u/{loginId}`·`ProfileService.findByLoginId`·Follow/Block/Report 컨트롤러 `@RequestParam loginId`). `UserSearchResult`·`ProfileView`에 `loginId` 추가(nickname은 표시 이름으로 유지 — 핸들/표시 분리). **부수 효과**: 닉네임 중복 허용(PR-2) 이후 `findByNickname`이 첫 일치 1명이라 오식별하던 팔로우/차단/신고 정합성 버그를 동시 해소. 인증은 아직 email(principal·14곳은 PR-4). TDD: 검색=login_id·닉네임 중복→login_id 정확 식별 회귀.
- **PR-4 인증 컷오버** ✅ #149 — wipe(ops) 선행 완료 → `loadUserByUsername`=findByLoginId만(email 폴백 없음), principal=login_id, OIDC principal(`BookTimerOidcUser`), 시드 `BOOKTIMER_ADMIN_LOGIN_IDS`. **설계 미세 보정**: ① 14곳 직접 `findByLoginId` 대신 **`CurrentUserService` 공유 리졸버**(findByLoginId 우선 → OAuth 첫 세션만 email 브리지) — OAuth는 첫 로그인 전 login_id가 없어 principal=login_id가 즉시 불가하므로 그 짧은 창을 email로 브리지(이메일 *로그인*은 여전히 차단). ② **로컬 login_id 캡처를 가입으로 이동**(로그인이 login_id 기준이라 가입 시 필요) — 온보딩 캡처는 OAuth 전용(`needsLoginId`로 조건부). ③ `SettingsController`는 principal→User→email로 서비스 시그니처 보존. **가장 큰 PR.** Flyway 없음. TDD: 로그인(login_id)·이메일 차단·리졸버·OIDC·온보딩 조건부·시드·login_id principal 컨트롤러 경로 전부 그린. **⚠️ 배포 전 prod ENV `BOOKTIMER_ADMIN_LOGIN_IDS` 설정 필요.**
- **PR-5 무결성 강화(조건부 NOT NULL)** ✅ #156 — 단순 `login_id NOT NULL`이 **OAuth와 충돌**함을 발견: OAuth는 `provision`→`registerOAuth`로 **login_id=null인 row를 먼저 INSERT**하고 온보딩에서 login_id를 정한다(그 창의 null은 정상). login_id는 불변이라 X식 가입-시 자동 핸들도 불가. 그래서 진짜 불변식 **`onboarded = true ⟹ login_id IS NOT NULL`을 조건부 CHECK**로 박음(`V15`, `ck_users_login_id_when_onboarded`). MySQL 8·H2(MySQL 모드) CHECK 강제. 메인 스위트는 Hibernate 생성이라 무영향(V14와 동일) → `FlywayMigrationTest`가 Flyway 스키마에 적용해 3경계(온보딩+null→거부·온보딩전 null→허용·정상→허용) 검증. **email 로그인 잔재 없음 재확인**: `loadUserByUsername`=`findByLoginId`만(폴백 없음), 남은 `findByEmail`은 설정 조회(principal→User→email)·OAuth 첫 세션 *해석* 브리지·find-or-create로 전부 **로그인 경로 아님**. Flyway만(V15) — 코드 동작 변화 없음. TDD(Red→Green). **login_id 도입 5단계 전부 완료 ✅.**

> §5의 "인증 컷오버" 본문은 PR-4를 가리킨다(핸들 컷오버 PR-3가 그 앞에 추가됨).
