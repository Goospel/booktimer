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

### 2. `ReadingTimer` — 누적 상태 (User와 1:1)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | |
| user | `@OneToOne User` (FK, unique) | |
| remainingSeconds | `long` | 현재 갚아야 할 누적 잔여(부채) |
| dailyIncrementSeconds | `long` | 증가값, 기본 `3600`(1h), 사용자 설정 |
| capSeconds | `long` | 누적 상한, 예 `18000`(5h) |
| lastAccrualDate | `LocalDate` | 마지막 Lazy 계산 기준일(유저 TZ) |
| createdAt / updatedAt | auditing | |

### 3. `ReadingSession` — 측정 기록 (User와 N:1)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | `Long` (PK) | |
| user | `@ManyToOne User` (FK) | |
| startedAt / endedAt | `Instant` | 타이머 start/stop |
| durationSeconds | `long` | stop 시 계산, remaining에서 차감 |
| book | `@ManyToOne Book` (nullable) | **추후** 책 단위 기록 확장점 |

---

## 관계 다이어그램

```
User 1 ──── 1 ReadingTimer      (누적 상태 + 설정)
User 1 ──── N ReadingSession    (개별 측정 기록)
ReadingSession N ── 1 Book?     (추후: 어떤 책을 읽었는지)
```

---

## Lazy 누적 로직 위치 (N-001)

- `ReadingTimer.accrue(LocalDate today)` 같은 **도메인 메서드**로 캡슐화.
- 순수 계산부는 DB 무관한 정적/도메인 로직으로 분리해 빠른 단위테스트:
  - 입력: `(remaining, increment, cap, daysElapsed)` → 출력: 새 `remaining`
  - 경계: 0일 / 1일 / N일 경과, cap 초과 클램프, 음수 방지

---

## 추후 확장 (지금 구현 안 함)

- `Book` 엔티티 (제목/저자) + `ReadingSession.book` 연결
- OAuth 계정 연동 테이블
- SNS: 팔로우, 공유 등 → 엔티티/스키마 설계는 [sns-design.md](sns-design.md) (follow 테이블·users.visibility·canView 게이트)
