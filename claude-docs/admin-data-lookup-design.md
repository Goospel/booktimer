# 관리자 데이터 조회 — 사용자 목록·드릴다운 설계 메모

> **상태**: 설계 (코드 없음). 관리자 대시보드 **④ 데이터 조회**(마지막 단계). ①접근경계 ✅ #144 · ②동선격리 ✅ #150/#151 · ③통계요약 ✅ #152 위에 얹는다.
> **왜 설계 먼저**: **개인정보(PII) 노출 폭이 가장 큰 화면**이다 — 이메일·가입 메타·독서 기록을 운영자가 직접 들여다본다. "무엇을 어디까지, 어떻게 가려서" 보여줄지 먼저 못 박는다.
>
> 관련: [plan.md](../plan.md) §관리자 대시보드 · [login-id-design.md](login-id-design.md)(login_id=불변 공개 핸들·식별 키) · [learning-notes.md](learning-notes.md) **N-037**(읽기 전용 집계).

---

## 0. 한눈에 — 이 메모가 정한 방향

| 항목 | 결정 | 확정? | 근거 |
|---|---|---|---|
| 범위 | **읽기 전용** 목록 + 드릴다운만 | ✅ | 수정·삭제 운영 액션은 CSRF·감사로그 검토 후 별도 단계 |
| 라우팅 | `GET /admin/users`(목록) · `GET /admin/users/{loginId}`(상세) | ✅ | login_id가 불변·유니크 식별 키 |
| **email 표시** | **마스킹 기본 + 클릭 시 전체**(`g***@gmail.com`) | ✅ | 최소 노출 원칙 + 실용 절충(사용자 결정) |
| 비밀번호 해시 | **절대 노출 안 함**(쿼리·DTO에서 제외) | ✅ | 민감값 — 화면에 둘 이유 없음 |
| **감사 로그** | **이번엔 보류** — Flyway 없이 순수 읽기 | ✅ | 1인 운영, 조회 audit 실익 적음. 운영 액션 단계에서 도입 |
| **드릴다운 범위** | 타이머 설정 + 최근 N개 세션 + 책장 요약 | ✅ | 한눈에 보기 좋은 균형(사용자 결정) |
| 페이지네이션 | `Pageable` 페이징 + login_id/nickname 검색 | ✅ | 사용자 증가 대비 — 전건 로딩 회피 |
| 접근 경계 | 기존 `/admin/**`→`hasRole("ADMIN")` 그대로 | ✅ | 추가 작업 없음(필터에서 한 번만) |
| Flyway | **없음**(스키마 무변경 — 감사로그 보류라 새 테이블 0) | ✅ | 전부 읽기 — count/조회뿐 |
| 운영자 자신 | 목록에 ADMIN도 보임(통계와 달리 *운영 대상*이라 포함) | ✅ | 통계의 "가입자 수"와 목적이 다름(운영 데이터 열람) |

---

## 1. 왜 — 무엇을 푸나

운영 데이터(사용자·타이머·세션·책)를 보려고 **매번 RDS에 직접 붙는 게 번거롭다.** ③ 통계 요약이 "전체 숫자"를 줬다면, ④는 **"개별 데이터를 들여다보는 창"**이다 — 한 사용자가 어떻게 쓰는지, 가입은 언제·어떤 경로(LOCAL/구글)인지, 온보딩은 했는지, 책은 몇 권·얼마나 읽었는지.

핵심 긴장: **편의 ↔ 개인정보.** 운영 화면이라도 PII는 최소로 노출하고, 보이더라도 한 겹 가린다.

---

## 2. 라우팅 / 화면

### 2.1 목록 — `GET /admin/users`

- **컬럼**: login_id(@핸들) · nickname · email(**마스킹**) · 이메일 인증 여부(인증/미인증 배지) · provider(LOCAL/GOOGLE) · 가입일(createdAt) · 온보딩 여부 · 역할(USER/ADMIN)
- **검색**: `?q=` — login_id 또는 nickname 부분일치(대소문자 무시). email로는 검색 안 함(노출 최소화).
- **정렬**: 가입일 내림차순(최근 가입 먼저) 기본.
- **페이지네이션**: `?page=&size=` — Spring Data `Pageable`. 기본 size=20.
- 각 행의 login_id는 `GET /admin/users/{loginId}` 상세로 링크.

### 2.2 드릴다운 — `GET /admin/users/{loginId}`

한 사용자의 운영 스냅샷:

- **계정**: login_id · nickname · email(마스킹+클릭 전체) · 이메일 인증 여부 · provider · 가입일 · 온보딩 · 역할 · 타임존
- **타이머 설정**(ReadingTimer, 1:1): 하루 증가값 · 누적 상한(cap) · 현재 잔여 · 마지막 누적일 · cap 도달 여부. (없을 수도 — 온보딩 전이면 null 가능 → "타이머 미설정"으로 표기)
- **최근 세션**(ReadingSession, 최근 N개·startedAt 내림차순): 책 제목 · 측정 길이 · 시작 시각. N=10 기본.
- **책장 요약**: 총 책 수 + 상태별(읽고싶음/읽는중/완독) 카운트 + 공개(PUBLIC) 책 수. 책 전체 나열은 안 함(요약만 — 드릴다운 더 깊게는 다음).

