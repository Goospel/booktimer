# SNS 기능 — 설계 문서 (구현 전 합의용)

> **상태**: ✅ **구현 완료** — 1~5단계 전부 출하됨(§7 참조). 이 문서로 공유 모델·프라이버시·관계·스키마를 먼저 못 박은 뒤 TDD로 구현했다. (이 문서는 설계 기록 — 단계별 구현 결과는 §7과 changelog에 있다.)
> **예외**: **§13 독서 스토리는 새 설계(2026-07-02 확정) — 미구현.** 구현 전 합의용 절차(이 문서의 존재 이유)를 그대로 따른 것.
> **왜 설계 먼저**: SNS는 **데이터 노출·권한 경계가 핵심**이라, 설계 없이 시작하면 되돌리기 어려운 결정(공개 범위·스키마)이 코드에 굳는다.
> 비공개 기록이 한 번 새면 회수 불가 — 인가 경계는 사후 패치가 아니라 **설계 단계의 1순위**다.
>
> 관련: [plan.md](../plan.md) §SNS 기능 · [domain-design.md](domain-design.md) · [learning-notes.md](learning-notes.md) **N-037**(저장 대상=관계+공개범위) · **N-017**(SSR↔SPA 전환 기준).

---

## 1. 확정 요구사항 (사용자 정의 2026-06-04)

> 사용자가 직접 정의한 1차 범위. 아래 설계는 이 요구사항을 만족하도록 잡았다. (이후 변경 시 이 절 + §0 표 갱신)

1. **사용자는 서로 팔로우할 수 있다.** → 관계 모델 = **팔로우(단방향)**.
2. **개인은 자신의 책장에서 어떤 책을 공개(오픈)할지 선택할 수 있다.** 전체 공개도, 일부 책만 비공개도 가능.
   → 공개 단위 = **책 단위(per-book) 공개/비공개**.
3. **책 검색 결과에서, 내가 팔로우하는 사람 중 몇 명이 그 책을 "원하는지/읽었는지" 확인할 수 있다.**
   → 인기 카운트는 **팔로우 스코프 집계**(전역 아님).
4. **사용자는 자신만의 개인 페이지(프로필)를 가진다.**
5. **사용자들은 서로의 메인 페이지에서 책장·잔디 같은 정보를 확인할 수 있다.** → 프로필 = 공개 책장 + 잔디.
6. **닉네임으로 서로를 검색할 수 있다. 따라서 닉네임은 중복 불가.** → 닉네임 = **유니크 핸들 겸 검색 키**.

---

## 0. 한눈에 — 이 문서가 못 박는 것

| 항목 | 결정 | 확정? | 근거 |
|---|---|---|---|
| 저장 대상 | **관계 + 공개범위만**. 독서기록(book/reading_session)은 그대로 | ✅ | N-037 |
| 관계 모델 | **팔로우(단방향)** | ✅ | 요구사항 1 |
| 공개 단위 | **책 단위(per-book) 공개/비공개** | ✅ | 요구사항 2 |
| 공개 기본값 | **비공개 기본** — 기존 책은 마이그레이션 시 전부 비공개 백필, 공개는 opt-in | ✅ | 프라이버시 사고 방지 |
| 닉네임 | **유니크 + NOT NULL**, 검색·프로필 URL 핸들 | ✅ | 요구사항 6 |
| 프로필 페이지 | 닉네임으로 접근, 공개 책장 + 잔디 노출 | ✅ | 요구사항 4·5 |
| 인기 카운트 | **팔로우 스코프**("내 팔로우 중 N명이 원함/읽음") | ✅ | 요구사항 3 |
| 화면 렌더 | 프로필·검색 = **SSR**(SEO·닉네임 검색), 피드/토글 = 인터랙션 보고 판단 | 🟡 | N-017 |
| 새 Flyway | `V7~`부터 (현재 마지막 V6), 번호는 머지 직전 확정 | ✅ | 충돌 회피 |

> ✅ = 사용자 요구사항/명확한 근거로 확정. 🟡 = 제안(착수 전 확인). **§11 열린 질문**에 남은 미결정 정리.

---

## 2. 핵심 통찰 — "남에게 보여주려면 DB에 저장해야 하나?" → 아니다 (N-037)

독서 데이터는 **이미 전부 저장돼 있다**:
- "누가 어떤 책을 읽었나/읽는 중인가" → `book`(`user_id` 소유, `status`, `isbn13` 식별)
- "얼마나 읽었나" → `reading_session`(`book_id` + `duration_seconds`)

SNS에서 남의 걸 보여주는 건 **데이터 추가가 아니라 조회 주체를 바꾸는 것**이다:
```
기존:  where user_id = 나
SNS:   where user_id = 그 사람   (단, 그 사람이 그 책을 공개했고 / 내가 볼 권한이 있을 때만)
```

따라서 SNS가 **새로 저장하는 건 독서기록이 아니라 세 가지**:
1. **관계** — 누가 누구를 팔로우하는가 (`follow`)
2. **책별 공개 여부** — 각 책을 공개할지 (`book.visibility`)
3. **유니크 닉네임** — 검색·프로필 핸들 (`users.nickname` 유니크화)

그리고 `where user_id`를 갈아끼우는 **그 순간 책별 공개 체크가 보안 경계**가 된다(비공개 책 누출 방지). 이게 가장 위험한 지점.

---

## 3. 공개 범위 / 프라이버시 모델

### 3.1 공개는 **책 단위** (요구사항 2)

프로필 통째 토글이 아니라, **책장의 각 책마다** 공개/비공개를 켠다.

```
// book.visibility — 책마다
enum Visibility {
    PRIVATE,    // 나만 (기본값)
    PUBLIC      // 누구나 볼 수 있음(개념상 비로그인 포함 — SEO 공개 프로필)
}
```

> ⚠️ **2단계 한정 단서(2026-06-04)**: 개념상 PUBLIC은 비로그인 포함이지만, **2단계 프로필 페이지는 로그인 사용자만** 열람으로 시작한다(§7.2). SEO 개방은 추후(§11-8).

- **2-state(공개/비공개)로 시작.** 요구사항이 "조회되게/안 되게"의 이분이라 PUBLIC/PRIVATE면 충분.
- (확장 여지) "팔로워에게만" 단계(`FOLLOWERS`)가 필요해지면 enum에 값 추가 — varchar 저장이라 스키마 확장 쉬움. 1차 제외.

### 3.2 기본값 = **비공개(PRIVATE)** — opt-in 공개

- 기존 사용자는 "내 책장은 나만 본다"를 전제로 등록했다. 마이그레이션으로 갑자기 공개되면 **프라이버시 사고**.
- `book.visibility` 컬럼 추가 시 **기존 모든 책을 PRIVATE로 백필**. 공개는 사용자가 책마다 명시적으로 켠다.

### 3.3 프로필은 "발견 가능", 내용은 책별 게이트

요구사항 6(닉네임 검색)·4(개인 페이지) 때문에 **프로필의 존재 자체는 공개**(닉네임으로 찾을 수 있음)다. 단 그 안의 **내용은 책별 공개 여부로 거른다**:

