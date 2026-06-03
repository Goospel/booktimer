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

### 도메인 TLD 이전 — `.click` → `.com`/`.app` (계획 ⏳ 2026-06-03, 우선순위: 중)

**왜**: 구글 로그인 게시 후 Chrome이 `booktimer.click`을 **"위험한 사이트"로 차단**(Safe Browsing 피싱 오탐)했다.
원인은 우리 코드가 아니라 **`.click` TLD의 낮은 평판 + 신규 도메인 + 로그인/OAuth 콜백** 조합(T-027, N-036).
당장은 **Search Console 보안문제 검토 요청**으로 해제하지만, `.click`은 평판이 근본적으로 낮아 **재발 위험**이 있다.

- [ ] **1차 대응**: Google Search Console에 `booktimer.click` 등록(DNS TXT 인증) → 보안 문제 검토 요청 → 해제 확인.
- [ ] **재발 시 근본 대응**: 평판 좋은 TLD로 이전 — `.com`(범용) 또는 `.app`(레지스트리가 HTTPS 강제, 평판 양호).
      따라오는 작업: 도메인 재구매 · ACM 인증서 재발급(DNS 검증) · Route 53 호스팅 영역 · ALB alias 재연결 ·
      **Google OAuth 클라이언트의 승인된 리디렉션 URI / JS origin 재등록** · privacy URL·앱 도메인 갱신 · (구) 도메인 301.
- 비용: 신규 도메인 등록비(연 단위) + 전환 작업 시간. 데드라인/오탐 재발이 트리거.

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

### 독서 잔디 — 색 농도를 "하루 목표(증가값)" 기준으로 (완료 ✅ 2026-06-03)

> **구현 완료** — `ContributionGraphBuilder.build/levelFor`에 `goalSeconds` 인자 추가, 하드코딩 임계
> (`LEVEL_THRESHOLDS_SECONDS`) 제거. 레벨을 목표 대비 비율로 교차곱(정수) 비교. `ReadingContributionService`·
> `BookContributionService`가 유저 `ReadingTimer.dailyIncrementSeconds`를 조회해 빌더에 전달(없으면 1시간 폴백).
> 범례 "적음/많음" → "목표 미달/목표 달성"으로 보강. TDD: 빌더 경계(0%·25%·50%·100%·초과·목표0 퇴화·목표 추종)
> + 서비스 목표 배선. 아래는 설계 근거 기록(참고용).

**문제**: 현재 잔디 레벨(0~4)은 `ContributionGraphBuilder`에 **하드코딩된 고정 임계**
(`LEVEL_THRESHOLDS_SECONDS = {15분, 30분, 60분}`)로만 정해진다. 유저별 **하루 목표**인
`ReadingTimer.dailyIncrementSeconds`(설정의 "증가값", 기본 1시간)를 **전혀 참조하지 않는다**.
지금 "1시간 읽으면 진초록"으로 보이는 건 *기본 증가값(1시간)과 하드코딩 임계(60분 초과=lv4)가 우연히
일치*하기 때문일 뿐 — 사용자가 증가값을 30분으로 바꾸면 목표는 30분인데 잔디는 여전히 1시간 넘게
읽어야 최고 농도가 된다(목표를 따라가지 않음).

**의미 정리**: `dailyIncrementSeconds`는 부채 누적 모델(N-001)에서 "매일 늘어나는 갚을 양" =
사실상 **그날의 독서 목표**다. 따라서 잔디 농도를 이 목표 대비 **달성 비율**로 칠하는 게 자연스럽다.

**결정 (사용자 합의 2026-06-03)**:
- **목표 기준 = 그날 평면 증가값**(`dailyIncrementSeconds`). 이월된 누적 부채(잔여)가 아니라 그날 증가값으로
  고정 — 잔디는 칸마다 기준이 같아야 직관적이라(이월분을 섞으면 칸마다 기준이 달라짐).
