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
- [ ] **(백로그) 온보딩에서 타임존도 받기** — 현재 온보딩 페이지는 초기값·증가값·상한만 묻는다.
      구글 가입자는 가입 폼이 없어 타임존이 기본값 `Asia/Seoul`로 생성된다(설정에서 변경 가능).
      잔디 자정 경계·일일 누적이 타임존에 의존(N-010)하므로 해외 사용자 대응 시 온보딩 폼에 타임존
      드롭다운 추가가 자연스럽다. **우선순위 낮음** — 해외 사용자 유입은 아직 먼 얘기. 그때 착수.

### SNS 기능 — README §2.4

> ⚠️ **구현 전 설계 먼저 (필수)** — 코드부터 짜지 말 것. 공유 모델·프라이버시·관계 모델을 먼저 못 박고
> 별도 설계 문서로 합의한 뒤에 TDD로 들어간다. 이 기능은 데이터 노출·권한 경계가 핵심이라
> 설계 없이 시작하면 되돌리기 어려운 결정(공개 범위·스키마)이 코드에 굳어버린다.

> 📐 **설계 문서 → [claude-docs/sns-design.md](claude-docs/sns-design.md)** (사용자 요구사항 1차 확정 반영 2026-06-04).
> **확정 요구사항**: ① 서로 팔로우(단방향) · ② **책 단위 공개/비공개**(책마다 오픈 선택) · ③ 검색 시 **내 팔로우 중**
> 몇 명이 원함/읽음(팔로우 스코프 카운트) · ④⑤ 개인 프로필 페이지(공개 책장+잔디) · ⑥ **닉네임 유니크**(검색·핸들).
> 설계: 책별 `visibility`(PRIVATE 기본 백필)·`follow` 테이블·닉네임 유니크화(기존 중복/NULL 백필 선결)·canViewBook 게이트·
> 잔디 viewer 가시성 필터(비공개 책 세션 간접 누출 차단)·로드맵(①닉네임+책공개→②프로필→③팔로우→④팔로우스코프 카운트→⑤악용).
> **결론: SNS 대부분 SSR로 충분 → API-first big-bang 불필요.** 카운트 status 매핑·k익명은 4단계에서 확정(§11-4·5 해결). 남은 열린 질문은 닉네임 변경 정책(§11-3)·프로필 SEO 개방(§11-8) 정도.

