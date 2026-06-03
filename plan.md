# BookTimer — 작업 계획 / 추후 할 일 (plan.md)

> 지금 당장 안 하지만 **놓치면 안 되는 할 일**을 모아두는 곳.
> 개요·도메인 규칙은 [README.md](README.md), 학습 개념은 [claude-docs/learning-notes.md](claude-docs/learning-notes.md),
> 함정·해결은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md).

MVP(누적 타이머 + 인증 + 설정 + 일자별 기록 + 계정 보안)는 구현·배포 완료 상태.
아래는 그 이후 로드맵과 미뤄둔 보강 항목.

---

## 🔒 보안 / 인프라

### HTTPS 적용 — ALB TLS termination (완료 ✅ 2026-06-02)

**한 일**: `booktimer.click` 도메인(Route 53) + ACM 인증서(DNS 검증) + ALB 443 리스너 +
HTTP→HTTPS 301 리다이렉트 + Route 53 alias. 배경 개념 **N-021**.

- [x] 도메인 확보 — Route 53에 `booktimer.click` 등록(무료 플랜은 등록 차단 → 유료 전환)
- [x] ACM 인증서 발급 (ap-northeast-2, DNS 검증, apex + www)
- [x] ALB HTTPS(443) 리스너 + 인증서 연결 (기존 타깃그룹)
- [x] HTTP(80) → HTTPS(443) 301 리다이렉트
- [x] Route 53 alias(apex/www) → ALB
- [x] 프록시 뒤 https 인식 — **`ForwardedHeaderFilter` 명시 빈**(`WebConfig`).
      ※ `server.forward-headers-strategy=framework` 프로퍼티는 Boot 4에서 무동작이라 명시 등록(T-014, N-022)
- [x] 세션 쿠키 `Secure`/`HttpOnly` (prod 전용)
- [x] 보안 그룹: ALB 인바운드 443 허용
- [ ] (후속) HSTS 헤더 — `.click`이 아닌 커스텀이면 명시 추가 (현재 ALB가 일부 적용)

### AWS 요금 가드레일 — Budgets 월 $50 알람 (완료 ✅ 2026-06-02)

**한 일**: AWS Budgets에 **비용 예산 `booktimer-monthly-50`**(월 $50, 고정, 기본/반복) 생성.
알림 4개 — 실제 50%($25)·80%($40)·100%($50) + **예측 100%**. 수신: 계정 이메일.
콘솔 수동 설정(로컬에 AWS CLI 없음, GitHub Actions OIDC + 콘솔/CloudShell 사용).

- 예상 baseline 월 $30~50(ALB + Fargate 0.25vCPU + RDS micro + Route53, NAT 없음) 상단에 임계치.
- Budgets는 비용 데이터 하루 ~3회 갱신 → 알림은 실시간 아님(몇 시간~하루).
- 임계치 기준은 **% (예산 대비)** — 콘솔 한글 라벨 "경우를 기준으로 설정됨"이 곧 % 기준(헷갈림 주의).

### 세션 외부화 — Spring Session JDBC (완료 ✅ 2026-06-02)

**왜**: 배포(태스크 교체) 때마다 **재로그인** 발생. 원인은 세션이 **JVM 메모리**(기본 `HttpSession`)에
저장돼 태스크가 죽으면 통째로 사라지기 때문. 게다가 태스크를 2개 이상으로 늘리면(무중단·스케일아웃)
요청이 분산돼 *평소에도* 세션이 오락가락 → 세션 외부화는 무중단/스케일의 **전제**다.

**한 일**: 세션을 RDS(MySQL)에 저장 — `spring-boot-starter-session-jdbc`(Boot 4는 autoconfig가 별도
모듈이라 raw `spring-session-jdbc`만으론 빈 미생성, N-024 패턴) + Flyway **V2**로 `SPRING_SESSION`·
`SPRING_SESSION_ATTRIBUTES` 생성. 운영은 `spring.session.jdbc.initialize-schema=none(never)`로 Flyway가
스키마 단일 소스, 테스트 H2는 embedded 자동 초기화. 개념 **N-029**, 함정 **T-020**.

