# BookTimer 📚⏱️

> 매일 일정 시간 책을 읽도록 독려하고 기록하는 **독서 타이머** 웹 애플리케이션
>
> 🌐 **Live**: https://booktimer.click

---

## 1. 프로젝트 개요

BookTimer는 사용자가 하루에 일정 시간 책을 읽도록 **독려**하고 그 기록을 **누적**하는 서비스다.

단순한 스톱워치가 아니라, **읽지 않은 시간이 다음 날로 누적되는** 타이머 메커니즘을 통해 "오늘 안 읽으면 내일 더 읽어야 한다"는 부담을 만들어 꾸준한 독서 습관을 유도한다.

**웹을 우선**으로 구현했고, 이후 앱(모바일)으로 확장할 계획이다.

**현재 상태**: 핵심 기능 구현·배포·운영 중. 누적 타이머 + 인증(아이디·소셜) + 책 단위 기록·책장 + 일자별 기록·잔디 + 소셜(공개 프로필·검색·팔로우·차단·신고) + 제휴 구매 링크 + 운영자 대시보드까지 출하했다. HTTPS·Google 소셜 로그인·무중단 롤링 배포 운영 중.

---

## 2. 핵심 기능

### 2.1 누적 증가 타이머 (핵심)

- 기본 기능은 **타이머**다.
- 매일 타이머의 목표 시간이 **증가값만큼 자동으로 증가**한다. (기본 1시간, **사용자 설정 가능**)
- 그날 채우지 못한 잔여 시간은 **다음 날로 이월**되어 누적된다.
- 단, 무한정 쌓여 좌절·이탈하지 않도록 **누적 상한(cap)** 을 둔다. (예: 5시간)

#### 동작 예시 (증가값 = 1시간 가정)

| 일자 | 시작 시 누적 목표 | 그날 소요 | 그날 종료 시 잔여 |
|---|---|---|---|
| 1일차 | 1시간 | 30분 읽음 | **30분 남음** |
| 2일차 | 30분 + 1시간 = **1시간 30분** | 0분 | **1시간 30분 남음** |
| 3일차 | 1시간 30분 + 1시간 = **2시간 30분** | … | … |

> 매일 +증가값이 더해지고, 읽은 만큼만 차감된다. 안 읽으면 부채처럼 쌓이되, cap을 넘지는 않는다.

### 2.2 인증 / 식별 모델

식별·인증·표시 역할을 **세 값으로 분리**한다 (인스타·X의 @handle 모델).

| 값 | 역할 | 공개 | 가변 |
|---|---|---|---|
| **로그인 아이디 (`login_id`)** | 로그인 + 내부 식별 + **공개 @핸들**(검색·프로필 URL) | 공개 | **불변** |
| **닉네임 (nickname)** | 화면 표시 이름 | 공개 | 자유 변경 (중복 허용) |
| **이메일 (email)** | 연락·복구·OAuth 계정 연결 | **비공개** | (정책 별도) |

- **로그인은 아이디(login_id)로** 한다 — 공개된 이메일을 로그인 표적에서 분리한 보안 결정. 이메일로는 로그인할 수 없다.
- **Google 소셜 로그인**(OIDC) — `email_verified`만 신뢰하는 find-or-create 프로비저닝. 소셜 계정은 비밀번호가 없어 관련 UX를 분기.
- **온보딩** — 첫 진입 시 아이디(소셜 가입자)·표시 이름·타이머 초기값을 한 번에 설정.
- 계정 보안: 비밀번호 변경 / 회원 탈퇴, 로그인 무차별 대입 방어(IP 잠금).
- 설계: [claude-docs/login-id-design.md](claude-docs/login-id-design.md)

### 2.3 책 단위 기록 / 책장

- **책 등록** + 책별 누적 독서 시간 추적. **측정은 반드시 책을 지정**해야 한다(어떤 책을 얼마나 읽었는지 명확화).
- 외부 도서 검색(알라딘 API) 연동 — 제목으로 검색해 등록.
- **책장 상태**: 읽고싶음 / 읽는 중 / 완독. 책별 **공개 여부**(PUBLIC/PRIVATE) 설정.
- 책 상세에서 **제휴(어필리에이트) 구매 링크** 제공(클릭 추적).

### 2.4 독서 기록 / 잔디

- 일자별 독서 시간 측정·저장, 누적 / 잔여 시간 추적.
- 그날 **읽은 책 제목**과 총 시간을 일자별로 표시.
- **잔디(기여 그래프)** — 날짜별 독서량을 시각화. 공개 프로필에 노출.

### 2.5 소셜 (SNS)

- **공개 프로필** `/u/{login_id}` — 잔디·공개 책장 등.
- **사용자 검색** — 아이디(@핸들) 기준.
- **팔로우 / 언팔로우**, **차단(block)**, **신고(report)**(쓰기 남용 방지 rate limiting).
- **팔로우 범위 인기 카운트 + drill-down** — "그 책을 원함/읽음인 내 팔로우 명단"을 같은 게이트(팔로우·PUBLIC·distinct)로 펼침(새 노출 0, IDOR 없음).
- 공개범위·관계·IDOR·스키마 설계: [claude-docs/sns-design.md](claude-docs/sns-design.md)