> 🗺️ **로드맵 진행 상태** (정본 상세: [sns-design.md §7](claude-docs/sns-design.md)). 단계별 ✅는 머지·배포 완료 기준.
> - ✅ **1단계** (PR #108·#109) — 닉네임 유니크화(V7) + 책별 공개 토글(V8, BookVisibility PRIVATE 기본)
> - ✅ **2단계** (PR #111) — 개인 공개 프로필 `/u/{nickname}`(SSR, PUBLIC 책장+잔디, 비공개 책 세션 간접 누출 차단 §3.5)
> - ✅ **3단계** (PR #112) — 닉네임 검색(부분일치·상한20) + 팔로우(follow V9, 자기팔로우 금지·멱등·언팔즉시) + 프로필 팔로우 카운트/버튼
> - ✅ **4단계** (PR #118) — 팔로우 스코프 인기 카운트("👥 팔로우 중 N명 원함 · M명 읽음", 책장·검색결과). 원함=WANT_TO_READ·읽음=READING∪FINISHED 확정, k익명 임계 없음(위험 제한적, 확정), isbn 일괄 group by(N+1 회피), Flyway 신규 없음
> - ✅ **4단계+ drill-down** — 카운트 배지를 클릭하면 "그 책을 원함/읽음인 **내 팔로우 명단**"(`GET /books/readers`). 카운트와 **같은 게이트**(팔로우·PUBLIC·distinct)로 신원만 펼침 — 각 팔로우 프로필의 PUBLIC 책장에서 어차피 보이는 것이라 새 노출 0, 임의 isbn에도 내 팔로우 공개책만(IDOR 없음). 행은 기존 `UserRowAssembler` 재사용. 전역 카운트는 채택 안 함(아래 §책 인기 카운트). Flyway 신규 없음. TDD
> - ✅ **5단계 (완료)** — 악용 방지. ✅ **차단(block)** (PR #121, 대칭 — 서로 팔로우·프로필 차단, V10, 차단 시 팔로우 양방향 해제, `/me/blocks` 해제, 탈퇴 정리). ✅ **신고(report)** (PR #127, reporter→reported, V11, 사유+상세, 쌍당 1건 멱등, 저장만(관리자 검토 추후), 탈퇴 정리). ✅ **레이트리밋 + 열거완화 + 차단 검색숨김** (`RateLimitService` 사용자별 인메모리 — FOLLOW 30/분·SEARCH 20/분·REPORT 10/시간, 초과 시 드롭/안내; 검색 결과 차단 사용자 필터; 인메모리=인스턴스별 한계는 backlog).
> - 부수 픽스: 탈퇴 시 follow·book FK 자식 정리(PR #112·#113), sweep T-029·N-040(PR #114).
> - 보강: 본인 팔로워/팔로잉 목록 `/me/followers`·`/me/following`(PR #119).
> - 검색 UX: 책 검색 기준 제목/저자 분리(PR #123, SNS 외 실사용 픽스 — 아래 "실사용 발견" 섹션).

> 💡 **헷갈리기 쉬운 점 — "남에게 보여주려면 DB에 저장해야 하나?" → 독서 데이터는 이미 다 저장돼 있다.**
> "누가 어떤 책을 읽었나/읽는 중인가/얼마나 읽었나"는 이미 `book`(user_id 소유, status) +
> `reading_session`(book_id + duration_seconds)에 들어 있다. SNS에서 남의 걸 보여주는 건 **데이터 추가가
> 아니라 조회 주체를 바꾸는 것**(`where user_id = 나` → `= 그 사람`). 따라서 SNS가 **새로 저장해야 하는 건
> 독서 기록이 아니라 두 가지뿐**이다 — ① **관계**(팔로우/친구), ② **공개 범위**(전체/팔로워/비공개).
> 이 둘만 새 테이블/컬럼(`V6__…`)으로 더하고, `book`/`reading_session`은 그대로 둔다.
> 그리고 `where user_id`만 갈아끼우는 순간 **IDOR/공개범위 체크가 보안 경계**가 된다(비공개 기록 누출 방지).
> 개념 상세: learning-notes **N-037**.

- 사용자 간 독서 기록 / 책별 시간 공유 (별도 설계 필요) — **저장 대상은 관계+공개범위, 독서기록 아님(위 💡)**
- **설계에서 먼저 정해야 할 것**(아래는 1~3단계 진행하며 대부분 확정·구현됨 — 정본 [sns-design.md](claude-docs/sns-design.md). 4·5단계 분만 미결):
  - **공유 범위/프라이버시**: 무엇이 공개인가(독서 시간/책 목록/잔디?) · 기본값은 비공개인가 공개인가 · 유저별 토글
  - **관계 모델**: 팔로우(단방향) vs 친구(양방향) vs 전체 공개 피드 — 무엇을 먼저?
  - **권한 경계(IDOR)**: 남의 기록 조회 시 노출 가능 항목 화이트리스트, 비공개 데이터 차단
  - **스키마/마이그레이션**: 새 Flyway 버전(관계·공개설정 테이블), 기존 데이터 기본 공개값
  - **악용/스팸**: 차단·신고, 공개 프로필 열거 완화
  - **SSR 부하·SEO**: 공개 프로필 페이지의 렌더 위치(N-017) — 검색 노출/수익 축과 연계
- 토대: 책 단위 기록(완료)이 핵심 콘텐츠 → 공유 단위 후보는 "책별 누적 시간/잔디".
- **프론트/앱과의 선후**: SNS 완성은 프론트 교체·앱의 선행조건이 *아니다*. 단 SNS UI를 두 번(SSR→SPA) 짜지
  않으려면 프론트 결정과 순서를 맞춰야 한다. 데이터 설계(위)는 프론트와 무관하게 먼저 가능 — §프론트엔드 전환 💡 참고.

#### 책 인기 카운트 — "몇 명이 이 책을 읽는가" (집계 노출)
> 위 💡와 같은 맥락: **새 독서 데이터를 저장하는 게 아니라 기존 `book`을 집계해 숫자만 보여주는 것**이다.
> "누가 이 책을 가졌나/읽는 중인가"는 이미 `book`(`isbn`/제목 식별, `user_id` 소유, `status`)에 있다.
> 책 식별 키(알라딘 ISBN)로 `book`을 `group by` 해 **사람 수만 count**하면 된다 — 추가 테이블 없이 시작 가능.

> 🚫 **전역(전체 사용자) 카운트는 채택 안 함 (사용자 결정 2026-06-04).** 처음엔 "전체에서 N명 읽는 중"을
> 검색결과·책장에 노출하려 했으나, **팔로우 가치를 희석**한다고 판단해 접었다 — 전역 카운트는 *팔로우 없이도*
> 공짜로 사회적 증거를 주므로 "궁금하면 팔로우" 동기가 약해진다. 대신 카운트를 **팔로우 스코프로 가두고**
> (4단계, 이미 구현) 거기에 **drill-down(누가 읽는지, 4단계+)**을 얹어 *팔로우할수록 더 보인다*는 희소성으로
> 핵심 루프를 강화하는 쪽으로 결정. 아래 두 항목의 "전역 distinct count" 구상은 그래서 **보류/철회**한다.

- [x] ~~검색 결과 리스트에 "읽는 중 N · 완독 M" **전역** 표시~~ → **철회**. 팔로우 스코프 카운트(4단계)로 충족.
- [x] ~~내 책장에서 "몇 명이 이 책을 읽는 중인지" **전역** 표시~~ → **철회**. 팔로우 스코프 카운트 + drill-down(4단계·4단계+)으로 충족.

> 아래는 **철회된 전역 카운트 원문 구상**(참고용 보존) — 위 🚫 결정 전 설계라 더는 진행하지 않는다.

- [ ] **(철회·참고용) 검색 결과 리스트에 "읽는 중 N · 완독 M" 작게 표시** — 책을 검색(`/books/search`)해 알라딘에서
      받아온 결과를 리스트로 보여줄 때, 각 책 항목에 **그 책을 읽고 있는/읽은 사람 수를 작은 숫자로만** 노출.
  - 집계: 알라딘 결과의 ISBN을 키로 `book`에서 `status`별 **distinct user 수** count
    (`READING` → "읽는 중 N", `DONE` → "완독 M"). 0명이면 숨기거나 회색 처리.
  - **프라이버시 = 집계 노출**: 개인 식별 없이 **숫자(기수)만** 보여준다 → 공개범위 설계 부담이 작다
    (누가 읽는지는 안 보여주므로 IDOR/공개범위 경계가 개인 기록 노출보다 약함). 단, **소수 인원일 때
    재식별 위험**은 검토(예: 1~2명이면 사실상 특정 가능) — k-익명성 관점에서 최소 노출 임계값 고려.
  - 성능: 검색 페이지당 N개 ISBN을 **한 번의 `group by` 쿼리로 일괄 집계**(N+1 회피). 캐시 후보.
- [ ] **(철회·참고용) 내 책장에서 "몇 명이 이 책을 읽는 중인지" 표시** — `/books`(내 책장) 목록의 각 책에
      **현재 그 책을 `READING` 상태로 가진 사람 수**를 함께 보여준다(나 포함/제외 정책 결정 필요).
  - 검색 결과 카운트와 **같은 집계 로직 재사용**(ISBN 키 distinct user count) — 표시 위치만 책장 목록.
  - "함께 읽는 사람" 동기부여 축 → 추후 "이 책 같이 읽는 사람 보기"(관계/공개범위 도입 후) 진입점 후보.
- **설계 메모**: 이 카운트 기능은 관계(팔로우) 없이도 **집계만으로 선출시 가능**(SNS 풀스택보다 가벼움).
  단 책 동일성 키(ISBN 정규화 — ISBN10/13, 개정판/세트 처리)와 0명·소수 처리, N+1 회피가 선결.

### 📚🧬 독서 성향 분석 — "책장 기반 MBTI" (아이디어 ⏳ 2026-06-04, 우선순위: 미정 / 기록만)

> ⚠️ **아직 구상 단계 — 기록만. 구현 금지.** 설계·스키마·기술선택 전부 미확정.

**컨셉**: 사용자의 **책장(보유/읽는중/완독 책 + 책별 누적 시간)** 을 입력으로, **AI를 돌리든 알고리즘을
짜든** 일정 과정을 거쳐 그 사람의 **독서 성향(=일종의 "독서 MBTI")** 을 도출한다. 이 성향 프로필을
토대로 두 갈래 컨텐츠를 얹는다 — ① **개인화 책 추천**, ② **사용자끼리 상호작용**(성향 비교·매칭·궁합 등).

**왜 BookTimer에 자연스러운가**: 핵심 입력(누가 어떤 책을 가졌나/얼마나 읽었나)이 이미 `book`·
`reading_session`에 다 있다(N-037 — SNS와 같은 맥락, 새 독서데이터 저장이 아니라 **기존 데이터 해석**).
SNS 토대(팔로우·공개범위·프로필)가 깔려 있어 ②의 사용자 상호작용도 얹을 자리가 있다.

**구상 갈래 (택1 아님 — 단계적 가능)**:
- **A. 규칙/알고리즘 기반(가벼움)** — 장르·카테고리·저자·완독률·독서 시간 분포 등에서 축(axis)을 뽑아
  점수화 → 성향 라벨 매핑. 외부 비용 0, 결정적·설명가능. **MVP·검증용으로 적합.**
- **B. AI(LLM) 기반(풍부함)** — 책 목록·메타를 LLM에 넣어 성향 서술/요약을 생성. 표현은 풍부하나
  **비용·지연·프롬프트 일관성·환각** 관리 필요. A로 축을 정하고 B는 "표현 레이어"로 얹는 하이브리드도 후보.

**다운스트림 컨텐츠**:
- **책 추천** — 성향 + 팔로우 스코프 인기(4단계 집계) + 알라딘 검색을 엮어 "당신 성향엔 이 책". 제휴(3%)와 연계.
- **사용자 상호작용** — 성향 라벨로 프로필 배지, "비슷한 성향 독자" 추천, 성향 궁합/비교, 성향별 모임 등.

**미확정·선결(나중에 정할 것)**:
- 성향 축/라벨 체계 설계(몇 축? 라벨 네이밍? 책 부족 시 처리 — 콜드스타트).
- 입력 프라이버시 — **PRIVATE 책을 분석에 쓸지**(타인에게 성향이 노출되면 비공개 책이 간접 누출될 수 있음 → §3.5 가시성 경계와 동일 주의). 분석은 본인 것만/공개 결과는 PUBLIC 책 기반 등.
- 책 동일성 키(ISBN 정규화)·장르 분류 출처(알라딘 카테고리?).
- A vs B vs 하이브리드 / 비용·캐싱 / 결과 갱신 주기(책 추가 시 재계산?).
- 결과 저장 모델(파생 캐시 vs 매번 계산), Flyway 신규 여부.
- 재미/오락 정확도 기대치 — "MBTI처럼 가볍게 즐기는 것" 포지셔닝(과신 금지 고지).

**선후**: 데이터 토대(책장·SNS)는 이미 있음 → **프론트와 독립으로 데이터/알고리즘 설계 선행 가능**.
구현 착수 전 **설계 문서 합의 필수**(SNS와 동일 원칙 — 프라이버시·노출 경계가 핵심).

### 관리자(개발자) 대시보드 — 운영 데이터 확인 (계획 ⏳ 2026-06-03, 우선순위: 중)

**왜**: 사용자/타이머/세션/책 등 운영 데이터를 확인하려고 **매번 RDS에 직접 접속하는 게 번거롭다**.
가입자 수·활성 사용자·총 독서 시간 같은 통계나 개별 데이터를 **웹에서 바로 볼 수 있는 관리 화면**이 필요하다.

> ⚠️ **접근 제어가 1순위 — 개발자(ADMIN)만 접근 가능해야 한다.** 운영 데이터(이메일·독서 기록 등 개인정보)가
> 걸려 있어, 일반 사용자에게 새면 개인정보 유출이다. 인가 경계를 먼저 못 박고 화면을 짠다.

- [ ] **접근 제어 (선결)** — `/admin/**`를 `hasRole("ADMIN")`로 보호.
  - 토대는 이미 있음: `Role.ADMIN` enum + `BookTimerUserDetailsService`가 `ROLE_ADMIN`으로 매핑.
    단 현재 `SecurityConfig`는 `anyRequest().authenticated()`만 — **`/admin/**` 역할 매처를 추가**해야 한다.
  - **내 계정을 ADMIN으로 승격하는 경로** 필요(가입은 `Role.USER` 고정). 후보: 운영 DB 1회 수동 `update`
    또는 부트스트랩 시드(설정값으로 지정한 이메일을 ADMIN으로). 자동 가입 승격은 금지(권한 상승 벡터).
- [ ] **통계 요약** — 가입자 수, 최근 N일 활성 사용자, 총/평균 독서 시간, 책 수, 세션 수 등 집계 카드.
  - 대부분 기존 테이블 **집계 쿼리**(N-037: 새 저장 아님, 읽기 전용) → DB 안 건드림. 무거운 집계는 캐시 후보.
- [ ] **데이터 조회** — 사용자 목록(이메일·가입일·온보딩 여부·provider), 사용자별 타이머/세션/책 드릴다운.
  - 읽기 전용 우선(수정·삭제 같은 운영 액션은 별도 단계 — CSRF·감사 로그 검토 후).
  - 개인정보 최소 노출: 필요한 컬럼만, 비밀번호 해시 등 민감값 제외.
- **메모**: SSR(Thymeleaf)로 가볍게 시작 가능(N-017 — 내부 도구라 SEO·인터랙션 요구 없음).
  외부 노출 0이 이상적 → 추후 IP 제한/별도 경로 등 추가 방어 검토. 운영 액션 추가 시 **감사 로그** 동반.

### 프론트엔드 전환 (SSR → SPA) · 앱 프론트 — 선후 의존 정리
- 현재 Thymeleaf SSR. API 계약 안정성 + 인터랙션 요구가 커지면 전환 (N-017)

> 💡 **"SNS가 어느 정도 완성돼야 프론트 교체·앱을 할 수 있나?" → 아니다. 순서가 거꾸로다.**
> SNS는 프론트 교체의 *선행조건*이 아니라, 인터랙션 요구를 키워 전환을 **정당화하는 사유**일 뿐이다.
> 무엇이 무엇을 진짜로 막는지로 보면:
>
> | 작업 | 진짜 선행조건 | SNS에 의존? |
> |---|---|---|
> | **앱 프론트(모바일)** | **안정적인 JSON API** | ❌ (API에 의존) |
> | **프론트 교체(SSR→SPA)** | 인터랙션 요구↑ / API 계약 안정(N-017) | ❌ (SNS가 *촉발*은 함) |
> | **SNS** | 데이터 설계(관계+공개범위) 합의 | — |
>
> - **앱 프론트는 SNS가 아니라 API에 막혀 있다.** 지금은 서버가 HTML을 그려 내려주는 SSR이라, 모바일 앱이
>   먹을 **JSON API가 없다.** 앱의 하드 선행조건은 "SNS 완성"이 아니라 "API 레이어 존재" — SNS가 0%여도
>   API만 있으면 앱은 가능하고, SNS가 100%여도 API가 없으면 앱은 불가능.
> - **프론트 교체의 트리거는 SNS가 아니라 인터랙션 요구(N-017).** SNS(피드·팔로우)는 그 요구를 *키우는*
>   대표 기능 → 선행조건이 아니라 정당화 사유.
> - **실질 의사결정 = "SNS를 두 번 짤 거냐":** SSR로 SNS를 다 짠 뒤 SPA로 교체하면 UI를 **두 번** 만든다.
>   API/SPA부터 깔면 SNS UI는 **한 번**. 갈림길은 **SNS 확신도** —
>   - SNS가 아직 가설(검증 전) → **SSR로 싸게 먼저 검증**(책 인기 카운트 같은 가벼운 집계 선출시가 후보), UI 재작업 감수.
>   - SNS·앱이 확정 방향 → **API부터 추출**해 SNS는 새 스택 위에 한 번만.
> - **단, SNS 데이터 설계(관계·공개범위·IDOR·Flyway 스키마)는 프론트와 완전히 독립** — 되돌리기 가장 어렵고
>   가장 프론트-무관한 부분이라, 프론트 결정과 무관하게 **지금 먼저 분리해서 진행 가능**(§SNS "구현 전 설계 먼저").

---

## 🩹 실사용에서 발견한 문제 (계획 외 — UX/사용성)

> 로드맵·설계에 **없던** 문제들. 내가 앱을 실제로 써 보며(주로 스크린샷으로) 발견해 즉석 수정한 것들이다.
> 기능 로드맵(계획적 확장)과 구분해 여기 모은다 — **"계획에 없었지만 실사용이 드러낸 결함"의 누적 기록**.
> 공통 교훈: **CSS flex 자식이 0으로 줄면 한글이 글자 단위로 깨진다**(N-032 류) / **검색·필터는 사용자 의도(기본값)를
> 명시적으로 고정**해야 한다. 새 문제를 발견하면 계속 추가.

| 증상 (실사용에서 본 것) | 원인 | 해결 | 상태 |
|---|---|---|---|
| 책장에서 **긴 책 제목이 깨져** 레이아웃이 무너짐 | `.book-meta`가 flex 안에서 0폭까지 줄어 한글이 글자 단위 줄바꿈 | `.book-meta flex:1 1 220px` + `.book-row flex-wrap` + `word-break:keep-all` (CSS만) | ✅ PR #110 |
| 대시보드 **하단 "내 책장·독서 기록" 링크가 글자 단위로 줄바꿈**(지저분) | 좁은 flex 컨테이너에서 링크 텍스트가 글자별로 쪼개짐 | 하단 링크를 **퀵 액션 타일 2열 그리드**로 재구성(`.quick-actions`/`.quick-tile`) | ✅ PR #116 |
| 측정 카드 **"읽을 책" 라벨이 세로로 깨지고** select가 카드 밖으로 튀어나옴 | inline-flex + 자식 min-width 미설정으로 라벨 수축·select 오버플로 | `.book-pick` flex+`white-space:nowrap`, select `flex:1 1 auto; min-width:0` | ✅ PR #117 |
| 페이지마다 **하단 네비 디자인이 제각각**(텍스트 링크 vs 타일) | 표준 양식 부재 — 페이지별로 따로 마크업 | `.link-row`를 타일 그리드 양식으로 **CSS만 일괄 통일**(전 페이지, 양식 표준 확정) | ✅ PR #122 |
| 내 책장에서 **상태별로 책을 골라 볼 수 없음**(전부 한 목록) | 필터 기능 자체가 없었음 | 서버사이드 상태 필터(`?status=`, 전체/읽고싶음/읽는중/완독 칩, q 유지) | ✅ PR #122 |
| **"모기" 검색 시 제목이 아닌 저자명("모기 겐이치로")이 먼저** 뜸 — 의도와 어긋남 | 알라딘 `QueryType=Keyword`가 제목·저자를 한꺼번에 매칭 | 검색 기준 **제목(기본)/저자 분리**(`BookSearchType`→`QueryType` Title/Author, 라디오 선택) | ✅ PR #123 |
| 위 분리 후에도 **제목 검색에 저자 매칭이 계속 섞임** — 알라딘 `QueryType=Title`이 문서("제목만")와 달리 저자까지 섞어 반환(비엄격) | 외부 API 동작이 문서와 불일치 — 클라이언트가 신뢰할 수 없음 | **결과 후필터**(`BookService.filterToSearchType`): 고른 기준 필드(제목/저자)에 검색어가 실제로 든 결과만 남김(공백·대소문자 정규화 contains). 외부 API 동작과 무관하게 보장 | ✅ PR #125 |
| 검색 폼 — **검색 바가 좁고 버튼이 과도하게 큼**(가로 배치라 버튼이 입력칸 폭을 잠식) | 입력+버튼을 한 줄 flex로 둬 폭이 경쟁 | 세로 배치 — 넓은 검색 바 위, 전체폭 버튼 아래(`.search-row` column) | ✅ PR #125 |

**관찰 중 / 후속 후보** (아직 미착수 — 발견했으나 우선순위 낮음):
- [ ] 검색 결과·책장에서 검색 기준에 **출판사** 차원 추가 여부(현재 제목/저자만; 알라딘 `Publisher` 지원).
- [ ] 상태 필터에 **공개여부(PUBLIC/PRIVATE) 차원** 추가 가능(현재 상태만).
- [ ] 책 동일성 키 **ISBN 정규화**(ISBN10/13·개정판·세트) — 인기 카운트 정확도·중복 표시에 영향(SNS 카운트 선결과 연계).
- [ ] 검색 후필터(#125)의 **페이저 과대 집계** — 알라딘 `totalResults`(필터 전)로 페이지 수를 계산하므로, 필터로 많이 걸러지면 "N페이지"가 실제보다 많게 보임. 현재는 표시만 헐겁고 동작엔 무해. 정확히 하려면 필터 후 기준 재계산 또는 알라딘 호출 자체를 더 정확히(추후).

---

## ⚖️ 법무 / 지식재산

### 저작권 등록 — 컴퓨터프로그램 저작물 (계획 ⏳ 2026-06-04, 우선순위: 낮음)

**왜**: 분쟁(소스코드 도용 등) 대비 **증거 확보**. 한국은 무방식주의라 등록 없이도 저작권은 이미 우리 것이지만,
등록하면 추정력(저작자·창작일)·대항력·법정손해배상·과실 추정의 이점이 생긴다. 창작 후 **1년 내** 등록해야
창작일 추정이 유효하므로 빠를수록 유리.

> 📄 **상세 가이드 → [claude-docs/copyright-registration.md](claude-docs/copyright-registration.md)** (조건·서류·절차·비용 정본).

- [ ] 저작자 확정 — 개인 vs 법인(업무상저작물)
- [ ] 등록 범위 정리 — 직접 작성 코드 / 오픈소스·서드파티(Spring·드라이버 등) 제외분 구분
- [ ] 발췌할 소스코드 범위 결정(영업비밀 고려, 일부 발췌 가능)
- [ ] CROS(cros.or.kr) 온라인 신청 — 프로그램 등록신청서 + 등록신청명세서 + 소스 업로드 + 수수료(온라인 5만 원대)
- [ ] (선택) 서비스명 "BookTimer" 보호 원하면 **상표 출원**(특허청 KIPO, 저작권과 별개) 검토
- [ ] (선택) 핵심코드 **임치(Escrow)** 제도 병행 검토
- **메모**: 분쟁 대비가 주목적이면 신청 전 한국저작권위원회 무료 상담(1800-5455) 1회 권장. 법률 자문 아님.

---

## 📣 홍보 / 마케팅

> 서비스를 알리는 축. 아직 구체화 전 — 아이디어만 모아두는 단계.

### 커뮤니티 기반 홍보 (계획 ⏳ 2026-06-04, 우선순위: 미정)

**아이디어**: 북카페 같은 **독서 커뮤니티를 통한 홍보**를 우선 검토.
독서 타이머·책 기록이 핵심이라 책 좋아하는 사람이 모인 곳과 결이 맞는다.

- [ ] 자세한 채널·방식·메시지는 **아직 미정** — 추후 구체화.

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

### Fargate CPU 상향 — 로그인(BCrypt) 지연 (완료 ✅ 2026-06-04, PR #132 / 배포 검증은 run)
- **증상**: 로그인이 체감상 느림.
- **원인**: DB 아님(`findByEmail`은 유니크 인덱스 단건 조회 — 수 ms). 범인은 **BCrypt 비밀번호 검증**(의도적 CPU 집약) ×
  **태스크 `cpu:256`=0.25 vCPU**(Fargate 최소). 1/4 코어 스로틀이라 BCrypt가 수백 ms~1s까지 늘어남. JVM JIT 워밍업도 가중.
  실측: health·/login GET는 60~150ms 정상 → 차이는 로그인 POST의 BCrypt뿐.
- **한 일**: `deploy/task-definition.json`의 `cpu` **256→512**(0.5 vCPU), `memory` **512→1024**로 상향.
  (Fargate는 CPU·메모리 조합이 정해져 있어 cpu 512면 memory 최소 1024 — JVM 힙 여유도 같이 확보.) BCrypt 강도(10)는
  **낮추지 않음**(보안). DB는 손대지 않음. ※ Fargate는 vCPU·메모리 비례 과금 — 비용 소폭 증가.
- 개념: learning-notes(로그인 지연 ≠ DB, BCrypt×작은 vCPU / CPU 집약 해시는 의도된 느림 — 해법은 강도↓가 아니라 CPU↑).
- **검증**: 설정 변경은 머지 후 실제 배포(run)에서 로그인 POST 지연 단축으로 확인.

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
| 2026-06-04 | 한국 저작권 등록 가이드 신설(claude-docs/copyright-registration.md) — 조건·서류·절차·비용 정리. 법무 섹션 + README 링크 추가 |
| 2026-06-03 | 독서 잔디 대시보드 노출 + 열 순서 반전(최근=왼쪽, 스크롤 없이 최근 보이게) — 빌더 단일 소스로 세 잔디 일관. TDD |
| 2026-06-03 | SNS 기능 항목에 "구현 전 설계 먼저(필수)" 강조 + 착수 전 합의 사항(공유범위·관계모델·IDOR·스키마·악용·SSR) 명시 |
| 2026-06-03 | 측정 카드 — 타임존(원시 시작시각) 노출 제거 → 읽는 책 + 책별 누적 독서 시간 표시(#97). TDD |
| 2026-06-03 | SNS 확장 시 새로 저장할 건 독서기록 아닌 관계+공개범위 — N-037 + plan.md SNS 보강(#98) |
| 2026-06-03 | 가입 후 온보딩 페이지 — 타이머 초기값(시작 잔여)+증가값+상한 직접 설정(Flyway V6 users.onboarded, 첫 진입 게이트, 기존 사용자 백필 true). 가입 시 무조건 1h 시드를 사용자 선택으로. TDD |
| 2026-06-03 | SNS — 책 인기 카운트(집계 노출) 항목 추가: ① 검색 결과 리스트에 "읽는 중 N·완독 M" 작게 표시, ② 내 책장에 "몇 명이 읽는 중" 표시. ISBN 키 distinct user count 재사용, 관계 없이 선출시 가능, 소수 재식별·N+1·ISBN 정규화 선결 |
| 2026-06-03 | 관리자(개발자) 대시보드 항목 추가 — 매번 RDS 접속 없이 운영 데이터·통계를 웹에서 확인. 접근 제어(ADMIN만, `/admin/**` hasRole) 선결, ADMIN 승격 경로 필요, 통계는 읽기 전용 집계(N-037). 온보딩 타임존 백로그도 함께 |
| 2026-06-04 | 프론트/앱↔SNS 선후 의존 정리 메모 추가 — SNS는 프론트 교체의 선행조건 아님(촉발 사유일 뿐), 앱은 SNS가 아닌 JSON API에 의존, 실질 결정은 "SNS UI를 SSR→SPA로 두 번 짤 것인가". SNS 데이터 설계는 프론트와 독립이라 선행 가능 |
| 2026-06-04 | SNS 설계 문서(claude-docs/sns-design.md) 작성 — 공개범위(PRIVATE 기본 opt-in)·관계(팔로우 단방향)·IDOR canView 게이트·스키마(follow V7/visibility V8)·4단계 로드맵·화면별 SSR/SPA 판단·열린 질문. 결론: SNS 대부분 SSR로 충분 → API-first big-bang 불필요 |
| 2026-06-04 | SNS 사용자 요구사항 1차 확정 반영 — 공개 단위를 프로필 토글→**책 단위**로, 인기 카운트를 전역→**팔로우 스코프**로, **닉네임 유니크**(검색·핸들) 추가. 잔디 viewer 가시성 필터(비공개 책 간접 누출 차단)·canViewBook·닉네임 백필 선결 반영. 로드맵 재정렬(①닉네임+책공개→②프로필→③팔로우→④팔로우스코프 카운트→⑤악용) |
| 2026-06-04 | 잔디 프라이버시 확정 — 공개 안 한 책은 타인 잔디·총시간에서 제외(PUBLIC 책 세션만, book=null도 제외) |
| 2026-06-04 | SNS 1단계 일부 — **닉네임 유니크화 구현**(Flyway V7 중복 백필+`uk_users_nickname`, LOCAL 가입 중복 거부, 소셜 자동 유일화 `NicknameAllocator`, 설정 변경 중복 거부, `existsByNickname`). TDD. 남은 1단계: 책별 공개 토글(`book.visibility`) |
| 2026-06-04 | 소셜 사용자 닉네임 직접 지정 — 구글 가입자가 자동 임시닉 대신 **온보딩에서 닉네임 직접 입력**(폼 필드 추가·prefill·중복 거부). 자동 배정은 온보딩 완료 전 임시값. TDD |
| 2026-06-04 | SNS 1단계 완성 — **책별 공개 토글**(BookVisibility PRIVATE/PUBLIC, Flyway V8 default PRIVATE 백필, `POST /books/{id}/visibility` 소유권 강제, 책장 셀렉트). 닉네임 유니크와 합쳐 1단계 완료 → 다음은 2단계 프로필 페이지(/u/{nickname}). TDD |
| 2026-06-04 | 책장 긴 제목 깨짐 수정 — `.book-meta flex:1 1 220px` + `.book-row flex-wrap` + `word-break:keep-all`(PR #110, CSS만) |
| 2026-06-04 | SNS 2단계 — **개인 공개 프로필 페이지 `GET /u/{nickname}`**(SSR). PUBLIC 책장 + 공개 잔디(PUBLIC 책 세션만, §3.5). 닉네임 404·비로그인 차단·본인도 PUBLIC만(공개 미리보기). `ProfileService`, `publicDailyHistory`/`publicTotalSecondsByBook` 가시성 필터, 대시보드 진입 링크. 신규 마이그레이션 없음. TDD Red→Green 2사이클 |
| 2026-06-04 | SNS 3단계 — **닉네임 검색 + 팔로우**. `GET /search`(부분일치·최소2글자·상한20), `Follow`(Flyway V9)+`FollowService`(자기팔로우 금지·멱등·언팔즉시), `POST /follow`·`/unfollow`(오픈리다이렉트 방어), 프로필에 팔로워/팔로잉 카운트+팔로우 버튼. 검색결과=닉네임+공개책수+팔로우버튼. 탈퇴 시 follow 정리. 대시보드 검색 링크. TDD. (별도 발견: 탈퇴 시 book 미삭제 FK 위반 — 후속 분리) |
| 2026-06-04 | 대시보드 UX 수정 — 하단 바로가기 링크를 퀵 액션 타일 2열 그리드로(한글 글자깨짐 구조 해결, PR #116), 측정 카드 '읽을 책' 라벨 세로깨짐·select 카드밖 넘침 수정(`.book-pick` flex+nowrap+min-width:0, PR #117). CSS/템플릿만 |
| 2026-06-04 | SNS 보강 — **본인 팔로워/팔로잉 목록**(`/me/followers`·`/me/following`, PR #119). 본인 프로필에서만 카운트가 목록으로 링크(남 프로필은 카운트만, privacy 유지). 경로에 닉네임 없어 항상 본인 기준(보안 경계 자동). 행은 검색과 동일(`UserRowAssembler` 공용 추출→검색·목록 재사용). `FollowListService`+`findByFollowee/FollowerOrderByCreatedAtDesc`. Flyway 신규 없음. TDD |
| 2026-06-04 | SNS 4단계 — **팔로우 스코프 인기 카운트**(PR #118). 책장·검색결과 각 책에 "👥 팔로우 중 N명 원함 · M명 읽음". 원함=WANT_TO_READ·읽음=READING∪FINISHED, k익명 임계 없음(drill-down 없어 위험 제한적). `BookRepository.followScopePopularity`(isbn 일괄 group by·팔로우 theta조인·PUBLIC·distinct, N+1 회피)+`FollowScopePopularityService`+`books.html` 프래그먼트. Flyway 신규 없음. TDD(서비스 단위·집계 통합·컨트롤러 끝단) |
| 2026-06-04 | SNS 5단계 시작 — **차단(block)**(PR #121, 대칭). 차단 관계면 서로 팔로우 불가·서로 프로필 404(`existsBetween` 양방향 게이트). 차단 시 기존 팔로우 양방향 해제. `Block`(V10)+`BlockService`+`BlockRepository`, `/block`·`/unblock`·`/me/blocks`(해제는 여기서 — 차단 후 상대 프로필 404라). 프로필에 차단 버튼, 탈퇴 정리에 block 추가. 함정: 동적 path 변수 표현식은 앞서 회피. TDD(서비스·컨트롤러·purge inOrder). 남은 5단계: 신고·레이트리밋·열거완화 |
| 2026-06-04 | UI — ① 하단 네비(`.link-row`)를 대시보드 퀵 액션과 **동일한 타일 양식으로 통일**(CSS만, 전 페이지 일괄 — 표준 양식 확정). ② 내 책장 **상태 필터**(전체/읽고싶음/읽는중/완독, 서버사이드 `?status=`, 부합 책만 표시, q 유지, 필터 칩 활성 표시). `BookController` status 파싱·필터, `books.html` 칩+빈 메시지 분기, `.shelf-filter` CSS. TDD(컨트롤러 필터). (PR #122) |
| 2026-06-04 | 책 검색 **기준 분리**(제목/저자) — 기존 알라딘 `QueryType=Keyword`가 제목·저자를 함께 매칭해 "모기" 검색 시 저자명 매칭이 먼저 떠 의도와 어긋남. 기본=제목, 라디오로 저자 전환. `BookSearchType` enum(제목/저자→`QueryType` Title/Author, `from()` 폴백), 포트 시그니처에 검색기준 추가, `AladinBookSearchClient.buildSearchUrl` 정적 추출(URL 단위테스트), 컨트롤러 `?type=` 파싱·페이징 유지, `books.html` 라디오 + `.search-type`/`.search-row` CSS. TDD(enum·서비스 위임·URL·컨트롤러 모델). (PR #123) |
| 2026-06-04 | 책 검색 정확도·폼 레이아웃 — ① **제목 검색 후필터**(`BookService.filterToSearchType`): 알라딘 `QueryType=Title`이 문서와 달리 저자까지 섞어 반환(비엄격)하는 걸 방어, 고른 기준 필드에 검색어가 실제로 든 결과만 남김(공백·대소문자 정규화 contains, #123 후속). ② 검색 폼 **세로 배치**(넓은 바 위 + 전체폭 버튼 아래, `.search-row` column). 알려진 한계: 페이저가 필터 전 totalResults로 계산해 과대 집계(무해). TDD(제목/저자 후필터·정규화). (PR #125) |
| 2026-06-04 | (아이디어 기록만) **독서 성향 분석 "책장 기반 MBTI"** 항목 추가 — 책장(보유/읽는중/완독+책별 시간)을 AI 또는 알고리즘으로 분석해 독서 성향 도출 → ① 개인화 책 추천 ② 사용자 상호작용(성향 비교·매칭) 컨텐츠. 입력은 기존 데이터(N-037), SNS 토대 재사용. 미확정 多(축 설계·PRIVATE 책 프라이버시·ISBN/장르·A규칙 vs B AI vs 하이브리드). 구현 전 설계 합의 필수. 문서만 |
| 2026-06-04 | **홍보/마케팅 섹션 신설** — 북카페 등 독서 커뮤니티 기반 홍보 아이디어만 가볍게 기록(세부 미정). 문서만 |
| 2026-06-04 | plan.md 정리 — **"실사용에서 발견한 문제(계획 외 UX/사용성)" 섹션 신설**. 스크린샷 피드백으로 발견·수정한 6건(PR #110·#116·#117·#122·#123)을 갱신 이력에서 한 표로 모음(증상·원인·해결·PR) + 후속 후보(출판사 검색 차원·공개여부 필터·ISBN 정규화) 명시. 로드맵 줄에 #123 부수 픽스 반영. 문서만 |
| 2026-06-04 | **전역 인기 카운트 철회 → 팔로우 카운트 drill-down 채택**(4단계+). 전역 카운트는 *팔로우 없이도* 공짜 사회적 증거를 줘 팔로우 가치를 희석한다고 판단(사용자 결정). 대신 카운트 배지 클릭 시 "그 책 원함/읽음인 내 팔로우 명단"(`GET /books/readers`) — 카운트와 같은 게이트(팔로우·PUBLIC·distinct)로 신원만 펼침(새 노출 0, IDOR 없음), 기존 `UserRowAssembler` 재사용. `BookRepository.followScopeReaders` + `FollowScopeReadersService`/`FollowScopeReaders` + `book-readers.html`. Flyway 신규 없음. TDD(서비스 통합·컨트롤러 끝단). 함정 T-### 기록(Thymeleaf th:each+th:replace 우선순위·파라미터 fragment 인라인 NPE) |
| 2026-06-04 | **Fargate CPU 상향**(로그인 BCrypt 지연 완화) — `deploy/task-definition.json` `cpu` 256→512(0.5 vCPU), `memory` 512→1024. 원인은 DB 아닌 BCrypt(의도된 CPU 집약)×0.25 vCPU 스로틀. 강도(10)는 유지(보안), CPU만 상향이 정답. 배포 검증은 run. 설정만(PR #132) |