| 프로필 구성요소 | 노출 규칙 |
|---|---|
| 닉네임 | 항상 공개(검색 키) |
| 책장(책 목록) | **PUBLIC인 책만** 보임. PRIVATE 책은 타인에게 아예 안 보임 |
| 책별 누적 시간 | 그 책이 PUBLIC일 때만(분 단위) |
| 잔디(컨트리뷰션) | **viewer 기준으로 계산** — §3.5 |
| 총 독서 시간 | §3.5 정책 따름 |

### 3.4 노출 항목 화이트리스트 (공개돼도 절대 안 나가는 것)

응답 DTO에 **아예 담지 않는다**(필드 누락이 아니라 설계적 차단):
- 이메일, `password_hash`, provider 식별자, 로그인 IP/세션
- 타이머 내부값(remaining/cap 등 부채 상태 — 사생활, 동기부여와 무관)
- 정확한 타임스탬프(몇 시에 읽었는지) → **날짜 단위로만**(잔디는 일자 농도라 이미 안전)

### 3.5 잔디·총시간의 프라이버시 — 비공개 책 세션 처리 (✅ 확정 2026-06-04)

잔디(일자별 독서 시간)·총 독서 시간은 **세션을 합산**해 만든다. 일부 책이 PRIVATE면, **그 책의 세션이 타인이 보는 잔디·총시간에 합산되면 비공개 책 독서가 간접 누출**된다(무슨 책인진 몰라도 "이 날 N분 읽었다"가 샘).

**✅ 결정 (사용자 2026-06-04): "공개하지 않은 책은 잔디에 노출하지 않는다."**
- **타인에게 보이는 잔디·총시간은 PUBLIC 책의 세션만**으로 계산(viewer-dependent). 본인이 보는 잔디는 전부 합산(지금과 동일).
- **책 미지정(book=null) 세션도 타인 잔디에서 제외** — "공개로 명시되지 않은 활동"이라 비노출이 일관적.
- 구현: 잔디 계산을 **viewer 기준으로 분기** — 타인 조회 시 세션을 `where book.visibility=PUBLIC`로 필터한 뒤 빌더에 넘긴다.
  순수 빌더(`ContributionGraphBuilder`)는 그대로 두고, **서비스에서 세션 필터링**으로 끼우면 빌더 시그니처 영향 최소(§11-7).

---

## 4. 관계 모델 — 팔로우(단방향) (요구사항 1)

- **팔로우 = 즉시 성립(승인 없음).** 비공개 보호는 책별 `visibility`가 담당하므로 승인제가 없어도 안전.
- 자기 자신 팔로우(`follower==followee`) 금지 — 도메인 검증 + 테스트로 고정.
- 중복 팔로우 금지 — DB 유니크 제약 + 앱 레벨 이중.
- (후속) 비공개 계정 승인제·차단(block)이 필요해지면 `follow.status`(PENDING/ACCEPTED)·`block` 테이블로 확장. 1차 제외.

> 팔로우 vs 친구(양방향): 독서 공유는 "관심 있는 사람 구독"에 가까워 단방향이 자연스럽고 가볍다. 승인 상태 머신이 없어 스키마·로직 단순.

---

## 5. 권한 경계 (IDOR) — 가장 위험한 지점

### 5.1 책 단위 가시성 게이트

`viewer`(보는 사람)가 특정 **책**을 볼 수 있는가:

```
canViewBook(viewer, book):
    if viewer == book.owner:           return true        # 내 책
    return book.visibility == PUBLIC                       # 남의 책은 PUBLIC만
    # (후속) FOLLOWERS 도입 시: || (book.visibility==FOLLOWERS && isFollowing(viewer, book.owner))
    # (후속) block 도입 시 맨 앞: if isBlocked(book.owner, viewer) return false
```

- 이 체크를 **모든 "남의 책/책장/잔디/시간" 조회 진입점에서 강제**(컨트롤러 진입 즉시). 빼먹으면 곧 IDOR.
- 비로그인 viewer는 PUBLIC만 통과.
- 프로필 페이지 렌더 시: `where user_id=대상 and visibility=PUBLIC`로 책장을 거르고, 잔디는 그 PUBLIC 책 세션만으로 계산(§3.5).

### 5.2 닉네임 검색 / 열거 완화 (요구사항 6)

- 프로필 URL은 **닉네임 핸들** `/u/{nickname}` (순번 id 노출 회피 — 전수 크롤링 방지).
- 닉네임 검색은 사용자를 찾아주되, **찾은 프로필의 내용은 책별 게이트**가 거른다(비공개 책은 안 보임).
- 검색은 사용자 *존재*를 노출하지만 *독서 내용*은 노출 안 함 — 책 공개는 별개.

### 5.3 권한 실패 응답

- 비공개 책 직접 접근(예: `/books/{id}` 남의 PRIVATE 책) → **404**(403 대신). "있는데 못 본다"(403)는 존재 누설 → 비공개는 "없는 것처럼" 404가 열거 방어에 유리.

### 5.4 TDD로 못 박을 보안 케이스
- 본인 → 자기 PRIVATE 책도 보임
- 타인 PUBLIC 책 → 비로그인 포함 보임
- 타인 PRIVATE 책 → 본인 외 전원 404
- 프로필 페이지 → PUBLIC 책만 목록에 뜨고 PRIVATE 책은 누락(개수·시간에도 안 잡힘)
- 잔디 → 타인 시점엔 PUBLIC 책 세션만 반영(§3.5 확정 정책대로)
- DTO에 이메일/해시/타이머 내부값 **절대 안 실림**(직렬화 누출 테스트)

---

## 6. 스키마 / 마이그레이션

### 6.1 `book` — 책별 공개 여부 (요구사항 2)

```sql
-- V7__book_visibility.sql (예시 — H2/MySQL 공통 문법 확인)
alter table book add column visibility varchar(20) not null default 'PRIVATE';
-- 기존 모든 책은 'PRIVATE'로 백필 — 갑작스런 공개 사고 방지(opt-in)
```
- enum varchar 저장(N-023 Flyway 관례, MySQL·H2 공통). 길이 20.
- 인기 카운트·프로필 조회가 visibility로 자주 필터 → `(isbn13, visibility)` 또는 `(user_id, visibility)` 인덱스 검토.

### 6.2 `users.nickname` — 유니크 + NOT NULL (요구사항 6)

```sql
-- V8__nickname_unique.sql
-- ⚠️ 선결: 기존 중복/NULL 닉네임 백필 후에 제약을 건다(아래 순서)
-- 1) NULL·중복 닉네임을 유니크하게 정리(예: 닉네임 없거나 충돌 시 'user{id}' 류로 채움)
-- 2) not null + unique 제약 부여
alter table users modify column nickname varchar(50) not null;   -- (정리 후)
alter table users add constraint uq_users_nickname unique (nickname);
```
- ⚠️ **현재 닉네임은 nullable·중복 허용**(domain-design.md). 운영 DB에 **중복/NULL이 있을 수 있어** 제약 추가 전 **백필이 선결**. 백필 규칙(충돌 시 suffix, NULL은 `user{id}`)을 마이그레이션에 포함.
- 닉네임 변경 허용 여부·정책(변경 시 핸들 URL 깨짐·검색 캐시)은 §11 열린 질문.

### 6.3 `follow` — 관계 (요구사항 1)