### 2.6 운영자 대시보드

- `/admin` (ROLE_ADMIN 전용) — ENV 시드(`BOOKTIMER_ADMIN_LOGIN_IDS`)로 부트스트랩 승격.
- **운영 통계 요약** — 가입자 수·온보딩 완료·최근 7일 활성·총 책/세션·평균 독서 시간(읽기 전용 집계).
- **데이터 조회** — 사용자 목록(검색·페이징) + 드릴다운(타이머 설정·최근 세션·책장 요약). **PII 최소 노출**(비밀번호 해시 미전송, 이메일 마스킹).
- 운영자는 검색·공개 프로필에서 숨겨져 일반 사용자 동선과 분리.
- 설계: [claude-docs/admin-data-lookup-design.md](claude-docs/admin-data-lookup-design.md)

### 2.7 (설계 단계 — 미구현) 독서 성향 분석 · 구독

- **책장 기반 성향 분석("독서 MBTI")** — 사실 집계는 코드, 해석·서술만 LLM. 설계: [claude-docs/reading-personality-design.md](claude-docs/reading-personality-design.md)
- 성향·추천·채팅을 묶은 **월 정액 구독** 비즈니스 모델 구상(plan.md). **구현 전 설계 합의 필수.**

---

## 3. 기술 스택

| 영역 | 기술 | 비고 |
|---|---|---|
| **프론트엔드** | Thymeleaf (SSR) | 웹 우선. SPA 전환은 보류(API 계약·SEO 판단) |
| **백엔드** | Spring Boot 4.0.6 (Java 21) | 빌드 툴 Gradle (Groovy DSL) |
| **인증** | Spring Security | 아이디+비밀번호 자체 인증 + Google OAuth2/OIDC, 세션 외부화(JDBC) |
| **DB / 마이그레이션** | MySQL + JPA(Hibernate), **Flyway** | 운영 MySQL, 테스트는 H2 인메모리. 스키마는 Flyway가 단일 소스 |
| **클라우드** | AWS (ECS Fargate, ALB, RDS, ACM, Route 53) | Docker 컨테이너 배포, ALB TLS termination, 무중단 롤링 배포 |
| **CI/CD** | GitHub Actions | 빌드 / 테스트 / OIDC 키리스 배포 자동화 |

---

## 4. 로드맵

- [x] **MVP** — 누적 증가 타이머(사용자 설정 증가값 + cap) + 일자별 독서 시간 기록
- [x] AWS + Docker 배포 파이프라인 (GitHub Actions, OIDC 키리스)
- [x] HTTPS 적용 (도메인 + ALB TLS termination)
- [x] 세션 외부화(Spring Session JDBC) + 무중단 롤링 배포
- [x] OAuth 소셜 로그인 (Google)
- [x] **로그인 아이디(공개 @핸들) 도입** — 이메일을 로그인 식별자에서 분리
- [x] **책 단위 기록** — 책 등록(알라딘 검색) + 책별 누적 시간 + 책장 상태/공개여부
- [x] **잔디(기여 그래프)** + 일자별 읽은 책 표시
- [x] **소셜** — 공개 프로필·검색·팔로우·차단·신고·팔로우 범위 인기 drill-down
- [x] **제휴 구매 링크**(알라딘 어필리에이트)
- [x] **운영자 대시보드** — 통계 요약 + 데이터 조회
- [ ] 카카오/네이버 등 추가 provider (선택)
- [ ] 독서 성향 분석("독서 MBTI") + 구독 비즈니스 모델 (설계 완료, 구현 전 합의 필요)
- [ ] 프론트엔드 프레임워크 교체 (SSR-가능 프레임워크 검토 — 보류)
- [ ] 앱(모바일) 확장

---

## 5. 아키텍처

```
[ 사용자 브라우저 ]
        │  HTTPS
        ▼
[ AWS ALB ]  ── TLS termination (ACM 인증서), HTTP→HTTPS 리다이렉트
        │  HTTP (내부)
        ▼
[ ECS Fargate : Spring Boot ]
        │   ├─ Thymeleaf 뷰 (SSR)
        │   ├─ Spring Security (아이디/비밀번호 + Google OIDC)
        │   └─ Spring Session (세션을 RDS에 외부화 → 무상태 앱 서버)
        ▼
[ JPA / Hibernate ]   ── 스키마는 Flyway 마이그레이션이 단일 소스
        ▼
[ RDS (MySQL) ]

도메인/DNS: Route 53 (booktimer.click)
CI/CD: GitHub Actions → Docker 이미지 → ECS 롤링 배포 (start-then-stop, 자동 롤백)
```

