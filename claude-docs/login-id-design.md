# 로그인 아이디(login_id) 도입 — 식별/인증 분리 설계 메모 (구현 전 합의용)

> **상태**: 설계 초안 (⏳ 2026-06-05, 방향 합의 — B안 + **기존 데이터 wipe로 그린필드 단순화**). **코드 없음.** 단계적 PR로 TDD 착수.
> **왜 설계 먼저**: 인증/식별 경계 변경이다 — `principal.getName()`으로 유저를 찾는 컨트롤러 14곳, `findByEmail` 21곳. auth는 깨지면 위험하다.
>
> 관련: [plan.md](../plan.md) §관리자 대시보드 · [sns-design.md](sns-design.md) §3.5(가시성 경계) · [learning-notes.md](learning-notes.md) **N-037**(식별=관계/속성 분리).

---

## 0. 한눈에 — 이 메모가 정한 방향

| 항목 | 결정 | 확정? | 근거 |
|---|---|---|---|
| 로그인 식별자 | **별도 `login_id`(아이디)** — email 아님 | ✅ | 공개 이메일 = 로그인 표적 노출 |
| principal(주체) | **`login_id`로 전면 전환 (B안)** | ✅ | 장기 일관성 — email은 속성으로 강등 |
| email 역할 | 연락/복구/OAuth 연결용 **속성** | ✅ | 식별을 email에 묶지 않음 |
| nickname 역할 | **공개 핸들**(프로필/검색) — login_id와 별개·비공개 | ✅ | nickname 공개라 로그인 식별자 부적합 |
| **기존 사용자** | **전부 wipe (그린필드 리셋)** — 스냅샷 없이 | ✅ | 출시 전·친구뿐, 백필/전환 복잡성 제거 |
| OAuth 유저 | **온보딩에서 login_id 직접 선택**(자동생성 없음) | ✅ | 전원이 진짜 아이디 보유, 임시값 불필요 |
| 마이그레이션 | V13 `login_id varchar unique`(처음 nullable, 최종 NOT NULL) | ✅ | wipe라 백필 없음, 증분 PR 안전 |
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

## 2. 식별 모델 — login_id 보편화 (B안)

| 값 | 역할 | 공개? | 유니크 |
|---|---|---|---|
| **login_id** | **로그인 + 내부 식별(principal)** | ❌ 비공개 | ✅ |
| email | 연락·복구·OAuth 계정 연결 | ❌ | ✅ |
| nickname | **공개 핸들**(`/u/{nickname}`·검색) | ✅ 공개 | ✅(V7) |

- **principal = login_id (전원)**. local·OAuth 모두 보유, `principal.getName() = login_id`.
- **nickname을 login_id로 재활용 ❌** — 공개 핸들이라 로그인 식별자로 쓰면 "표적 공개" 재발. login_id는 **별도 비공개 컬럼**.
- **login_id는 어디에도 화면/URL/API 노출 금지**(§5).

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
-- NOT NULL 강화는 모든 생성 경로가 login_id를 채운 뒤(PR-4): alter ... modify login_id ... not null
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

### 가입/온보딩에서 login_id 선택
- **LOCAL 가입**: 가입 폼에 아이디 입력(+비번·닉네임·타임존). 유니크·형식 검증.
- **OAuth 가입**: 첫 로그인 → 온보딩(이미 타임존/타이머 설정 화면)에서 **아이디도 입력**. 자동생성·임시값 없음 → 전원이 직접 정한 login_id 보유.

### 노출 금지
- login_id는 프로필·검색·잔디·SNS·URL 어디에도 노출하지 않는다(공개 핸들은 nickname뿐). email도 타인 비노출(현행).

---

## 6. 열린 질문 (착수 전 확정)

- ~~**login_id 형식 규칙**~~ **확정 ✅** — 표준(GitHub/X/Reddit 공통): charset `a-z0-9_`, 길이 **3~20자**, **소문자 정규화 저장**(대소문자 구분 안 함 → `Goospel`=`goospel` 충돌 방지), **예약어 차단**(`admin`·`root`·`me`·`api` 등). `varchar(50)` 컬럼은 여유로 유지(검증은 20자).
- **login_id NOT NULL 강화 시점**: 모든 생성 경로(가입·OAuth 온보딩)가 채운 뒤 = PR-4. (백필 없으니 단순.)
- **wipe 실행 타이밍/방법**: 컷오버 배포 직전 수동 DELETE(역순) — 실행 스크립트·순서 확정.
- **login_id 변경 정책**: 변경 허용 여부·빈도·쿨다운(닉네임 §11-3과 동행).

---

## 7. PR 단계 / 선후

각 단계 독립 PR + TDD(Red→Green 가시화). auth 단계마다 "로그인·OAuth·14곳 조회 안 깨짐" 회귀.

- **PR-1 스키마 + 도메인** — V13(`login_id` nullable unique). `User`에 login_id 필드·검증·팩토리 반영(형식 규칙). **동작 변화 없음**(인증은 아직 email). TDD: 도메인 규칙·FlywayMigrationTest. ※ `User.of(...)` 시그니처 변경이 가입 서비스·다수 테스트로 파급 — 같이 처리.
- **PR-2 가입 + OAuth 온보딩** — LOCAL 가입 폼·OAuth 온보딩에서 login_id 입력+유니크/형식 강제. 신규 유저 login_id 채움. 로그인은 아직 email. TDD: 가입/온보딩 유니크·검증.
- **PR-3 인증 컷오버** — wipe(ops) 선행 → `loadUserByUsername`=findByLoginId, principal=login_id, OIDC principal, 14곳 전환, 로그인폼 라벨, 시드 `BOOKTIMER_ADMIN_LOGIN_IDS`. **가장 큰 PR.** TDD: 로그인·OAuth·각 컨트롤러 회귀·시드.
- **PR-4 NOT NULL 강화** — 모든 경로가 채운 뒤 `login_id` NOT NULL(+ 필요 시 email 로그인 잔재 제거 확인).

> 이 메모는 **합의용 초안**이다. 구현은 §6 🟡(특히 login_id 형식 규칙) 확정 후 PR-1부터.