```sql
-- V9__follow.sql
create table follow (
    id          bigint       not null auto_increment,
    follower_id bigint       not null,   -- 팔로우하는 사람
    followee_id bigint       not null,   -- 팔로우당하는 사람
    created_at  datetime(6)  not null,
    primary key (id),
    constraint uq_follow unique (follower_id, followee_id),     -- 중복 팔로우 방지
    constraint fk_follow_follower foreign key (follower_id) references users(id),
    constraint fk_follow_followee foreign key (followee_id) references users(id)
);
create index idx_follow_follower on follow (follower_id);   -- 내가 팔로우하는 사람
create index idx_follow_followee on follow (followee_id);   -- 나를 팔로우하는 사람
```

### 6.4 안 건드리는 것
- `reading_session` — 변경 없음(N-037). 조회 `where`와 가시성 필터만 바뀐다.
- `book`은 컬럼 1개(visibility) 추가뿐 — 기존 행/로직 무변경.

### 6.5 Flyway 번호 조율
- 현재 마지막 **V6**(`V6__user_onboarded.sql`). SNS는 **V7부터**. 위 V7/V8/V9는 예시 — 단계별로 쪼개 머지하므로 **번호는 각 PR 머지 직전 확정**.
- ⚠️ 다중 세션 동시 작업 시 번호 충돌 주의([CLAUDE.md](../CLAUDE.md) 워크트리 규칙).

---

## 7. 단계별 구현 로드맵 (incremental — big-bang 아님)

> 요구사항을 의존 순서로 쪼갬. 각 단계 독립 출시 가능. **팔로우 스코프 카운트(요구사항 3)는 팔로우+책 공개에 의존**하므로 뒤쪽.

### 7.1 1단계 — 닉네임 유니크화 + 책별 공개 토글 (기반) ✅ 완료 (2026-06-04)
- ✅ **닉네임 유니크화 완료 (2026-06-04)**: Flyway **V7**(중복 백필 `-{id}` → `uk_users_nickname` 유니크 제약).
  앱 레벨 — LOCAL 가입은 중복 거부(`NicknameAlreadyExistsException` → 폼 에러), **소셜 로그인은 거부 못 하므로 자동
  유일화**(`NicknameAllocator` `base-2…`), 설정 변경도 중복 거부(자기 현재 닉 유지는 허용). `existsByNickname` 추가. TDD.
  ※ nickname은 V1부터 NOT NULL이라 NULL 백필 불필요 — 중복만 정리.
- ✅ **소셜 사용자 닉네임 직접 지정 (2026-06-04)**: 구글 가입자는 자동 배정된 임시 닉(`구글러-2` 등)을 쓰지 않고,
  **온보딩 페이지에서 직접 닉네임을 정한다**(온보딩 폼에 닉네임 필드 추가, 기존 닉 prefill, 중복 거부·자기 닉 유지 허용).
  자동 배정은 온보딩 완료 전까지의 임시값 역할(닉은 NOT NULL이라 계정 생성 시점에 유효값 필요). LOCAL도 동일 폼 사용.
- ✅ **책별 공개 토글 완료 (2026-06-04)**: `BookVisibility`(PRIVATE/PUBLIC, 기본 PRIVATE) + Flyway **V8**
  (`book.visibility`, default PRIVATE 백필 — 기존 책 전부 비공개) + 책장에서 책마다 공개/비공개 셀렉트
  (`POST /books/{id}/visibility`, 소유권 강제=IDOR 방어). `Book.makePublic/makePrivate/isPublic`. TDD.
- 아직 남의 걸 보는 화면은 없음(공개 토글·닉네임만). **다음 = 2단계 프로필 페이지**의 토대 완성.

### 7.2 2단계 — 개인 프로필 페이지 (요구사항 4·5) — ✅ 구현 완료 (2026-06-04)

> 착수 전 4개 결정 확정(사용자 2026-06-04). 아래가 구현 기준. 닉네임 검색 UI는 **이번 범위 제외**.
>
> **✅ 구현 완료 (2026-06-04)**: `GET /u/{nickname}` SSR(`profile.html`) — `ProfileService`(profile 패키지)가 PUBLIC 책장 +
> 공개 잔디를 조립. 가시성 필터는 `ReadingHistoryService.publicDailyHistory`(잔디)·`BookReadingStatsService.publicTotalSecondsByBook`(책별 시간)·
> `BookRepository.findByUserAndVisibility…`(책장)에 분산, 순수 빌더는 무변경(§11-7). 닉네임 404, 비로그인 차단(default-deny 자동),
> 본인도 PUBLIC만(공개 미리보기). 신규 마이그레이션 없음. TDD Red→Green 2사이클(단위 잔디·책시간 필터 + 통합 컨트롤러). 대시보드에 진입 링크.

**범위(확정)**: 프로필 페이지 1개만. 닉네임 검색 화면은 제외(추후/3단계 팔로우와 함께). 직접 URL 접근.

**라우트**: `GET /u/{nickname}` — **SSR**(Thymeleaf), 신규 view `profile`. 닉네임은 유니크(V7)라 1:1.

**접근 제어(확정 — 로그인 한정 시작)**:
- **로그인 사용자만.** 비로그인 차단 → SecurityConfig에서 `/u/**`는 `authenticated`.
  ⚠️ 설계 §3.1·§8의 "PUBLIC=비로그인 포함(SEO 공개)"는 **이번엔 보류**. SEO 개방이 필요해지면 `/u/**` permitAll로 열고 그때 재검토(열린 질문 §11-8).
- 없는 닉네임 → **404**(§5.3 — 존재 누설 회피와 일관).

**뷰어 무관 "공개 프로필" 뷰(확정)**:
- 이 페이지는 **항상 "남에게 보이는 공개 프로필"**. 본인이 자기 닉으로 들어와도 **PUBLIC 책·세션만** 보인다.
- 즉 이 페이지에선 canViewBook을 **소유자 예외 없이 균일하게 `visibility==PUBLIC`**로 적용(로직 단순·일관, "남이 이렇게 본다" 미리보기 역할).
- 본인의 전체 책장·잔디(PRIVATE 포함)는 기존 `/books`·대시보드에서 본다(역할 분리).

**표시 정보(확정 — 공개 책장 + 잔디)**:
| 구성요소 | 내용 | 규칙 |
|---|---|---|
| 헤더 | 닉네임 | 항상 노출(검색 키). 이메일·타이머·부채값 등 §3.4 화이트리스트 차단(DTO 미포함) |
| 공개 책장 | PUBLIC 책 목록(제목·저자·상태·책별 누적시간) | `where user_id=대상 and visibility=PUBLIC`. PRIVATE는 목록·개수·시간에서 **완전 누락** |
| 잔디 | 일자별 농도 | **viewer 기준(§3.5)** — PUBLIC 책 세션만 빌더에 전달. book=null 세션 제외 |

**구현 포인트**:
- 닉네임 조회: `UserRepository.findByNickname`(유니크 1건). 없으면 404.
- 잔디 가시성 필터: **서비스에서 세션을 `book.visibility==PUBLIC`로 필터한 뒤 순수 `ContributionGraphBuilder`에 전달**(§11-7). 빌더 시그니처 무변경.
- 책별 누적시간: 기존 bookTimes 계산을 PUBLIC 책으로 한정해 재사용.