- **비율 4단계 매핑** (목표 100% 달성 시 최고 농도 = 진초록):

  | 그날 독서 / 목표 | 레벨 | 색 |
  |---|---|---|
  | 0% (안 읽음) | 0 | 회색 |
  | 0 초과 ~ 25% | 1 | 연초록 |
  | 25% ~ 50% | 2 | |
  | 50% ~ 100% 미만 | 3 | |
  | **100% 이상 (목표 달성/초과)** | 4 | 진초록 |

  - 경계는 기존 코드 관례대로 "이하 포함"으로 둔다(예: 정확히 25%는 lv1, 정확히 50%는 lv2, 정확히 100%는 lv4).

**구현 메모 (별도 PR에서, TDD)**:
- `ContributionGraphBuilder.build(...)` / `levelFor(...)` 시그니처에 **목표 초(`goalSeconds`) 인자 추가** —
  현재 고정 `LEVEL_THRESHOLDS_SECONDS` 대신 목표 비율로 레벨 계산.
- **부동소수 회피 — 교차곱**으로 비율 비교(정수 long 유지):
  - `seconds <= 0` → lv0
  - `seconds * 4 <= goal` → lv1 (≤25%)
  - `seconds * 2 <= goal` → lv2 (≤50%)
  - `seconds < goal` → lv3 (<100%)
  - 그 외(`seconds >= goal`) → lv4 (목표 달성/초과)
- **목표 0 퇴화 처리는 자동**: `goal=0`이면 위 식에서 읽은 날(`seconds>0`)은 전부 lv4로 떨어진다
  (div-by-zero 없음). "목표 없음 = 읽기만 하면 만점"으로 합리적 — 별도 분기 불필요.
- `ReadingContributionService`가 유저의 `ReadingTimer`(→ `dailyIncrementSeconds`)를 읽어 빌더에 넘겨야 함
  (현재는 `historyService` + `clock`만 의존 → 타이머 조회 의존성 1개 추가). 타이머 미존재 케이스 정책 정하기
  (없으면 기본 1시간 fallback 등).
- **TDD 경계 테스트**: 0% / 0 바로 초과 / 정확히 25·50·100% / 100% 초과 / **목표 0(퇴화)** / 목표보다 큰 잔여 이월.
- (선택) `history.html` 범례·툴팁을 "목표 대비 %"로 보강, 범례 라벨 "적음/많음" → "목표 대비" 뉘앙스 검토.

**참고**: 색 자체(`app.css`의 `.level-0~4`)와 그리드 레이아웃은 그대로 재사용 — 바뀌는 건 *레벨을 정하는 기준*뿐.

### 책 단위 기록 (Book) — README §2.3
- **1단계 완료 ✅ 2026-06-02**: 책 등록·목록(`/books`). 알라딘 OpenAPI 검색(포트/어댑터 `BookSearchClient`
  → `AladinBookSearchClient`, TTBKey=env) → "책장에 추가", 상태(읽고싶음/읽는중/완독)·삭제, 소유권 검사.
  키 없으면 수동 입력 폴백. 구매링크에 제휴 태그 토대(제휴 고지 푸터 포함). Flyway V3.
  ⏳ 외부: **알라딘 TTBKey 발급**(env `BOOKTIMER_ALADIN_TTB_KEY`) 후 검색 라이브 활성화.
- **2단계 완료 ✅ 2026-06-03**: `ReadingSession`에 nullable `book` 연결(Flyway V4) + 타이머 시작 시 책 선택
  (대시보드 드롭다운, "선택 안 함" 포함, 소유권 검사) + 책별 누적 시간 집계(`BookReadingStatsService`,
  완료·책지정 세션 합) → `/books`에 책별 시간 표시. 측정 중 책은 대시보드에 노출.
  ⚠️ 디버깅: SSR 앱엔 ObjectMapper 빈 없음(T-022)은 1단계에서 발생, 해결됨.