> 없는 loginId → **404**(운영 화면이라 존재 누설 회피는 불필요하지만, 일관되게 `ResponseStatusException(NOT_FOUND)`).

---

## 3. email 마스킹 — 규칙

최소 노출: 목록·상세 모두 **기본은 마스킹**, 클릭(또는 `<details>`/토글)으로 전체 표시.

**마스킹 규칙**(서버에서 마스킹 문자열을 만들어 내려보냄 — 원문은 토글 시에만):
- local part 첫 1글자 + `***` + `@` + 도메인 전체. 예: `goospel@gmail.com` → `g***@gmail.com`
- local part가 1글자면: `*` + `@domain`. 예: `a@x.com` → `*@x.com`
- 도메인은 가리지 않음(식별엔 부족하고, 가입 분포 파악엔 유용).

> **주의**: "클릭 시 전체"는 원문 email을 클라이언트로 내려야 한다. 운영 화면(ADMIN만 접근)이라 허용하되, **목록 기본 응답에도 원문이 DOM에 실린다**(가린 건 CSS/JS 표시뿐). 1차는 단순하게 — 서버가 마스킹 문자열과 원문을 함께 주고 토글. (더 엄격히 하려면 원문은 별도 fetch로 미루는 방법이 있으나 과설계 — 보류.)

---

## 4. 쿼리 / 서비스 설계

전부 **읽기 전용**. 새 저장 없음(N-037).

- **목록**: `userRepository.findBy...` 페이징 — login_id/nickname 부분일치 + `Pageable`. 새 메서드(예: `Page<User> searchForAdmin(String q, Pageable)`), 또는 Spring Data 파생 쿼리 2개(q 유무 분기). email 검색 없음.
- **드릴다운 조립**(`AdminUserDetailService`):
  - `findByLoginId(loginId)` → 없으면 빈 Optional → 컨트롤러 404.
  - 타이머: `ReadingTimerRepository.findByUser(user)`(Optional).
  - 최근 세션: 신규 `ReadingSessionRepository.findTopNByUser...OrderByStartedAtDesc`(Pageable로 N개).
  - 책장 요약: `BookRepository.count...` 재사용 가능한 것 + 상태별 카운트(기존 `countByUserAndVisibility` 있음, 상태별은 추가 필요할 수 있음 — `countByUserAndStatus`).
- **DTO**: `AdminUserRow`(목록 행), `AdminUserDetail`(상세) — **둘 다 passwordHash 필드 없음**(엔티티에서 뽑을 때 제외).

> N+1 주의: 목록은 User만 — 책/세션 카운트를 행마다 안 부른다(목록엔 계정 메타만). 카운트는 드릴다운에서만.

---

## 5. 보안 / 프라이버시 체크리스트

- [x] 접근 경계: `/admin/**`→`hasRole("ADMIN")` (기존). 컨트롤러 재검사 없음.
- [x] passwordHash 비노출: DTO·쿼리에서 제외.
- [x] email 최소 노출: 마스킹 기본, 검색 대상에서 email 제외.
- [x] 읽기 전용: 수정·삭제 엔드포인트 없음(POST 없음 → 이 화면엔 CSRF 표면도 없음).
- [ ] 감사 로그: **보류**(운영 액션 도입 시 함께). 그때 "누가 언제 누구를 조회/변경" 적재.
- [x] IDOR 무관: 운영자는 전체 열람이 정당 — 단, 식별 키는 불변 login_id(추측 어려움은 부차적).

---

## 6. 단계 (PR 분할 제안)

작게 나눌 수 있으나, 읽기 전용·Flyway 없음이라 **한 PR로 충분**:

1. 목록(`/admin/users`) — 페이징·검색·마스킹 + `admin-users.html`
2. 드릴다운(`/admin/users/{loginId}`) — 타이머·최근세션·책장요약 + `admin-user-detail.html`
3. `/admin`(대시보드 랜딩)에 "사용자 목록" 진입 링크

> TDD: 목록 페이징·검색(q 매칭/미매칭)·email 마스킹 규칙(경계: 1글자 local)·드릴다운 조립(타이머 없음 폴백·최근 N 제한·책장 카운트)·없는 loginId 404·**USER 권한 403**(접근 경계 회귀) Red→Green.

---

## 7. 의식적 보류 (이번 범위 밖)

- **운영 액션**(비밀번호 리셋·계정 정지·역할 변경·삭제) — CSRF·감사로그·되돌리기 정책 선결.
- **감사 로그 테이블** — 위와 묶어서.
- **책 전체/세션 전체/잔디 풀 상세** — 요약으로 시작, 필요하면 깊게.
- **CSV 내보내기·필터(provider별·온보딩별)** — 수요 생기면.
- **email 원문 지연 로딩**(목록 DOM에서 원문 제거) — 과설계, 보류.