**TDD 보안 케이스(§5.4 부분집합 — 먼저 실패 테스트로 못 박는다)**:
- 없는 닉네임 → 404
- 타인 프로필: PUBLIC 책만 목록에 뜨고 PRIVATE 책 누락(개수·책별 시간에도 안 잡힘)
- 잔디: 타인 PUBLIC 책 세션만 반영, PRIVATE·book=null 세션 제외
- **본인 자기 프로필도 PUBLIC만**(공개 미리보기) — 자기 PRIVATE 책 누락
- 비로그인 접근 → 인증 요구(로그인 리다이렉트)
- DTO 직렬화 누출 없음(이메일/해시/타이머 내부값)

**스키마**: 신규 마이그레이션 없음(닉네임 유니크=V7, visibility=V8 이미 적용). follow(V9)는 3단계.

**이번에 안 하는 것**: 팔로우 버튼(3단계) · 닉네임 검색 UI(보류) · 인기 카운트(4단계) · 3-state FOLLOWERS(후속).

### 7.3 3단계 — 닉네임 검색 + 팔로우 (요구사항 1·6) — ✅ 구현 완료 (2026-06-04)

> 검색과 팔로우를 **한 묶음**으로 간다(사용자 결정 2026-06-04): 남을 발견할 길(검색)이 없으면 팔로우가 무용지물 —
> "검색 → 프로필 → 팔로우"가 한 흐름으로 완결돼야 한다. 닉네임 검색은 요구사항 6, §7.2에서 보류했던 것.
>
> **✅ 구현 완료 (2026-06-04)**: `GET /search`(부분일치·최소2글자·상한20, `UserSearchService`), `Follow`(V9)+`FollowService`(자기팔로우 금지·멱등·언팔즉시),
> `POST /follow`·`/unfollow`(오픈리다이렉트 방어), 프로필에 팔로워/팔로잉 카운트+팔로우 버튼(`ProfileService` viewer 기준 following/self).
> 검색 결과 = 닉네임+공개책수+팔로우버튼. 회원 탈퇴 시 follow 양방향 정리(`AccountService.purge`). 대시보드에 검색 링크. TDD Red→Green(서비스 단위 2 + 컨트롤러 통합 3).
> ※ 별도 발견(범위 밖): 탈퇴 시 book 미삭제 FK 위반(기존) — 후속 작업으로 분리.

**범위(확정)**: ① 닉네임 검색 화면 ② 팔로우/언팔로우 ③ 프로필에 팔로우 버튼 + 팔로워/팔로잉 **카운트만**. 관계 목록 화면은 후속.

**검색(확정 — 부분일치)**:
- `GET /search?q=...` — SSR(view `search`). 닉네임 **부분일치**(`like %q%`).
- 가드: **최소 2글자**(미만이면 결과 없이 안내), **결과 상한 20**(열거·크롤링 완화 §9). 로그인 사용자만(default-deny).
- 결과 항목(확정): **닉네임(프로필 링크) + 공개 책 수 + 팔로우/언팔 버튼**(현재 내가 팔로우 중인지로 분기). 본인이 결과에 걸리면 버튼 대신 "나" 표시.
- 공개 책 수: `BookRepository.countByUserAndVisibility(user, PUBLIC)`(결과 ≤20이라 건당 카운트 허용).

**팔로우(확정)**:
- `follow` 테이블(V9) — `(follower_id, followee_id)` 유니크. `Follow` 엔티티 + `FollowRepository`.
- **자기 자신 팔로우 금지**(도메인 검증 + 버튼 비노출). **중복 팔로우 멱등**(유니크 제약 + `existsBy` 가드 — 두 번 눌러도 1행). **언팔로우 즉시**(승인 없음, §4).
- `POST /follow` / `POST /unfollow`(대상 닉네임 파라미터, CSRF, PRG로 돌아온 화면으로 리다이렉트).
- 프로필 페이지: **팔로워 N · 팔로잉 M 카운트** + 팔로우/언팔 버튼(자기 프로필이면 버튼 없음).
- 카운트: `countByFollowee`(팔로워 수) · `countByFollower`(팔로잉 수).
- **목록 조회(본인 전용) ✅ 2026-06-04**: 본인 프로필에서만 카운트가 `/me/followers`·`/me/following`로 링크
  (남의 프로필은 카운트만 — privacy 유지). 경로에 닉네임이 없어 항상 본인 기준(보안 경계 자동). 목록 행은
  검색과 동일(`UserRowAssembler`→`UserSearchResult`: 닉네임·공개책수·팔로우버튼). `FollowListService`,
  `findByFollowee/FollowerOrderByCreatedAtDesc`. 큰 목록 시 행당 카운트=N쿼리(현 규모 충분, 필요 시 일괄화).

**스키마(V9 — 신규 테이블, additive·안전)**:
```sql
create table follow (
    id bigint not null auto_increment,
    follower_id bigint not null,
    followee_id bigint not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_follow unique (follower_id, followee_id),
    constraint fk_follow_follower foreign key (follower_id) references users(id),
    constraint fk_follow_followee foreign key (followee_id) references users(id)
);
create index idx_follow_follower on follow (follower_id);
create index idx_follow_followee on follow (followee_id);
```
- 메인 테스트 스키마는 ddl-auto(엔티티 파생), V9는 FlywayMigrationTest가 H2로 별도 검증(N-… 분리 관례).

**TDD 도메인·보안 케이스(먼저 실패 테스트로)**:
- 자기 자신 팔로우 → 거부(예외)
- 중복 팔로우 → 멱등(1행 유지)
- 언팔로우 후 `isFollowing=false`, 팔로워 수 감소
- 검색: 부분일치 매칭 / 2글자 미만 빈 결과 / 상한 20 / 본인은 팔로우 버튼 없음 / 공개 책 수 = PUBLIC만 카운트
- 프로필: 팔로워·팔로잉 카운트 정확, 자기 프로필엔 팔로우 버튼 없음
- 비로그인 → 검색·팔로우 차단(로그인 리다이렉트)

**이번에 안 하는 것**: 인기 카운트(4단계 ✅) · 차단/신고/레이트리밋(5단계) · 승인제(후속).
(팔로워/팔로잉 **본인 전용 목록 화면**은 위 "목록 조회"에서 추가 완료 ✅ 2026-06-04.)

### 7.4 4단계 — 팔로우 스코프 인기 카운트 (요구사항 3) — 구현 완료 ✅ 2026-06-04
- 책 검색 결과·내 책장 각 책에 **"👥 팔로우 중 N명 원함 · M명 읽음"** 표시.
- 집계: 내가 팔로우한 사용자(followee) ∩ 그 책(isbn13) ∩ **그 책이 내게 보이는 것(PUBLIC)** 의 status별 distinct user count.
- **status 매핑 확정**(§11-4): 원함 = `WANT_TO_READ` · **읽음 = `READING` ∪ `FINISHED`**.
- **k-익명성 확정**(§11-5): **임계 없음 — 1명부터 항상 표시**. 근거: 1차엔 "누가 읽는지" drill-down 목록이 없어 재식별 위험 제한적(사용자 확정 2026-06-04). drill-down 도입 시 재검토.
- **N+1 회피**: 페이지의 isbn 목록을 한 번의 `group by`로 일괄 집계(팔로우 theta 조인 포함) — `BookRepository.followScopePopularity`.
- 구현: `FollowScopePopularityService.countByIsbn(viewer, isbns)` → `Map<isbn, FollowScopePopularity(want, read)>`,
  `BookController#books`가 책장+검색결과 isbn을 모아 1회 호출·모델 주입, `books.html` `followPopularity` 프래그먼트(0 버킷·데이터 없는 isbn 미표시).
  Flyway 신규 없음(기존 book/follow 조회). TDD: 서비스 단위 + 집계 통합(실 H2 — 팔로우 스코프·PRIVATE 제외·distinct·status 버킷) + 컨트롤러 끝단.