- **3단계** (조각별 PR로 진행):
  - **①-a 책 시작 시 상태 자동 전환 완료 ✅ 2026-06-03**: 읽고싶음 책으로 타이머를 시작하면 자동으로
    읽는중 전환(`Book.startReading()` 멱등 — 읽는중/완독은 불변, 완독 되돌리지 않음). 전환 시에만 저장
    (`ReadingSessionService`). TDD: BookTest·ReadingSessionServiceTest·ReadingSessionControllerTest.
  - **②-b 제휴 클릭 추적 완료 ✅ 2026-06-03**: "구매"를 서버 경유(`GET /books/{id}/buy`)로 카운트한 뒤
    알라딘 제휴링크로 302 리다이렉트(`Book.clickCount`, Flyway V5). 소유권(IDOR)·링크 없으면 미집계.
    수익 데이터 토대(어떤 책이 구매 의향을 내나). 개념: N-033(클릭 추적 GET 리다이렉트·CSRF·오픈 리다이렉트).
    TDD: BookTest·BookServiceTest·BookControllerTest.
  - **③-c 책 상세 페이지 완료 ✅ 2026-06-03**: `GET /books/{id}` — 책 메타 + **책별 잔디**(그 책 세션만 필터,
    `ContributionGraphBuilder` 순수 빌더 재사용) + 일자별 기록 + 누적 시간. 소유권 검사(IDOR, 없으면 책장으로).
    `BookContributionService`(세션 패키지, Clock+유저 TZ 오늘) + `findByUserAndBook`. 책장에서 제목 클릭 진입.
    TDD: BookContributionServiceTest(단위)·BookControllerTest(렌더·IDOR). **책 3단계 완료.**
- SNS 확장의 핵심 컨텐츠 토대

### OAuth 소셜 로그인
- [x] **구글(Google OIDC)** — 완료·배포 (2026-06-02). find-or-create 프로비저닝, principal=email 통일,
      소셜 계정 UX 분기(비번 카드 숨김). Google 동의 화면은 Testing(테스트 사용자만) → 추후 게시(Publish)