- **새 인프라·추가 비용 0** (기존 RDS 재사용). 이 규모엔 성능 충분.
- ⚠️ **이 배포 직후 1회는 전원 재로그인** — 세션 쿠키 이름이 `JSESSIONID`→`SESSION`으로 바뀌고 기존
  인메모리 세션은 어차피 소멸. 이후부터는 배포에도 로그인 유지.
- **(향후) Spring Session + Redis(ElastiCache)로 전환** — 트래픽/세션 쓰기가 늘면 DB 부하·지연 측면에서
  Redis(인메모리, TTL 네이티브)가 유리. JDBC→Redis는 의존성·설정 교체로 비교적 단순. 지금은 비용(예산 $50)
  고려해 JDBC 유지, 전환은 트래픽 신호가 오면.

### 무중단 배포 — ECS 롤링 deploymentConfiguration (적용·검증 완료 ✅ 2026-06-02)

- **증상**: 배포 시 홈페이지가 잠깐 먹통(503), 버튼 동작 반영 안 됨. 원인: 단일 태스크(`desiredCount=1`)가
  **죽고→새로 뜨는 공백** 동안 ALB에 healthy 타깃이 없음. (재로그인 문제는 위 세션 외부화로 **별도 해결**됨.)
- **한 일**: ECS 서비스 `deploymentConfiguration`을 멱등 적용하는 워크플로 신설
  (`.github/workflows/zero-downtime-config.yml`, `workflow_dispatch`):
  - `minimumHealthyPercent=100` + `maximumPercent=200` → desiredCount=1이어도 새 태스크를 **추가로** 띄워
    ALB 헬스 통과 후에야 옛 태스크 드레인 → 교체 중 항상 healthy 태스크 ≥1.
  - `deploymentCircuitBreaker{enable=true, rollback=true}` → 새 태스크 안정화 실패 시 **자동 롤백**.
  - 한 번 적용하면 영속(매 배포는 task def만 교체, deploymentConfiguration은 안 건드림 → 드리프트 없음).
  - **코드 변경 없음**(앱), OIDC 역할의 기존 `ecs:UpdateService` 권한으로 충분.