### 7.5 5단계 — 악용/스팸 대응 (운영 안정화)
- **차단(block) — 구현 완료 ✅ 2026-06-04 (대칭)**: 차단 관계가 한 방향이라도 있으면 둘은 **서로 팔로우 불가·서로 프로필 404**
  (`BlockRepository.existsBetween` 양방향 게이트 — FollowService·ProfileService에 끼움). 차단 시 기존 팔로우 **양방향 해제**.
  자기 차단 금지·중복 멱등·언차단 즉시. 저장은 단방향(`Block` blocker→blocked, V10), 효과만 대칭.
  차단 후 상대 프로필이 404라 거기서 해제 불가 → **`/me/blocks`(본인 차단 목록)에서 해제**. 프로필에 차단 버튼, 탈퇴 정리(purge)에 block 양방향 추가.
  대칭 선택 근거: 멘탈모델·구현 단순(둘 사이 차단 존재 한 번만 검사)·강한 단절(사용자 확정 2026-06-04).
  - 차단한 사용자를 **검색 결과에서도 숨기기 — 구현 완료 ✅ 2026-06-04 (PR-2)**: `UserSearchService`가 결과를 `existsBetween`로 필터(양방향). 프로필이 대칭 404라 검색에 떠도 못 여는 모순 제거.
