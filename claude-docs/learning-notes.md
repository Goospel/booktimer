# 학습 노트 — 작업 중 모르고 물어봐서 배운 것들

> 면접에서 본인이 직접 설명할 수 있는 수준으로 본인 이해 확립.
> 같은 질문 두 번 안 묻기.

## 📑 목차

- [N-001. 누적 카운터 일일 리셋 — 배치 스케줄러 vs Lazy 계산](#n-001-누적-카운터-일일-리셋--배치-스케줄러-vs-lazy-계산)
- [N-002. Gradle toolchain + foojay-resolver — 로컬에 없는 JDK 자동 확보](#n-002-gradle-toolchain--foojay-resolver--로컬에-없는-jdk-자동-확보)
- [N-003. Spring Boot 4 starter 네이밍 변화](#n-003-spring-boot-4-starter-네이밍-변화)
- [N-004. Claude Code 훅으로 워크플로 강제 — 가이드(soft) vs 훅(hard)](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard)
- [N-006. PowerShell 5.1 — native stderr 가 `$EAP=Stop` 과 만나 스크립트를 죽이는 함정](#n-006-powershell-51--native-stderr-가-eapstop-과-만나-스크립트를-죽이는-함정)
- [N-007. Spring Boot 4 autoconfigure / 테스트 슬라이스 모듈화 — 패키지 이동](#n-007-spring-boot-4-autoconfigure--테스트-슬라이스-모듈화--패키지-이동)
- [N-008. JPA Auditing — 누가 시각을 채우나, 그리고 슬라이스 테스트의 함정](#n-008-jpa-auditing--누가-시각을-채우나-그리고-슬라이스-테스트의-함정)
- [N-009. 계층별 테스트 전략 — 도메인 단위 / 슬라이스 / 서비스 mock (테스트 피라미드)](#n-009-계층별-테스트-전략--도메인-단위--슬라이스--서비스-mock-테스트-피라미드)
- [N-010. 테스트 가능한 시간 — Clock 주입 + 절대 시점 vs 유저 타임존 "오늘"](#n-010-테스트-가능한-시간--clock-주입--절대-시점-vs-유저-타임존-오늘)
- [N-011. Spring Security 폼 로그인 — UserDetailsService + PasswordEncoder 두 빈이 인증을 켠다](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-두-빈이-인증을-켠다)
- [N-012. 인증 주체 ≠ 도메인 엔티티 — principal로 도메인 User를 다시 잇고, 접속을 Lazy 누적 트리거로](#n-012-인증-주체--도메인-엔티티--principal로-도메인-user를-다시-잇고-접속을-lazy-누적-트리거로)
- [N-013. Spring Boot 컨테이너화 — 멀티스테이지 Dockerfile + 운영 설정 외부화](#n-013-spring-boot-컨테이너화--멀티스테이지-dockerfile--운영-설정-외부화)
- [N-014. AWS CLI는 로컬에서 실행되지만 클라우드에 작용 — 콘솔/CLI/CloudShell, bash vs PowerShell](#n-014-aws-cli는-로컬에서-실행되지만-클라우드에-작용--콘솔clicloudshell-bash-vs-powershell)
- [N-015. GitHub Actions → AWS 키 없이 배포 — OIDC 페더레이션 + ECS 롤링 배포](#n-015-github-actions--aws-키-없이-배포--oidc-페더레이션--ecs-롤링-배포)
- [N-016. ECS 헬스체크와 콜드스타트 — ALB 타깃 헬스 vs 컨테이너, grace period](#n-016-ecs-헬스체크와-콜드스타트--alb-타깃-헬스-vs-컨테이너-grace-period)
- [N-017. SSR(Thymeleaf)→SPA 전환 시점 — "백엔드 몇 %"가 아니라 API 계약 안정성 + 인터랙션 요구](#n-017-ssrthymeleafspa-전환-시점--백엔드-몇-가-아니라-api-계약-안정성--인터랙션-요구)
- [N-018. 퍼블릭 IP ≠ 인터넷 접근 — 서브넷 라우트테이블이 진짜 관문](#n-018-퍼블릭-ip--인터넷-접근--서브넷-라우트테이블이-진짜-관문)
- [N-019. DB 유니크 제약은 무결성의 마지막 방어선이지, 사용자 검증의 첫 방어선이 아니다](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다)
- [N-020. CI 트리거 필터 — `paths-ignore`는 "전부 매칭될 때만" 스킵하는 안전 기본값](#n-020-ci-트리거-필터--paths-ignore는-전부-매칭될-때만-스킵하는-안전-기본값)
- [N-021. HTTPS는 앱이 아니라 앞단에서 끝낸다 — TLS termination (ALB/ACM)](#n-021-https는-앱이-아니라-앞단에서-끝낸다--tls-termination-albacm)
- [N-022. 프록시 뒤의 앱은 X-Forwarded-*를 신뢰해야 한다 — forward-headers와 명시 빈](#n-022-프록시-뒤의-앱은-x-forwarded를-신뢰해야-한다--forward-headers와-명시-빈)
- [N-023. ddl-auto=update의 한계 — 스키마 드리프트와 마이그레이션(Flyway)](#n-023-ddl-autoupdate의-한계--스키마-드리프트와-마이그레이션flyway)
- [N-024. Spring Boot 4의 autoconfig 모듈 분리 + 기존 DB에 Flyway 도입(baseline)](#n-024-spring-boot-4의-autoconfig-모듈-분리--기존-db에-flyway-도입baseline)
- [N-025. 로그인 지연의 범인은 보통 DB가 아니라 BCrypt × 작은 vCPU](#n-025-로그인-지연의-범인은-보통-db가-아니라-bcrypt--작은-vcpu)
- [N-026. OAuth find-or-create의 함정(email_verified) + Spring Security가 막아주지 않는 것(brute-force)](#n-026-oauth-find-or-create의-함정email_verified--spring-security가-막아주지-않는-것brute-force)
- [N-027. OAuth 동의 화면은 provider가 제공 / 개인정보처리방침은 앱 제작자 책임 — 게시(Production)와 검증](#n-027-oauth-동의-화면은-provider가-제공--개인정보처리방침은-앱-제작자-책임--게시production와-검증)
- [N-028. catch-all 예외 핸들러는 프레임워크의 상태보유 예외(404 등)까지 삼킨다 — 상태코드 보존](#n-028-catch-all-예외-핸들러는-프레임워크의-상태보유-예외404-등까지-삼킨다--상태코드-보존)
- [N-029. 인메모리 세션은 인스턴스가 죽으면 사라진다 — 세션 외부화와 무상태 앱 서버](#n-029-인메모리-세션은-인스턴스가-죽으면-사라진다--세션-외부화와-무상태-앱-서버)
- [N-030. 무중단 롤링 배포 — min/max healthy percent로 "헬스 통과 후 교체", circuit breaker 자동 롤백](#n-030-무중단-롤링-배포--minmax-healthy-percent로-헬스-통과-후-교체-circuit-breaker-자동-롤백)
- [N-031. SameSite=Lax로 CSRF 사전 차단 — 그리고 세션 쿠키 속성은 프로퍼티가 아니라 명시 CookieSerializer 빈으로](#n-031-samesitelax로-csrf-사전-차단--그리고-세션-쿠키-속성은-프로퍼티가-아니라-명시-cookieserializer-빈으로)
- [N-032. 다중 세션 동시 작업 — git worktree로 워킹 트리 분리 (브랜치만으론 부족)](#n-032-다중-세션-동시-작업--git-worktree로-워킹-트리-분리-브랜치만으론-부족)
- [N-033. 분석용 클릭 추적은 GET 리다이렉트 — CSRF 면제와 오픈 리다이렉트 트레이드오프](#n-033-분석용-클릭-추적은-get-리다이렉트--csrf-면제와-오픈-리다이렉트-트레이드오프)
- [N-034. 부모 엔티티 삭제와 자식 FK — 연결 끊기(unlink) vs 함께 삭제(cascade), 그리고 같은 버그의 두 예외](#n-034-부모-엔티티-삭제와-자식-fk--연결-끊기unlink-vs-함께-삭제cascade-그리고-같은-버그의-두-예외)

---

## N-001. 누적 카운터 일일 리셋 — 배치 스케줄러 vs Lazy 계산

**한 줄 요약**: "매일 일정량 자동 증가"하는 카운터는 자정 배치로 전 사용자를 돌리기보다, 사용자가 접속할 때 경과 일수만큼 소급 계산(Lazy)하는 편이 타임존 처리와 비용 면에서 유리하다.

### 자세한 설명

BookTimer의 핵심은 "매일 목표 시간이 +증가값 되고, 안 읽은 잔여는 다음 날로 이월"되는 타이머다. 이 "다음 날" 갱신을 구현하는 두 가지 길이 있다.

**1. 배치 스케줄러 방식**
- 자정마다 스케줄러(예: Spring `@Scheduled`, 크론)가 전 사용자 레코드를 순회하며 `목표 += 증가값` 처리.
- 문제점:
  - **타임존**: 사용자마다 자정 시각이 다름 → "어느 자정"에 돌릴지 복잡. 단일 서버 자정에 일괄 처리하면 해외 사용자에게 어긋남.
  - **비용**: 접속도 안 한 사용자까지 매일 전부 UPDATE. 사용자 수가 늘수록 부담.
  - **결합도**: 스케줄러라는 별도 인프라/실패 지점이 생김.

**2. Lazy 계산 방식 (채택)**
- 갱신을 "쓰는 시점"이 아니라 "읽는 시점"으로 미룸.
- 사용자가 타이머 화면에 진입할 때:
  ```
  경과일수 = (오늘_날짜(사용자TZ) - 마지막계산일) in days
  if 경과일수 > 0:
      목표 += 경과일수 × 증가값      // 안 들어온 날도 소급
      목표 = min(목표, cap)         // 누적 잔여 총합 상한
      마지막계산일 = 오늘_날짜
  ```
- 장점:
  - **타임존**: 계산할 때 그 사용자의 TZ로 `오늘`을 구하면 됨 → 사용자별 자정이 자연스럽게 반영.
  - **비용**: 접속한 사용자만, 접속할 때 한 번 계산. 유휴 사용자엔 비용 0.
  - **인프라 단순**: 별도 스케줄러 불필요.
- 트레이드오프:
  - "접속 안 해도 쌓인다"는 결과는 **다음 접속 시점에 한꺼번에** 반영됨 (실시간 아님). cap이 있어 폭증은 막힘.
  - 통계/푸시 알림처럼 "접속 안 한 사용자에게도 능동적으로" 무언가 해야 하면 결국 배치가 필요 → 그땐 하이브리드.

### 일반화 포인트 (면접 답변용)

- 이건 DB에서 **"파생 값을 미리 계산(eager/write-time)할까, 조회 시 계산(lazy/read-time)할까"** 의 고전적 트레이드오프다.
- 판단 기준: **읽기/쓰기 빈도 비율**과 **누가 트리거를 갖는가**.
  - 갱신 트리거(시간 경과)가 외부에 있고, 결과는 본인이 볼 때만 필요 → Lazy 유리.
  - 모든 사용자에게 동시에 결과를 보여줘야 함(랭킹 등) → 배치/미리계산 유리.

### 코드 위치

- (구현 예정) 타이머 조회 서비스 — Lazy 누적 로직
- 관련: `README.md` 6~7번 (도메인 규칙 + 의사 코드)

### 관련 노트

- (아직 없음)

---

## N-002. Gradle toolchain + foojay-resolver — 로컬에 없는 JDK 자동 확보

**한 줄 요약**: Gradle의 Java toolchain은 "이 프로젝트는 JDK 21로 빌드한다"를 선언하는 기능이고, foojay-resolver 플러그인을 붙이면 로컬에 그 버전이 없을 때 Gradle이 알아서 다운로드해 쓴다. 덕분에 개발 PC에 깔린 JDK 버전과 무관하게 빌드가 재현된다.

### 자세한 설명

BookTimer를 Java 21로 만들었는데, 작업 PC엔 **Java 25만** 깔려 있었다. 그런데도 빌드가 성공했다 — 왜?

- `build.gradle`의 toolchain 선언:
  ```groovy
  java {
      toolchain {
          languageVersion = JavaLanguageVersion.of(21)
      }
  }
  ```
  이건 "이 프로젝트는 **JDK 21로 컴파일/실행한다**"는 선언이다. Gradle을 띄운 JVM(25)과 **별개**로, 빌드에 쓸 JDK를 따로 지정하는 것. → 팀원마다 로컬 JDK가 달라도 산출물이 동일.

- 문제: 로컬에 JDK 21이 없으면? Gradle은 설치된 JDK들을 탐색하는데, 21이 없으면 **빌드 실패**한다.

- 해결: `settings.gradle`에 **foojay-resolver-convention** 플러그인 추가
  ```groovy
  plugins {
      id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
  }
  ```
  이러면 Gradle이 없는 toolchain을 **foojay Disco API**(Adoptium 등 배포처를 모아둔 메타 API)를 통해 자동 다운로드해서 캐시(`~/.gradle/...`)에 깔고 쓴다. 그래서 JDK 25만 있던 환경에서도 21 빌드가 성공한 것.

### 일반화 포인트 (면접 답변용)

- **"빌드 실행 JVM"과 "빌드 대상(타깃) JDK"는 다른 개념**이다. toolchain은 후자를 고정해 *빌드 재현성*을 확보한다.
- Spring Initializr가 기본으로 foojay를 넣어주진 않는다. 로컬에 타깃 JDK가 없으면 직접 추가해야 한다.
- 비슷한 사상: Node의 `.nvmrc`, Python의 pyenv — "프로젝트가 요구하는 런타임 버전을 코드로 선언하고 자동 확보".

### 코드 위치

- `settings.gradle` — foojay-resolver-convention 플러그인
- `build.gradle` — `java.toolchain.languageVersion`

### 관련 노트

- [N-003. Spring Boot 4 starter 네이밍 변화](#n-003-spring-boot-4-starter-네이밍-변화)

---

## N-003. Spring Boot 4 starter 네이밍 변화

**한 줄 요약**: Spring Boot 4.x부터 starter 의존성 이름이 일부 바뀌었다. 대표적으로 `spring-boot-starter-web` → `spring-boot-starter-webmvc`, 그리고 테스트 의존성이 `spring-boot-starter-test` 하나가 아니라 **모듈별 test starter**(`...-webmvc-test`, `...-data-jpa-test` 등)로 쪼개졌다.

### 자세한 설명

start.spring.io로 받은 `build.gradle`이 3.x 예제와 달라서 당황할 수 있다. 오늘(2026-05) 기준 Initializr 디폴트가 **Spring Boot 4.0.6**이었고, 의존성 이름이 다음과 같았다.

| 3.x 관습 | 4.x (이 프로젝트) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` (통합 1개) | `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, `-validation-test`, `-thymeleaf-test`, `-actuator-test` (모듈별) |

- 의미: 4.x는 starter를 **더 잘게 모듈화**했다. 필요한 슬라이스만 가져와 의존성 그래프를 가볍게 한다는 방향.
- 실무 함정: 인터넷 예제(대부분 2.x~3.x)를 그대로 복붙하면 `spring-boot-starter-web`를 못 찾거나, 테스트에서 특정 슬라이스 의존성이 없어 컴파일 깨질 수 있다. **버전에 맞는 starter 이름을 확인**해야 한다.

### 일반화 포인트 (면접 답변용)

- 프레임워크 메이저 버전업 시 **의존성 좌표(coordinates)·자동설정·기본값**이 바뀔 수 있다 → 예제 코드의 "어느 버전 기준인가"를 항상 의식.
- starter는 "관련 의존성 묶음(BOM 관리)" — 잘게 쪼개면 빌드/테스트가 빨라지지만 사용자가 더 명시적으로 골라야 한다 (편의 vs 명시성 트레이드오프).

### 코드 위치

- `build.gradle` — dependencies 블록

### 관련 노트

- [N-002. Gradle toolchain + foojay-resolver](#n-002-gradle-toolchain--foojay-resolver--로컬에-없는-jdk-자동-확보)

---

## N-004. Claude Code 훅으로 워크플로 강제 — 가이드(soft) vs 훅(hard)

**한 줄 요약**: "main 직접 push 금지", "테스트 통과 없이 커밋 금지" 같은 워크플로 규칙은 CLAUDE.md 메모(soft, 모델이 읽고 판단)와 settings.json 훅(hard, 하네스가 도구 호출 자체를 차단)의 두 층으로 강제할 수 있다. 판단이 필요한 규칙은 가이드, 무조건 막아야 하는 규칙은 훅 — 둘을 역할 분담하는 게 핵심.

### 자세한 설명

BookTimer에 두 가지 규칙을 훅으로 박았다.
- `block-main-push.ps1` — `git push` 가 main/master 를 직접 겨냥하면 차단.
- `require-tests-before-commit.ps1` — 스테이징에 `.java` 변경이 있으면 `./gradlew test` 를 돌리고 실패 시 커밋 차단.

**왜 가이드(CLAUDE.md)만으로는 부족한가**
- CLAUDE.md 규칙은 "모델이 읽고 따르는" soft 규칙이다. 대부분 잘 지키지만, 드물게 잊거나 맥락상 생략할 수 있다.
- "절대 일어나면 안 되는 일"(main 오염, 깨진 코드 커밋)은 모델 판단에 맡기기엔 위험 → 하네스 레벨에서 **물리적으로** 막는 훅이 필요.

**훅의 구조 (Claude Code PreToolUse)**
- `settings.json` 의 `hooks.PreToolUse` 에 `matcher`(예: `Bash|PowerShell`)와 실행할 command 를 등록.
- 도구 실행 **직전에** 훅이 호출되며, 도구 입력(JSON)이 stdin 으로 들어온다 → 스크립트가 명령 문자열을 검사.
- **exit code 의 의미**: `0` = 통과, **`2` = 차단**(도구 실행 안 됨, stderr 가 모델에게 전달됨), 그 외 = 일반 에러.
- **설계 원칙 3가지**:
  1. **Fail-open** — 입력 파싱 실패, 도구 부재 등 "판단 불가" 상황에선 통과시킨다(정상 작업 방해 금지). 막는 것보다 흘리는 게 안전한 경우.
  2. **명시적 override 토큰** — `ALLOW_MAIN_PUSH` / `SKIP_TESTS` 처럼, 사용자가 명시적으로 허용했을 때만 우회할 탈출구를 둔다. 규칙이 100% 경직되면 정당한 예외(RED 테스트 선커밋 등)에서 막혀버린다.
  3. **좁은 매칭** — 관심 명령만 잡고 나머지는 즉시 통과. (`git push` 아니면 바로 exit 0)

### 일반화 포인트 (면접 답변용)

- **정책(policy)을 어디서 강제하는가** 의 문제다. 같은 규칙도 "문서(사람이 읽음)" / "린트·CI(파이프라인)" / "pre-commit·hook(로컬 차단)" / "브랜치 보호 규칙(서버 차단)" 등 여러 층에서 강제할 수 있고, 각 층은 우회 가능성과 마찰이 다르다.
- soft(판단 여지) vs hard(물리 차단)의 트레이드오프: hard 는 안전하지만 정당한 예외까지 막을 수 있어 **override 설계**가 필수.
- 이건 git 의 서버측 branch protection 과 같은 사상 — 다만 여기선 "AI 에이전트의 도구 호출"을 가로채는 위치라는 점이 다르다.

### 코드 위치

- `.claude/settings.json` — PreToolUse 훅 등록
- `.claude/hooks/block-main-push.ps1`, `.claude/hooks/require-tests-before-commit.ps1`
- `CLAUDE.md` — 대응하는 soft 규칙(PR 우선, TDD)

### 관련 노트

- [N-006. PowerShell 5.1 native stderr 함정](#n-006-powershell-51--native-stderr-가-eapstop-과-만나-스크립트를-죽이는-함정) — 이 훅을 구현하다 실제로 만난 버그

---

## N-006. PowerShell 5.1 — native stderr 가 `$EAP=Stop` 과 만나 스크립트를 죽이는 함정

**한 줄 요약**: PowerShell 5.1에서 `$ErrorActionPreference='Stop'` 일 때, 외부(native) 실행파일이 stderr 로 뭔가를 출력하면 — 그 명령이 종료코드 0(성공)이어도 — PowerShell 이 이를 terminating error(`NativeCommandError`)로 승격시켜 스크립트를 그 줄에서 죽인다. 종료코드로 성공/실패를 판정하려던 로직이 통째로 망가진다.

### 자세한 설명

테스트 게이트 훅(`require-tests-before-commit.ps1`)이 `./gradlew test` 를 돌리고 종료코드로 통과 여부를 판정하도록 짰는데, **테스트가 통과해도 게이트가 항상 차단**되는 버그가 났다.

원인:
- 스크립트 상단에 `$ErrorActionPreference = 'Stop'` (다른 에러를 확실히 잡으려고).
- gradlew 는 정상 실행 중에도 stderr 로 경고를 찍는다:
  `OpenJDK 64-Bit Server VM warning: Sharing is only supported ...`
- PowerShell 5.1 은 native 명령의 stderr 출력을 ErrorRecord 로 감싸는데, `$EAP='Stop'` 이면 이게 **terminating error 로 승격** → `& $gradlew ... test` 줄에서 예외가 던져지고, 그 아래 `$LASTEXITCODE` 판정 로직은 **실행조차 안 됨**.
- 결과: 테스트 성공/실패와 무관하게 스크립트가 비정상 종료(exit 1) → `.java` 커밋이 전부 막힘. "차단은 되는데 이유가 틀린" 가짜 동작.

해결:
```powershell
# native 명령을 cmd.exe 로 격리 실행하고, 그 종료코드만 본다
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'           # 이 구간만 Stop 해제
cmd.exe /c "`"$gradlew`" -p `"$cwd`" test --console=plain >nul 2>nul"
$testExit = $LASTEXITCODE                      # gradlew 의 진짜 종료코드
$ErrorActionPreference = $prevEAP
```
- `cmd.exe /c` 안에서 `>nul 2>nul` 로 stdout/stderr 를 cmd 레벨에서 버리면, PowerShell 이 stderr 를 ErrorRecord 로 감쌀 일 자체가 없어진다.
- `$EAP` 를 그 구간만 `Continue` 로 두는 것도 함께 적용(이중 안전).

### 일반화 포인트 (면접 답변용)

- **"종료코드(exit code)"와 "stderr 출력"은 별개 신호다.** stderr 에 뭔가 찍혔다고 실패가 아니다(경고도 stderr 로 나온다). 성공/실패는 종료코드로 판정해야 한다.
- PowerShell 5.1 의 native 명령 처리는 이 둘을 혼동하게 만드는 함정이 있다 → native 도구(git, gradlew, docker 등) 호출 시 stderr 리다이렉트를 조심.
- 방어법: native 호출을 `cmd.exe /c` 로 격리하거나, stderr 를 명시적으로 분리 처리하고, 판정은 항상 `$LASTEXITCODE` 로.

### 코드 위치

- `.claude/hooks/require-tests-before-commit.ps1` — 테스트 실행 구간

### 관련 노트

- [N-004. Claude Code 훅으로 워크플로 강제](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — 이 버그가 난 훅

---

## N-007. Spring Boot 4 autoconfigure / 테스트 슬라이스 모듈화 — 패키지 이동

**한 줄 요약**: Spring Boot 4는 자동설정과 테스트 슬라이스를 umbrella 모듈에서 **기술별(모듈별) 아티팩트·패키지**로 쪼갰다. 그래서 `@DataJpaTest` 같은 슬라이스 애너테이션의 import 경로가 바뀌었다 — 의존성을 넣어도 옛 import면 "package does not exist"로 컴파일이 깨진다.

### 자세한 설명

`@DataJpaTest`로 Repository 슬라이스 테스트를 짰는데, 의존성(`spring-boot-starter-data-jpa-test`)이 분명히 있는데도 컴파일이 깨졌다.
```
error: package org.springframework.boot.test.autoconfigure.orm.jpa does not exist
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
```

- **3.x**: `@DataJpaTest` 위치 = `org.springframework.boot.test.autoconfigure.orm.jpa` (umbrella `spring-boot-test-autoconfigure` 한 덩어리).
- **4.x**: data-jpa 모듈로 이동 → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, 아티팩트 `spring-boot-data-jpa-test`.
- N-003(starter 네이밍 분화)과 **같은 뿌리** — 4.x의 "모듈별로 잘게 쪼갬" 방향이 자동설정·테스트 슬라이스의 **패키지 구조**에도 적용됐다.
- 함정: 의존성은 멀쩡한데 import만 옛 경로 → "package does not exist"라 원인을 **의존성 누락으로 오해**하기 쉽다. 실제론 import 경로 문제.
- 진단법(추측 금지): 클래스가 어느 jar/패키지인지 직접 확인.
  ```bash
  for j in $(find ~/.gradle/caches/modules-2 -name 'spring-boot*.jar'); do \
    unzip -l "$j" 2>/dev/null | grep -q 'DataJpaTest.class' && { echo "$j"; unzip -l "$j" | grep DataJpaTest; }; done
  ```

### 일반화 포인트 (면접 답변용)

- 메이저 버전업은 의존성 좌표뿐 아니라 **패키지 구조**도 바꾼다. "import가 안 잡힌다 = 의존성 누락"이라는 1차 추론이 틀릴 수 있음 → 클래스의 **실제 위치(jar)**를 확인하는 게 확실.
- 모듈화(잘게 쪼갬)는 빌드 경량화·명시성↑의 이점 대신, 마이그레이션 시 import 변경 비용을 만든다 (편의 vs 명시성, N-003과 동일 트레이드오프).

### 코드 위치

- `src/test/java/com/booktimer/user/UserRepositoryTest.java` — `@DataJpaTest` import (신 경로)
- 관련: `troubleshooting.md` T-006 (즉시 해결 절차)

### 관련 노트

- [N-003. Spring Boot 4 starter 네이밍 변화](#n-003-spring-boot-4-starter-네이밍-변화) — 같은 "모듈별 분화" 뿌리

---

## N-008. JPA Auditing — 누가 시각을 채우나, 그리고 슬라이스 테스트의 함정

**한 줄 요약**: `createdAt`/`updatedAt`을 코드가 매번 `set` 하지 않아도 JPA가 자동으로 채운다. 이건 `AuditingEntityListener`(엔티티 라이프사이클 콜백)가 하고, `@EnableJpaAuditing`이 그 리스너를 켜는 스위치다. 그런데 `@DataJpaTest` 슬라이스는 이 스위치를 자동으로 로드하지 않아 — 그냥 두면 시각이 `null`로 남는다.

### 자세한 설명

세 조각이 맞물려 동작한다:

1. **`@MappedSuperclass` 공통 베이스** (`BaseTimeEntity`) — 상속만 하고 자체 테이블은 없는 부모. `@CreatedDate`/`@LastModifiedDate` 필드를 여기 한 번만 두면 모든 엔티티가 컬럼으로 물려받는다(상속, 중복 제거).
2. **`@EntityListeners(AuditingEntityListener.class)`** — 이 엔티티의 persist/update 직전에 리스너의 콜백이 끼어든다. `AuditingEntityListener`가 그 순간 현재 시각을 `@CreatedDate`(최초 persist만)/`@LastModifiedDate`(persist+update) 필드에 써넣는다.
3. **`@EnableJpaAuditing`** — 위 리스너를 실제로 활성화하는 전역 스위치. **이게 없으면 리스너가 붙어 있어도 시각이 안 채워진다.** 보통 `@Configuration` 한 곳에 둔다.

**함정 — `@DataJpaTest`에선 auditing이 꺼져 있다**:
- `@DataJpaTest`는 "JPA에 필요한 빈만" 최소로 올리는 슬라이스다. 그래서 메인 앱의 `@EnableJpaAuditing`(일반 `@Configuration`)을 자동으로 줍지 않는다.
- 결과: 슬라이스 테스트에서 저장해도 `createdAt`이 `null` → "auditing이 왜 안 되지?"로 헤맨다. 의존성·애너테이션은 멀쩡한데 **스위치만 슬라이스 밖에 있는** 상황.
- 해결: 테스트에 `@Import(JpaConfig.class)`로 `@EnableJpaAuditing` 설정을 명시적으로 끌어온다.

```java
@DataJpaTest
@Import(JpaConfig.class)   // 이게 없으면 createdAt/updatedAt 이 null
class AuditingTest { ... }
```

### 일반화 포인트 (면접 답변용)

- "값을 코드가 안 넣었는데 채워졌다" = 누군가(리스너/콜백)가 라이프사이클에 끼어든 것. JPA auditing은 **persist/update 콜백**에 시각을 주입하는 메커니즘이다.
- **애너테이션이 곧 동작은 아니다** — `@CreatedDate`는 "여기에 시각을 넣어라"는 표식일 뿐, 실제로 넣는 주체(리스너)와 그 주체를 켜는 스위치(`@EnableJpaAuditing`)가 따로 있다. 표식·실행자·스위치 3분리.
- **슬라이스 테스트는 의도적으로 일부만 로드한다** — 편해 보이지만 "메인에선 되는데 슬라이스에선 안 되는" 차이를 만든다. 슬라이스가 무엇을 빼는지 알고 필요한 설정은 `@Import`로 명시적으로 넣어야 한다(N-007의 "슬라이스는 최소 구성" 감각과 연결).
- 시각 타입은 타임존 무관한 `Instant`를 썼다 — "언제 저장됐나"는 절대 시점이라 사용자 타임존(`timezone` 필드)과 분리하는 게 맞다.

### 코드 위치

- `src/main/java/com/booktimer/common/BaseTimeEntity.java` — 공통 베이스(`@MappedSuperclass` + 리스너)
- `src/main/java/com/booktimer/config/JpaConfig.java` — `@EnableJpaAuditing` 스위치
- `src/test/java/com/booktimer/AuditingTest.java` — `@Import(JpaConfig.class)` 슬라이스 테스트
- 관련: `troubleshooting.md` T-007 (슬라이스에서 createdAt null 즉시 해결)

### 관련 노트

- [N-007. Spring Boot 4 autoconfigure / 테스트 슬라이스 모듈화](#n-007-spring-boot-4-autoconfigure--테스트-슬라이스-모듈화--패키지-이동) — "슬라이스는 최소만 로드" 감각의 연장

---

## N-009. 계층별 테스트 전략 — 도메인 단위 / 슬라이스 / 서비스 mock (테스트 피라미드)

**한 줄 요약**: 같은 동작을 모든 계층에서 또 검증하지 않는다. 계층마다 "그 계층만의 책임"을 가장 싼 방법으로 테스트한다 — 도메인 규칙은 순수 단위 테스트, 영속성은 슬라이스(`@DataJpaTest`), 서비스의 **조립(orchestration)**은 레포지토리를 mock한 단위 테스트. 이게 테스트 피라미드(아래로 갈수록 많고 빠르고, 위로 갈수록 적고 느리다).

### 자세한 설명

`ReadingSessionService.stop()`은 "진행 중 세션을 찾아 → 종료하고 → 측정량을 타이머에서 차감하고 → 둘 다 저장"하는 **조립**이다. 이걸 어떻게 테스트할지 두 갈래가 있었다:

- **통합 테스트** (`@SpringBootTest`/`@DataJpaTest` + 실제 빈): 진짜 H2·트랜잭션으로 저장·조회·롤백까지 실증. 느리고 무겁다.
- **Mockito 단위 테스트**: 레포지토리를 mock으로 주입하고, 서비스가 **올바른 협력을 했는지**만 본다(중복이면 거부, `end` 후 `deduct` 호출, 양쪽 `save`). Spring 컨텍스트 없이 ms 단위로 끝난다.

여기선 **단위(mock)** 를 골랐다. 이유는 "각 책임이 이미 다른 곳에서 검증되기 때문":

| 검증 대상 | 책임 위치 | 테스트 종류 |
|---|---|---|
| 누적 차감이 0 밑으로 안 감(floor) | `ReadingTimer.deduct` | 도메인 단위 (경계값) |
| 종료 시각/길이 계산, 중복 종료 거부 | `ReadingSession.end` | 도메인 단위 |
| `findByUserAndEndedAtIsNull` 가 진행 중만 반환 | Repository | 슬라이스 `@DataJpaTest` |
| **이 조각들을 올바른 순서로 엮음** | `ReadingSessionService` | **서비스 mock 단위** |

서비스 테스트에서 실제 DB를 또 띄우면, 이미 슬라이스가 본 영속성을 중복 검증하면서 느려질 뿐이다. 서비스의 고유 책임은 "조립"이라 그것만 본다.

핵심 도구:
- `@ExtendWith(MockitoExtension.class)` + `@Mock` 레포지토리 + `@InjectMocks` 서비스 — 생성자 주입이면 Mockito가 mock을 꽂아준다.
- `when(repo.save(any())).thenAnswer(returnsFirstArg())` — 저장이 인자를 그대로 돌려주게 해, 저장 후 반환값을 검증.
- `verify(repo).save(x)` / `verify(repo, never()).save(any())` — "협력했는가"를 직접 단언(상태가 아니라 상호작용 검증).

### 일반화 포인트 (면접 답변용)

- **"무엇을 테스트하느냐"는 "무엇이 그 계층의 책임이냐"로 결정된다.** 도메인은 규칙, 레포지토리는 쿼리 매핑, 서비스는 조립. 책임이 다르면 테스트 종류도 다르다.
- **중복 커버리지는 비용이다.** 같은 동작을 단위·슬라이스·통합에서 3번 보면 느려지고 깨질 곳만 늘어난다. 피라미드는 "한 번만, 가장 싼 층에서".
- **상태 검증 vs 상호작용 검증**: 도메인은 결과 상태(`remaining == 0`)를, 조립은 상호작용(`deduct가 호출됐나`)을 본다. mock은 후자에 적합.
- 단, 통합 테스트를 아예 안 하는 게 아니다 — 와이어링·트랜잭션·실제 SQL은 슬라이스/소수의 통합이 책임진다. mock 단위는 그 위에 얹는 빠른 층.

### 코드 위치

- `src/main/java/com/booktimer/session/ReadingSessionService.java` — 조립 대상
- `src/test/java/com/booktimer/session/ReadingSessionServiceTest.java` — Mockito 단위 (`@InjectMocks`, `returnsFirstArg`, `verify`)
- 대비: `ReadingTimerTest`(도메인 단위), `ReadingSessionRepositoryTest`(슬라이스)

### 관련 노트

- [N-008. JPA Auditing — 슬라이스 테스트의 함정](#n-008-jpa-auditing--누가-시각을-채우나-그리고-슬라이스-테스트의-함정) — 슬라이스가 "무엇만 로드하는지" 감각

---

## N-010. 테스트 가능한 시간 — Clock 주입 + 절대 시점 vs 유저 타임존 "오늘"

**한 줄 요약**: `LocalDate.now()` 처럼 "지금"을 코드 안에서 직접 읽으면 테스트가 실행 시점·서버 타임존에 휘둘려 비결정적이 된다. "지금"을 `java.time.Clock` 으로 **주입**하면 테스트에서 `Clock.fixed(...)` 로 고정해 자정 경계까지 결정적으로 검증할 수 있다. 그리고 "절대 시점(instant)"과 "민간 날짜(오늘)"은 다른 개념 — 오늘은 누구의 타임존이냐에 따라 달라진다.

### 자세한 설명

누적 타이머는 "유저 타임존 기준 오늘"까지 따라잡아야 한다(N-001 Lazy 계산). 두 가지가 문제였다:

1. **"지금"을 어떻게 테스트하나** — 서비스가 `LocalDate.now()` 를 직접 부르면, 테스트는 "오늘"이 실제 오늘이라 매일 다른 결과가 나오고 자정 경계 같은 케이스를 짤 수 없다. 해결: `Clock` 을 빈으로 주입.
   - 운영: `@Bean Clock clock() { return Clock.systemUTC(); }`
   - 테스트: 빈 대신 `Clock.fixed(Instant.parse("2026-06-01T16:00:00Z"), ZoneOffset.UTC)` 를 직접 생성자에 주입 → "지금"이 그 순간으로 고정.

2. **절대 시점 ≠ 오늘** — `clock.instant()` 는 타임존과 무관한 한 점(UTC 기준 절대 시각)이다. 하지만 "오늘 며칠이냐"는 **보는 사람의 타임존**에 따라 다르다. 같은 순간이라도 서울(+9)에선 이미 다음 날일 수 있다.
   - `LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));`
   - 예: `2026-06-01T16:00Z` 라는 절대 시점 → 서울에선 `2026-06-02`, UTC에선 `2026-06-01`. 유저는 서울에 사니 "오늘"은 06-02.

이 둘을 합치면 자정 경계 테스트가 **TZ 버그를 잡는 함정**이 된다: 위 순간에 서울 유저의 타이머를 누적시키면 06-02까지 1일치가 쌓여야 한다. 만약 코드가 실수로 서버(UTC) 기준으로 오늘을 계산했다면 06-01이라 누적이 0 → 테스트가 빨갛게 실패해서 버그를 드러낸다.

```java
// 운영: 절대 시점은 시스템 시계가, '오늘'은 유저 TZ가 결정
LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));

// 테스트: Clock.fixed 로 '지금'을 고정 → 자정 경계도 재현 가능
var service = new ReadingTimerService(timerRepo, Clock.fixed(instant, ZoneOffset.UTC));
```

### 일반화 포인트 (면접 답변용)

- **부수효과(현재 시각 읽기)를 의존성으로 바꾼다.** `now()` 직접 호출은 숨은 전역 입력 → 주입하면 테스트가 통제권을 갖는다. 난수(`Random`)·UUID도 같은 처방.
- **시간엔 두 종류가 있다**: 타임라인의 한 점(`Instant`, TZ 무관, "언제 일어났나")과 달력/벽시계 값(`LocalDate`/`LocalDateTime`, TZ 의존, "사람이 부르는 날짜/시각"). 변환에는 항상 **누구의 타임존**이 필요하다.
- 저장은 절대 시점(`Instant`, auditing의 createdAt도 — N-008)으로, 도메인 경계(일일 리셋)는 유저 TZ로 — 역할을 분리한다.
- 테스트에서 자정·월말·DST 경계는 `Clock.fixed` 로 콕 집어 재현할 수 있어야 한다. "현재 시각에 의존하는 테스트"는 플래키의 단골.

### 코드 위치

- `src/main/java/com/booktimer/timer/ReadingTimerService.java` — `ofInstant(clock.instant(), 유저TZ)`
- `src/main/java/com/booktimer/config/TimeConfig.java` — `@Bean Clock`
- `src/test/java/com/booktimer/timer/ReadingTimerServiceTest.java` — `Clock.fixed` 자정 경계 테스트

### 관련 노트

- [N-001. 누적 카운터 일일 리셋 — Lazy 계산](#n-001-누적-카운터-일일-리셋--배치-스케줄러-vs-lazy-계산) — "오늘"까지 따라잡는 그 누적
- [N-009. 계층별 테스트 전략](#n-009-계층별-테스트-전략--도메인-단위--슬라이스--서비스-mock-테스트-피라미드) — 이 서비스도 mock + 고정 Clock 단위 테스트

---

## N-011. Spring Security 폼 로그인 — UserDetailsService + PasswordEncoder 두 빈이 인증을 켠다

**한 줄 요약**: Spring Boot는 보안 의존성만 있으면 기본 보안(폼 로그인 화면 + 임시 비번 단일 계정)을 자동으로 켠다. 하지만 "DB에 저장된 우리 사용자로 로그인"하려면 두 빈만 등록하면 된다 — 사용자를 조회하는 `UserDetailsService`와 비번을 검증하는 `PasswordEncoder`. 이 둘이 있으면 Spring이 `DaoAuthenticationProvider`를 자동 구성해 폼 로그인 인증을 처리한다.

### 자세한 설명

기본 Spring Boot 보안은 이미 많은 걸 준다: 모든 경로 차단(default-deny), `/login` 로그인 페이지 자동 생성, 미인증 요청을 `/login`으로 리다이렉트. **하지만** 인증되는 계정은 콘솔에 임시 비번이 찍히는 in-memory `user` 하나뿐이다. 우리 DB의 `User`로 로그인하려면 두 조각을 끼워야 한다.

1. **`UserDetailsService`** — "이 식별자(이메일)의 사용자가 누구인가"를 답한다. `loadUserByUsername(email)` 이 DB에서 `User`를 찾아 Security가 쓰는 `UserDetails`(username/password-hash/권한)로 변환. 없으면 `UsernameNotFoundException`.
   - 도메인 `Role`(USER/ADMIN)은 여기서 `ROLE_` 접두를 붙여 권한으로 매핑(`ROLE_USER`). 엔티티는 순수 값만 보관하고 접두는 보안 경계에서.
2. **`PasswordEncoder`** — 비번 검증 방식. `BCryptPasswordEncoder` 빈을 등록하면 로그인 시 입력 평문을 같은 방식으로 해싱해 저장된 해시와 비교.

이 **두 빈이 컨텍스트에 있으면** Spring Security가 `DaoAuthenticationProvider`(UserDetailsService로 조회 → PasswordEncoder로 검증)를 자동 조립한다. 별도 와이어링 코드가 거의 없다 — 빈 등록이 곧 설정.

`SecurityFilterChain` 빈으로 정책을 명시한다:
```java
http
  .authorizeHttpRequests(a -> a
      .requestMatchers("/login", "/error", "/css/**").permitAll()  // 공개
      .anyRequest().authenticated())                                // 나머지 인증 필요
  .formLogin(form -> form.permitAll())                              // 폼 로그인(세션)
  .logout(logout -> logout.permitAll());
// CSRF는 기본 활성 유지
```

**CSRF — 켜야 하나 꺼야 하나**: 세션 기반 폼 로그인에선 **켜둔다**(기본값). 브라우저가 세션 쿠키를 자동 전송하므로 CSRF 공격에 노출 → 토큰 보호 필요. 반대로 stateless 토큰(JWT) API는 쿠키를 안 쓰고 매 요청 토큰을 직접 실으므로 보통 끈다. "쿠키로 인증을 자동 전송하느냐"가 판단 기준.

### 일반화 포인트 (면접 답변용)

- **인증의 두 책임 분리**: "누구인가"(조회, `UserDetailsService`) vs "비번이 맞나"(검증, `PasswordEncoder`). Spring은 이 둘을 `AuthenticationProvider`로 합쳐 처리하며, 빈만 등록하면 자동 조립한다(설정보다 관례).
- **프레임워크 기본값을 알고 덮어쓴다**: 기본 보안이 이미 주는 것(default-deny, /login)과 안 주는 것(DB 인증, PasswordEncoder 빈)을 구분해야 "무엇을 추가해야 하는지"가 명확. 테스트의 Red도 "기본이 안 주는 것"(PasswordEncoder 빈 부재 → 컨텍스트 로딩 실패)을 노려야 의미 있다.
- **비번은 평문 저장·비교 절대 금지** — 단방향 해시(BCrypt, salt 내장)로 저장하고, 검증은 "입력을 같은 방식으로 해싱해 비교". 엔티티는 `passwordHash`만 받고 평문은 받지 않게 설계(해싱은 서비스/보안 책임).
- **CSRF 여부는 인증 매체로 결정**: 쿠키/세션 자동 전송 → CSRF ON, 요청마다 명시 토큰(Authorization 헤더) → OFF.

### 코드 위치

- `src/main/java/com/booktimer/security/BookTimerUserDetailsService.java` — 이메일→UserDetails, Role→ROLE_ 매핑
- `src/main/java/com/booktimer/config/SecurityConfig.java` — `PasswordEncoder`(BCrypt) + `SecurityFilterChain`
- `src/test/java/com/booktimer/security/SecurityConfigTest.java` — DB 사용자 폼 로그인 인증 통합 검증

### 관련 노트

- [N-004. Claude Code 훅으로 워크플로 강제](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — 정책을 어느 층에서 강제하나(보안 정책도 같은 사고)

---

## N-012. 인증 주체 ≠ 도메인 엔티티 — principal로 도메인 User를 다시 잇고, 접속을 Lazy 누적 트리거로

**한 줄 요약**: Spring Security가 들고 다니는 인증 주체(`UserDetails`/principal)는 우리 도메인 `User` 엔티티가 아니다. 둘은 별개 객체이고, 보통 **식별자(여기선 이메일)만 공유**한다. 그래서 컨트롤러에선 `principal.getName()`(=식별자)으로 도메인 `User`를 다시 조회해 잇는다. 그리고 "접속할 때 누적을 따라잡는"(N-001) Lazy 트리거를 **로그인 후 착지 화면(대시보드) 로드**에 두면, 배치 없이 자연스럽게 갱신된다.

### 자세한 설명

로그인하면 Security는 `SecurityContext`에 인증 주체(principal)를 담아 둔다. 이 principal은 `BookTimerUserDetailsService`가 만든 `UserDetails`(username=email, 비번 해시, 권한)이지 — JPA로 관리되는 우리 `User` 엔티티가 **아니다**.

- 왜 분리하나: 인증 주체는 "이 요청이 누구인가"만 알면 된다(가볍게, 세션에 직렬화). 도메인 `User`(연관, 영속성 컨텍스트, 지연로딩)를 통째로 세션에 박으면 무겁고 stale 위험이 있다. 그래서 **식별자만** 들고 다니고, 도메인이 필요한 시점에 DB에서 다시 읽는다.
- 잇는 법: 컨트롤러 메서드에 `java.security.Principal`을 주입받으면 `principal.getName()`이 username(=email)이다. 이걸로 `userRepository.findByEmail(email)` → 도메인 `User` 복원.
  - 대안: `@AuthenticationPrincipal UserDetails userDetails` 로 주입받아 `getUsername()`. principal 커스텀 타입을 만들면 도메인 일부를 principal에 얹을 수도 있지만, 식별자→재조회가 가장 단순·안전한 기본형.

```java
@GetMapping("/")
public String dashboard(Principal principal, Model model) {
    User user = userRepository.findByEmail(principal.getName())  // 인증 식별자 → 도메인 엔티티
            .orElseThrow(() -> new IllegalStateException("authenticated user not found"));
    ReadingTimer timer = timerService.accrueToToday(user);       // 접속 = Lazy 누적 트리거
    ...
}
```

**접속을 누적 트리거로**: N-001에서 "자정 배치 대신 접속 시 경과 일수만큼 따라잡는다"는 Lazy 누적을 설계했다. 그 트리거를 **어디에 둘지**가 이 증분에서 정해졌다 — 로그인 후 사용자가 처음 보는 화면(대시보드 `GET /`). 사용자가 들어올 때만, 그 사용자 것만 한 번 계산하면 되니 비용·타임존이 자연스럽다.

### 일반화 포인트 (면접 답변용)

- **인증 모델과 도메인 모델은 다른 관심사다.** principal은 "신원 토큰", 도메인 엔티티는 "비즈니스 상태". 식별자로 연결하고, 도메인은 필요할 때 영속성 계층에서 읽는다(세션에 엔티티를 통째로 담지 않는다 — 무게·stale·직렬화 문제).
- **읽기 시점 계산(Lazy)은 "읽는 진입점"에 트리거를 건다.** 파생 상태(누적 잔여)를 조회 시 계산하기로 했다면, 그 트리거는 사용자가 그 값을 보는 길목(대시보드 로드)에 두는 게 자연스럽다 — write-time 배치와의 트레이드오프(N-001)의 실제 배치 위치.
- principal→도메인 재조회가 매 요청 1번의 쿼리를 더하지만, 그게 stale/무게 문제보다 싸다. 정말 핫하면 캐시/커스텀 principal로 최적화(조기 최적화 금지).

### 코드 위치

- `src/main/java/com/booktimer/web/DashboardController.java` — `principal.getName()` → `findByEmail` → `accrueToToday`
- `src/main/java/com/booktimer/security/BookTimerUserDetailsService.java` — principal(username=email)을 만드는 쪽
- `src/test/java/com/booktimer/web/DashboardControllerTest.java` — `.with(user(email))`로 인증 주체 흉내 + 누적 검증

### 관련 노트

- [N-001. 누적 카운터 일일 리셋 — Lazy 계산](#n-001-누적-카운터-일일-리셋--배치-스케줄러-vs-lazy-계산) — 이 트리거가 적용하는 그 누적
- [N-011. Spring Security 폼 로그인](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-두-빈이-인증을-켠다) — principal(UserDetails)을 만드는 인증 설정

---

## N-013. Spring Boot 컨테이너화 — 멀티스테이지 Dockerfile + 운영 설정 외부화

**한 줄 요약**: Spring Boot 앱을 도커 이미지로 만들 때, **빌드용 JDK 스테이지와 실행용 JRE 스테이지를 분리**(멀티스테이지)하면 최종 이미지에 무거운 빌드 도구가 안 들어가 가볍고 안전하다. 그리고 DB 접속 같은 운영 설정·시크릿은 이미지에 굽지 않고 **환경변수 + `application-prod.properties` 프로필**로 외부에서 주입한다 — 같은 이미지를 어느 환경에든 띄운다.

### 자세한 설명

**1. 멀티스테이지 빌드 — 왜 두 단계인가**

```dockerfile
FROM eclipse-temurin:21-jdk AS build     # 빌드: 소스 → 부트 jar (gradle, JDK 필요)
...
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre              # 런타임: jar 실행만 (JRE면 충분)
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

- 한 단계로 JDK 이미지에 다 담으면, 최종 이미지에 **컴파일러·gradle·소스·캐시**까지 들어가 무겁고 공격 표면이 넓다.
- 멀티스테이지는 빌드 결과물(jar)만 런타임 스테이지로 `COPY --from`. 최종 이미지엔 **JRE + jar**만 → 작고 깔끔.
- 레이어 캐시: 빌드 스크립트/래퍼를 소스보다 먼저 COPY하면, 소스만 바뀔 때 의존성 다운로드 레이어가 캐시된다(빌드 가속).
- 테스트는 이미지 빌드에서 `-x test`로 빼고 **CI 게이트가 따로** 돌린다 — 이미지 빌드는 산출물 생성에 집중, 검증은 파이프라인 책임(역할 분리).

**2. plain jar vs 부트(executable) jar**

- Spring Boot는 빌드 시 jar를 **둘** 만든다: 실행 가능한 부트 jar(의존성 포함, `java -jar`로 바로 실행)와 일반 `*-plain.jar`(클래스만, 라이브러리로 쓸 때).
- Dockerfile이 `build/libs/*.jar`를 단일 복사하면 둘 다 잡혀 **모호**해진다. `build.gradle`에서 `tasks.named('jar') { enabled = false }`로 plain jar를 끄면 부트 jar만 남아 깔끔하다.

**3. 운영 설정·시크릿 외부화**

- DB URL/비번을 코드/이미지에 박으면 시크릿이 새고, 환경마다 이미지를 다시 빌드해야 한다.
- `application-prod.properties`에 **placeholder**만 두고 값은 컨테이너 환경변수로 주입:
  ```properties
  spring.datasource.url=${SPRING_DATASOURCE_URL}
  spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
  spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
  spring.jpa.hibernate.ddl-auto=update
  spring.docker.compose.enabled=false   # 개발 전용 기능 — 운영 컨테이너엔 docker 소켓 없음
  ```
- 프로필 활성화는 `SPRING_PROFILES_ACTIVE=prod`(Dockerfile `ENV` 또는 실행 시). 같은 이미지를 dev/prod에 그대로 띄우고 **환경변수만 다르게** → "한 번 빌드, 어디서나 실행".
- Spring의 relaxed binding 덕에 `SPRING_DATASOURCE_URL` 환경변수는 `spring.datasource.url`로 자동 매핑되지만, prod 프로필에 명시해 두면 "이 환경이 무엇을 요구하는가"가 문서화되고 누락 시 기동이 fail-fast로 막힌다.

**4. 헬스체크 엔드포인트**

- 로드밸런서/배포 파이프라인이 "떴는지" 확인할 경로가 필요 → Spring Actuator `/actuator/health`(기본 노출). 단, 보안이 전 경로를 잠그면 헬스체크가 401로 실패하므로 **그 경로만 공개**(`permitAll`)해야 한다.

### 일반화 포인트 (면접 답변용)

- **이미지는 불변(immutable) 산출물, 설정은 주입**: "한 번 빌드한 이미지를 환경변수만 바꿔 모든 환경에 띄운다"가 12-factor의 config 원칙. 시크릿을 이미지에 굽지 않는 이유(유출·재빌드).
- **멀티스테이지 = 빌드 의존성과 런타임 의존성의 분리**: 최종 이미지 크기·공격 표면 최소화. 컴파일러는 빌드에만 필요하지 실행엔 불필요.
- **빌드와 검증의 책임 분리**: 이미지 빌드에서 테스트를 빼고 CI 게이트가 검증 — N-009(계층별 테스트)·N-004(정책을 어느 층에서 강제)와 같은 "관심사를 알맞은 곳에" 사상.
- 로컬에서 임시 DB 컨테이너 + 앱 이미지로 **스모크 테스트**(health UP, 스키마 생성 확인)하면 클라우드 가기 전에 설정 오류를 싸게 잡는다.

### 코드 위치

- `Dockerfile` — 멀티스테이지(JDK 빌드 → JRE 런타임)
- `.dockerignore` — 빌드 컨텍스트 경량화
- `src/main/resources/application-prod.properties` — env-var datasource + prod 설정
- `build.gradle` — `tasks.named('jar') { enabled = false }` (plain jar 비활성)
- `src/main/java/com/booktimer/config/SecurityConfig.java` — `/actuator/health` 공개

### 관련 노트

- [N-009. 계층별 테스트 전략](#n-009-계층별-테스트-전략--도메인-단위--슬라이스--서비스-mock-테스트-피라미드) — 검증을 알맞은 층에 두는 사상(이미지 빌드 vs CI 게이트)
- [N-010. 테스트 가능한 시간 — Clock 주입](#n-010-테스트-가능한-시간--clock-주입--절대-시점-vs-유저-타임존-오늘) — "부수효과/환경 의존을 주입으로 빼낸다"의 설정 버전

---

## N-014. AWS CLI는 로컬에서 실행되지만 클라우드에 작용 — 콘솔/CLI/CloudShell, bash vs PowerShell

**한 줄 요약**: AWS를 다루는 길은 세 가지다 — 웹 **콘솔**(클릭), **AWS CLI**(`aws ...` 명령), **CloudShell**(브라우저 안 터미널). CLI 명령은 "AWS 전용 터미널"에서 도는 게 아니라 **내 로컬 셸에서 실행되고, 효과만 클라우드에 미친다**(설정한 자격증명으로 AWS API 호출). 그리고 가이드의 `aws` 명령은 보통 bash 문법이라 Windows PowerShell에 그대로 붙이면 깨진다.

### 자세한 설명

처음 배포 가이드를 보면 `aws ecs ...` 같은 명령이 줄줄이 있는데, "이걸 어디에 치는 거지?"가 헷갈린다. 정리:

- **AWS를 조작하는 3가지 인터페이스**
  - **콘솔(Console)**: 웹 UI에서 클릭으로. 처음 감 잡기 좋지만 재현·자동화가 어렵다.
  - **AWS CLI**: `aws <서비스> <동작>` 명령. 같은 일을 코드로 — 재현·스크립트·문서화에 유리.
  - **CloudShell**: 콘솔 안에 떠 있는 브라우저 터미널. **AWS CLI가 미리 깔려 있고 로그인 자격증명도 자동 연결**. 로컬 설치 없이 CLI를 바로 쓴다.
- **CLI 명령은 어디서 도나**: 내 로컬 터미널(또는 CloudShell)에서 프로세스로 실행된다. 다만 `aws ...`는 로컬에서 계산하는 게 아니라, `aws configure`(또는 SSO/역할)로 설정한 **자격증명으로 AWS API를 HTTP 호출**해 클라우드의 리소스를 만들고 조회한다. → **"명령은 로컬에서, 효과는 클라우드에서."**
- **명령 종류를 구분**: 한 가이드 안에도 `aws ...`(AWS CLI), `docker ...`(Docker CLI, 로컬 이미지 작업), `export`/`sed`/`cat <<EOF`/`$(...)`(셸 문법, 순수 로컬 보조)가 섞인다. 전부 같은 터미널에서 치지만 작용 대상이 다르다.
- **셸 함정 (bash vs PowerShell)**: 대부분의 AWS 예제는 **bash** 문법이다.
  - `export VAR=...`(bash) ↔ `$env:VAR=...`(PowerShell)
  - `$(cmd)` 명령치환은 둘 다 되지만, `sed`·히어독(`cat <<'EOF'`)은 PowerShell에 없다.
  - 그래서 Windows에선 **AWS CloudShell이나 Git Bash/WSL**에서 돌리는 게 마찰이 적다. PowerShell 고집 시 문법을 일일이 번역해야 한다(T-026의 한글 깨짐처럼, "셸이 다르면 문법도 다르다"의 또 다른 사례).

### 일반화 포인트 (면접 답변용)

- **CLI는 "API의 얇은 래퍼"다.** 콘솔 클릭이든 CLI든 SDK든 결국 같은 AWS API를 호출한다 — 인터페이스만 다를 뿐. 그래서 CLI로 한 일은 IaC(Terraform/CloudFormation)로 옮기기도 자연스럽다.
- **인증과 실행 위치는 별개**: 명령이 도는 곳(로컬/CloudShell/CI 러너)과, 그 명령이 무슨 권한으로 클라우드를 만지는가(자격증명·IAM 역할)는 분리해서 생각해야 한다. CI에서는 이 자격증명을 OIDC로 주입한다(다음 노트 주제와 연결).
- **"내 터미널 = 내 OS의 셸"**: 같은 명령도 bash/PowerShell/cmd에서 문법이 다르다. 가이드의 셸 전제를 먼저 확인하는 습관.

### 코드 위치

- `claude-docs/deploy-aws.md` — "어디서 실행하나" 섹션(CloudShell 추천 + 셸 주의)
- 관련: 글로벌 환경이 Windows PowerShell 5.1이라 bash 가이드 실행 시 이 구분이 특히 중요

### 관련 노트

- [N-013. Spring Boot 컨테이너화](#n-013-spring-boot-컨테이너화--멀티스테이지-dockerfile--운영-설정-외부화) — 이 배포의 산출물(이미지)
- [N-002. Gradle toolchain](#n-002-gradle-toolchain--foojay-resolver--로컬에-없는-jdk-자동-확보) — "실행 환경과 대상 환경 분리" 사고의 또 다른 예

---

## N-015. GitHub Actions → AWS 키 없이 배포 — OIDC 페더레이션 + ECS 롤링 배포

**한 줄 요약**: CI(GitHub Actions)가 AWS에 배포하려면 AWS 권한이 필요한데, **액세스 키를 GitHub Secrets에 저장하는 대신 OIDC 페더레이션**을 쓰면 워크플로가 실행될 때마다 **단기 토큰으로 IAM 역할을 assume**한다 — 장기 자격증명을 어디에도 저장하지 않는다. 그 역할로 ECR에 이미지를 올리고, ECS는 **새 태스크 정의 리비전 등록 → 서비스 업데이트**로 무중단에 가깝게 롤링 배포한다.

### 자세한 설명

**1. 왜 OIDC인가 (키 저장의 문제)**
- 전통 방식: IAM 사용자 액세스 키(AKIA...) + 시크릿을 GitHub Secrets에 저장 → 워크플로가 그걸로 인증. 문제: **장기 자격증명이 유출되면 무기한 악용**, 주기적 로테이션 부담.
- OIDC 방식: GitHub의 OIDC 공급자를 AWS IAM에 **신뢰 등록**(`token.actions.githubusercontent.com`). 워크플로 실행 시 GitHub가 발급한 **단기 OIDC 토큰**을 AWS에 제시하면, AWS가 검증 후 **임시 자격증명(수십 분 유효)** 을 내준다. → GitHub에 저장하는 건 (비밀이 아닌) **역할 ARN뿐**.
- 신뢰정책으로 **누가 assume할 수 있는지** 좁힌다: `sub`가 `repo:Goospel/booktimer:*`인 토큰만 허용 → 다른 레포·다른 계정은 이 역할을 못 쓴다.
- 워크플로 쪽 요건: `permissions: id-token: write`(OIDC 토큰 발급) + `aws-actions/configure-aws-credentials`에 `role-to-assume`.

**2. 최소권한 배포 역할**
- 이 역할에 준 권한: ECR push, ECS(`RegisterTaskDefinition`/`UpdateService`/`Describe*`), 그리고 `iam:PassRole`(태스크 실행역할을 ECS에 넘기는 권한, 리소스를 그 역할로 한정).
- `PassRole`이 핵심 함정: 배포 역할이 "태스크가 쓸 실행역할"을 ECS에 넘기려면 명시적 `PassRole` 허용이 필요하다(권한 상승 방지 장치).

**3. ECS 롤링 배포 흐름**
```
build & push 이미지(ECR, :sha 태그)
  → 태스크 정의(JSON)에 새 이미지 주입
  → register-task-definition (새 리비전 생성)
  → update-service (서비스가 새 리비전으로 태스크 교체 — 헬스 통과 후 옛 태스크 종료)
  → 안정화 대기
```
- 태스크 정의를 **리포에 두고(IaC)** placeholder만 치환하는 방식을 택했다. `aws ecs describe-task-definition` 산출물을 그대로 다시 등록하려 하면 `taskDefinitionArn`/`revision`/`status` 같은 **읽기전용 필드**가 섞여 `register`가 거부한다 — 버전관리된 깨끗한 정의를 소스로 쓰면 이 함정을 피하고 "배포 = 코드"가 된다.
- ALB 타깃그룹의 헬스체크(`/actuator/health`)가 새 태스크를 healthy로 판정해야 트래픽이 옮겨간다 → 무중단에 가깝다.

### 일반화 포인트 (면접 답변용)

- **단기 자격증명 > 장기 키**: "비밀을 저장하지 않는다"가 가장 안전하다. OIDC 워크로드 아이덴티티 페더레이션은 CI/CD의 표준 — GitHub↔AWS뿐 아니라 GCP/Azure, 쿠버네티스 서비스어카운트도 같은 사상.
- **신뢰 경계를 조건으로 좁힌다**: 역할을 만들 때 "누가(어느 레포/브랜치) assume 가능한가"를 `sub` 조건으로 제한 — 자격증명이 아니라 **신원(identity)** 기반 접근제어.
- **배포는 선언적 교체**: 명령형으로 "기존 컨테이너 죽이고 새로 띄워"가 아니라, 원하는 상태(새 태스크 정의)를 등록하면 오케스트레이터가 헬스 기반으로 교체. 실패 시 롤백도 리비전 되돌리기로 단순.
- **`PassRole`**: 한 역할이 다른 역할을 서비스에 넘길 때 명시 허용이 필요한 권한 상승 방지 장치 — AWS IAM 설계 단골 질문.
- N-014의 "인증과 실행 위치 분리"가 여기서 구체화: 명령은 CI 러너에서 돌지만, 권한은 OIDC로 주입된 임시 역할에서 온다.

### 코드 위치

- `.github/workflows/deploy.yml` — OIDC 자격증명 + ECR push + ECS 롤링 배포
- `deploy/task-definition.json` — IaC 태스크 정의(placeholder)
- `claude-docs/deploy-aws.md` 6-2 — OIDC 공급자 + 배포역할 신뢰/권한 정책

### 관련 노트

- [N-014. AWS CLI 로컬 실행·클라우드 작용](#n-014-aws-cli는-로컬에서-실행되지만-클라우드에-작용--콘솔clicloudshell-bash-vs-powershell) — 인증과 실행 위치 분리의 연장
- [N-013. Spring Boot 컨테이너화](#n-013-spring-boot-컨테이너화--멀티스테이지-dockerfile--운영-설정-외부화) — 배포되는 이미지
- [N-004. 훅으로 워크플로 강제](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — "정책을 어느 층에서 강제하나"의 CI 버전

---

## N-016. ECS 헬스체크와 콜드스타트 — ALB 타깃 헬스 vs 컨테이너, grace period

**한 줄 요약**: ECS 서비스가 "안정화"되려면 새 태스크가 **헬스체크를 통과**해야 한다. ALB 뒤에 둔 서비스는 ALB **타깃그룹 헬스체크**(HTTP 경로 응답)로 건강을 판정하는데, 앱 **콜드스타트가 느리면** ECS의 **헬스체크 유예(grace period)** 안에 통과를 못 해 태스크가 비정상으로 종료·재시작을 반복한다. 즉 "앱은 정상인데 배포가 안 끝나는" 상황의 핵심은 **시작 속도 vs 유예/헬스체크 타이밍**이다.

### 자세한 설명

**헬스체크가 두 층위로 있다**
- **컨테이너 헬스체크**(태스크 정의 `healthCheck`): 컨테이너 안에서 명령 실행(예: `curl localhost`). 안 넣으면 생략 가능.
- **ALB 타깃그룹 헬스체크**: ALB가 타깃(태스크 IP:포트)으로 **HTTP 요청**(예: `/actuator/health`)을 보내 200이면 healthy. ALB 뒤 서비스는 보통 이게 "건강"의 기준이고, ECS는 이 결과로 태스크를 살리고 죽인다.
  - 통과 조건: `healthy-threshold`(예: 2)회 **연속** 성공. 간격 30초면 ≈60초 필요.
  - 경로가 **인증 없이 200**을 줘야 한다(Spring Security가 막으면 401 → 영원히 unhealthy). 그래서 `/actuator/health`를 `permitAll`로 열었다.

**grace period(헬스체크 유예)의 역할**
- 새 태스크가 막 떴을 때 앱은 아직 부팅 중이라 헬스체크가 당연히 실패한다. ECS가 이걸로 바로 죽이면 영원히 못 뜬다.
- `health-check-grace-period-seconds`는 "태스크 시작 후 이 시간 동안은 ELB 헬스 실패로 죽이지 마라"는 유예다.
- **함정**: 유예 < (콜드스타트 + 헬스 2회 통과 시간) 이면, 앱이 준비되기도 전에/직후에 유예가 끝나 ECS가 태스크를 죽인다 → 무한 재시작(T-009). 이 프로젝트는 Fargate 0.25 vCPU에서 콜드스타트 ~100초인데 유예 120초라 빠듯해 실패 → **300초로** 늘려 해결.

**롤링 배포와의 관계**
- ECS 롤링: 새 태스크를 띄워 **healthy** 된 뒤 옛 태스크를 드레이닝·종료(minimumHealthyPercent 100 / maximumPercent 200이면 잠깐 2개 공존). 새 태스크가 grace 안에 healthy 못 되면 "배포가 안정화 안 됨" → 파이프라인의 안정화 대기가 실패.
- 그래서 **배포 성공 = 새 태스크가 헬스 통과**. 느린 시작은 배포 신뢰성에 직접 영향.

### 일반화 포인트 (면접 답변용)

- **"앱이 떴다"와 "오케스트레이터가 건강하다고 본다"는 다르다.** 후자는 헬스체크(경로·포트·인증·임계치)와 유예 타이밍의 함수. 배포가 멈추면 로그(앱 정상?)와 서비스 이벤트(헬스 실패?)를 같이 봐야 원인이 갈린다.
- **유예는 콜드스타트에 맞춰 잡는다.** 시작이 느린 런타임(JVM/Spring)은 grace를 넉넉히. 근본 해결은 시작 단축(CPU↑, 지연 초기화, AOT/네이티브 이미지).
- **헬스 엔드포인트는 인증 예외**로 둬야 외부 LB가 찌를 수 있다 — 보안 정책의 화이트리스트에 포함.
- 작은 vCPU(Fargate 0.25)는 비용은 싸지만 **콜드스타트·워밍업이 느려** 배포·오토스케일 반응성이 떨어진다(비용 vs 반응성 트레이드오프).

### 코드 위치

- `deploy/task-definition.json` — 컨테이너 포트 8080, 로그, (컨테이너 헬스체크는 생략하고 ALB에 위임)
- ALB 타깃그룹 헬스체크 경로 `/actuator/health`, 서비스 `health-check-grace-period-seconds`(120→300)
- `src/main/java/com/booktimer/config/SecurityConfig.java` — `/actuator/health` 공개
- 관련: `troubleshooting.md` T-009(grace 부족), T-010(배포 경쟁)

### 관련 노트

- [N-015. GitHub Actions → AWS 키 없이 배포](#n-015-github-actions--aws-키-없이-배포--oidc-페더레이션--ecs-롤링-배포) — 이 헬스체크를 기다리는 그 롤링 배포
- [N-013. Spring Boot 컨테이너화](#n-013-spring-boot-컨테이너화--멀티스테이지-dockerfile--운영-설정-외부화) — health 엔드포인트 공개·운영 프로필

---

## N-017. SSR(Thymeleaf)→SPA 전환 시점 — "백엔드 몇 %"가 아니라 API 계약 안정성 + 인터랙션 요구

**한 줄 요약**: 서버 렌더링(Thymeleaf)에서 프론트 프레임워크(React/Vue 등 SPA)로 옮기는 판단 기준은 "백엔드가 몇 % 완성됐나"가 아니다. 전환의 진짜 비용은 백엔드를 **HTML 렌더링 → JSON API 제공**으로 바꾸는 것(컨트롤러 반환형·인증 방식 재설계)이므로, ① **API 계약(엔드포인트)이 안정**돼 두 번 안 만들 시점이고, ② **서버 렌더로는 못 받치는 인터랙션 요구**(실시간 갱신 등)가 생긴 시점이 신호다.

### 자세한 설명

배포된 BookTimer를 직접 써 보니 Thymeleaf UI가 빈약해 "프론트 프레임워크가 필요하다"는 욕구가 생겼다. 그런데 "언제 옮기나"의 기준이 "백엔드 X% 완성"이라는 직관은 틀렸다.

**1. 전환의 진짜 비용 = 백엔드를 API-first로 바꾸는 것**
- SPA로 가면 백엔드가 더 이상 HTML을 그리지 않고 데이터(JSON)만 준다.
  - `return "dashboard"`(뷰 이름) → `return ResponseEntity<DashboardDto>`(JSON)
  - 인증: **세션 쿠키 + CSRF 토큰**(폼 로그인) → SPA용 전략 재설계(세션 유지 or JWT, CORS 허용). N-011의 "CSRF는 인증 매체로 결정"이 여기서 다시 걸린다.
- 이건 한 번에 크게 바뀌는 비용이라, **자주 안 바뀔 만큼 도메인/엔드포인트가 굳은 뒤** 옮겨야 프론트를 두 번 안 만든다 → 기준 ①.

**2. 서버 렌더의 한계가 신호 ②**
- BookTimer 타이머는 본질적으로 실시간 인터랙티브다: 화면에서 초가 째깍 올라가야 하고, start/stop이 지금은 `POST → redirect → 전체 리로드`다. 순수 Thymeleaf로는 어색.
- "이 인터랙션을 서버 렌더로는 못 받친다"가 분명해지면 그게 프레임워크가 **실질 가치**를 주는 지점.

**3. 전면 전환은 무거우니 "다리(bridge)"를 먼저**
- 당장의 빈약한 UI 통증은 아키텍처를 안 건드리는 가벼운 수단으로 먼저 해소 가능:
  - **htmx / Alpine.js + CSS** — 째깍 타이머, 리로드 없는 부분 갱신. 백엔드는 여전히 Thymeleaf.
- 풀 SPA 전환(REST API + 인증 재설계)은 **인터랙션이 무거운 기능 직전**에. BookTimer라면 책 단위 기록/SNS 들어가기 직전 — 그걸 Thymeleaf로 만들었다 React로 다시 만들면 두 번 일이므로.

### 일반화 포인트 (면접 답변용)

- **아키텍처 전환의 타이밍은 "완성도 %"가 아니라 "계약 안정성 + 비용이 정당화되는 요구"로 잡는다.** 비싼 마이그레이션은 되돌리기 어려운 부분(여기선 API 계약·인증 모델)이 굳은 뒤 한 번에.
- **SSR vs SPA는 "어디서 HTML을 만드나"의 선택**: SSR은 초기 로딩·SEO·단순함, SPA는 풍부한 인터랙션·부분 갱신. 둘 사이엔 htmx/Alpine 같은 중간 지대가 있어 전면 전환 없이 통증만 덜 수 있다(점진적 마이그레이션).
- **전환 비용의 핵심은 보통 "경계의 재계약"**: 뷰 템플릿 교체가 아니라 백엔드↔프론트 사이 계약(HTML→JSON)과 인증 매체(쿠키 세션→토큰)가 바뀌는 것. 그래서 도메인이 흔들릴 때 옮기면 계약을 반복해서 다시 쓴다.
- **조기 최적화 회피와 같은 결**: 필요(인터랙션 요구)가 분명해지기 전에 SPA로 가면, 안 굳은 API를 프론트가 따라다니며 재작업한다.

### 코드 위치

- (현재) `src/main/resources/templates/*.html` — Thymeleaf SSR 뷰
- (현재) `src/main/java/com/booktimer/web/*Controller.java` — 뷰 이름 반환(HTML 렌더). 전환 시 JSON 반환형으로 바뀔 후보
- `src/main/java/com/booktimer/config/SecurityConfig.java` — 세션+CSRF 폼 로그인(전환 시 인증 전략 재설계 지점)
- 관련: `README.md` 4번 로드맵(프론트엔드 프레임워크 교체 항목)

### 관련 노트

- [N-011. Spring Security 폼 로그인](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-두-빈이-인증을-켠다) — CSRF/세션 vs 토큰: SPA 전환 시 재설계되는 인증 매체
- [N-001. 누적 카운터 일일 리셋 — Lazy 계산](#n-001-누적-카운터-일일-리셋--배치-스케줄러-vs-lazy-계산) — "비용이 정당화될 때까지 미룬다"는 같은 결의 판단(write-time vs read-time)

---

## N-018. 퍼블릭 IP ≠ 인터넷 접근 — 서브넷 라우트테이블이 진짜 관문

**한 줄 요약**: 리소스(Fargate 태스크, EC2 등)에 **퍼블릭 IP가 있어도** 그 서브넷의 **라우트테이블에 `0.0.0.0/0 → 인터넷 게이트웨이(IGW)`** 경로가 없으면 인터넷에 못 나간다. 퍼블릭 IP는 "주소"일 뿐, 실제 길을 여는 건 라우트테이블이다. Fargate가 시크릿(SSM)·이미지(ECR)·로그(CloudWatch)를 가져오려면 egress 경로(IGW / NAT / VPC 엔드포인트)가 반드시 필요하다.

### 자세한 설명

ECS Fargate 배포에서 새 태스크가 **SSM Parameter Store에서 시크릿을 못 가져와**(`ResourceInitializationError ... context deadline exceeded`) 시작도 못 하고 죽는 일이 있었다(T-011). 서비스는 `assignPublicIp=ENABLED`였는데도.

**"퍼블릭 서브넷"의 진짜 정의**
- 흔한 오해: "퍼블릭 IP를 받으면 인터넷에 나간다." → **틀림.**
- 패킷이 인터넷으로 나가려면 서브넷에 연결된 **라우트테이블**이 `목적지 0.0.0.0/0 → IGW`를 가져야 한다. 이 라우트가 있는 서브넷이 곧 "퍼블릭 서브넷". 퍼블릭 IP는 그 길 위에서 응답을 받기 위한 주소일 뿐, 길 자체는 라우트테이블이 만든다.
- 즉 **퍼블릭 IP + IGW 라우트** 둘 다 있어야 양방향 인터넷. 하나라도 없으면 막힌다.

**egress가 필요한 이유 (Fargate는 시작부터 외부를 부른다)**
- Fargate 태스크는 뜨자마자 **시크릿(SSM/Secrets Manager)·이미지(ECR)·로그(CloudWatch Logs)** 같은 AWS API를 호출한다. 이게 막히면 `ResourceInitializationError`로 **컨테이너가 시작조차 못 한다**(앱 코드 도달 전).
- egress를 주는 길은 세 가지:
  - **IGW**(퍼블릭 서브넷 + 퍼블릭 IP): 가장 단순. 공용 인터넷 경유로 AWS API 호출.
  - **NAT 게이트웨이**(프라이빗 서브넷): 퍼블릭 IP 없이 아웃바운드만. 비용 발생.
  - **VPC 엔드포인트**(PrivateLink): 인터넷 없이 사설로 SSM/ECR/Logs에 도달. 가장 안전하나 엔드포인트별 설정 필요.

**권한 실패 vs 네트워크 실패 구분**
- "시크릿을 못 가져옴"은 두 원인이 있다: ① 실행역할 권한 부족(`AccessDenied`) ② 엔드포인트 도달 실패(타임아웃 `context deadline exceeded`). 에러 메시지로 갈린다 — AWS가 후자엔 "connection issue ... check your **task network configuration**"이라고 직접 알려준다.

**비대칭 서브넷 = 비결정적 실패**
- 이 사건의 핵심 교훈: 네트워크 설정(서브넷 목록)은 **서비스 레벨로 모든 태스크에 동일**한데, 결과가 "됐다 안 됐다" 했다. 원인은 서비스에 물린 **서브넷 2개의 라우트가 비대칭**(하나는 IGW 있음, 하나는 없음)이었고, **태스크 배치가 둘 사이 비결정적**이라 좋은 서브넷에 걸리면 성공·나쁜 서브넷이면 실패했기 때문.
- 일반 교훈: "가끔 되고 가끔 안 되는" 인프라 실패는 **여러 리소스(서브넷/AZ) 간 설정 비대칭**을 의심하라. 평균이 아니라 **개별 경로**를 비교해야 보인다.

### 일반화 포인트 (면접 답변용)

- **퍼블릭 IP ≠ 인터넷 접근.** 퍼블릭 서브넷의 정의는 "라우트테이블이 0.0.0.0/0을 IGW로 보낸다"이다. 보안그룹(상태ful 방화벽)·NACL·라우트테이블·IGW/NAT는 각각 다른 층 — 하나만 봐선 도달성을 못 판단한다.
- **클라우드 매니지드 서비스도 "네트워크 위에" 있다.** SSM/ECR 같은 API는 마법으로 닿는 게 아니라 엔드포인트(공용 또는 PrivateLink)로의 경로가 필요. Fargate가 "시작 실패"하면 앱 버그 이전에 **부팅 시 외부 의존(시크릿·이미지·로그) 도달성**을 먼저 의심.
- **egress 설계 3선택**(IGW / NAT / VPC 엔드포인트)은 비용·보안·복잡도 트레이드오프 — 퍼블릭 IP+IGW는 싸고 단순, VPC 엔드포인트는 인터넷 노출 없이 안전.
- **비결정적 실패 ⇒ 설정 비대칭 의심**: 동일해 보이는 리소스 풀(서브넷/AZ/노드) 사이의 미세한 차이가 "운에 따라" 드러난다.

### 코드 위치

- 인프라(리포 밖): `subnet-018…` RTB에 `create-route`로 `0.0.0.0/0 → igw-…` 추가해 해결
- `deploy/task-definition.json` — SSM `secrets`(이게 부팅 시 egress를 요구) + awslogs
- 관련: `troubleshooting.md` T-011(즉시 진단·해결 절차)

### 관련 노트

- [N-015. GitHub Actions → AWS 키 없이 배포 — OIDC + ECS 롤링 배포](#n-015-github-actions--aws-키-없이-배포--oidc-페더레이션--ecs-롤링-배포) — 이 배포 파이프라인이 띄우는 태스크가 겪은 문제
- [N-016. ECS 헬스체크와 콜드스타트](#n-016-ecs-헬스체크와-콜드스타트--alb-타깃-헬스-vs-컨테이너-grace-period) — "앱이 떴다 ≠ 배포 성공"의 또 다른 층(여기선 "시작조차 못 함")
- [N-013. Spring Boot 컨테이너화](#n-013-spring-boot-컨테이너화--멀티스테이지-dockerfile--운영-설정-외부화) — 시크릿 외부화(SSM)가 egress 의존을 만든 지점

---

## N-019. DB 유니크 제약은 무결성의 마지막 방어선이지, 사용자 검증의 첫 방어선이 아니다

**한 줄 요약**: DB 유니크 제약(예: 이메일)은 데이터 무결성을 **끝에서** 보장하는 안전망이다. 하지만 그것에만 의존해 앱이 사전 확인을 안 하면, 제약 위반이 `DataIntegrityViolationException`으로 터져 처리되지 않으면 **500**이 된다. 사용자 친화적 검증(앱의 사전 확인 → 친절한 에러)과 무결성 보장(DB 제약)은 **다른 계층의 다른 책임**이고, 둘 다 필요하다.

### 자세한 설명

회원가입에서 이미 가입된 이메일로 다시 가입하면 prod가 500 whitelabel을 냈다(T-012). `User`에 유니크 제약은 있었지만, 등록 서비스가 사전 확인 없이 `save`만 했다.

**왜 둘 다 필요한가 — 계층별 책임**
- **앱의 사전 확인**(`existsByEmail` → 친절한 폼 에러): 사용자 경험을 위한 것. "이미 가입된 이메일입니다"를 빨간 글씨로 보여줘 사용자가 고치게 한다. UX의 책임.
- **DB 유니크 제약**: 데이터 무결성을 위한 것. 앱 버그·동시성·다른 진입점(배치, 다른 서비스)으로 중복이 들어오는 걸 **물리적으로** 막는 최후의 보루. 정합성의 책임.
- 사전 확인만 있고 DB 제약이 없으면 → **레이스로 중복이 새어 들어간다**(둘이 동시에 확인 통과 후 둘 다 insert). DB 제약만 있고 사전 확인이 없으면 → **위반이 500으로 새어 사용자에게 흉하게 보인다**. 그래서 **둘 다**.

**레이스(TOCTOU)와 3중 방어**
- 사전 확인(check)과 저장(insert) 사이에는 시간 간격이 있어, 두 동시 요청이 모두 확인을 통과한 뒤 한쪽이 insert에서 충돌할 수 있다(check-then-act 레이스).
- 그래서 가장 견고한 형태는: ① 앱 사전 확인(흔한 경로의 친절 에러) + ② DB 유니크 제약(무결성) + ③ 컨트롤러에서 `DataIntegrityViolationException`도 잡아 같은 친절 에러로(레이스로 새어온 위반을 500 대신 부드럽게).

**"검증을 어디서 하나"의 일반 원리**
- 같은 규칙도 여러 층에서 강제될 수 있고(N-004의 soft/hard 사고와 같은 결), 각 층은 목적이 다르다: **클라이언트(즉시 피드백) → 앱 검증(UX·비즈니스 규칙) → DB 제약(무결성 불변식)**. 위층은 친절하지만 우회 가능, 아래층은 견고하지만 사용자에게 직접 노출되면 흉하다. → 위에서 친절하게 막고, 아래에서 확실하게 받친다.

### 일반화 포인트 (면접 답변용)

- **유효성 검증(validation)과 무결성 제약(constraint)은 다른 관심사**다. 전자는 사용자/비즈니스 규칙(앱), 후자는 데이터 정합성 불변식(DB). DB 제약을 "검증 수단"으로만 쓰면 위반 시 UX가 깨진다.
- **check-then-act는 동시성 하에 안전하지 않다.** 사전 확인은 UX를 좋게 하지만 유일성을 보장하지 못한다 — 유일성은 DB 유니크 제약(원자적)이 보장하고, 앱은 그 위반을 우아하게 처리한다.
- **예외를 사용자 경계에서 번역하라.** 하위(영속) 예외(`DataIntegrityViolationException`)가 그대로 500으로 새지 않게, 컨트롤러/핸들러에서 의미 있는 도메인 에러·폼 에러로 바꾼다.
- **테스트 함정**: `@Transactional` 통합 테스트는 매번 롤백돼 "상태 누적(중복 등)" 버그를 못 잡는다. 그런 버그는 한 트랜잭션 안에서 선행 데이터를 만든 뒤 재시도해 재현하거나, prod/스테이징에서 별도로 검증.

### 코드 위치

- `src/main/java/com/booktimer/user/UserRegistrationService.java` — 저장 전 `existsByEmail` 사전 확인
- `src/main/java/com/booktimer/user/EmailAlreadyExistsException.java` — 도메인 예외
- `src/main/java/com/booktimer/web/SignupController.java` — 예외 → 이메일 필드 에러(+레이스 대비 `DataIntegrityViolationException` catch)
- `src/main/java/com/booktimer/user/User.java` — `@UniqueConstraint(uk_users_email)`(무결성 보루)
- 관련: `troubleshooting.md` T-012(이 버그의 진단·해결)

### 관련 노트

- [N-004. 훅으로 워크플로 강제 — 가이드(soft) vs 훅(hard)](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — "정책을 어느 층에서 강제하나"의 같은 사고(친절 vs 견고)
- [N-011. Spring Security 폼 로그인](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-두-빈이-인증을-켠다) — 비번 평문 금지처럼, 보안·무결성은 마지막 계층에서 보장

---

## N-020. CI 트리거 필터 — `paths-ignore`는 "전부 매칭될 때만" 스킵하는 안전 기본값

**한 줄 요약**: GitHub Actions의 `push` 트리거에 `paths-ignore`를 걸면, **그 push에서 바뀐 파일이 전부(all) 그 패턴에 해당할 때만** 워크플로를 건너뛴다. 하나라도 패턴 밖 파일이 끼면 정상 실행된다. 그래서 "문서만 바뀌면 배포 스킵, 코드가 한 줄이라도 있으면 배포"가 누락 위험 없이 성립한다 — 기본은 실행, 순수 무관 변경일 때만 생략.

### 자세한 설명

배포 워크플로(`deploy.yml`)가 `main` push마다 무조건 돌아서, README·troubleshooting 같은 문서만 고쳐도 ~5분짜리 테스트 게이트 + ECR 빌드 + ECS 롤링 배포가 통째로 실행됐다. 배포할 산출물이 없는데 매번 5분을 태우는 게 낭비였다.

```yaml
on:
  push:
    branches: [main]
    paths-ignore:
      - '**.md'           # 모든 마크다운
      - 'claude-docs/**'  # 문서 디렉터리
      - '.claude/**'      # 로컬 훅·설정(배포 산출물과 무관)
  workflow_dispatch: {}   # 수동 트리거는 paths 필터 영향 없음
```

**핵심 의미 — `paths-ignore`의 판정은 "전부(all) or 아무거나(any)"가 관건**
- `paths-ignore`: push의 **변경 파일이 전부** 무시 패턴에 매칭되면 → **스킵**. 하나라도 벗어나면 실행.
- `paths`(반대): 변경 파일 중 **하나라도** 포함 패턴에 매칭되면 → 실행. 전부 벗어나면 스킵.
- 그래서 배포처럼 "빠뜨리면 안 되는" 워크플로엔 **`paths-ignore`(거부 목록)가 안전**하다. 새 소스 디렉터리가 생겨도 자동으로 "실행" 쪽이다(기본 실행). 반대로 `paths`(허용 목록)를 쓰면 새 경로를 목록에 추가하는 걸 잊는 순간 **조용히 배포가 안 되는** 함정이 생긴다.

**안전 기본값(fail-safe default)의 방향**
- 위험한 쪽이 "실행 안 됨(배포 누락)"이라면, 기본값을 "실행"으로 두고 예외만 빼는 설계가 옳다. `paths-ignore`는 정확히 그 형태 — 화이트리스트가 아니라 블랙리스트라서 "모르는 건 일단 배포".
- 같은 사고: 방화벽 default-deny vs default-allow, 보안은 default-deny(모르면 막아)지만 **배포 트리거는 default-run(모르면 배포해)** 가 맞다 — 무엇이 더 위험한 실패냐로 기본값 방향이 정해진다.

**부수 주의점**
- `paths`와 `paths-ignore`는 **함께 쓰면 안 된다**(상호 배타적, 한쪽만).
- `workflow_dispatch`(수동)·`schedule` 등 다른 트리거는 paths 필터의 영향을 받지 않는다 → 문서만 바꾼 날에도 수동 배포는 언제든 가능.
- 경로 필터는 **push/​PR 트리거에서만** 동작한다(tag·release 등엔 별도 규칙).
- 검증 묘수: 필터를 추가하는 그 커밋 자체가 워크플로 파일(=무시 대상 아님)을 건드리므로, 머지하면 워크플로가 **정상 실행**된다 → YAML 유효성과 트리거 동작을 한 번에 확인. "문서만 바꾼" 후속 커밋으로 스킵도 따로 확인.

### 일반화 포인트 (면접 답변용)

- **CI 비용은 트리거 설계로 줄인다.** 모든 변경에 전체 파이프라인을 돌리는 건 단순하지만 낭비 — "이 변경이 이 파이프라인의 산출물에 영향을 주나?"로 트리거를 좁힌다(경로 필터, 변경 감지, 모노레포의 affected 빌드).
- **허용 목록 vs 거부 목록은 "기본값 방향"의 문제다.** 빠뜨렸을 때 더 위험한 쪽을 기본값으로 둔다. 배포 누락이 위험하면 거부 목록(`paths-ignore`)으로 "기본 실행", 무분별 실행이 위험하면 허용 목록(`paths`)으로 "기본 스킵".
- **"안전망은 막는 커밋 자신으로 검증된다"** — 가드/필터를 넣는 변경이 스스로 그 가드를 통과·작동시키게 설계하면(여기선 워크플로 파일 변경=실행 트리거), 별도 실험 없이 즉시 신뢰를 얻는다.

### 코드 위치

- `.github/workflows/deploy.yml` — `on.push.paths-ignore`
- 관련: N-015(이 워크플로의 OIDC 배포), N-016(배포 후 헬스체크)

### 관련 노트

- [N-004. 훅으로 워크플로 강제 — 가이드(soft) vs 훅(hard)](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — "정책을 어느 층에서, 어떤 기본값으로 강제하나"의 같은 결
- [N-015. GitHub Actions → AWS 키 없이 배포](#n-015-github-actions--aws-키-없이-배포--oidc-페더레이션--ecs-롤링-배포) — 이 트리거가 거는 그 배포 파이프라인

---

## N-021. HTTPS는 앱이 아니라 앞단에서 끝낸다 — TLS termination (ALB/ACM)

**한 줄 요약**: 공개 서비스의 HTTPS는 보통 애플리케이션 코드가 아니라 **앞단(로드밸런서/CDN)에서 TLS를 해제(termination)**한다. 사용자↔로드밸런서 구간만 HTTPS면 외부 위협(도청·변조·세션 탈취)은 막히고, 로드밸런서↔앱(VPC 사설망) 구간은 HTTP여도 실무상 허용된다. 그래서 "Spring을 HTTPS로 바꾼다"가 아니라 "ALB에 인증서(ACM) 붙이고 앱은 거의 그대로 둔다"가 정석이다.

### 자세한 설명

HTTP는 평문이라 전송 구간의 누구든(같은 와이파이의 공격자, 중간 라우터, ISP) 내용을 보고 바꿀 수 있다. 인증·계정 기능이 있는 서비스에서 구체적 피해:
- **로그인 비밀번호**(`POST /login`의 password) 평문 노출
- **세션 쿠키**(`JSESSIONID`) 탈취 → 그 계정으로 로그인됨(session hijacking)
- **비밀번호 변경·회원 탈퇴** 같은 민감 요청을 가로채거나 변조

HTTPS(=HTTP over TLS)는 이 구간을 **암호화 + 무결성 + 서버 신원확인(인증서)**으로 막는다.

**왜 앱에 직접 TLS를 박지 않나 — TLS termination의 위치**

```
사용자  ──HTTPS(443)──▶  ALB / CloudFront  ──HTTP(80)──▶  ECS(Spring 앱)
        (공개 구간, 암호화)   ↑ 여기서 TLS 해제          (VPC 내부 사설망)
                            ACM 인증서 부착
```

- **공개 구간(사용자↔LB)**만 HTTPS면 외부 위협은 전부 막힘 — 위협 모델상 위험한 곳은 인터넷 구간이다.
- **내부 구간(LB↔앱)**은 AWS VPC 사설망이라 HTTP여도 실무상 허용(원하면 여기도 mTLS 가능하나 보통 생략).
- 인증서 발급·갱신·TLS 핸드셰이크 같은 무겁고 까다로운 일을 **ALB + ACM이 대신** 처리 → 앱은 keystore·인증서 갱신을 신경 쓸 필요가 없다. Spring에 직접 `server.ssl.*` + keystore를 박는 것보다 운영이 압도적으로 단순.

**앱이 그래도 알아야 하는 것 — proxy 뒤에 있다는 사실**
- LB가 TLS를 풀고 HTTP로 전달하면, 앱은 자기가 `http://`로 불렸다고 착각해 리다이렉트 URL·쿠키 Secure 판단을 틀리게 한다.
- LB는 원래 스킴을 `X-Forwarded-Proto` 헤더로 알려준다. Spring에서 `server.forward-headers-strategy=framework`(또는 `native`)로 이 헤더를 신뢰하게 하면 앱이 "나는 https로 호출됐다"를 올바로 인식한다.
- 세션 쿠키에 `Secure` 플래그(HTTPS로만 전송), 이후 HSTS 헤더(브라우저에 "다음부터 무조건 HTTPS")까지가 마무리.

### 일반화 포인트 (면접 답변용)

- **TLS termination은 "어디서 암호화를 푸느냐"의 설계 결정**이다. 보통 엣지(LB/CDN/리버스프록시)에서 풀고 내부는 평문 — 위협이 집중된 공개 구간만 보호하면 비용 대비 효과가 크고, 인증서 관리가 한 곳에 모인다.
- **앱은 종종 프록시 뒤에 있다.** 그러면 클라이언트의 진짜 IP·스킴·호스트는 `X-Forwarded-*` 헤더로 전달되고, 앱은 이를 신뢰하도록 설정해야 리다이렉트·쿠키·로깅이 맞는다(단, 신뢰 경계 안에서만 신뢰 — 외부에서 위조 가능하므로 프록시가 덮어써야 함).
- **로컬 개발은 HTTP로 충분**하다 — 위험한 전송 구간(인터넷)이 없으니 인증서·HTTPS 셋업의 마찰을 질 이유가 없다. 보안 조치는 위협이 있는 곳에 건다.
- **관리형 인증서(ACM 등)는 갱신 자동화**가 핵심 가치 — 수동 인증서는 만료로 사이트가 죽는 사고가 흔하다.

### 코드 위치 / 적용 위치

- (예정) `.github`·인프라 레벨 — ALB HTTPS(443) 리스너 + ACM 인증서, HTTP(80)→HTTPS 리다이렉트 규칙
- (예정) `src/main/resources/application.properties` (prod) — `server.forward-headers-strategy=framework`, 세션 쿠키 `Secure`
- 작업 항목으로 `plan.md`의 "보안 / 인프라"에 기록됨

### 관련 노트

- [N-015. GitHub Actions → AWS 키 없이 배포](#n-015-github-actions--aws-키-없이-배포--oidc-페더레이션--ecs-롤링-배포) — 같은 ECS/ALB 인프라 위
- [N-016. ECS 헬스체크와 콜드스타트](#n-016-ecs-헬스체크와-콜드스타트--alb-타깃-헬스-vs-컨테이너-grace-period) — 그 ALB가 트래픽을 라우팅하는 동일 계층
- [N-011. Spring Security 폼 로그인](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-두-빈이-인증을-켠다) — HTTPS가 보호하려는 그 인증 자격증명

---

## N-022. 프록시 뒤의 앱은 X-Forwarded-*를 신뢰해야 한다 — forward-headers와 명시 빈

**한 줄 요약**: ALB가 TLS를 종료하고 평문 HTTP로 앱에 넘기면, 앱은 자기가 http로 불렸다고 착각한다. 프록시가 붙여주는 `X-Forwarded-Proto/Host/Port`를 신뢰(ForwardedHeaderFilter)해야 앱이 "나는 https로 호출됐다"를 올바로 인식해 **리다이렉트 URL·OAuth `redirect_uri`를 https로** 만든다. N-021(인프라가 TLS를 끝낸다)의 짝 — 앱 측 대응.

### 자세한 설명

TLS termination(N-021) 구조에서 앱이 받는 요청은 평문 http다. 그대로면 `request.getScheme()`이 `http`, 호스트는 내부 주소가 된다. 그러면:
- 스프링이 만드는 리다이렉트(Location)·절대 URL이 `http://내부주소`가 됨
- 특히 OAuth2 인가요청의 `redirect_uri`가 `http://...`로 생성 → 구글은 https만 허용하므로 `redirect_uri_mismatch`로 로그인 자체가 깨짐

프록시는 원래 정보를 헤더로 알려준다: `X-Forwarded-Proto: https`, `X-Forwarded-Host`, `X-Forwarded-Port`. `ForwardedHeaderFilter`가 이 헤더로 요청을 감싸면 `getScheme()`/`getRequestURL()`이 https·원래 호스트를 반환한다.

**Boot에서 켜는 두 방법, 그리고 함정**:
- 프로퍼티 `server.forward-headers-strategy=framework` — 보통 이걸로 ForwardedHeaderFilter가 등록된다. **하지만 Boot 4의 모듈 분리 환경에서 그 자동구성 빈이 활성화 안 돼 무동작인 사례**가 있었다(T-014).
- **명시 빈 등록**(`FilterRegistrationBean<ForwardedHeaderFilter>`, `HIGHEST_PRECEDENCE`) — 버전·구성에 무관하게 확실. 보안 필터보다 먼저 실행돼 요청 스킴을 먼저 바로잡아야 한다.

**신뢰 경계**: forwarded 헤더는 클라이언트가 위조할 수 있다. 그래서 "프록시 뒤(사설 네트워크)에만 노출되고, 그 프록시가 헤더를 덮어쓴다"는 전제에서만 신뢰해야 안전하다. 우리 앱은 ALB를 통해서만 도달 가능하므로 전제 충족.

### 일반화 포인트 (면접 답변용)

- **TLS termination을 쓰면 앱은 프록시 뒤에 있다는 사실을 알아야 한다.** 클라이언트의 진짜 스킴/호스트/IP는 `X-Forwarded-*`(또는 `Forwarded`)로 오고, 앱은 이를 신뢰하도록 설정해야 리다이렉트·쿠키 Secure 판단·OAuth redirect_uri·로깅이 맞는다.
- **"프로퍼티가 맞는데 효과가 없다"** 면 그 프로퍼티가 의존하는 자동구성이 실제로 켜졌는지 의심하라. 핵심 동작은 명시 빈으로 못 박으면 환경 의존성이 사라진다.
- **이런 동작은 MockMvc로 안 잡힌다** — 서블릿 컨테이너 필터(FilterRegistrationBean)는 실서버에서만 적용된다. `webEnvironment=RANDOM_PORT` + 실제 HTTP로 검증.

### 코드 위치

- `src/main/java/com/booktimer/config/WebConfig.java` — ForwardedHeaderFilter 명시 빈
- `src/test/java/com/booktimer/config/ForwardedHeadersHttpsTest.java` — RANDOM_PORT 종단 검증
- 관련: troubleshooting T-014(프로퍼티 무동작 함정)

### 관련 노트

- [N-021. HTTPS는 앞단에서 TLS termination](#n-021-https는-앱이-아니라-앞단에서-끝낸다--tls-termination-albacm) — 이 노트의 인프라 측 짝
- [N-012. 인증 주체 ≠ 도메인 엔티티](#n-012-인증-주체--도메인-엔티티--principal로-도메인-user를-다시-잇고-접속을-lazy-누적-트리거로) — 같은 OAuth 로그인 흐름

---

## N-023. ddl-auto=update의 한계 — 스키마 드리프트와 마이그레이션(Flyway)

**한 줄 요약**: Hibernate `ddl-auto=update`는 **새 컬럼·테이블만 추가**하고 **기존 컬럼의 제약 변경(NOT NULL 완화, 타입·길이 변경, 컬럼/제약 삭제)은 하지 않는다**. 그래서 엔티티를 바꿔도 운영 DB가 안 따라오는 **스키마 드리프트**가 생긴다. 근본 해법은 버전 관리되는 **마이그레이션 도구(Flyway/Liquibase)**다.

### 자세한 설명

`passwordHash`를 소셜 계정 지원을 위해 nullable로 바꿨는데, 운영 INSERT가 `Column 'password_hash' cannot be null`로 500이 났다(T-015). 원인은 `ddl-auto=update`가 새 컬럼(`auth_provider`)은 추가하면서도 기존 `password_hash`의 NOT NULL은 **건드리지 않았기** 때문 — 엔티티(nullable)와 DB(NOT NULL)가 어긋난 채 배포된 것.

**`update`가 하는 일 / 안 하는 일**:
- 한다: 없는 테이블 생성, 없는 컬럼 추가, (일부) 인덱스/FK 추가
- 안 한다: 기존 컬럼의 nullable·타입·길이 변경, 컬럼/테이블 삭제, 제약 제거 — **파괴적이거나 데이터 영향이 있는 변경은 일절 안 함**(안전을 위해)
- 게다가 적용 순서·결과가 방언·버전에 따라 달라 **운영에서 신뢰 불가**

**마이그레이션 도구(Flyway)가 근본책인 이유**:
- 스키마 변경을 `V2__make_password_nullable.sql`처럼 **명시 SQL 스크립트**로 작성 → 코드처럼 리뷰·커밋
- 각 스크립트는 **정확히 한 번, 순서대로** 적용되고 `flyway_schema_history`에 기록 → 환경 간 동일·재현 가능, 드리프트 없음
- 단, **자동이 아니다**: ALTER는 본인이 작성. 그리고 **이미 ddl-auto로 만들어진 기존 DB에 도입하려면 baseline**이 필요하다(현재 스키마를 v1로 표시 → 그 이후 버전만 적용). 도입 시 `ddl-auto`는 `validate`(또는 none)로 내려 자동 변경을 끈다.

### 일반화 포인트 (면접 답변용)

- **`ddl-auto=update`는 개발 편의 기능이지 운영 마이그레이션 도구가 아니다.** prod 스키마는 명시적·버전관리·재현가능해야 한다 → Flyway/Liquibase.
- **엔티티 변경 ≠ 스키마 변경.** ORM이 모든 변경을 반영해주지 않는다(특히 기존 컬럼 제약). 변경의 "종류"를 알고, 파괴적/제약 변경은 마이그레이션으로 명시.
- **기존 DB에 마이그레이션 도구를 들일 땐 baseline이 핵심** — 안 그러면 도구가 처음부터 다시 만들려다 충돌한다.
- **이런 불일치는 H2 테스트로 안 잡힌다**(테스트는 매번 새 스키마 생성). 운영은 누적된 기존 스키마라 드리프트가 prod에서만 터진다 → 스테이징/마이그레이션으로 방어.

### 코드 위치

- `src/main/java/com/booktimer/config/PasswordHashNullableSchemaFix.java` — 임시 보정(prod 기동 시 멱등 ALTER), Flyway 도입 시 제거 예정
- `src/main/resources/application-prod.properties` — `ddl-auto=update`(→ 추후 `validate` + Flyway)
- 관련: troubleshooting T-015(이 사건), plan.md(Flyway 도입 항목)

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 같은 "스키마/제약은 신중히" 결
- [N-008. JPA Auditing](#n-008-jpa-auditing--누가-시각을-채우나-그리고-슬라이스-테스트의-함정) — 같은 JPA/스키마 영역

---

## N-024. Spring Boot 4의 autoconfig 모듈 분리 + 기존 DB에 Flyway 도입(baseline)

**한 줄 요약**: ① Spring Boot 4는 거대한 `spring-boot-autoconfigure`를 **기술별 모듈**(`spring-boot-jdbc`/`-jpa`/`-flyway`…)로 쪼갰다 — 라이브러리(`flyway-core`)만 추가하면 **클래스는 있지만 자동설정 빈이 안 생긴다**. 해당 `spring-boot-<tech>` 모듈(보통 스타터가 끌어옴)이 있어야 한다. ② **이미 운영 중인 DB에 Flyway를 도입**할 땐 `baseline-on-migrate=true`로 "현재 스키마=V1 적용됨"을 표시하고, V1은 신규 환경에서만 실제로 실행되게 한다.

### 자세한 설명

**(1) Boot 4 autoconfig 모듈화** — Boot 3까진 `spring-boot-autoconfigure` 한 덩어리가 모든 통합의 자동설정을 담았다. Boot 4는 이를 기술별로 분리했다. 그래서 `flyway-core`만 의존에 넣으면 `org.flywaydb.*` 클래스는 컴파일·런타임에 있지만 `FlywayAutoConfiguration`이 클래스패스에 없어 **`Flyway` 빈이 생성되지 않는다**(`NoSuchBeanDefinitionException`). 해결은 autoconfig 모듈 `org.springframework.boot:spring-boot-flyway` 추가(이게 `flyway-core`를 전이로 끌어온다). 같은 결의 함정을 webmvc·jdbc·jpa·test 슬라이스에서 이미 봤다(N-007/T-006) — **"라이브러리를 넣었다 ≠ 자동설정이 켜졌다"** 가 Boot 4의 일반 교훈.

**(2) 기존 DB에 Flyway 도입(baseline)** — `ddl-auto=update`로 굴러온 운영 DB엔 `flyway_schema_history`가 없다. 그냥 Flyway를 켜면 비어있지 않은 스키마에 V1(create table…)을 실행하려다 충돌한다. 그래서:
- `baseline-on-migrate=true` + `baseline-version=1`: 첫 기동 때 **비어있지 않은 스키마를 발견하면** history 테이블을 만들고 "V1까지 적용됨"으로 **마킹만** 한다(V1 실행 X). 이후 `V2+`만 적용.
- **신규/빈 환경**(테스트 H2, 새 배포)에선 baseline이 트리거되지 않아 **V1부터 실제로 실행** → 스키마 생성.
- 즉 **V1 = "현재 운영 스키마의 스냅샷"**. 그래서 V1 작성 기준을 추측하지 말고 Hibernate가 생성하는 DDL을 export해 맞췄다.
- 이식성: enum 컬럼은 네이티브 `enum(...)` 대신 `varchar`로(@Enumerated(STRING) 의미 유지, MySQL·H2 공통 실행). 시각은 `datetime(6)`.

**ddl-auto는 validate가 아니라 none으로** 내렸다: 기존 운영 스키마와 엔티티 매핑의 미세한 타입 차이로 `validate`가 기동을 막을 위험이 있어서. 드리프트 검증은 별도 테스트(`FlywayMigrationTest`)가 격리 H2에 V1을 적용한 뒤 `ddl-auto=validate`로 따로 한다.

### 일반화 포인트 (면접 답변용)

- **Boot 4에선 "스타터"를 쓰는 이유가 더 분명해졌다** — 스타터가 라이브러리 + 자동설정 모듈을 함께 끌어온다. 라이브러리만 직접 박으면 빈이 안 뜰 수 있다.
- **Flyway는 마법이 아니다 — 기존 DB엔 baseline이 출입증.** V1은 "지금 스키마"를 그대로 그린 것이어야 신규 환경과 기존 환경이 같은 그림을 공유한다.
- **cutover 위험 관리**: 첫 전환에서 `validate`는 기동 실패 위험이 있으니 `none` + 별도 검증 테스트로 안전하게.

### 코드 위치

- `build.gradle` — `spring-boot-flyway`(autoconfig) + `flyway-mysql`
- `src/main/resources/db/migration/V1__init_schema.sql` — baseline 스키마
- `src/main/resources/application.properties` — `baseline-on-migrate`/`baseline-version`
- `src/test/java/com/booktimer/migration/FlywayMigrationTest.java` — 격리 H2에서 V1 적용 + validate 검증
- 관련: T-016(빈 미생성), N-023(왜 Flyway인가)

### 관련 노트

- [N-023. ddl-auto=update의 한계](#n-023-ddl-autoupdate의-한계--스키마-드리프트와-마이그레이션flyway) — 이 도입의 동기
- [N-007 / T-006](#) — 같은 Boot 4 "패키지/모듈 이동" 결의 함정

---

## N-025. 로그인 지연의 범인은 보통 DB가 아니라 BCrypt × 작은 vCPU

**한 줄 요약**: "로그인이 느리다"의 원인은 대개 DB가 아니다. 로그인은 **인덱스 단건 조회(빠름) + BCrypt 비밀번호 검증(의도적으로 느린 CPU 집약 연산)**으로 이뤄지는데, vCPU가 작으면(예: Fargate 0.25 vCPU) BCrypt가 수백 ms~1s로 늘어난다. 해법은 **해시 강도를 낮추는 게 아니라(=보안 약화) CPU를 늘리는 것**.

### 자세한 설명

로그인 POST가 하는 일:
1. `findByEmail` — email 유니크 인덱스 **단건 조회** → 수 ms. DB가 작아도 빠르다.
2. **BCrypt 검증** — `passwordEncoder.matches(raw, hash)`. BCrypt는 **work factor(strength)** 만큼 키 스트레칭을 반복하는 **CPU 집약** 연산이다(강도 10 = 2^10 라운드). **느린 게 정상이자 목적** — 무차별 대입을 비싸게 만든다.

**왜 운영에서 더 느린가**: BCrypt 시간은 거의 전적으로 CPU 속도에 비례한다. 노트북(풀 코어)에선 ~50ms여도, **Fargate 0.25 vCPU(코어의 1/4, 버스트 스로틀)** 에선 수백 ms~1s까지 늘 수 있다. 거기에 **JVM JIT 워밍업**(작은 vCPU에선 더 느림)이 더해져 배포·유휴 직후 첫 로그인이 특히 굼뜨다.

**진단법 — DB를 의심하기 전에 분리 측정**:
- 정적/경량 경로(헬스, 로그인 *페이지* GET)와 로그인 *POST* 의 지연을 비교한다. 전자가 빠른데(예: 60~150ms) 로그인만 느리면 → 차이는 그 경로에만 있는 **BCrypt(+CPU)** 다. (실제로 BookTimer에서 이렇게 좁혔다.)
- CloudWatch에서 로그인 순간 **CPU 사용률이 100% 근처로 튀는지** 확인.
- "매번 느림" → CPU/BCrypt / "배포 직후만" → JVM 워밍업.

### 일반화 포인트 (면접 답변용)

- **"느리다 = DB 문제"는 성급한 결론.** 요청이 하는 일을 단계로 쪼개 **어디에 시간이 쓰이는지 분리 측정**하는 게 먼저다. 인증은 의외로 CPU(해싱) 바운드다.
- **BCrypt/Argon2 같은 패스워드 해시는 "느린 게 기능"** — 그래서 튜닝 손잡이는 둘이다: 보안을 위해 **강도는 유지/상향**, 지연이 문제면 **CPU를 키운다**. 강도를 낮춰 속도를 버는 건 보안을 파는 것.
- **작은 컨테이너(0.25 vCPU)의 함정**: CPU 집약 작업(해싱, JIT, 직렬화)이 불균형하게 느려진다. 비용 절감과 지연 사이의 트레이드오프를 의식적으로.

### 코드 위치

- `src/main/java/com/booktimer/config/SecurityConfig.java` — `BCryptPasswordEncoder()`(기본 강도 10)
- `deploy/task-definition.json` — `cpu: 256`(0.25 vCPU) ← 지연의 실제 원인, 상향 후보(plan.md)
- 관련: plan.md "Fargate CPU 상향" 항목

### 관련 노트

- [N-016. ECS 헬스체크와 콜드스타트](#) — 같은 "작은 태스크/워밍업" 결
- [N-011. Spring Security 폼 로그인](#) — 로그인 인증 흐름(UserDetailsService + PasswordEncoder)

---

## N-026. OAuth find-or-create의 함정(email_verified) + Spring Security가 막아주지 않는 것(brute-force)

**한 줄 요약**: 소셜 로그인의 "이메일로 사용자 찾거나 만들기(find-or-create)"는 **검증된 이메일(`email_verified=true`)일 때만** 안전하다 — 안 그러면 자동 계정 연결이 탈취 벡터가 된다. 그리고 Spring Security는 CSRF·세션고정은 기본으로 막아주지만 **무차별 대입(brute-force) 방어는 직접** 해야 한다.

### 자세한 설명

**(1) OAuth find-or-create와 `email_verified`**

소셜 로그인이 성공하면 provider가 준 이메일로 우리 사용자를 찾고, 없으면 만든다(`OAuthUserProvisioningService.provision`). 즉 **이메일을 신원(identity)으로** 쓴다. 여기엔 숨은 전제가 있다 — "그 이메일을 로그인한 사람이 실제로 소유한다".

provider가 이메일 소유를 보증하지 않으면(=`email_verified`가 아니면) 이 전제가 깨진다:
- 같은 이메일의 **기존 LOCAL(이메일/비번) 계정**이 있으면, 비번 없이 그 계정에 자동 로그인된다(자동 연결).
- 공격자가 **피해자 이메일을 미검증 상태로 주장**하는 소셜 계정을 만들 수 있으면 → 피해자 계정 탈취.

구글은 항상 이메일을 검증하므로 *구글 한정* 현재 위험은 낮다. 그러나 (a) provider 추가(카카오/네이버 — 검증 정책 상이), (b) 엣지케이스를 대비한 **방어 한 겹**으로 `email_verified == true`가 아니면 프로비저닝 전에 거부해야 한다. 클레임이 **없으면(null) "검증 안 됨"으로 간주**(fail-safe)한다.

> 설계 포인트: 게이트를 네트워크에 묶인 어댑터(`OidcUserService`)가 아니라 **순수 서비스(`provision`)**에 두면 단위 테스트로 "미검증→거부 / 검증→통과"를 결정적으로 검증할 수 있다(N-009 계층 분리와 같은 결).

**(2) Spring Security가 막아주는 것 ≠ 전부**

Spring Security는 **CSRF**(기본 ON), **세션 고정 보호**(로그인 시 세션 ID 교체)를 기본 제공한다. 하지만 **로그인 무차별 대입(brute-force) 방어는 기본 제공하지 않는다** — `POST /login`을 무한히 때려도 막는 게 없다. 직접 만들어야 한다:

- **실패 집계**: 인증 성공/실패는 Spring Security가 **이벤트**(`AbstractAuthenticationFailureEvent` / `AuthenticationSuccessEvent`)로 발행한다(발행 보장하려면 `AuthenticationEventPublisher` 빈 등록). 이벤트의 `Authentication.getDetails()`가 `WebAuthenticationDetails` → 거기서 **클라이언트 IP**를 꺼낸다.
- **차단**: 잠긴 키의 요청을 **인증 매니저에 닿기 전에 단락**하는 필터(`OncePerRequestFilter`)를 `UsernamePasswordAuthenticationFilter` 앞에 끼운다.
- **키 선택 — 이메일이 아니라 IP**: 이메일을 키로 하면 공격자가 피해자 이메일로 일부러 실패시켜 **그 계정을 잠그는 DoS**가 가능하다. IP 기준이면 공격 출처만 막힌다(분산 출처엔 약 → 앞단 WAF 레이트리밋과 함께 쓰는 다층 방어).

### 일반화 포인트 (면접 답변용)

- **"이메일은 식별자가 될 수 있지만, 검증된 이메일일 때만."** OAuth find-or-create에서 `email_verified`를 안 보면 자동 계정 연결이 탈취 벡터가 된다. 클레임 부재는 fail-safe로 "미검증" 처리.
- **"프레임워크가 막아주는 것과 아닌 것을 구분하라."** CSRF·세션고정은 Spring Security 기본 ON, 그러나 brute-force·레이트리밋·계정 잠금은 **직접** 해야 한다. "보안 프레임워크를 썼으니 안전"은 착각.
- **잠금 키 설계의 트레이드오프**: 이메일 키(피해자 잠금 DoS) vs IP 키(분산 공격에 약). 정답은 다층(앱 IP 잠금 + 앞단 WAF).
- **테스트 가능한 보안**: 보안 규칙도 순수 코어로 분리하면(시간은 `Clock` 주입) 경계값(임계치 직전/도달/만료/성공 리셋)을 결정적으로 테스트할 수 있다.

### 코드 위치

- `src/main/java/com/booktimer/user/OAuthUserProvisioningService.java` — `provision(...)`에 `email_verified` 게이트
- `src/main/java/com/booktimer/security/BookTimerOidcUserService.java` — `oidcUser.getEmailVerified()` 전달
- `src/main/java/com/booktimer/security/LoginAttemptService.java` — IP별 실패 집계 + 잠금(코어, `Clock` 주입)
- `src/main/java/com/booktimer/security/LoginAttemptEventListener.java` — 인증 이벤트 → 집계
- `src/main/java/com/booktimer/security/LoginAttemptFilter.java` — 잠긴 IP 단락
- `src/main/java/com/booktimer/config/SecurityConfig.java` — 필터 배선 + `AuthenticationEventPublisher` 빈

### 관련 노트

- [N-011. Spring Security 폼 로그인](#) — 인증 흐름의 토대(이 위에 방어를 얹음)
- [N-012. 인증 주체 ≠ 도메인 엔티티](#) — principal(email) 통일 규약 — OAuth/폼 공통
- [N-009. 계층별 테스트 전략](#) — 순수 코어 분리로 보안 규칙도 단위 테스트

---

## N-027. OAuth 동의 화면은 provider가 제공 / 개인정보처리방침은 앱 제작자 책임 — 게시(Production)와 검증

**한 줄 요약**: OAuth 로그인의 **동의 화면(consent screen)** UI는 provider(Google)가 자동으로 띄운다 — 내가 만들 일이 없다. 반면 **개인정보처리방침(Privacy Policy)** 문서는 그 화면에 *링크로 노출될 뿐*, 내용은 앱 제작자가 쓰고 호스팅해야 한다. 둘은 다른 것이다. 그리고 요청 스코프가 **non-sensitive**(`openid`/`email`/`profile`)면 Google **검증(verification) 절차 없이 즉시 게시(Publish)** 할 수 있고, 코드 변경도 없다.

### 자세한 설명

"게시 전에 동의 절차를 구성해야 한다"는 말을 "내가 동의 화면을 만들어야 한다"로 오해하기 쉽다. 실제로 섞여 있는 건 **세 가지 다른 책임**이다.

| 항목 | 누가 담당 | 설명 |
|---|---|---|
| **동의 화면(consent screen) UI** | provider(Google) | "이 앱이 당신의 이메일·프로필에 접근하려 합니다 → 허용/거부" 그 화면 자체. 내가 만들 필요 없음 — provider가 자동 렌더 |
| **개인정보처리방침 문서** | **앱 제작자** | 동의 화면에 **링크로** 걸리는 법적 문서. provider는 링크를 보여줄 뿐, 내용은 내가 작성·호스팅 |
| **브랜딩(앱 이름·지원 이메일·로고)** | 앱 제작자 | 동의 화면에 노출되는 표시 정보. provider 콘솔에서 설정 |

**왜 provider가 내 정책 문서를 요구하나**: 앱이 사용자 데이터(이메일)를 받기 때문이다. provider 정책상 "사용자 데이터를 받는 앱은 그 데이터를 어떻게 다루는지 사용자에게 고지"해야 하고, 그 고지 수단이 개인정보처리방침 링크다. provider가 대신 써주지 않는다.

**게시(Testing → Production)와 검증(verification)은 별개**:
- **Testing**: provider가 지정한 테스트 사용자(Google은 최대 100명)만 로그인 가능. 개발/초기엔 이 상태.
- **Production(게시)**: 누구나 로그인 가능.
- **검증(verification)**: provider의 수동 심사. **민감(sensitive)·제한(restricted) 스코프**(Gmail 읽기, 드라이브 등)를 요청할 때만 필요하고, 며칠~몇 주 걸린다.
- 핵심: **non-sensitive 스코프(`openid`/`email`/`profile`)만 쓰면 검증 없이 즉시 게시**할 수 있다. 개인정보처리방침 URL도 이 경우 하드 차단 조건이 아닌 **선택/권장 필드**인 경우가 많다(있으면 깔끔). 게시는 콘솔에서 "Publish app" 클릭 한 번, **코드 변경 0**.

### 일반화 포인트 (면접 답변용)

- **"동의 흐름(메커니즘)"과 "동의에 필요한 콘텐츠(정책 문서)"를 구분하라.** provider가 제공하는 건 *흐름·UI*이고, 앱 제작자가 채우는 건 *신원 정보·법적 문서·요청 스코프*다. "OAuth 붙였으니 동의는 알아서 되겠지"와 "정책 문서까지 내가 준비"를 헷갈리면 안 된다.
- **요청 스코프의 민감도가 게시 비용을 결정한다.** non-sensitive면 검증 없이 즉시 게시(저비용), sensitive/restricted면 provider 수동 심사(고비용·지연). → 처음부터 "정말 필요한 최소 스코프만" 요청하는 게 최소권한 원칙이자 운영 비용 절감.
- **게시 ≠ 검증.** 사용자 수 제한 해제(게시)와 provider 심사(검증)는 다른 트리거다 — 스코프가 가벼우면 게시만 하면 된다.

### 코드 위치

- (코드 변경 없음) — Google Cloud Console의 OAuth 동의 화면 설정 / Publish app
- `plan.md` "OAuth 소셜 로그인 → 동의 화면 게시(Production 전환)" 체크리스트

### 관련 노트

- [N-026. OAuth find-or-create의 함정 + brute-force](#) — 게시의 보안 전제(email_verified·brute-force)는 이미 충족
- [N-012. 인증 주체 ≠ 도메인 엔티티](#) — principal=email 통일, OAuth/폼 공통

---

## N-028. catch-all 예외 핸들러는 프레임워크의 상태보유 예외(404 등)까지 삼킨다 — 상태코드 보존

**한 줄 요약**: `@ExceptionHandler(Exception.class)`로 "처리 안 된 예외는 다 500 + 친절한 에러 페이지"를 만들면, **프레임워크가 정상적으로 던지는 상태보유 예외**(없는 리소스 404 등)까지 함께 삼켜 500으로 둔갑시키고 에러 로그를 도배한다. 자기 상태코드를 들고 오는 예외는 **더 좁은 타입의 핸들러로 먼저 잡아 그 코드를 보존**해야 한다.

### 자세한 설명

전역 예외 핸들러의 의도는 "내가 미처 처리 못 한 *예기치 못한* 예외(예: NPE, `IllegalStateException`)를 흉한 whitelabel 대신 친절한 화면 + 500으로 바꾸자"였다. 그런데 `@ExceptionHandler(Exception.class)`는 글자 그대로 **모든 예외**를 잡는다 — 여기엔 프레임워크가 **정상 흐름으로** 던지는 것도 포함된다:

- 없는 정적 리소스/매핑 안 된 경로 → `NoResourceFoundException`(원래 **404**)
- 코드가 명시적으로 던진 `ResponseStatusException`(원하는 상태코드 내장)
- 검증 실패(`HandlerMethodValidationException` 등, 보통 **400**)

이것들이 catch-all에 잡히면 전부 **500**으로 바뀐다. 증상:
- 브라우저가 매 페이지마다 자동 요청하는 `/favicon.ico`가 없으면 → **요청마다 500 + `log.error` 스택트레이스** → 운영 로그가 노이즈로 도배. 진짜 500을 찾기 어려워진다.
- 클라이언트는 "없는 페이지"인데 서버 장애(500)로 오인하게 된다.

**해결 — 상태보유 예외를 더 좁은 타입으로 먼저 잡는다**:
```java
// 자기 상태코드를 들고 오는 예외 → 그 코드 보존 (Exception 핸들러보다 우선)
@ExceptionHandler({ResponseStatusException.class, NoResourceFoundException.class})
public String handleStatusException(Exception ex, Model model, HttpServletResponse response) {
    int status = ((ErrorResponse) ex).getStatusCode().value();  // 둘 다 ErrorResponse 구현
    response.setStatus(status);
    log.debug(...);            // 서버 결함 아님 → error 아닌 debug
    return "error";
}

@ExceptionHandler(Exception.class)   // 진짜 예기치 못한 것만 500
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public String handleUnexpected(Exception ex, Model model) { log.error(...); ... }
```
- `@ExceptionHandler`는 **가장 구체적인 타입이 우선** 적용된다 → 404 예외는 위 핸들러가, 나머지는 catch-all이.
- 로그 레벨도 분리: 상태보유(클라이언트 상황)는 `debug`, 진짜 예기치 못한 것만 `error`.
- 함정: Boot 4(Spring 7)에서 `NoResourceFoundException`이 `ResponseStatusException` 하위가 아니게 바뀜 → 두 타입을 따로 잡고 공통 인터페이스 `ErrorResponse.getStatusCode()`로 코드를 읽어야 한다(T-019).

### 일반화 포인트 (면접 답변용)

- **"모든 예외를 잡는다"는 너무 넓다.** catch-all은 *내 코드의 버그*뿐 아니라 *프레임워크의 정상 신호(404/400)*까지 잡는다 → 의미 있는 상태코드를 500으로 뭉갠다. 예외 처리는 "예기치 못한 것"과 "이미 의미가 정해진 것"을 구분해야 한다.
- **예외에 담긴 상태코드는 정보다 — 보존하라.** HTTP 의미(404=없음, 400=잘못된 요청, 500=서버 잘못)는 클라이언트·모니터링·검색엔진이 다르게 해석한다. 다 500으로 만들면 그 정보가 사라진다.
- **로그 레벨 = 심각도.** 클라이언트가 없는 URL을 친 건 `error`가 아니다(서버는 멀쩡). 잘못된 레벨은 알림 피로와 진짜 사고 은폐를 부른다.
- **핸들러 우선순위는 타입 구체성으로 정해진다** — 넓은 핸들러 옆에 좁은 핸들러를 두어 "예외(특수 케이스)의 예외"를 표현한다.

### 코드 위치

- `src/main/java/com/booktimer/web/GlobalExceptionHandler.java` — 상태보유 핸들러 + catch-all 분리
- `src/test/java/com/booktimer/web/GlobalExceptionHandlerTest.java` — 없는 리소스가 404(500 아님)임을 검증
- 관련: `troubleshooting.md` T-019 (Boot 4 상속 변경 함정)

### 관련 노트

- [N-011. Spring Security 폼 로그인](#) — 보안 예외는 필터 단계(`ExceptionTranslationFilter`)에서 처리돼 이 핸들러로 안 옴(영역 분리)
- [N-019. DB 유니크 제약 — 방어선의 위치](#) — "어느 계층/타입이 무엇을 책임지나"의 같은 사고

---

## N-029. 인메모리 세션은 인스턴스가 죽으면 사라진다 — 세션 외부화와 무상태 앱 서버

**한 줄 요약**: 기본 `HttpSession`은 **그 앱 인스턴스(JVM)의 메모리**에 저장된다. 그래서 컨테이너/태스크가 교체되면(배포·스케일·크래시) 세션이 통째로 사라져 사용자는 **다시 로그인**해야 하고, 인스턴스가 2개 이상이면 요청이 분산돼 *평소에도* 세션이 오락가락한다. 해법은 세션을 **공유 외부 저장소**(DB·Redis)로 빼서 앱 서버를 *무상태(stateless)*로 만드는 것 — 이것이 무중단 배포·수평 확장의 전제다.

### 자세한 설명

배포할 때마다 재로그인이 발생했다. 범인은 세션 저장 위치다.

- **기본 동작**: 폼 로그인하면 Spring Security는 인증 정보(`SecurityContext`)를 `HttpSession`에 담고, 브라우저엔 세션 ID 쿠키(`JSESSIONID`)만 준다. 이 세션 객체는 **그 앱 인스턴스의 힙 메모리**에 있다.
- **무엇이 깨지나**:
  - **배포(태스크 교체)**: ECS가 옛 태스크를 죽이고 새 태스크를 띄우면 → 옛 태스크 메모리의 세션 전부 소멸 → 쿠키는 남아 있어도 새 태스크엔 그 세션이 없음 → 재로그인.
  - **수평 확장(인스턴스 N개)**: 로드밸런서가 요청을 여러 태스크로 분산하는데, 내 세션은 그중 한 태스크에만 있음 → 다른 태스크로 라우팅되면 로그인 안 된 것처럼 보임. (sticky session으로 한 태스크에 고정할 수 있으나, 그 태스크가 죽으면 똑같이 소멸.)
- **해법 — 세션 외부화**: 세션을 앱 메모리가 아니라 **모든 인스턴스가 공유하는 저장소**에 둔다. 그러면 어느 태스크가 받아도 같은 세션을 읽고, 태스크가 죽어도 저장소에 남는다. 앱 서버는 세션 상태를 안 들고 있는 *무상태*가 되어 자유롭게 교체·증설 가능.
  - 이 프로젝트: **Spring Session JDBC** — 세션을 기존 RDS(MySQL)에 저장(`SPRING_SESSION` 테이블). 새 인프라·비용 0. `HttpSession` API는 그대로 두고 저장 백엔드만 갈아끼움(필터가 가로채 저장소로 위임) — 애플리케이션 코드 변경 없음.
  - **JDBC vs Redis**: Redis(ElastiCache)는 인메모리라 빠르고 TTL 만료가 네이티브 → 세션 쓰기가 많을 때 유리. 대신 별도 인스턴스·비용. 트래픽 작을 땐 기존 DB 재사용(JDBC)이 비용·운영 면에서 합리적. 둘 다 "외부 공유 저장소"라는 본질은 같고 교체도 의존성·설정 수준.
- **부수 효과**: CSRF 토큰도 세션에 저장되므로(기본 `HttpSessionCsrfTokenRepository`) 세션 외부화로 함께 영속화된다. 단, 도입 배포 1회는 쿠키 이름이 바뀌고(`JSESSIONID`→`SESSION`) 기존 인메모리 세션이 소멸해 전원 재로그인 — 이후부턴 유지.

### 일반화 포인트 (면접 답변용)

- **"상태를 어디에 두느냐"가 확장성을 가른다.** 앱 인스턴스 메모리에 사용자 상태(세션)를 두면 그 인스턴스에 묶인다(stateful) → 교체·증설에 약함. 상태를 외부 저장소로 빼면 앱은 무상태가 되어 *마음대로 죽이고 늘릴 수 있다* — 클라우드 네이티브(12-factor의 "Processes are stateless")의 핵심.
- **세션 기반 vs 토큰 기반**: 서버 세션을 외부화하는 대신, 상태를 클라이언트로 미는 JWT 같은 토큰 방식도 있다. 토큰은 서버 저장소가 필요 없지만(무상태) 즉시 무효화·정교한 만료가 어렵다. 세션 외부화는 서버가 제어권을 유지하면서 무상태 앱 서버를 얻는 절충. (인증 매체에 따른 CSRF 판단은 N-011.)
- **재로그인 ≠ 데이터 손실**: 도메인 데이터(타이머 등)는 DB에 있어 안 사라진다. 사라지는 건 *세션*뿐 — 증상을 정확히 분리해야 올바른 해법(세션 저장소)에 도달한다. "배포 때 먹통"(가용성, 무중단 배포)과 "재로그인"(세션 위치)은 **다른 문제**다.
- **무중단 배포의 전제**: 태스크를 겹쳐 띄우려면(롤링) 세션이 공유돼야 한다 — 안 그러면 무중단으로 띄워도 새 태스크로 간 사용자는 로그아웃. 그래서 세션 외부화가 먼저다.

### 코드 위치

- `build.gradle` — `spring-boot-starter-session-jdbc`(Boot 4 autoconfig 모듈 동봉, T-020) + `-test`
- `src/main/resources/db/migration/V2__spring_session.sql` — 세션 테이블(운영 스키마 단일 소스)
- `src/main/resources/application-prod.properties` — `spring.session.jdbc.initialize-schema=never`(Flyway가 소유)
- `src/test/java/com/booktimer/security/SessionJdbcPersistenceTest.java` — 로그인 세션이 JDBC에 영속화되는지 검증
- 관련: `troubleshooting.md` T-020(스타터 필요), `plan.md`(무중단 배포·향후 Redis 전환)

### 관련 노트

- [N-011. Spring Security 폼 로그인 — 세션 기반 인증, CSRF 판단](#)
- [N-012. 인증 주체 ≠ 도메인 엔티티](#) — 세션엔 식별자만, 도메인은 DB에서 재조회(세션을 가볍게)
- [N-024. Boot 4 autoconfig 모듈 분리(Flyway)](#) — 스타터를 써야 빈이 생기는 같은 패턴(세션도 동일)

---

## N-030. 무중단 롤링 배포 — min/max healthy percent로 "헬스 통과 후 교체", circuit breaker 자동 롤백

### 한 줄 요약

ECS 롤링 배포는 **새 태스크가 ALB 헬스체크를 통과한 뒤에야** 옛 태스크를 내리도록
`minimumHealthyPercent=100`/`maximumPercent=200`을 주면 단일 태스크여도 무중단이 된다.
`deploymentCircuitBreaker{rollback}`은 새 태스크가 안정화에 실패하면 자동으로 직전 리비전으로 되돌린다.

### 자세한 설명

**왜 배포 때 잠깐 먹통이었나.** 배포 = 컨테이너(태스크) 교체. 만약 "옛 태스크를 먼저 죽이고
→ 새 태스크를 띄운다"면, 그 사이 ALB 타깃그룹에 healthy 타깃이 0개가 되는 **공백**이 생긴다
(503). 단일 태스크(`desiredCount=1`)일수록 이 공백이 그대로 노출된다.

**두 비율이 교체 순서를 결정한다.** ECS 롤링 배포는 desiredCount 대비 두 한도로 동작한다:

- `minimumHealthyPercent` — 배포 중 **유지해야 할 최소 healthy 비율**. 100%면 옛 태스크를
  "새 태스크가 healthy 되기 전엔" 못 내린다 → 공백 0.
- `maximumPercent` — 일시적으로 띄울 수 있는 **최대 비율**. 200%면 desiredCount=1이어도
  잠깐 2개(옛+새)까지 허용 → 새 태스크를 *추가로* 띄울 여유가 생긴다.

즉 `min=100 / max=200` 조합이 "**먼저 띄우고(scale up) → 새 태스크 헬스 통과 → 옛 태스크 드레인 후 종료**"
순서를 강제한다(= start-then-stop). 둘 중 하나라도 빠지면(`max=100`이면 추가로 못 띄우고,
`min=0`이면 먼저 죽여도 되고) stop-then-start 공백이 생길 수 있다.

**"헬스 통과"의 의미.** 새 태스크가 RUNNING이라고 트래픽을 받는 게 아니다. ALB 타깃그룹
헬스체크(`/actuator/health`)를 연속 통과(healthy threshold)해야 타깃이 healthy로 등록되고,
그때 ECS가 옛 태스크 드레인을 시작한다. 그래서 grace period(앱 부팅 유예)와 헬스체크 간격이
*교체 속도*를 좌우한다(N-016과 연결).

**deregistration delay(연결 드레이닝)는 다운타임 원인이 아니다.** 옛 태스크를 내릴 때 진행 중
요청을 마저 처리하라고 기다리는 시간(기본 300s). 길면 배포가 *느릴* 뿐, 그동안 새 태스크가
이미 트래픽을 받으므로 가용성엔 영향 없다. 흔한 오해 — 단축은 속도 최적화이지 무중단 자체와 무관.

**circuit breaker — 나쁜 배포 방어.** 새 태스크가 계속 헬스체크에 실패하면(잘못된 이미지/설정),
`rollback=true`면 ECS가 자동으로 직전 안정 리비전으로 되돌린다. min=100과 합쳐지면 "옛 태스크는
살아있고 새 태스크만 실패 → 자동 롤백" → 실패한 배포도 무중단.

**전제: 세션 외부화(N-029).** 교체 중 2개 태스크가 동시에 트래픽을 받으므로 세션이 인메모리면
요청이 튄다. 무중단 배포는 무상태 앱 서버를 전제로 한다 — 그래서 세션 외부화를 먼저 했다.

**적용은 코드가 아니라 인프라 설정.** 앱 코드 0줄. `aws ecs update-service --deployment-configuration ...`
한 번이면 서비스에 영속된다(평소 배포는 task definition만 교체, 이 설정은 안 건드림 → 드리프트 없음).

### 코드 위치

- `.github/workflows/zero-downtime-config.yml` — deploymentConfiguration을 멱등 적용(workflow_dispatch)
- `claude-docs/deploy-aws.md` §12-1 — update-service 명령 + 선택적 TG 드레이닝/헬스체크 단축(권한 주의)
- 관련: `plan.md`(무중단 배포 항목)

### 관련 노트

- [N-029. 인메모리 세션 → 세션 외부화](#) — 무중단 배포의 전제(교체 중 다중 태스크가 세션 공유)
- [N-016. ECS 헬스체크와 콜드스타트 — grace period](#) — "헬스 통과 후 교체"에서 헬스의 정의
- [N-015. OIDC + ECS 롤링 배포](#) — 같은 롤링 파이프라인, 여기에 배포 설정을 더한 것

---

## N-031. SameSite=Lax로 CSRF 사전 차단 — 그리고 세션 쿠키 속성은 프로퍼티가 아니라 명시 CookieSerializer 빈으로

### 한 줄 요약

세션 쿠키에 `SameSite=Lax`를 두면 브라우저가 교차 사이트 요청에 쿠키를 자동 첨부하지 않아 CSRF의
1차 차단이 된다. 단, 세션 외부화(Spring Session) 후엔 쿠키를 `DefaultCookieSerializer`가 쓰므로
`server.servlet.session.cookie.*` 프로퍼티가 무동작 → **명시 `CookieSerializer` 빈**으로 설정해야 한다.

### 자세한 설명

**SameSite가 막는 것.** CSRF(Cross-Site Request Forgery)는 공격자 사이트가 사용자의 인증 쿠키를
얹어 우리 서버에 요청을 위조하는 공격이다. `SameSite` 쿠키 속성은 브라우저가 **다른 사이트에서 출발한
요청엔 쿠키를 안 붙이게** 한다:

- `Strict` — 교차 사이트면 무조건 안 붙임. 가장 강하지만, 외부 링크로 들어오거나 **OAuth 리다이렉트
  콜백**(구글 → 우리 콜백 URL)에서도 쿠키가 안 실려 로그인 흐름이 깨질 수 있다.
- `Lax` — 일반 교차 사이트 요청(이미지·폼 POST·iframe 등)엔 안 붙이되, **최상위 GET 내비게이션**
  (주소창 이동/링크 클릭)엔 붙임. OAuth 콜백이 최상위 GET이라 호환된다. → **우리 선택.**
- `None` — 항상 붙임(+`Secure` 필수). 교차 사이트 임베드가 필요한 서드파티 쿠키용.

**CSRF 토큰과의 관계 — 중복이 아니라 다층 방어.** 우리는 이미 Spring Security CSRF 토큰을 쓴다.
SameSite=Lax는 그 위에 얹는 **사전 차단막**이다. 토큰 검증까지 가기 전에 브라우저 레벨에서 교차 사이트
쿠키 자체를 막으니, 토큰 누락/우회 시도의 표면이 줄어든다. "쿠키 기반 인증"의 기본 하드닝 3종은
`SameSite` + `HttpOnly`(JS 접근 차단=XSS 세션 탈취 방어) + `Secure`(HTTPS 전송 한정).

**함정 — 세션 외부화 후 프로퍼티가 무동작.** Spring Boot에선 보통 `server.servlet.session.cookie.same-site`
같은 프로퍼티로 끝난다. 그런데 세션을 외부화(Spring Session JDBC, [[N-029]])하면 세션 쿠키(`SESSION`)는
서블릿 컨테이너가 아니라 **Spring Session의 `DefaultCookieSerializer`** 가 쓴다. 이 조합(Boot 4)에선
그 프로퍼티가 직렬화기에 연결되지 않아 **조용히 무동작** — `Set-Cookie`엔 `Path=/`만 붙는다.
이는 `server.forward-headers-strategy` 프로퍼티가 무동작이라 `ForwardedHeaderFilter`를 명시 빈으로
등록해야 했던 [[N-022]]와 **같은 부류**의 함정이다("표준 프로퍼티인데 안 먹음 → 명시 빈으로").

**해결 — 명시 빈.** `CookieSerializer` 빈을 직접 등록하면 Boot 기본 직렬화기 자동구성이 물러나고
(`@ConditionalOnMissingBean`) Spring Session이 이 빈을 쓴다. `Secure`는 HTTPS에서만 의미가 있고
로컬(http)에선 켜면 쿠키가 아예 안 실려 로그인이 안 되므로, prod 프로퍼티 값으로 분기한다.

**파생 교훈 — 잠재 갭.** 프로퍼티가 무동작이라는 건, 세션 외부화 직후엔 prod에서 의도했던
`Secure`/`HttpOnly`도 SESSION 쿠키엔 안 붙고 있었다는 뜻이다(겉으론 문제 없어 보였음). 명시 빈이
SameSite·HttpOnly·Secure 셋을 한 번에 바로잡는다. **일반 교훈**: 보안 속성은 "설정했다"가 아니라
**실제 산출물(여기선 `Set-Cookie` 헤더)을 직접 확인**해야 한다.

### 코드 위치

- `src/main/java/com/booktimer/config/WebConfig.java` — `cookieSerializer` 빈(SameSite=Lax/HttpOnly/Secure)
- `src/main/resources/application.properties` — 무동작 프로퍼티 대신 빈을 가리키는 주석
- `src/test/java/com/booktimer/security/SessionCookieSameSiteTest.java` — Set-Cookie 헤더로 속성 검증
- 함정 정리: `troubleshooting.md` T-021

### 관련 노트

- [N-022. 프록시 뒤 앱은 X-Forwarded-* 신뢰 — Boot 4에선 명시 빈](#) — 같은 "프로퍼티 무동작 → 명시 빈" 함정
- [N-029. 인메모리 세션 → 세션 외부화](#) — 쿠키 주체가 컨테이너→Spring Session으로 바뀐 원인
- [N-026. Spring Security가 막아주지 않는 것(brute-force)](#) — "기본기 위에 직접 더하는 하드닝" 같은 맥락

---

## N-032. 다중 세션 동시 작업 — git worktree로 워킹 트리 분리 (브랜치만으론 부족)

**한 줄 요약**: 여러 Claude Code 세션을 한 폴더(워킹 트리)에서 동시에 돌리면 파일·브랜치가 충돌한다. 브랜치를 나눠도 같은 폴더면 `git checkout` 이 폴더 전체를 갈아끼워 소용없다 — **격리 단위는 브랜치가 아니라 워킹 트리**다. `git worktree` 로 세션마다 별도 폴더를 주면 한 repo를 공유하면서 충돌 없이 병렬 작업할 수 있다. 단 Flyway 버전·공유 문서·포트 같은 repo 공유 자원은 폴더를 나눠도 따로 조율해야 한다.

### 자세한 설명

**충돌의 근원 = 워킹 트리 공유.** 동시성 버그가 공유 가변 상태에서 나오듯, 두 세션이 한 폴더를 공유하면 그 폴더가 공유 상태가 된다. 실제로 이 프로젝트에서, 한 세션이 메인 폴더를 자기 feature 브랜치로 `checkout` 해 작업 중인데(세션 시작 스냅샷은 `main`이었다) 다른 세션의 `plan.md` 편집이 **그 feature 브랜치 위에 얹히는** 일이 일어났다.

**브랜치 ≠ 격리.** 흔한 오해가 "브랜치를 나누면 된다"인데, 같은 폴더에서 `git checkout <branch>` 는 그 폴더의 **파일 전체를 그 브랜치 상태로 갈아끼운다** → 같은 폴더를 보는 다른 세션의 파일까지 통째로 바뀐다. 나눠야 할 건 브랜치가 아니라 폴더다.

**git worktree — 한 repo, 여러 폴더, 각자 다른 브랜치.**
```
git worktree add ../proj-feat -b feat/x main   # 새 폴더 + 새 브랜치(main 기준)
git worktree list                              # 트리 목록
git worktree remove ../proj-feat               # 작업·머지 후 정리
```
- git 객체·refs·히스토리는 공유하되 **워킹 트리(폴더)만 분리**. 한 폴더에서 커밋하면 다른 폴더에서 `git fetch` 로 보인다.
- **워크트리 = 새 브랜치 한 세트**: 같은 브랜치를 두 워크트리에 동시 체크아웃할 수 없다 → "워크트리 만들어" = 사실상 "새 브랜치 파서 거기서" 와 한 묶음. PR 우선 워크플로와 그대로 맞물린다.
- 빌드 산출물(`build/`), Gradle 데몬 락, H2도 폴더별 독립.

**낙관적 동시성 가드 — "File modified since read".** 도구가 파일을 덮어쓰기 직전, 읽은 뒤 외부에서 바뀌었으면 차단한다. 이건 버그가 아니라 **lost update 방지**(낙관적 잠금). 정답 절차는 *다시 읽기 → 그쪽 변경 보존 → 내 변경만 재적용*.

**"늦었나?" — 미커밋이면 안 늦었다.** 미커밋 변경은 브랜치에 묶이지 않고 워킹 트리에 떠 있을 뿐이라, 어느 브랜치로든 깨끗이 옮길 수 있다(이상적 순서는 *편집 전 분리*지만). "늦어서 곤란"한 시점은 **엉뚱한 브랜치에 커밋·push·머지까지 한 뒤**다.

**worktree로도 남는 공유 자원** (폴더를 나눠도 충돌 → 조율 필요):
- **Flyway 버전 번호**(`V5__`, `V6__`) — 두 세션이 같은 번호를 쓰면 충돌. 번호 구역 배정 또는 머지 후 부여.
- **공유 문서**(plan.md / README / CLAUDE.md / 노트들) — 작게·원자적으로, 편집 직전 재읽기.
- **앱 포트 8080** — 두 세션 `bootRun` 충돌 → 트리별 `server.port` 분리.

### 일반화 포인트 (면접 답변용)

- **격리의 단위를 정확히 잡아라.** 충돌은 "공유되는 가변 상태"에서 온다. 멀티 세션 작업에서 그 상태는 *워킹 트리*다. 브랜치는 그 트리가 가리키는 포인터일 뿐이라, 트리를 공유하면 브랜치를 나눠도 소용없다.
- **낙관적 잠금(read-before-write)** 으로 lost update를 막는 건 DB 버전 컬럼·ETag·`If-Match` 와 같은 사상. 도구의 "modified since read" 차단이 그 구현체.
- **정책은 두 층에서 강제** — soft(CLAUDE.md: 작업 전 git 상태 확인 후 분리 *판단*) + hard(SessionStart 훅: 매 세션 git 상태 자동 표시·경고). [N-004](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard)의 soft/hard 역할 분담과 같은 구조다. 단 "다른 세션이 *진짜* 떠있는지"는 git만으론 단정 못 해, 훅은 정보·경고까지(판단은 모델 몫).

### 코드 위치

- `.claude/hooks/warn-multi-session.ps1` — SessionStart 훅(현재 브랜치/미커밋/워크트리 표시 + 경고)
- `.claude/settings.json` — `SessionStart` 훅 등록
- `CLAUDE.md` — "🪢 다중 세션 동시 작업 — 워크트리 분리" soft 규칙

### 관련 노트

- [N-004. Claude Code 훅으로 워크플로 강제 — 가이드(soft) vs 훅(hard)](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard) — 같은 soft/hard 두 층 강제 사상

---

## N-033. 분석용 클릭 추적은 GET 리다이렉트 — CSRF 면제와 오픈 리다이렉트 트레이드오프

**한 줄 요약**: 제휴 "구매" 링크처럼 *외부로 나가면서 클릭을 집계*하는 기능은, 우리 서버의 경유 엔드포인트(`GET /books/{id}/buy`)로 보내 카운트를 올린 뒤 302로 외부 링크에 리다이렉트한다. 링크 클릭은 폼이 아니라 `<a>` 내비게이션이라 CSRF 토큰을 붙이기 어렵고, Spring Security는 GET을 CSRF 검사에서 면제하므로 GET으로 둔다 — "GET은 상태를 바꾸지 않는다(safe/idempotent)"는 원칙을 분석 목적상 의도적으로 깨는 것. 대신 리다이렉트 대상을 **우리 DB에 저장된 값으로만** 제한해 오픈 리다이렉트를 막는다.

### 자세한 설명

**왜 직접 링크가 아니라 경유 엔드포인트인가.** 책장에서 `<a href="알라딘링크">` 로 바로 보내면 클릭이 우리 서버를 거치지 않아 *몇 번 눌렸는지* 알 수 없다. 수익(제휴 수수료)의 핵심 질문은 "어떤 책이 실제 구매 의향을 내는가"이고, 그 데이터는 클릭이 우리 서버를 한 번 거쳐야만 쌓인다. 그래서 `href` 를 `@{/books/{id}/buy}` 로 바꿔 **집계 → 리다이렉트** 2단계로 만든다. (광고/제휴 네트워크의 클릭 트래커가 다 이 구조다.)

**GET이 상태를 바꾸는 문제.** HTTP 규약상 GET은 *safe*(상태 불변)·*idempotent* 해야 한다. 그런데 이 엔드포인트는 GET이면서 카운트를 +1 한다 — 규약 위반이다. 그럼에도 GET을 쓰는 이유:
- 링크 클릭(`<a>`)은 GET만 낼 수 있고, **CSRF 토큰을 실을 자리가 없다**(POST 폼이라야 hidden token을 넣는다). Spring Security 기본은 GET/HEAD/OPTIONS/TRACE를 CSRF 검사에서 면제하므로, GET으로 두면 토큰 없이도 통과한다.
- 부작용이 "분석 카운터 증가"뿐이라 **악용해도 피해가 사용자 자신의 통계 노이즈**에 그친다(돈·권한 변동 없음). 트레이드오프가 받아들일 만하다.
- 봇 프리페치/크롤러가 GET을 미리 당겨 카운트를 부풀릴 수 있다는 게 대가 — 정밀 과금이 아니라 *경향 데이터*라 감수한다. 정확성이 필요해지면 그때 POST+토큰 비콘이나 봇 필터로 강화한다.

**오픈 리다이렉트 방어 = 신뢰할 수 있는 출처로만 리다이렉트.** "리다이렉트 대상 URL을 외부 입력에서 받는다"는 건 전형적 오픈 리다이렉트 취약점(피싱에 악용)이다. 여기선 리다이렉트 대상이 **클릭 시점의 요청 파라미터가 아니라, 등록 때 알라딘 검색 결과로 우리 DB에 저장된 `purchaseLink`** 뿐이다. 사용자가 클릭 순간에 임의 URL을 끼워 넣을 수 없다. 설령 자기 책에 임의 링크를 저장해도 **리다이렉트되는 건 자기 자신**이라 피싱이 성립하지 않는다. 즉 "대상을 우리가 통제하는 데이터로 한정"이 방어선이다.

**소유권·없음 처리.** 집계도 IDOR을 따른다 — `findByIdAndUser` 로 내 책일 때만 카운트(남의 책 클릭으로 통계 오염 방지). 구매링크가 없는 책(수동 등록)은 갈 곳이 없으니 카운트하지 않고 책장으로 되돌린다 — "없음"을 노출하지 않는 것도 IDOR 일관성.

### 일반화 포인트 (면접 답변용)

- **클릭 추적은 "경유 후 리다이렉트" 패턴.** 외부로 나가는 링크의 효과를 측정하려면 내 서버를 한 번 거치게 한다(트래커). 측정·수익 분석의 기본형.
- **GET vs POST는 CSRF·안전성과 묶여 있다.** 상태를 바꾸면 원칙은 POST(+CSRF 토큰)다. 링크라서 GET이 불가피하면, *부작용의 무게*를 따져 면제를 감수할지 정한다 — 카운터처럼 가벼우면 OK, 결제·삭제처럼 무거우면 절대 GET 금지.
- **오픈 리다이렉트의 본질은 "대상 출처".** 리다이렉트 URL을 사용자 입력에서 받으면 취약, 서버가 통제하는 데이터(화이트리스트/내 DB)에서만 받으면 안전. `?next=` 류를 검증 없이 따라가지 말 것.

### 코드 위치

- `src/main/java/com/booktimer/web/BookController.java` — `GET /books/{id}/buy` (집계 후 리다이렉트, 예외 시 책장)
- `src/main/java/com/booktimer/book/BookService.java` — `recordPurchaseClick` (소유권 + 링크 있을 때만 집계)
- `src/main/java/com/booktimer/book/Book.java` — `clickCount`, `recordPurchaseClick()`
- `src/main/resources/db/migration/V5__book_click_count.sql` — `click_count` 컬럼(default 0)

### 관련 노트

- [N-031. SameSite=Lax로 CSRF 사전 차단](#n-031-samesitelax로-csrf-사전-차단--그리고-세션-쿠키-속성은-프로퍼티가-아니라-명시-cookieserializer-빈으로) — CSRF를 다루는 자매 노트(여기선 GET 면제를 *이용*하는 쪽)
- [N-012. 인증 주체 ≠ 도메인 엔티티 — IDOR 방지 findByIdAndUser](#n-012-인증-주체--도메인-엔티티--principal로-도메인-user를-다시-잇고-접속을-lazy-누적-트리거로) — 같은 소유권 강제 패턴

---

## N-034. 부모 엔티티 삭제와 자식 FK — 연결 끊기(unlink) vs 함께 삭제(cascade), 그리고 같은 버그의 두 예외

**한 줄 요약**: 자식이 FK로 가리키는 부모를 지우려면 자식을 먼저 처리해야 한다(앱이 트랜잭션 안에서 unlink/삭제하거나, DB의 `ON DELETE`). 어느 쪽인지는 **데이터의 도메인 의미**로 정한다 — 기록을 남겨야 하면 연결만 끊고(set null), 부모에 종속된 데이터면 함께 삭제. 그리고 같은 "FK 미정리" 버그가 영속성 컨텍스트에 자식이 로드돼 있냐에 따라 ORM 예외(`TransientPropertyValueException`)와 DB 예외(`DataIntegrityViolationException`)의 두 얼굴로 나타난다.

### 자세한 설명

`reading_session.book`은 nullable이다("책 미지정 측정 허용"). 책을 삭제할 때 그 책을 가리키는 세션을 어떻게 할지 두 갈래:

- **함께 삭제(cascade)**: 세션도 지운다 → 그날 읽은 기록(잔디·누적 시간)이 사라진다. ✗ (책을 책장에서 뺐다고 읽은 사실이 없어지면 안 된다)
- **연결 끊기(unlink, set null)**: 세션은 남기고 `book_id`만 null로 → "책 미지정 측정"이 된다. ✓ 독서 기록·총 시간 보존.

판단 기준: **자식이 부모 없이도 의미가 있나.** 독서 세션은 책과 독립적으로 "그 시간에 읽었다"는 사실을 가지므로 unlink. (주문항목처럼 부모 없으면 무의미한 자식은 cascade.)

**정리를 어디서 하나 — 앱 vs DB**:

- **앱 레벨(채택)**: 삭제 유스케이스가 트랜잭션 안에서 자식을 먼저 처리한다 — `unlinkBook`(벌크 `UPDATE ... SET book_id=null`) → `delete(book)`. 같은 트랜잭션이라 commit 시 FK 만족. 테스트(H2)·운영(MySQL)이 동일하게 동작해 회귀 테스트로 잡힌다. `AccountService.purge`(세션→타이머→유저 순 삭제)와 같은 패턴.
- **DB 레벨**: FK에 `ON DELETE SET NULL`(또는 CASCADE). DB가 자동 처리하지만, 이 프로젝트의 메인 테스트는 Hibernate `ddl-auto`로 스키마를 만들고 Flyway는 꺼져 있어(테스트 설정) `ON DELETE`가 테스트 스키마에 반영되지 않는다 → 테스트와 운영이 갈린다. 그래서 앱 레벨을 택했다.

**같은 버그의 두 예외 (왜 테스트와 운영이 다른가)**:

- 부모를 `em.remove`하면, **영속성 컨텍스트에 로드된 자식**이 그 부모를 참조한 채 flush될 때 Hibernate가 "삭제 예정(=transient) 부모를 참조"로 보고 `TransientPropertyValueException`을 던진다(ORM 층, DB 가기 전).
- 자식이 컨텍스트에 **없으면** ORM은 모른 채 통과 → commit 시 **DB FK**가 막아 `DataIntegrityViolationException`(DB 층).
- 테스트는 한 트랜잭션에서 세션을 막 저장해 컨텍스트에 있으니 전자, 운영의 삭제 요청은 `book`만 로드하니 후자. "테스트와 운영의 예외 타입이 다르다"의 흔한 정체.

> 벌크 `@Modifying` 주의: JPQL 벌크 UPDATE는 영속성 컨텍스트를 우회한다 → 호출 전후 일관성을 위해 `flushAutomatically`(전: 보류된 insert를 flush)/`clearAutomatically`(후: 스테일 캐시 clear)로 보정한다.

### 일반화 포인트 (면접 답변용)

- **FK 제약은 "고아 자식"을 막는 안전장치**다. 부모 삭제 전 자식 정리(연결 끊기/함께 삭제)를 명시적으로 설계해야 하고, 그 선택은 데이터의 도메인 의미(기록 보존 vs 종속)로 결정한다.
- **같은 무결성 위반이라도 누가 먼저 잡느냐로 예외가 갈린다** — ORM(영속성 컨텍스트에 자식이 있으면)이면 `TransientPropertyValueException`, DB면 `DataIntegrityViolationException`. "왜 테스트와 운영의 예외가 다르지?"의 답.
- **컨트롤러의 예외 catch는 실제 던져지는 타입을 포함해야** 한다 — 좁은 `IllegalArgumentException`만 잡으면 `DataIntegrityViolationException`이 500으로 샌다(N-028·N-019와 같은 결: 프레임워크/DB가 던지는 예외가 좁은 처리를 빠져나간다).

### 코드 위치

- `src/main/java/com/booktimer/book/BookService.java` — `delete`(unlink 후 삭제)
- `src/main/java/com/booktimer/session/ReadingSessionRepository.java` — `unlinkBook`(벌크 UPDATE, flush/clear 자동)
- 대비: `src/main/java/com/booktimer/user/AccountService.java` — `purge`(FK 순서 삭제)
- 관련: `troubleshooting.md` T-023

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선이지, 사용자 검증의 첫 방어선이 아니다](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — DB 제약을 앱이 어떻게 다루나
- [N-028. catch-all 예외 핸들러는 프레임워크의 상태보유 예외(404 등)까지 삼킨다 — 상태코드 보존](#n-028-catch-all-예외-핸들러는-프레임워크의-상태보유-예외404-등까지-삼킨다--상태코드-보존) — 좁은/넓은 catch와 예외 누수

---

## 🔄 누적 갱신

| 일자 | 추가 항목 |
|---|---|
| 2026-05-30 | 초안 + N-001 (누적 카운터 일일 리셋: Lazy 계산) |
| 2026-05-31 | N-002 (Gradle toolchain + foojay), N-003 (Spring Boot 4 starter 네이밍) |
| 2026-05-31 | N-004 (Claude Code 훅 워크플로 강제), N-006 (PowerShell 5.1 native stderr 함정) |
| 2026-05-31 | N-007 (Boot 4 autoconfigure/test-slice 모듈화 — 패키지 이동) |
| 2026-06-01 | N-008 (JPA Auditing — 리스너/스위치 분리, @DataJpaTest 슬라이스 함정) |
| 2026-06-01 | N-009 (계층별 테스트 전략 — 도메인 단위/슬라이스/서비스 mock, 테스트 피라미드) |
| 2026-06-01 | N-010 (테스트 가능한 시간 — Clock 주입, 절대 시점 vs 유저 TZ 오늘) |
| 2026-06-01 | N-011 (Spring Security 폼 로그인 — UserDetailsService + PasswordEncoder 자동 조립, CSRF 판단) |
| 2026-06-01 | N-012 (인증 주체 ≠ 도메인 엔티티 — principal→findByEmail 재조회, 접속을 Lazy 누적 트리거로) |
| 2026-06-01 | N-013 (Spring Boot 컨테이너화 — 멀티스테이지 Dockerfile, plain jar 비활성, prod 설정 외부화, health 공개) |
| 2026-06-01 | N-014 (AWS CLI 로컬 실행·클라우드 작용, 콘솔/CLI/CloudShell, bash vs PowerShell 셸 함정) |
| 2026-06-01 | N-015 (GitHub Actions→AWS 키리스 배포 — OIDC 페더레이션 + ECS 롤링 배포, PassRole) |
| 2026-06-01 | N-016 (ECS 헬스체크와 콜드스타트 — ALB 타깃 헬스 vs 컨테이너, grace period 함정) |
| 2026-06-01 | N-017 (SSR→SPA 전환 시점 — 백엔드 %가 아니라 API 계약 안정성 + 인터랙션 요구, htmx/Alpine 다리) |
| 2026-06-01 | N-018 (퍼블릭 IP ≠ 인터넷 접근 — 서브넷 라우트테이블이 진짜 관문, Fargate egress, 비대칭=비결정적 실패) |
| 2026-06-01 | N-019 (DB 유니크 제약은 무결성의 마지막 방어선 — 앱 사전확인(UX)+DB 제약(무결성)+레이스 catch 3중, validation vs constraint) |
| 2026-06-02 | N-020 (CI 트리거 필터 — paths-ignore는 "전부 매칭될 때만" 스킵, 거부 목록=안전 기본값(기본 실행), paths vs paths-ignore) |
| 2026-06-02 | N-021 (HTTPS는 앞단에서 TLS termination — ALB/ACM, 공개 구간만 암호화, 내부 HTTP 허용, X-Forwarded-Proto + forward-headers) |
| 2026-06-02 | N-022 (프록시 뒤 앱은 X-Forwarded-* 신뢰 — forward-headers, Boot 4에선 ForwardedHeaderFilter 명시 빈, RANDOM_PORT로만 검증) |
| 2026-06-02 | N-023 (ddl-auto=update 한계 — 기존 컬럼 제약 변경 안 함→스키마 드리프트, 근본은 Flyway 마이그레이션+기존 DB baseline) |
| 2026-06-02 | N-024 (Boot 4 autoconfig 모듈 분리 — flyway-core만으론 빈 미생성→spring-boot-flyway / 기존 DB에 Flyway 도입은 baseline-on-migrate, V1=현재 스키마) |
| 2026-06-02 | N-025 (로그인 지연 ≠ DB — 인덱스 단건 조회+BCrypt(CPU 집약), 작은 vCPU에서 증폭 / 해법은 강도↓ 아니라 CPU↑, 분리 측정으로 진단) |
| 2026-06-02 | N-026 (OAuth find-or-create는 email_verified일 때만 안전(자동 연결 탈취 방어) / Spring Security는 brute-force 미방어 — 직접 IP 잠금, 이벤트+필터) |
| 2026-06-02 | N-027 (OAuth 동의 화면 UI는 provider 제공 / 개인정보처리방침은 앱 제작자 책임 — non-sensitive 스코프는 검증 없이 즉시 게시, 게시 ≠ 검증) |
| 2026-06-02 | N-028 (catch-all @ExceptionHandler(Exception)이 프레임워크의 상태보유 예외(404 등)까지 삼켜 500으로 둔갑 → 좁은 타입 핸들러로 상태코드 보존, 로그 레벨 분리) |
| 2026-06-02 | N-029 (인메모리 HttpSession은 인스턴스 교체 시 소멸→재로그인 / 세션 외부화(JDBC·Redis)로 무상태 앱 서버, 무중단·수평확장의 전제, 세션 vs 토큰, 재로그인≠데이터손실) |
| 2026-06-02 | N-030 (무중단 롤링 배포 — min=100/max=200으로 "헬스 통과 후 교체"(start-then-stop), circuit breaker 자동 롤백, deregistration delay는 속도일 뿐 다운타임 원인 아님, 세션 외부화가 전제, 적용은 인프라 설정) |
| 2026-06-02 | N-031 (SameSite=Lax로 CSRF 사전 차단(Lax는 OAuth 콜백 호환, Strict는 깸) / 세션 외부화 후 세션 쿠키는 DefaultCookieSerializer가 써서 server.servlet.session.cookie.* 무동작→명시 CookieSerializer 빈, N-022 자매 함정, 보안 속성은 Set-Cookie 직접 확인) |
| 2026-06-03 | N-032 (다중 세션 동시 작업은 git worktree로 워킹 트리 분리 — 브랜치만으론 부족(checkout이 폴더 전체 전환), 미커밋이면 사후 분리 가능, "modified since read"=낙관적 잠금, Flyway 번호·공유문서·포트는 여전히 조율 / SessionStart 훅+CLAUDE.md soft 두 층) |
| 2026-06-03 | N-033 (분석용 클릭 추적은 경유 엔드포인트 GET 리다이렉트 — 링크 클릭은 CSRF 토큰 못 실음→GET 면제 이용, "GET은 safe" 원칙을 가벼운 부작용에 한해 의도적 위반, 오픈 리다이렉트는 대상을 내 DB 값으로 한정해 방어, IDOR 일관) |
| 2026-06-03 | N-034 (부모 삭제와 자식 FK — unlink(set null, 기록 보존) vs cascade(종속 삭제)는 도메인 의미로 결정 / 정리는 앱(트랜잭션 내, 테스트=운영) vs DB(ON DELETE, ddl-auto 테스트엔 미반영) / 같은 FK 미정리가 영속성 컨텍스트 유무로 TransientPropertyValueException(ORM) vs DataIntegrityViolationException(DB) 두 얼굴, T-023) |