- **검증 완료 ✅**: 워크플로로 설정 적용 후 실배포를 돌리며 `https://booktimer.click/actuator/health`를
  1초 주기로 폴링 — 배포 도중·종료까지 **끊김 없이 200**(503 없음). 재로그인도 없음(#73과 결합).
- **선택적 후속(미적용)**: 타깃그룹 `deregistration_delay`(기본 300s→60s)·헬스체크 간격(30s→15s) 단축은
  교체 *속도* 최적화일 뿐 다운타임 원인은 아님. `elasticloadbalancing:Modify*` 권한이 필요해
  현재 OIDC 역할로는 불가 — 적용 시 deploy-aws.md의 해당 절(IAM 권한 추가 + CloudShell 1회) 참고.

---

## 📖 기능 로드맵

### 독서 잔디 (컨트리뷰션 그래프) — 완료 ✅ 2026-06-02
- GitHub 잔디 스타일 1년치(53주 × 7요일) 히트맵을 `/history` 상단에 렌더. 일자별 독서 시간을
  색 농도 0~4로 표시(0/≤15분/≤30분/≤60분/초과 — 상수라 조정 쉬움), 상단 월 라벨·좌측 요일 라벨·hover 툴팁.
- 데이터는 기존 `ReadingHistoryService.dailyHistory` 재사용. "오늘"은 Clock+유저 타임존(N-010).
- 순수 빌더 `ContributionGraphBuilder`(스프링 무관)로 그리드 계산 → 경계값 단위테스트. 렌더는 Thymeleaf+CSS grid.
- (후속 아이디어) 연속일(streak) 표시, 대시보드에도 노출, 색 단계 사용자 데이터 분포 기반 조정.

### 책 단위 기록 (Book) — README §2.3
- **1단계 완료 ✅ 2026-06-02**: 책 등록·목록(`/books`). 알라딘 OpenAPI 검색(포트/어댑터 `BookSearchClient`
  → `AladinBookSearchClient`, TTBKey=env) → "책장에 추가", 상태(읽고싶음/읽는중/완독)·삭제, 소유권 검사.
  키 없으면 수동 입력 폴백. 구매링크에 제휴 태그 토대(제휴 고지 푸터 포함). Flyway V3.
  ⏳ 외부: **알라딘 TTBKey 발급**(env `BOOKTIMER_ALADIN_TTB_KEY`) 후 검색 라이브 활성화.
- **2단계 완료 ✅ 2026-06-03**: `ReadingSession`에 nullable `book` 연결(Flyway V4) + 타이머 시작 시 책 선택
  (대시보드 드롭다운, "선택 안 함" 포함, 소유권 검사) + 책별 누적 시간 집계(`BookReadingStatsService`,
  완료·책지정 세션 합) → `/books`에 책별 시간 표시. 측정 중 책은 대시보드에 노출.
  ⚠️ 디버깅: SSR 앱엔 ObjectMapper 빈 없음(T-022)은 1단계에서 발생, 해결됨.
- **3단계 (아이디어)**: 책 시작 시 상태 자동 전환(읽고싶음→읽는중), 책 상세 페이지(책별 잔디/세션 목록), 제휴 클릭 추적.
- SNS 확장의 핵심 컨텐츠 토대

### OAuth 소셜 로그인
- [x] **구글(Google OIDC)** — 완료·배포 (2026-06-02). find-or-create 프로비저닝, principal=email 통일,
      소셜 계정 UX 분기(비번 카드 숨김). Google 동의 화면은 Testing(테스트 사용자만) → 추후 게시(Publish)
- [ ] **동의 화면 게시(Production 전환)** — 테스트 사용자 100명 제한 해제, 누구나 Google 로그인 가능.
      스코프가 non-sensitive(`openid`/`email`/`profile`)라 **Google 검증 절차 없이 즉시 게시**, 코드 변경 0.
      체크리스트: ① 개인정보처리방침 URL 준비(이메일 수집 → 동의 화면이 요구할 수 있음) → ② 동의 화면 브랜딩
      확인(앱 이름·지원 이메일·로고가 사용자에게 노출) → ③ Console에서 Publish app.
      ※ 보안 전제는 이미 충족(하드닝 #1 email_verified·#2 brute-force 완료, 사이트·LOCAL 가입은 이미 공개).
- [ ] 카카오/네이버 등 추가 provider (선택)

### SNS 기능 — README §2.4
- 사용자 간 독서 기록 / 책별 시간 공유 (별도 설계 필요)

### 프론트엔드 전환 (SSR → SPA)
- 현재 Thymeleaf SSR. API 계약 안정성 + 인터랙션 요구가 커지면 전환 (N-017)

---

## 🧹 기술 부채 / 후속 정리

### Flyway 마이그레이션 도입 (완료 ✅ 2026-06-02)
- **왜**: `ddl-auto=update`는 기존 컬럼 제약(NOT NULL 등)을 못 바꿔 스키마 드리프트 발생 — 실제로 소셜 계정
  `password_hash` nullable 변경이 prod에 미반영돼 500 사고(T-015, N-023).
- **한 일**:
  - [x] `spring-boot-flyway`(autoconfig 모듈) + `flyway-mysql` 의존 추가 — Boot 4는 Flyway autoconfig가
        별도 모듈(`flyway-core`만으론 빈 미생성, T-016/N-024)
  - [x] `V1__init_schema.sql` baseline 작성 — enum→varchar로 MySQL·H2 공통 실행, 시각 datetime(6)
  - [x] 기존 운영 DB **baseline** (`baseline-on-migrate=true`, `baseline-version=1` → 기존 DB는 V1 적용
        표시만 하고 실행 X, 신규 환경만 V1 실행)
  - [x] `ddl-auto`를 prod·test 모두 `none`으로 전환 (validate 대신 none — 크로스-다이얼렉트 validate
        취약성 + 운영 기동 실패 위험 회피. 드리프트는 `FlywayMigrationTest`가 격리 H2에서 validate로 검증)
  - [x] **`PasswordHashNullableSchemaFix` 제거** (V1이 nullable 보장)
  - [x] (부수) @DataJpaTest 슬라이스 3종이 `@Import(JpaConfig.class)` 누락으로 순서 의존이던 것 수정(T-017)

### 회원 인증/계정 보안 하드닝 (우선순위: 높음)
> 2026-06-02 보안 점검 결과. 기본기(BCrypt·CSRF·세션고정보호·재인증·IDOR 없음·XSS 없음)는 양호.
> 아래는 보강 항목. **상세 위협 분석은 공개 노출 부담이 있어 private 노트에 별도 기록**(이 repo 공개).
- [x] **OAuth 이메일 검증 강제** (완료 2026-06-02) — `provision`에 `email_verified` 게이트(아니면 거부, null=미검증 처리).
      자동 계정 연결 탈취 방어. 개념 **N-026**.
- [x] **로그인 무차별 대입 방어** (완료 2026-06-02) — IP별 연속 실패 5회→15분 잠금(`LoginAttemptService` + 인증 이벤트 집계
      + `LoginAttemptFilter` 단락). 키를 이메일 아닌 IP로(피해자 잠금 DoS 회피). 개념 **N-026**.
- [x] **세션 쿠키 `SameSite=Lax` 명시** (완료 2026-06-02) — `WebConfig#cookieSerializer` 명시 빈으로 SameSite=Lax
      + HttpOnly + (prod)Secure. 세션 외부화 후 세션 쿠키는 `DefaultCookieSerializer`가 써서
      `server.servlet.session.cookie.*` 프로퍼티가 무동작이라 명시 빈 필요(T-021, N-031). **파생 수정**: 이
      함정 탓에 prod의 Secure/HttpOnly도 SESSION 쿠키엔 안 먹던 잠재 갭(#73 이후)을 같이 잡음.
- [ ] (검토) 소셜 계정 탈퇴 시 재확인 단계, 가입 시 계정 열거 완화
- [ ] (후속) 무차별 대입 방어 보강 — 지수 백오프, 다중 인스턴스 대비 공유 저장소(현재 인메모리=인스턴스별), 앞단 WAF 레이트리밋

### Fargate CPU 상향 — 로그인(BCrypt) 지연 (우선순위: 중)
- **증상**: 로그인이 체감상 느림.
- **원인**: DB 아님(`findByEmail`은 유니크 인덱스 단건 조회 — 수 ms). 범인은 **BCrypt 비밀번호 검증**(의도적 CPU 집약) ×
  **태스크 `cpu:256`=0.25 vCPU**(Fargate 최소). 1/4 코어 스로틀이라 BCrypt가 수백 ms~1s까지 늘어남. JVM JIT 워밍업도 가중.
  실측: health·/login GET는 60~150ms 정상 → 차이는 로그인 POST의 BCrypt뿐.
- **할 일**: `deploy/task-definition.json`의 `cpu`를 **512(0.5)~1024(1 vCPU)**로 상향(메모리도 비례). BCrypt 강도(10)는
  **낮추지 말 것**(보안). DB는 손대지 않음. ※ Fargate는 vCPU·메모리 비례 과금 — 비용 소폭 증가.
- 개념: learning-notes(로그인 지연 ≠ DB, BCrypt×작은 vCPU / CPU 집약 해시는 의도된 느림 — 해법은 강도↓가 아니라 CPU↑).

### 전역 예외 핸들러가 404를 500으로 삼킴 (완료 ✅ 2026-06-02, PR #72)
- `GlobalExceptionHandler(@ExceptionHandler(Exception.class))`가 `NoResourceFoundException`(예: `/favicon.ico`)까지
  잡아 **500**으로 응답·로그 도배하던 문제.
- **한 일**: `{ResponseStatusException, NoResourceFoundException}`를 잡는 좁은 핸들러를 catch-all 위에 두고
  `((ErrorResponse) ex).getStatusCode()`로 상태코드 보존(404는 404로). Boot 4에서 `NoResourceFoundException`이
  `ResponseStatusException`을 더는 상속 안 해(둘 다 `ErrorResponse` 구현) 타입 지정에 주의(T-019/N-028).

### GitHub Actions Node 20 deprecation (완료 ✅ 2026-06-02)
- **왜**: 2026-06-16부터 GitHub Actions가 Node 24를 강제 — Node 20 런타임 액션은 경고/중단.
- **한 일**: 배포 워크플로의 node20 액션을 node24 최신 major로 갱신 —
  `actions/checkout@v4→@v6`, `actions/setup-java@v4→@v5`, `aws-actions/configure-aws-credentials@v4→@v6`
  (v5는 아직 node20이라 v6 필요). `amazon-ecr-login@v2`·`amazon-ecs-render-task-definition@v1`·
  `amazon-ecs-deploy-task-definition@v2`는 이미 node24라 유지. breaking change는 우리 사용 패턴(bare 사용,
  boolean 입력 없음, hosted runner)에 무해함을 릴리스 노트로 확인. 검증은 머지 후 실제 배포(run).

---

## 🔄 갱신 이력

| 일자 | 내용 |
|---|---|
| 2026-06-02 | plan.md 신설 — HTTPS(ALB TLS termination) 항목 + 기존 로드맵 정리 |
| 2026-06-02 | HTTPS·OAuth(구글) 완료 반영 + 기술부채(Flyway 도입/404 핸들러/Actions Node20) 추가 |
| 2026-06-02 | Flyway 도입 완료 처리 + 회원 인증/계정 보안 하드닝 항목 추가(상세는 private 노트) |
| 2026-06-02 | Fargate CPU 상향(로그인 BCrypt 지연) 항목 추가 |
| 2026-06-02 | 보안 하드닝 #1 OAuth email_verified·#2 brute-force 완료 처리(N-026) |
| 2026-06-02 | GitHub Actions Node 24 갱신 완료(checkout@v6/setup-java@v5/configure-aws-credentials@v6) |
| 2026-06-02 | 세션 외부화(Spring Session JDBC) 완료 — 재로그인 해결(N-029/T-020), 무중단 배포(ECS 롤링) 백로그 추가, 향후 Redis 전환 명시 |
| 2026-06-02 | 무중단 배포 — ECS deploymentConfiguration(min=100/max=200 + circuit breaker rollback) 멱등 적용 워크플로 신설(N-030) |
| 2026-06-02 | 무중단 배포 검증 완료 — 워크플로 적용 후 실배포 중 /actuator/health 폴링이 끊김 없이 200(503 없음) |
| 2026-06-02 | 세션 쿠키 SameSite=Lax 완료(명시 CookieSerializer 빈) + prod Secure/HttpOnly 잠재 갭 동시 수정(T-021/N-031). 404→500 stale 항목 #72 완료로 정리 |
| 2026-06-02 | 독서 잔디(컨트리뷰션 그래프) 완료 — /history에 1년치 히트맵(ContributionGraphBuilder 순수 빌더 + 서비스 + Thymeleaf/CSS), TDD |
| 2026-06-02 | 책 단위 기록 1단계 완료 — /books 등록·목록(알라딘 검색 포트/어댑터 + 수동 폴백, 상태·삭제·소유권), Flyway V3, 제휴 토대. TDD |
| 2026-06-03 | 책 단위 기록 2단계 완료 — 세션↔책 연결(Flyway V4)·타이머 시작 시 책 선택·책별 누적 시간 집계/표시. T-022 기록. TDD |
| 2026-06-03 | 알라딘 TTBKey SSM 주입(검색·제휴 라이브) + 도서 검색 페이징(이전/다음, totalResults 기반). TDD |