- **신고(report) — 구현 완료 ✅ 2026-06-04 (PR-1)**: 프로필에서 사용자를 신고(사유 스팸/괴롭힘/부적절/기타 + 상세). `report` 테이블(V11),
  `(reporter, reported)` 유니크로 **쌍당 1건**(중복 신고 스팸 방지·멱등), 자기 신고 금지(도메인 검증). block과 같은 구조(`ReportService`,
  `POST /report` 오픈리다이렉트 방어, 탈퇴 purge에 report 양방향 정리). TDD Red→Green. ※ reason은 enum→STRING, `ReportReason.from()`은
  잘못된 값을 OTHER로 폴백(신고가 막히지 않게).
  - **관리자 신고함 — 구현 완료 ✅ 2026-06-08 (PR #245)**: 처음엔 "저장만"이고 검토 UI가 추후였으나, 운영자가 후속 처리하려면 화면이
    필요해 신설. `/admin/reports`(ADMIN 전용)에서 신고자→대상·사유·상세·시각을 **최신순**(id desc tiebreak)으로 보고, 처리 끝난 신고를
    삭제(POST·CSRF). 문의함(`/admin/feedback`)과 같은 패턴 — `AdminReportRow` DTO로 트랜잭션 안에서 신고자·대상(LAZY) 평면화(OSIV 비의존).
- **레이트리밋 + 열거 완화 — 구현 완료 ✅ 2026-06-04 (PR-2)**: `RateLimitService`(인메모리 고정 윈도우, **사용자별** 키 —
  인증 엔드포인트라 IP 아님; LoginAttemptService 미러)가 액션별 한도를 건다 — `RateLimitAction` FOLLOW(30/분)·SEARCH(20/분)·
  REPORT(10/시간). 컨트롤러에서 `allow(action, userId)` 체크: 초과 시 팔로우/신고는 조용히 드롭, 검색은 안내만 그린다(`rateLimited`).
  **열거 완화** = 기존 가드(최소 2글자·상한 20·없는 닉 404) + **검색 레이트리밋**(크롤링/전수 열거 속도 제한). `Clock` 주입(테스트 결정성).
  **한계**: 인메모리=인스턴스별 → 다중 인스턴스/롤링 배포 중 분산 우회 가능(로그인 제한기와 동일, 공유 저장소·WAF는 다층 방어 백로그). TDD Red→Green.
- **5단계 완료** — 차단·신고·레이트리밋·열거완화·검색숨김 모두 ✅. 관리자 신고 검토 화면도 ✅(PR #245). (후속 여지: 승인제 팔로우, 공유 저장소 레이트리밋.)

---

## 8. 화면별 SSR / SPA 판단 → 프론트 결정과 연결

> [plan.md §프론트엔드 전환]의 "두 번 짜기" 리스크를 **실측**하는 자리.

| SNS 화면 | 렌더 | 이유 |
|---|---|---|
| 개인 프로필(`/u/{nickname}`) | **SSR** | 목록·잔디 렌더 위주. (2단계는 로그인 한정 시작 §7.2 — SEO 개방 시 비로그인 진입점) |
| 닉네임 검색 결과 | **SSR** | 목록 렌더, 인터랙션 미미 |
| 팔로우 스코프 인기 카운트(검색·책장) | **SSR** | 기존 SSR 페이지에 숫자만 추가 |
| 책별 공개 토글 | SSR + htmx | 단일 액션. 현재 스택(htmx/Alpine)으로 충분 |
| 팔로우/언팔로우 버튼 | SSR + htmx | 동일 |
| (후속) 팔로잉 피드 | **판단 보류** | 무한 스크롤·실시간성 크면 SPA 후보. 초기엔 시간순 페이지네이션 SSR |

**결론**: 요구사항의 SNS 화면이 **전부 SSR로 충분/유리**(특히 SEO·닉네임 검색이 필요한 프로필). **"SNS = SPA 필요" 전제가 약하다** → "두 번 짜기" 리스크 작음 → **API-first big-bang 불필요.** 피드가 실제로 인터랙션 헤비해지면 그 화면만 incremental 전환.

---

## 9. 악용 / 남용 시나리오 (설계 시 대비)

- **비공개 책 누출** — §5 책 단위 게이트. 최우선. 잔디·총시간 간접 누출(§3.5)도 같이 차단.
- **닉네임 열거·크롤링** — 핸들 검색은 존재만 노출(내용은 책 게이트), PUBLIC만 인덱싱, 레이트리밋.
- **재식별(팔로우 카운트)** — 소수 팔로우 시 최소 임계값.
- **스토킹·괴롭힘** — 차단(block) 기능(후속), 단방향 팔로우라 차단으로 끊기.
- **스팸 팔로우** — 레이트리밋, 대량 팔로우 탐지.
- **닉네임 사칭/스쿼팅** — 유니크라 선점 가능 → 변경 정책·예약어(관리자/시스템) 검토.
- **직렬화 누출** — DTO 화이트리스트(§3.4), 엔티티 직접 직렬화 금지.

---

## 11. 미결정 / 열린 질문 (착수 전 확인)

> 요구사항으로 해결된 것: 관계=팔로우 / 공개단위=책별 / 핸들=유니크 닉네임 / 카운트=팔로우 스코프 / **잔디=PUBLIC 책만(§3.5 ✅)**. 아래는 남은 것.

1. ✅ **(해결) 잔디·총시간 프라이버시(§3.5)** — "공개하지 않은 책은 잔디에 노출 안 함"(사용자 2026-06-04). 타인 잔디는 PUBLIC 책 세션만, 책 미지정 세션도 제외.
2. **닉네임 백필 규칙** — 기존 중복 닉네임 정리(NULL은 이미 NOT NULL이라 없음). 채택: **가장 먼저 가입한(낮은 id) 쪽 유지, 이후 중복은 `-{id}` 접미사**(id가 PK라 유일 보장). 사용자는 이후 설정에서 변경 가능.
3. **닉네임 변경 허용?** — 허용 시 핸들 URL·검색·외부 링크 깨짐 정책(영구 고정 vs 변경 가능 + 과거 핸들 처리).
4. ✅ **(해결) 인기 카운트 status 매핑** — 원함=`WANT_TO_READ`, **읽음=`READING`∪`FINISHED`**(사용자 확정 2026-06-04).
5. ✅ **(해결) 인기 카운트 k-익명성** — **임계 없음, 1명부터 항상 표시**(사용자 확정 2026-06-04). drill-down(누구인지 목록)이 1차엔 없어 재식별 위험 제한적이라는 판단. drill-down 도입 시 재검토.
6. **공개 3-state 필요?** — 지금은 PUBLIC/PRIVATE 2-state. "팔로워에게만"(FOLLOWERS) 단계가 1차에 필요한가? (제안: 후속)
7. **잔디 viewer 의존 계산 위치** — `ContributionGraphBuilder`는 순수 빌더라 가시성 필터를 서비스에서 세션 필터링으로 넣는 게 자연스러움(빌더 시그니처 영향 최소).
8. **프로필 SEO 개방(비로그인 열람)** — 2단계는 **로그인 한정**으로 시작(확정 2026-06-04). PUBLIC 프로필을 비로그인·검색엔진에 열지(SecurityConfig `/u/**` permitAll), 연다면 크롤링·열거 완화(레이트리밋·robots)와 함께. 후속 결정.

---

## 12. 다음 액션

- [ ] §11 열린 질문 사용자와 확정
- [ ] 확정되면 **1단계(닉네임 유니크 + 책별 공개 토글)** 부터 TDD 착수 — 남을 보는 화면이 없어 가장 안전한 출발점
- [ ] `users.nickname` 유니크화 전 **기존 데이터 중복/NULL 점검 + 백필 마이그레이션** 설계(운영 DB)
- [ ] 각 단계 착수 전 canViewBook/잔디 가시성 필터 **보안 테스트 우선 작성**(§5.4)

> 이 문서는 **살아있는 문서** — 결정이 바뀌면 §1·§0 표와 해당 절을 갱신한다.

---

## 13. 독서 스토리 — 인스타 스토리식 24h 문장 공유 (설계 확정 2026-07-02 · 구현 전)

> **컨셉**: 책을 읽다 인상 깊은 **문장**을 올리면 **나를 팔로우한 사람에게 24시간만** 보이고 만료되는
> 텍스트 카드. UI는 인스타 스토리 문법 — 상단 아바타 링(미열람 강조) → 탭하면 풀스크린 카드·진행바·
> 자동 넘김·좌우 탭. 배경·1차 방향(텍스트만·책 연결 선택)은 [plan.md](../plan.md) §독서 스토리(2026-06-29).
>
> **착수 근거**: 원래 "밀도 신호 뒤" 백로그였으나 **사용자 결정(2026-07-02)으로 지금 착수** — 설계를 먼저
> 이 절로 못 박고(§전략 SNS 규칙 그대로) TDD 구현으로 넘긴다. 구현 핸드오프는 `claude-docs/plans/`의 계획 md.

### 13.0 한눈에 — 이 절이 못 박는 것

| 항목 | 결정 | 확정 |
|---|---|---|
| 공개범위 | **팔로워 한정 단일**(+작성자 본인). `visibility` 컬럼 없이 시작 — 전체공개는 additive 확장 후속 | ✅ 사용자 2026-07-02 |
| 콘텐츠 | **텍스트 카드만**(1~500자, 비공백) + 배경 팔레트(닫힌 코드) + 책 연결(선택) | ✅ 사용자 2026-06-29 |
| 책 연결 | **본인 소유 + PUBLIC 책만** 첨부 가능 — §3.5 불변식("PRIVATE는 타인 표면에 안 샌다") 예외 없이 유지 | ✅ §13.2 |
| 열람 추적 | **`story_view` 테이블** — 미열람 링(기기 무관) + 작성자에게 열람자 목록("누가 봤나") v1 포함 | ✅ 사용자 2026-07-02 |
| 장수 | **여러 장** — 작성자별 묶음 순차 재생. 활성 상한 20장 + 작성 레이트리밋 | ✅ 사용자 2026-07-02 |
| 만료 | `created_at + 24h` **표시 필터**(만료 잡 없음). 데이터 보존, 물리 삭제 배치는 후속 | ✅ 사용자 2026-07-02 |
| 본인 삭제 | 작성자는 자기 스토리 **즉시 삭제** 가능(v1 — 실수 게시 회수 수단) | ✅ |
| 신고 | **기존 사용자 신고(Report) 재사용** — 뷰어에서 작성자 신고, detail에 스토리 원문 발췌 자동 첨부 | ✅ §13.5 |
| 레이트리밋 | `RateLimitAction.STORY_CREATE(10, 1시간)` | ✅ §13.5 |
| 렌더 | 새 섬 아님 — **shared 스토리 컴포넌트**를 dashboard·profile 두 섬에 삽입 | ✅ §13.7 |
| 스키마 | `story`·`story_view` 신규 테이블 2개 — **번호는 머지 직전 확정**(2026-07-02 현재 최신 V55 → V56·V57 예상) | ✅ §13.3 |

### 13.1 v1 범위

**포함**: 작성(문장 + 공개 책 선택 + 배경색) · 홈(`/`) 스트립(내 스토리 + 팔로잉 아바타 링, 미열람 강조·정렬) ·
풀스크린 뷰어(진행바·자동 넘김·좌우 탭·열람 기록) · 책방(`/u/{loginId}`) 아바타 링(그 사람 스토리) ·
본인 스토리의 열람자 수/목록/삭제 · 신고 · 차단·레이트리밋 게이트.

**제외(후속 백로그)**: 전체공개 옵션 · 사진/이미지 스토리(인프라 비용 — 2026-06-29 결정 유지) ·
카드 안 구매링크/책 페이지 링크(풀스크린 탭 UI와 오탭 충돌 + 남의 책 링크 대상 설계 필요 — 표시는 하되 링크는 후속) ·
만료 후 본인 보관함 · 물리 삭제 배치 · 콘텐츠 단위 신고 · 답장/DM.

### 13.2 노출 경계 (가장 위험한 지점 — §5 정신)

**canViewStory(viewer, story)** — id를 직접 받는 모든 진입점에서 강제:

```
1. 작성자 == viewer          → 허용 (단 만료분은 v1에선 본인도 화면 진입점 없음 — 보관함 후속)
2. 차단(existsBetween 양방향) → 404/빈 결과 (존재 누설 금지)
3. viewer가 작성자를 팔로우   → 아니면 미노출
4. 만료(createdAt < now-24h)  → 미노출
5. 불변식: ADMIN 작성자·login_id null 작성자 제외 (FoF 쿼리 노출 불변식과 동일, N-055)
```

- **피드 쿼리** = 기존 `followScopePopularity` theta 조인 패턴 미러:
  `select s from Story s, Follow f where f.followee = s.user and f.follower = :viewer and s.createdAt >= :cutoff`
  (+ ADMIN·login_id null 제외). **차단 필터는 쿼리에 없어도 안전** — "팔로우 존재 → 차단 없음" 불변식
  (차단 시 팔로우 양방향 해제 + 차단 중 팔로우 생성 불가, §7.5)이 write 시점에 보장. 이 불변식을
  **회귀 테스트로 못 박는다**("차단하면 피드에서 사라진다"). 단 id 직접 진입점(열람 기록 POST)은
  차단·팔로우·만료 **전체 게이트 재검사**(IDOR·stale id 방어).
- **비공개 책 간접 누출 차단**: 스토리에 붙일 수 있는 책 = **본인 소유 + PUBLIC만**.
  - 근거: 책 라벨(《제목》·표지)이 팔로워에게 보이므로, PRIVATE 책 첨부를 허용하면 비공개 책장이 새는
    유일 경로가 된다. §3.5 불변식을 예외 없이 유지하는 쪽이 구현·테스트 모두 단순.
  - 작성 UI는 공개 책만 목록에 올리고 "비공개 책은 책장에서 공개로 바꾸면 붙일 수 있어요" 안내 한 줄.
  - 대안 기각 기록: ① "첨부 = 그 문맥 공개 동의로 간주" — 불변식이 둘로 갈라져 테스트·리뷰 부담 증가, 기각.
    ② "첨부 시 자동 PUBLIC 전환" — 숨은 부수효과(책장 상태 변경)라 기각. 명시 전환 UI가 생기면 후속 재검토.
  - **첨부 후 책이 PRIVATE로 바뀌면**: 스토리는 라이브 참조라 **표시 시점에 `book.visibility` 재검사** —
    비공개면 책 라벨만 숨기고 문장은 유지(작성자 본인 뷰 포함 동일 규칙 — 단순·일관). "한 번 새면 회수 불가"를
    표시 시점 게이트로 보완하는 셈.
- **DTO 화이트리스트(§3.4 동일)**: 피드·열람자 응답에 노출하는 것 = loginId·nickname·profileCharacterCode·
  스토리(text·bgCode·책 라벨(제목/표지)·createdAt·viewed)뿐. 이메일·타이머 내부값 등은 설계적 차단.
- 책방 링 노출 조건: **팔로워/본인만**. 비팔로워 방문자에겐 링 자체 미표시 — `of/{loginId}` API는 비팔로워에
  **200 + 빈 배열**(스토리 유무 정보도 안 샘. 404는 차단·ADMIN·미존재 전용 — 프로필 가드와 일관).

### 13.3 스키마 (가안 — 번호는 머지 직전 확정, 멀티세션 충돌 회피)

```sql
-- V56__story.sql (가안)
create table story (
    id          bigint       not null auto_increment,
    user_id     bigint       not null,             -- 작성자
    book_id     bigint       null,                 -- 선택 첨부(본인 소유+PUBLIC만, §13.2)
    text        varchar(500) not null,
    bg_code     varchar(20)  null,                 -- 배경 팔레트 닫힌 코드(자유 hex 금지 — 스타일 주입 차단)
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    constraint fk_story_user foreign key (user_id) references users (id),
    constraint fk_story_book foreign key (book_id) references book (id)
);
create index ix_story_user_created on story (user_id, created_at);

-- V57__story_view.sql (가안)
create table story_view (
    id          bigint      not null auto_increment,
    story_id    bigint      not null,
    viewer_id   bigint      not null,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    constraint uk_story_view unique (story_id, viewer_id),   -- 열람 기록 멱등
    constraint fk_story_view_story  foreign key (story_id)  references story (id),
    constraint fk_story_view_viewer foreign key (viewer_id) references users (id)
);
create index ix_story_view_viewer on story_view (viewer_id);
```

- 관례 준수: varchar 코드 저장·datetime(6)·`fk_`/`ix_`/`uk_` 네이밍(V1~). 엔티티는 `BaseTimeEntity` 상속(createdAt=만료 기준).
- **FK 자식 정리(T-023·T-029 재발 방지)** — 세 경로 모두 실 H2 통합 테스트로 못 박는다:
  1. **회원 탈퇴**(`AccountService.purge`): [내가 남긴 story_view] → [내 스토리에 달린 story_view] → [내 스토리] 순으로
     삭제를 **기존 book 삭제보다 앞에** 끼운다(story.book_id가 book을 참조하므로 book이 먼저 지워지면 FK 위반).
  2. **책 삭제**(`BookService.delete`): `reading_session.unlinkBook` 패턴 미러 — `story.book_id`를 null로 풀고 책 삭제(스토리는 유지).
  3. **스토리 삭제**(본인 삭제): story_view 먼저 삭제 후 스토리 삭제.
- 메인 테스트 스키마는 ddl-auto(엔티티 파생), 마이그레이션 자체는 `FlywayMigrationTest`가 H2로 검증(기존 관례).

### 13.4 API (전부 로그인 필수 — default-deny. record DTO + `Principal`+`CurrentUserService` + 404 통일 관례)

| 메서드 | 경로 | 역할 | 게이트 |
|---|---|---|---|
| GET | `/api/stories/feed` | 홈 스트립: 내 활성 스토리 + 팔로잉 작성자별 그룹 | 피드 쿼리 자체가 게이트(§13.2) |
| GET | `/api/stories/of/{loginId}` | 책방: 그 사람 활성 스토리 | 소셜 가시성 가드(ADMIN·차단·미존재→404) + 비팔로워 빈 배열 + 본인 허용 |
| POST | `/api/stories` | 작성 `{text, bookId?, bgCode?}` | 검증(1~500자 비공백·본인 소유+PUBLIC 책·팔레트 코드) + `STORY_CREATE` 레이트리밋 + 활성 상한 20 |
| DELETE | `/api/stories/{id}` | 본인 삭제 | 소유자 아니면 404 (IDOR) |
| POST | `/api/stories/{id}/view` | 열람 기록(멱등 — `uk_story_view`) | canViewStory 전체 재검사. **본인 열람은 기록 안 함**(인스타 동일) |
| GET | `/api/stories/{id}/viewers` | 열람자 목록 | **작성자 본인만**(아니면 404). 차단 관계 열람자는 목록에서 제외 |

- **개별 스토리 GET 없음** — 본문(≤500자)이 피드 응답에 통째로 실리므로 상세 조회가 불필요, 노출 경계 진입점 최소화.
- 소셜 가시성 가드는 `ProfileService.resolveVisibleTarget`(현재 private)을 **공용으로 승격해 재사용** —
  "프로필 조회·드릴다운이 동일 보장 공유(분기 금지)" 주석의 정신 그대로, 스토리도 같은 한 곳을 탄다.
- 피드 응답 형태(가안): `{ mine: AuthorStories|null, groups: AuthorStories[] }`,
  `AuthorStories = { loginId, nickname, profileCharacterCode, allViewed, stories: StoryCard[] }`,
  `StoryCard = { id, text, bgCode, bookTitle?, bookCoverUrl?, createdAt, viewed }`.
  정렬: 내 스토리 맨 앞 고정 → 미열람 있는 작성자(최신 스토리 desc) → 전부 열람한 작성자. 그룹 내부는 작성순 asc(인스타 동일).

### 13.5 모더레이션

- **신고 = 기존 Report 재사용**: 뷰어의 신고 버튼 → 기존 신고 모달(작성자를 신고), detail 앞에
  `[스토리#{id}] {원문 발췌 ~200자}`를 자동 첨부(전체 500자 내 절삭). 만료 후에도 행이 보존되므로(§13.6)
  운영자는 관리자 신고함에서 id로 원문 대조 가능. 한계 = `(reporter, reported)` 쌍당 1건 유니크라 같은
  작성자 재신고 불가 — 기존 사용자 신고와 동일한 기존 제약. 콘텐츠 단위 신고가 필요해지면 별도
  `story_report` CREATE-only 테이블로 후속(기존 유니크 재정의는 MySQL/H2 DROP 구문 갈림 함정 — V26 주석 참조).
- **차단**: 차단 시 팔로우 양방향 해제(기존 동작) → 피드에서 자동 소멸. `of/{loginId}`·열람자 목록도 `existsBetween` 게이트.
- **레이트리밋**: `RateLimitAction.STORY_CREATE(10, Duration.ofHours(1))`. 초과 시 **안내 응답**(무음 드롭 아님 —
  프론트가 "잠시 후 다시 올려주세요" 안내). FOLLOW의 무음 드롭과 다른 결정인 근거: 팔로우는 멱등 토글이라
  무음이 무해하지만, 작성은 콘텐츠 소실이라 사용자가 원인을 알아야 한다.
- **활성 상한 20장**: 활성(미만료) 스토리 20장 도달 시 작성 거부 + 안내 — 뷰어 진행바 UI 보호 + 도배 방지.
  (레이트리밋만으론 24h 내 이론상 240장 가능 — 상한이 뷰어 경험을 지킨다.)

### 13.6 만료·데이터 수명

- **만료 = 표시 필터**: `cutoff = clock.instant() - 24h` (Clock 주입, **절대시간이라 사용자 타임존·자정 경계 무관**).
  만료 잡·스케줄러 없음.
- **보존**: 만료 후에도 DB 보존(신고 대응 원문 근거 + 후속 보관함 여지). 텍스트 500자뿐이라 용량 부담 미미.
  물리 삭제 배치는 용량·개인정보 삭제 요구가 생기면 후속 도입. (사용자 확정 2026-07-02)
- **본인 삭제 = 즉시 물리 삭제**(story_view 포함). 실수 게시 회수 수단 — 24h 강제 노출 방지.

### 13.7 프론트 — shared 컴포넌트 (새 섬 아님)

- plan.md 초안의 "새 Vue 섬 `frontend/src/story/`"를 **정정**: 스트립은 홈·책방 **기존 섬 내부 상단**에 들어가야
  하므로 별도 섬(별도 마운트 포인트·엔트리)이 아니라 **`frontend/src/shared/story/` 공용 컴포넌트**로 만들어
  dashboard 섬(홈 스트립+작성 진입)·profile 섬(아바타 링)에 삽입한다. `vite.config.ts` 무변경(새 엔트리 없음),
  **dashboard·profile 두 섬 재빌드** 필요(`require-bundle-build.ps1` 훅이 하드 강제, T-063).
- 구성(가안): `StoryStrip.vue`(아바타 링 가로 스트립 — 미열람 링 강조는 서버 `allViewed`/`viewed` 기준, 기기 무관) ·
  `StoryViewer.vue`(풀스크린 오버레이 — 장당 ~5초 진행바·자동 넘김·좌우 탭·Esc/배경 탭 닫기, 카드 표시 시점에
  열람 POST(멱등), 본인 스토리엔 열람자 수/목록/삭제) · `StoryComposer.vue`(작성 모달 — 500자 카운터·공개 책
  드롭다운·배경 팔레트 스와치) · `storyApi.ts`(fetch 래퍼 — `shared/follow.ts`의 `getCsrfToken()` 재사용).
- 오버레이 전례 재사용: 마을 도감(z-index 50)·신고 모달(z-index 200) 패턴. 아바타 = `profileCharacterCode` 기존 렌더 재사용.
- ⚠️ **머지 전 실 브라우저 1회 게이트**(CLAUDE.md 프론트 검증 규칙) — 자동 넘김 타이머·오버레이·열람 POST 타이밍은
  헤드리스로 가짜 green이 나기 쉬운 클래스(T-053).

### 13.8 TDD로 못 박을 경계 케이스 (Red→Green 필수 — 각 단계 실패 확인 후 구현)

- **노출 게이트**: 팔로워→보임 / 비팔로워→안 보임 / 본인→보임 / 차단(양방향)→404·피드 소멸 /
  만료 정확히 24h→제외·24h−1s→포함(Clock.fixed 경계) / ADMIN·login_id null 작성자 피드 제외(N-055) /
  비팔로워 `of/{loginId}`→빈 배열(404 아님)
- **작성**: 501자·공백만 거부 / PRIVATE 책·남의 책 첨부 거부 / 팔레트 외 bgCode 거부 / 활성 20장 도달 시 거부 /
  레이트리밋 초과 시 안내 응답
- **책 라벨 재검사**: 첨부 후 책 PRIVATE 전환 → 피드에서 책 라벨만 숨고 문장 유지
- **삭제**: 본인 OK(view 자식 있어도 FK 위반 없음 — **실 H2**) / 타인 404(IDOR)
- **열람**: 두 번 POST→1행(멱등) / 본인 열람 기록 안 됨 / 비팔로워·만료·차단 POST→404 /
  열람자 목록 작성자만·차단 관계 제외
- **FK 통합(실 H2, T-023·T-029)**: 스토리+열람 픽스처 가진 회원 탈퇴 성공(flush) / 스토리 붙은 책 삭제 성공+`book_id` null화
- **피드 조립**: 그룹핑·정렬(미열람 우선)·`allViewed` 계산·내 스토리 분리
- **마이그레이션**: `FlywayMigrationTest`로 신규 V 2건 H2 적용 검증

### 13.9 열린 질문 해소 기록 (plan.md 2026-06-29 → 2026-07-02 사용자 확정)

| 열린 질문 | 결정 |
|---|---|
| 공개범위 — 팔로워 한정 단일 vs 전체공개 허용 | **팔로워 한정 단일**(컬럼 없음, 확장은 additive 후속) |
| 조회 표시("누가 봤나") v1 포함? | **포함** — 미열람 링이 어차피 `story_view`를 요구, 열람자 목록은 그 위에 거의 공짜 + 작성자 리텐션 후크 |
| 여러 장 vs 1장 | **여러 장** — 스키마 비용 0(1행=1장), 활성 상한 20 + 레이트리밋으로 남용 방지 |
| 만료 스토리 물리 삭제 / 미열람 추적 위치 | **보존 + 표시 필터**(물리 삭제 배치는 후속) / 미열람 추적 = `story_view`(서버, 기기 무관) |

**남은 후속 백로그**: 전체공개 옵션 · 사진 스토리 · 카드 내 구매링크/책 링크 · 만료 후 본인 보관함 ·
물리 삭제 배치 · 콘텐츠 단위 신고(`story_report`) · 답장/DM 없음 유지 여부.