- [x] **동의 화면 게시(Production 전환) 완료 ✅ 2026-06-03** — 테스트 사용자 100명 제한 해제, 누구나 Google 로그인 가능.
      스코프가 non-sensitive(`openid`/`email`/`profile`)라 **Google 검증 절차 없이 즉시 게시**, 코드 변경 0.
      체크리스트: [x] ① 개인정보처리방침 페이지 (`GET /privacy`, 공개·permitAll, 로그인/가입에서 링크) →
      [x] ② 동의 화면 브랜딩(앱 이름·지원 이메일) + 처리방침 URL `https://booktimer.click/privacy` 등록 →
      [x] ③ Console에서 Publish app(게시 상태=프로덕션, 사용자 유형=외부).
      ※ "OAuth 사용자 한도 100명"은 **민감/제한 범위 요청 시에만** 적용 — non-sensitive만 쓰므로 표시되어도 미적용(실질 무제한).
      ※ 보안 전제는 이미 충족(하드닝 #1 email_verified·#2 brute-force 완료, 사이트·LOCAL 가입은 이미 공개).
      ※ 게시 과정에서 Chrome "위험한 사이트" 오탐(T-027) 발생 → Search Console 도메인 인증 후 재평가로 **자연 해소**(Safe Browsing 등재 없음 확인).
- [ ] 카카오/네이버 등 추가 provider (선택)

### SNS 기능 — README §2.4

> ⚠️ **구현 전 설계 먼저 (필수)** — 코드부터 짜지 말 것. 공유 모델·프라이버시·관계 모델을 먼저 못 박고
> 별도 설계 문서로 합의한 뒤에 TDD로 들어간다. 이 기능은 데이터 노출·권한 경계가 핵심이라
> 설계 없이 시작하면 되돌리기 어려운 결정(공개 범위·스키마)이 코드에 굳어버린다.

> 💡 **헷갈리기 쉬운 점 — "남에게 보여주려면 DB에 저장해야 하나?" → 독서 데이터는 이미 다 저장돼 있다.**
> "누가 어떤 책을 읽었나/읽는 중인가/얼마나 읽었나"는 이미 `book`(user_id 소유, status) +
> `reading_session`(book_id + duration_seconds)에 들어 있다. SNS에서 남의 걸 보여주는 건 **데이터 추가가
> 아니라 조회 주체를 바꾸는 것**(`where user_id = 나` → `= 그 사람`). 따라서 SNS가 **새로 저장해야 하는 건
> 독서 기록이 아니라 두 가지뿐**이다 — ① **관계**(팔로우/친구), ② **공개 범위**(전체/팔로워/비공개).
> 이 둘만 새 테이블/컬럼(`V6__…`)으로 더하고, `book`/`reading_session`은 그대로 둔다.
> 그리고 `where user_id`만 갈아끼우는 순간 **IDOR/공개범위 체크가 보안 경계**가 된다(비공개 기록 누출 방지).
> 개념 상세: learning-notes **N-037**.

- 사용자 간 독서 기록 / 책별 시간 공유 (별도 설계 필요) — **저장 대상은 관계+공개범위, 독서기록 아님(위 💡)**
- **설계에서 먼저 정해야 할 것**(착수 전 합의 사항):
  - **공유 범위/프라이버시**: 무엇이 공개인가(독서 시간/책 목록/잔디?) · 기본값은 비공개인가 공개인가 · 유저별 토글
  - **관계 모델**: 팔로우(단방향) vs 친구(양방향) vs 전체 공개 피드 — 무엇을 먼저?
  - **권한 경계(IDOR)**: 남의 기록 조회 시 노출 가능 항목 화이트리스트, 비공개 데이터 차단
  - **스키마/마이그레이션**: 새 Flyway 버전(관계·공개설정 테이블), 기존 데이터 기본 공개값
  - **악용/스팸**: 차단·신고, 공개 프로필 열거 완화
  - **SSR 부하·SEO**: 공개 프로필 페이지의 렌더 위치(N-017) — 검색 노출/수익 축과 연계
- 토대: 책 단위 기록(완료)이 핵심 콘텐츠 → 공유 단위 후보는 "책별 누적 시간/잔디".

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
| 2026-06-03 | (계획) 독서 잔디 색 농도를 하루 목표(증가값) 기준 비율 4단계로 — 설계만 기록, 구현은 별도 PR(워크트리 분리) |
| 2026-06-03 | 개인정보처리방침 페이지(`GET /privacy`, 공개) 추가 — OAuth 동의 화면 게시 체크리스트 ① 완료(②③은 Console 수동). TDD |
| 2026-06-03 | Safe Browsing "위험한 사이트" 오탐(`.click` 신규 도메인+로그인/OAuth) — T-027/N-036 기록 + 도메인 TLD 이전(.com/.app) 백로그 추가 |
| 2026-06-03 | OAuth 동의 화면 게시 완료(②③ 체크) — 프로덕션 전환, 100명 제한 해제(non-sensitive라 한도 미적용). Safe Browsing 오탐은 Search Console 인증 후 자연 해소(T-027 보강) |
| 2026-06-03 | 독서 잔디 색 농도를 하루 목표(증가값) 기준 비율 4단계로 — 완료(빌더 goalSeconds 인자·교차곱, 서비스 타이머 조회, 범례 "목표 대비"). TDD |
| 2026-06-03 | 독서 잔디 대시보드 노출 + 열 순서 반전(최근=왼쪽, 스크롤 없이 최근 보이게) — 빌더 단일 소스로 세 잔디 일관. TDD |
| 2026-06-03 | SNS 기능 항목에 "구현 전 설계 먼저(필수)" 강조 + 착수 전 합의 사항(공유범위·관계모델·IDOR·스키마·악용·SSR) 명시 |
| 2026-06-03 | 측정 카드 — 타임존(원시 시작시각) 노출 제거 → 읽는 책 + 책별 누적 독서 시간 표시(#97). TDD |
| 2026-06-03 | SNS 확장 시 새로 저장할 건 독서기록 아닌 관계+공개범위 — N-037 + plan.md SNS 보강(#98) |
| 2026-06-03 | 가입 후 온보딩 페이지 — 타이머 초기값(시작 잔여)+증가값+상한 직접 설정(Flyway V6 users.onboarded, 첫 진입 게이트, 기존 사용자 백필 true). 가입 시 무조건 1h 시드를 사용자 선택으로. TDD |
