# BookTimer 개발 일지 (DEVLOG)

> 기능이 **어떤 흐름으로 추가됐고**, 그 과정에서 **어떤 문제를 만나** **어떻게 해결**했는지 시간순으로 정리한 개발 일지.
> 개념 상세는 [learning-notes](claude-docs/learning-notes.md)(N-###), 함정·해결 절차는 [troubleshooting](claude-docs/troubleshooting.md)(T-###) 참고.

개발은 일관된 규율 위에서 진행됐다 — **PR 우선**(main 직접 push 금지, 훅으로 하드 차단), **TDD Red→Green**(실패 테스트를 먼저 짜고 실행으로 실패를 확인한 뒤 최소 구현), 그리고 **막힐 때마다 함정/개념을 즉시 기록**(PKM).

---

## Phase 0 — 기반 다지기 (2026-05-31)

처음부터 "두 번 헤매지 않기"를 목표로 안전장치부터 깔았다.

- **PR 우선 워크플로 하드 차단 훅** — `main`에 직접 커밋을 막는 git 훅.
- **TDD 게이트** — 스테이징에 `.java` 변경이 있으면 커밋 전 `./gradlew test`를 강제 실행.
- **테스트 인프라(H2)** — 운영은 MySQL, 테스트는 H2 인메모리로 분리해 Docker 없이 독립 실행.

이 단계에서 이미 빌드/툴체인 관련 개념을 노트로 남기기 시작했다(N-002, N-003 — foojay toolchain 자동 다운로드 등).

---

## Phase 1 — 핵심 도메인: Lazy 누적 타이머

BookTimer의 정체성인 **"안 읽으면 다음 날로 빚처럼 쌓이는"** 타이머를 가장 먼저, 그리고 순수 도메인 로직으로 구현했다.

- `AccrualCalculator` + `ReadingTimer` — 경과 일수만큼 목표를 누적하고 cap으로 클램프하는 계산.
- **경계값 테스트 먼저**: 0일 경과 / 여러 날 경과 / cap 초과 / 자정 경계.

> 설계 결정: 배치 스케줄러 없이 **Lazy 계산** — 사용자가 접속할 때 `마지막 계산일 ~ 오늘`의 경과일을 소급 적용. 접속 안 한 날도 빠짐없이 누적되고 항상 cap 이하로 클램프된다.

---

## Phase 2 — User 도메인 + JPA 영속성

타이머를 사용자에 묶고 영속화 계층을 세웠다.

- `User` 엔티티 + `Role`, `ReadingTimer ↔ User @OneToOne`.
- `UserRepository` / `ReadingTimerRepository` + `@DataJpaTest` 슬라이스 테스트.
- **JPA Auditing**(`createdAt`/`updatedAt`) 공통 베이스(`BaseTimeEntity`).

**만난 문제**
- Boot 4에서 `@DataJpaTest` 등 테스트 어노테이션의 **패키지 위치가 이동**해 import가 깨짐 → T-006 / N-007로 기록(이후 `@AutoConfigureMockMvc`도 같은 이슈라 T-006 보강).
- JPA Auditing이 동작하려면 `@EnableJpaAuditing` + `@EntityListeners`가 필요 → T-007 / N-008.

---

## Phase 3 — 독서 세션 (측정 → 차감)

실제 "읽은 시간"을 측정해 누적 목표에서 깎는 흐름.

- `ReadingSession` 엔티티(start/end + duration 계산).
- `ReadingTimer.deduct` — 측정량을 누적 잔여에서 차감.
- `ReadingSessionService` — start/stop 오케스트레이션.
- 가입 시 `ReadingTimer`를 함께 부트스트랩(`UserRegistrationService`).
- `ReadingTimerService` — 접속 시 누적(accrue) 적용, **`Clock` 주입**으로 시간을 테스트 가능하게.

**핵심 개념 기록**
- 계층별 테스트 전략(N-009), 테스트 가능한 시간 — `Clock` 주입(N-010).
- 가입→start/stop→차감 happy path를 `@SpringBootTest` 통합 테스트로 한 번 더 묶어 검증.

---

## Phase 4 — 인증 & 웹 화면

Spring Security로 로그인을 붙이고 첫 화면을 띄웠다.

- `UserDetailsService` — 도메인 `User`를 Security 인증 주체로 매핑.
- `SecurityConfig` — formLogin + BCrypt + 인가 규칙.
- 회원가입 시 **평문 비밀번호를 BCrypt로 해싱**해 저장.
- 회원가입 폼/컨트롤러, **대시보드(GET /)** — 접속 시 누적 적용 + 잔여/진행 세션 표시, 세션 start/stop 버튼.

**만난 문제**
- 리다이렉트 응답 단언에서 헛짚음 → T-008.
- **인증 주체 ≠ 도메인 엔티티**라는 구분, 그리고 "접속"을 Lazy 누적 트리거로 삼는 설계를 N-011 / N-012로 정리.

---

## Phase 5 — 배포 파이프라인 (AWS + CI/CD)

로컬에서 도는 앱을 클라우드로 올렸다.

- **컨테이너화 + 운영 프로필**(배포 Phase 0) — 운영 설정 외부화(N-013).
- **AWS ECS Fargate** 태스크 정의 + 배포 가이드(Phase 1).
- **GitHub Actions CI/CD**(Phase 2) — 테스트 → ECR 푸시 → ECS 롤링 배포. AWS **키리스(OIDC) 배포**(N-015).

**만난 문제 (인프라 디버깅)**
- ECS Fargate 기동/롤링 배포에서 막힘 → T-009 / T-010 / N-016.
- **태스크가 SSM에 도달 못 함** — 서브넷에 IGW 라우트/네트워킹 경로가 없어서. 원인과 해결을 T-011 / N-018로 기록.
- (문서 운영) 문서만 바뀐 push에서 배포가 도는 낭비 → `paths-ignore`로 필터(N-020).

---

## Phase 6 — UI/UX 폴리시

배포 뼈대 위에 실사용 가능한 화면을 입혔다.

- **Alpine.js 실시간 째깍 타이머**(UI 다리 1-A) — 화면에서 시간이 흐르는 체감.
- **htmx 부분 갱신 start/stop**(1-B) — 전체 리로드 없이 라이브 영역만 갱신.
- 설정 페이지(닉네임/타임존/증가값/cap), 커스텀 로그인 페이지(회원가입 링크/안내).
- 일자별 독서 기록 조회 화면(README §2.2).
- 타임존 입력을 자유 텍스트 → **드롭다운(select)** 으로 전환(검증 단순화).
- MVP 마감 폴리시 — **cap 도달 배지** + 친절한 에러 페이지.

**만난 문제**
- 가입 시 **중복 이메일**이 500으로 터짐 → 친절한 필드 에러로 전환(T-012/T-013, N-019).
- 가입 **당일치 증가값 누락** — 타이머를 0이 아니라 증가값으로 시드하도록 수정.

---

## Phase 7 — 계정 보안

- 비밀번호 변경 + 회원 탈퇴(`AccountService`). 둘 다 TDD로.

---

## Phase 8 — HTTPS 적용 (ALB TLS termination)

"지금 HTTP인데 안전하지 않잖아?"라는 의문에서 출발해 프로덕션에 HTTPS를 붙였다.

- 도메인 확보(**booktimer.click**, Route 53) → ACM 인증서(DNS 검증) → ALB **443 리스너** → **HTTP→HTTPS 301 리다이렉트** → Route 53 alias.
- 개념: **TLS termination** — 공개 구간은 HTTPS, ALB 뒤 내부 구간은 HTTP(N-021).

**만난 문제**
- 무료 플랜에서 도메인 등록이 막혀 유료로 전환.
- 인증서 발급/DNS 전파 대기에서 `dig` 부재 → `dns.google/resolve`로 우회.
- **프록시 뒤 https 인식 실패** — `server.forward-headers-strategy=framework` 프로퍼티가 **Boot 4에서 무동작**. `ForwardedHeaderFilter`를 **명시 빈**으로 등록해 해결. MockMvc로는 필터가 안 걸려 `@SpringBootTest(RANDOM_PORT)` + `HttpClient`로 검증(T-014 / N-022).
- 세션 쿠키 `Secure`/`HttpOnly`를 prod 전용으로 추가.

---

## Phase 9 — OAuth 소셜 로그인 (Google OIDC)

폼 로그인과 소셜 로그인을 한 화면에 통합했다.

- `AuthProvider`, `OAuthUserProvisioningService`(find-or-create), `BookTimerOidcUserService`.
- principal name을 **email로 통일**해 폼/소셜 사용자를 동일하게 취급.
- 소셜 계정 UX 분기 — 비밀번호 변경 카드/탈퇴 시 비번 입력을 **숨김**(`th:if="${localAccount}"`).
- ECS 태스크 정의 secrets에 Google 자격증명 연결(파라미터 스토어 경유).

---

## Phase 10 — 프로덕션 500 디버깅 & 기술 부채 인식

배포 직후 Google 로그인에서 **500**이 터졌다.

- **증상**: 소셜 로그인 시 `Column 'password_hash' cannot be null`.
- **근본 원인**: `ddl-auto=update`는 **컬럼을 추가만 할 뿐 기존 컬럼의 NOT NULL 제약을 풀지 못한다**. 소셜 사용자는 비밀번호가 없어 null로 저장되는데, 운영 DB의 `password_hash`가 여전히 NOT NULL → INSERT 실패. (= 스키마 드리프트)
- **접속 함정**: 사설 RDS(퍼블릭 접근 불가, NAT 없음)라 CloudShell/수동으로 DB에 붙어 직접 `ALTER`를 칠 수 없었음.
- **해결**: prod 프로필 `ApplicationRunner`(`PasswordHashNullableSchemaFix`)가 기동 시 `information_schema`를 확인하고 필요할 때만 **멱등 `ALTER`**로 컬럼을 NULL 허용으로 보정. 배포 후 Google 로그인 정상 진입 확인.

> 이 사고는 **근본 해법이 따로 있다**는 걸 드러냈다 → **Flyway 마이그레이션 도입**(임시 보정 제거, `ddl-auto`를 validate로). [plan.md](plan.md) 기술 부채에 등재. 관련 개념·함정은 N-023 / T-015.

---

## 회고 — 무엇이 잘 작동했나

- **TDD + 경계값 우선**: Lazy 누적/cap/자정 경계 같은 도메인 핵심이 회귀 없이 안정.
- **PKM(N-###/T-###) 즉시 기록**: Boot 4 패키지 이동, 프록시 헤더, 스키마 드리프트 같은 함정을 다시 만나도 바로 대응.
- **PR 우선 + 훅**: 테스트 통과 없이는 커밋이 막혀, 깨진 채로 main에 들어가는 일이 없었다.

## 다음 (요약)

- Flyway 도입(임시 스키마 보정 대체), 404를 500으로 삼키는 전역 핸들러 수정, GitHub Actions Node 20→24 갱신 — 상세는 [plan.md](plan.md).

---

## 부록 — 기능 흐름 한눈에

| Phase | 핵심 산출물 | 관련 PR |
|---|---|---|
| 0 | PR 훅 / TDD 게이트 / H2 테스트 인프라 | #1, #2 |
| 1 | Lazy 누적 타이머 도메인 | #8 |
| 2 | User/Timer 엔티티 + JPA + Auditing | #11~#17 |
| 3 | ReadingSession 측정·차감 | #18~#26 |
| 4 | Security 폼 로그인 + 가입 + 대시보드 | #27~#35 |
| 5 | 컨테이너화 → ECS Fargate → CI/CD | #36~#42 |
| 6 | 실시간 UI / 설정 / 기록 조회 / cap 배지 | #44~#56 |
| 7 | 계정 보안(비번 변경·탈퇴) | #57 |
| 8 | HTTPS (ALB TLS termination) | #58, #59 |
| 9 | Google OAuth (OIDC) | #59 |
| 10 | 소셜 로그인 500 fix + 기술부채 등재 | #60, #61 |
