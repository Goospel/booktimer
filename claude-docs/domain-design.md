# 도메인 설계 — 엔티티 구조

> 핵심 도메인 규칙은 [README.md](../README.md) 6~7번, 학습 노트는 [learning-notes.md](learning-notes.md) 참고.
> 이 문서는 엔티티/관계/필드의 **설계 결정**을 기록한다. 구현은 TDD(테스트 먼저)로 진행.

## 확정된 설계 결정

| 결정 | 선택 | 비고 |
|---|---|---|
| 누적 상태 위치 | **별도 엔티티 `ReadingTimer` (User와 1:1)** | 관심사 분리, 누적 로직 단위테스트 용이 |
| 설정값(증가값/cap) 위치 | **`ReadingTimer`에 함께** | 지금 단계 최소 구조. 추후 필요 시 분리 |
| 시간 저장 단위 | **초(seconds, `long`)** | DB·계산 단순, 경계 테스트 명확 |

---

## 엔티티

### 1. `User` — 회원/계정

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | auto |
| email | `String`, unique, not null | 로그인 ID |
| passwordHash | `String`, not null | 이미 해시된 비밀번호(BCrypt). 평문 저장 금지 |
| nickname | `String` | 표시명 (추후 SNS) |
| timezone | `String` (IANA, 예 `Asia/Seoul`) | Lazy 누적의 "오늘" 기준 |
| role | `enum Role { USER, ADMIN }` | 권한 |
| createdAt / updatedAt | auditing | `@CreatedDate` / `@LastModifiedDate` |

### 2. `ReadingTimer` — 하루 목표 (User와 1:1)

> ⚠️ **2026-06-07 모델 전환(PR #217 → #218):** 부채를 **7일 윈도우 per-day 유도 모델**로 바꾸면서
> 이 엔티티의 누적 상태가 사라졌다. 아래 *원본* 표의 `remainingSeconds`/`capSeconds`/`lastAccrualDate`는
> PR #218(V20 마이그레이션)에서 **DROP**됐고, 부채는 이제 `reading_session`에서 유도한다(저장 안 함).
> 현재 `ReadingTimer`의 유일한 상태는 **하루 목표**(`dailyIncrementSeconds` — 이름만 유지, 의미는 "목표")다.
> 배경·근거: [learning-notes.md](learning-notes.md) **N-058**(전환)·**N-001**(옛 모델).

**현재 (PR #218 이후):**

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | |
| user | `@OneToOne User` (FK, unique) | |
| dailyIncrementSeconds | `long` | 하루 목표(초), 기본 `3600`(1h), 사용자 설정 |
| createdAt / updatedAt | auditing | |

<details><summary>옛 모델 (PR #217 이전 — 근거 보존)</summary>

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | |
| user | `@OneToOne User` (FK, unique) | |
| remainingSeconds | `long` | 현재 갚아야 할 누적 잔여(부채) |
| dailyIncrementSeconds | `long` | 증가값, 기본 `3600`(1h), 사용자 설정 |
| capSeconds | `long` | 누적 상한, 예 `18000`(5h) |
| lastAccrualDate | `LocalDate` | 마지막 Lazy 계산 기준일(유저 TZ) |
| createdAt / updatedAt | auditing | |

</details>

### 3. `ReadingSession` — 측정 기록 (User와 N:1)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | |
| user | `@ManyToOne User` (FK) | |
| startedAt / endedAt | `Instant` | 타이머 start/stop |
| durationSeconds | `long` | stop 시 계산. (옛 모델은 remaining에서 차감했으나, 지금은 날짜별로 합산돼 7일 윈도우 부채를 유도 — PR #217) |
| book | `@ManyToOne Book` (nullable) | **추후** 책 단위 기록 확장점 |

---

## 관계 다이어그램

```
User 1 ──── 1 ReadingTimer      (누적 상태 + 설정)
User 1 ──── N ReadingSession    (개별 측정 기록)
ReadingSession N ── 1 Book?     (추후: 어떤 책을 읽었는지)
```

---

## ~~Lazy 누적 로직 위치 (N-001)~~ → 7일 윈도우 유도 부채로 대체 (PR #217/#218)

> ⚠️ 아래는 **옛 단일-카운터 모델**의 설계다. 부채를 유도 모델로 바꾸며 `AccrualCalculator`(순수 계산부)와
> `ReadingTimer.accrueUntil`(도메인 메서드)은 **제거**됐다(PR #218). 지금의 순수 계산부는
> `WeeklyDebtCalculator`(입력: `(날짜→읽은 초 맵, 하루목표, 오늘)` → 출력: `WeeklyDebt`), 얇은 서비스는
> `ReadingDebtService`다. 경계 테스트(세션 0 / 오늘 부분충족 / 목표 초과 / 윈도우 경계 / 자정 TZ)는 그대로 유효.
> 근거: [learning-notes.md](learning-notes.md) **N-058**.

<details><summary>옛 설계 (근거 보존)</summary>

- `ReadingTimer.accrue(LocalDate today)` 같은 **도메인 메서드**로 캡슐화.
- 순수 계산부는 DB 무관한 정적/도메인 로직으로 분리해 빠른 단위테스트:
  - 입력: `(remaining, increment, cap, daysElapsed)` → 출력: 새 `remaining`
  - 경계: 0일 / 1일 / N일 경과, cap 초과 클램프, 음수 방지

</details>

---

## 추후 확장 (지금 구현 안 함)

- `Book` 엔티티 (제목/저자) + `ReadingSession.book` 연결
- OAuth 계정 연동 테이블
- SNS: 팔로우, 공유 등 → 엔티티/스키마 설계는 [sns-design.md](sns-design.md) (follow 테이블·users.visibility·canView 게이트)