> 인프라 상세(보안 그룹, IAM, 파라미터 등)는 `deploy/` 및 [claude-docs/deploy-aws.md](claude-docs/deploy-aws.md) 참고.

---

## 6. 핵심 도메인 규칙 요약

| 규칙 | 결정 |
|---|---|
| **증가값(daily increment)** | 매일 목표에 더해지는 양. **사용자 설정**, 기본 1시간 |
| **잔여 시간(carry-over)** | 그날 못 채운 시간은 소멸하지 않고 다음 날 목표에 누적 |
| **누적 상한(cap)** | **누적 잔여 총합**에 cap (예: 5시간). 이월 총량이 cap을 넘지 않음 → "따라잡기 불가" 좌절 방지 |
| **일일 리셋 시점** | **자정 00:00, 사용자 타임존 기준**. 이 시점에 +증가값 및 이월 처리 |
| **리셋 계산 방식** | **Lazy 계산** — 사용자 접속 시 `마지막 계산일 ~ 오늘`의 경과 일수만큼 누적·cap 적용. 배치 스케줄러 없음 |
| **식별/인증 분리** | 로그인·식별·공개 핸들 = `login_id`(불변·유니크), 표시 = nickname(가변·중복허용), 이메일 = 비공개 속성 |
| **측정엔 책 필수** | 측정 세션은 반드시 책을 지정(어떤 책을 읽었는지 명확). 레거시 null-book 세션은 읽기·집계만 보존 |
| **공개 경계** | 책 공개여부(PUBLIC/PRIVATE) + 차단 관계로 조회 주체를 게이트(IDOR 방지) |
| **타이머 신뢰성** | 부정 사용(켜두고 안 읽기) 검증 안 함. 자기 양심 기반 |

---

## 7. Lazy 누적 계산 로직 (의사 코드)

사용자가 타이머 화면에 진입할 때마다 호출:

```
경과일수 = (오늘_날짜(사용자TZ) - 마지막계산일) in days

if 경과일수 > 0:
    목표 += 경과일수 × 증가값          // 안 들어온 날도 소급 누적
    목표 = min(목표, cap)             // 누적 잔여 총합 상한
    마지막계산일 = 오늘_날짜
// 그날 읽은 만큼 목표에서 차감 (별도 측정 세션에서 처리)
```

> 핵심: 접속 안 한 날도 경과일수로 소급되며, 그 결과는 항상 cap 이하로 클램프된다.

---

## 8. 빌드 / 실행

```bash
./gradlew build          # 빌드 (Windows: gradlew.bat)
./gradlew test           # 테스트 (H2 인메모리 — Docker 불필요)
./gradlew bootRun        # 로컬 실행 (MySQL은 compose.yaml로 자동 기동, Docker 필요)
```

- toolchain: Java 21 (로컬에 없어도 foojay-resolver 가 자동 다운로드)
- 테스트는 H2 인메모리로 독립 실행되며, 운영은 MySQL을 사용한다. 스키마는 **Flyway** 마이그레이션이 단일 소스(`src/main/resources/db/migration`).
- 실행 시 Spring Security가 전 엔드포인트를 기본 잠금 — 가입/로그인 후 사용.

---

## 9. 결정 보류 / 추후 확정 필요

- cap 정확한 값 (예시는 5시간) 및 cap 도달 시 UX 표시(경고/안내)
- 아이디(login_id)·이메일 변경 정책(허용 여부·쿨다운)
- 독서 성향 분석의 분석 축·프라이버시·AI 모델, 구독 가격 가설 검증
- 추가 소셜 provider(카카오/네이버) 도입 여부

---

## 10. 관련 문서

- [DEVLOG.md](DEVLOG.md) — 개발 일지 (기능 추가 흐름 / 만난 문제 / 해결)
- [plan.md](plan.md) — 추후 할 일 / 로드맵 / 기술 부채 / 갱신 이력
- [claude-docs/domain-design.md](claude-docs/domain-design.md) — 도메인 모델 설계
- [claude-docs/login-id-design.md](claude-docs/login-id-design.md) — 로그인 아이디(식별/인증 분리) 설계
- [claude-docs/sns-design.md](claude-docs/sns-design.md) — 소셜(공개범위·관계·IDOR·스키마) 설계
- [claude-docs/admin-data-lookup-design.md](claude-docs/admin-data-lookup-design.md) — 운영자 데이터 조회 설계
- [claude-docs/reading-personality-design.md](claude-docs/reading-personality-design.md) — 독서 성향 분석(미구현) 설계
- [claude-docs/deploy-aws.md](claude-docs/deploy-aws.md) — AWS 배포 가이드
- [claude-docs/learning-notes.md](claude-docs/learning-notes.md) — 작업 중 배운 개념 정리
- [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md) — 함정·해결 기록
- [claude-docs/copyright-registration.md](claude-docs/copyright-registration.md) — 한국 저작권 등록 가이드
