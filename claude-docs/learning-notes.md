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
- [N-035. 제휴(어필리에이트) 수익 모델 — 귀속 신원·정산 분리, 왜 알라딘만 / 다나와는 안 되나](#n-035-제휴어필리에이트-수익-모델--귀속-신원정산-분리-왜-알라딘만--다나와는-안-되나)
- [N-036. Safe Browsing은 서버가 아니라 도메인 평판·휴리스틱으로 차단 — TLD 평판이 신규 사이트 오탐을 키운다](#n-036-safe-browsing은-서버가-아니라-도메인-평판휴리스틱으로-차단--tld-평판이-신규-사이트-오탐을-키운다)
- [N-037. SNS로 확장해도 도메인 데이터는 새로 저장하지 않는다 — 새로 필요한 건 '관계 + 공개범위', 기존 데이터는 조회 주체만 바뀐다](#n-037-sns로-확장해도-도메인-데이터는-새로-저장하지-않는다--새로-필요한-건-관계--공개범위-기존-데이터는-조회-주체만-바뀐다)
- [N-038. 온보딩 게이트는 단일 진입점에 두면 인터셉터가 필요 없다 + 시드값 vs 사용자 초기값 분리 + NOT NULL 컬럼은 신규=기본·기존=백필](#n-038-온보딩-게이트는-단일-진입점에-두면-인터셉터가-필요-없다--시드값-vs-사용자-초기값-분리--not-null-컬럼은-신규기본기존백필)
- [N-039. 제약을 뒤늦게 강화하려면 기존 위반 데이터부터 백필한다 (backfill)](#n-039-제약을-뒤늦게-강화하려면-기존-위반-데이터부터-백필한다-backfill)
- [N-040. mock 단위테스트는 DB 제약(FK·유니크)을 검증하지 못한다](#n-040-mock-단위테스트는-db-제약fk유니크을-검증하지-못한다)
- [N-041. 외부 검색 API의 "필드 한정" 옵션은 문서대로 동작하지 않을 수 있다 — 결과를 신뢰 말고 후필터로 불변식을 강제](#n-041-외부-검색-api의-필드-한정-옵션은-문서대로-동작하지-않을-수-있다--결과를-신뢰-말고-후필터로-불변식을-강제)
- [N-042. flex-basis는 주축(main axis) 크기다 — 컨테이너 방향(row↔column)을 바꾸면 같은 `flex` 단축속성이 가로↔세로로 뒤바뀐다](#n-042-flex-basis는-주축main-axis-크기다--컨테이너-방향rowcolumn을-바꾸면-같은-flex-단축속성이-가로세로로-뒤바뀐다)
- [N-043. Rate Limiting — 요청 속도 제한으로 남용·과부하·비용을 막는다 (토큰 버킷, 429)](#n-043-rate-limiting--요청-속도-제한으로-남용과부하비용을-막는다-토큰-버킷-429)
- [N-044. CSRF 숨김필드는 세션을 lazy 생성한다 — 큰 페이지·하단 폼이면 응답 버퍼 커밋 후라 실패](#n-044-csrf-숨김필드는-세션을-lazy-생성한다--큰-페이지하단-폼이면-응답-버퍼-커밋-후라-실패)
- [N-045. Spring Data에서 "최신 N건"은 Pageable로 limit — 파생 메서드 이름으로 못 쓰는 정렬+개수 제한을 @Query에 얹는다](#n-045-spring-data에서-최신-n건은-pageable로-limit--파생-메서드-이름으로-못-쓰는-정렬개수-제한을-query에-얹는다)
- [N-046. 식별자 3분할 — 로그인/공개핸들/표시이름은 각자 다른 축이고, 공개 핸들을 뭘로 두느냐는 보안 동치다](#n-046-식별자-3분할--로그인공개핸들표시이름은-각자-다른-축이고-공개-핸들을-뭘로-두느냐는-보안-동치다)
- [N-047. 불변 식별자는 대리키(surrogate PK) 위에서 도메인 규칙으로 강제한다 — DB가 막아주지 않는다](#n-047-불변-식별자는-대리키surrogate-pk-위에서-도메인-규칙으로-강제한다--db가-막아주지-않는다)
- [N-048. 유니크 사전확인은 정규화한 값으로, 그리고 엔티티를 바꾸기 전에 한다 — JPA auto-flush가 미영속 자기자신을 오탐한다](#n-048-유니크-사전확인은-정규화한-값으로-그리고-엔티티를-바꾸기-전에-한다--jpa-auto-flush가-미영속-자기자신을-오탐한다)
- [N-049. 운영 통계는 새 저장 없는 읽기 집계 — Flyway 무변경, 시간창 집계는 Clock 주입으로 결정화](#n-049-운영-통계는-새-저장-없는-읽기-집계--flyway-무변경-시간창-집계는-clock-주입으로-결정화)
- [N-050. 운영 화면 PII 최소노출은 층이다 — 안 싣기가 가리기보다 우선, 마스킹은 표시일 뿐 비노출이 아니다](#n-050-운영-화면-pii-최소노출은-층이다--안-싣기가-가리기보다-우선-마스킹은-표시일-뿐-비노출이-아니다)
- [N-051. 상태 의존 불변식은 단순 NOT NULL이 아니라 조건부 CHECK로 — 생성 순서(지연 채움)와 충돌 없이 무결성을 박는다](#n-051-상태-의존-불변식은-단순-not-null이-아니라-조건부-check로--생성-순서지연-채움와-충돌-없이-무결성을-박는다)
- [N-052. 계정 열거(account enumeration) — 존재 여부를 응답으로 흘리지 않기, 그리고 가입이 까다로운 이유](#n-052-계정-열거account-enumeration--존재-여부를-응답으로-흘리지-않기-그리고-가입이-까다로운-이유)
- [N-053. OAuth 자동 계정 연결(find-or-create)의 양방향 위협 — verified email은 한 방향만 막고, 미검증 로컬 가입이 pre-hijacking을 연다](#n-053-oauth-자동-계정-연결find-or-create의-양방향-위협--verified-email은-한-방향만-막고-미검증-로컬-가입이-pre-hijacking을-연다)
- [N-054. 외부 API가 채워주는 식별자라도 집계 키로 쓰려면 적재 시점 정규화가 필요하다 — 빈 값·표기 차이가 group-by를 오염시킨다](#n-054-외부-api가-채워주는-식별자라도-집계-키로-쓰려면-적재-시점-정규화가-필요하다--빈-값표기-차이가-group-by를-오염시킨다)

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

**4. 실제 판단(BookTimer, 2026-06): 아직 SSR 유지 — 트리거 0개, 게다가 SEO가 수익과 직결**
- 책 기능(검색·제휴·상세·잔디)을 한참 추가하는 중이라 **① API 계약이 아직 안 굳었고**, 라이브 타이머는 이미 **htmx 프래그먼트로 ② 인터랙션 요구가 해소**됐다. 모바일 앱·포트폴리오 동기도 없음 → **전환 트리거 0개**.
- **결정적 추가 축 — SEO = 제휴 수익.** 목표가 제휴 수익이면 책 페이지가 검색에 잡혀 유입→구매 클릭이 나야 한다. **SSR은 SEO에 유리, 순수 CSR SPA(React 기본형)는 불리**. 즉 수익 방향과 SSR이 같은 편이다. 비용($66/월)도 별도 프론트 빌드·배포를 더하면 늘어 — 줄여야 할 방향과 반대.
- **그래서 옮긴다면 순수 CSR이 아니라 SSR-가능 프레임워크(Next.js 등)로** — SEO·초기로딩을 지키면서 인터랙션만 얻는다. "React로 간다 = SEO 포기"가 아니다(렌더 위치 선택의 문제).

### 일반화 포인트 (면접 답변용)

- **아키텍처 전환의 타이밍은 "완성도 %"가 아니라 "계약 안정성 + 비용이 정당화되는 요구"로 잡는다.** 비싼 마이그레이션은 되돌리기 어려운 부분(여기선 API 계약·인증 모델)이 굳은 뒤 한 번에.
- **SSR vs SPA는 "어디서 HTML을 만드나"의 선택**: SSR은 초기 로딩·SEO·단순함, SPA는 풍부한 인터랙션·부분 갱신. 둘 사이엔 htmx/Alpine 같은 중간 지대가 있어 전면 전환 없이 통증만 덜 수 있다(점진적 마이그레이션).
- **전환 비용의 핵심은 보통 "경계의 재계약"**: 뷰 템플릿 교체가 아니라 백엔드↔프론트 사이 계약(HTML→JSON)과 인증 매체(쿠키 세션→토큰)가 바뀌는 것. 그래서 도메인이 흔들릴 때 옮기면 계약을 반복해서 다시 쓴다.
- **조기 최적화 회피와 같은 결**: 필요(인터랙션 요구)가 분명해지기 전에 SPA로 가면, 안 굳은 API를 프론트가 따라다니며 재작업한다.
- **렌더 위치 결정엔 "비기능 요구"도 들어간다 — 특히 SEO.** 제품의 수익 모델이 검색 유입에 기댄다면(예: 제휴 수익) SSR이 곧 매출 채널이라, "더 멋진 프론트"라는 기능 욕구만으로 순수 CSR로 가면 매출 축을 깎는다. 옮기더라도 SSR-가능 프레임워크(Next.js 등)를 골라 SEO를 지키는 게 정답 — "SPA로 간다 = SEO 포기"는 거짓 이분법.

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
- `deploy/task-definition.json` — `cpu: 512`(0.5 vCPU)·`memory: 1024` ← 지연 원인이던 0.25 vCPU에서 상향(PR #132). 강도(10)는 유지
- 관련: plan.md "Fargate CPU 상향" 항목(완료 ✅)

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

**후속 실전 사고 — 같은 함정 두 번째, 그리고 세 가지 교정.** 규칙을 문서화한 직후 또 당했다: 한 세션이 공유 폴더에서 `feat/affiliate` 작업·커밋하는 동안, 다른 세션이 같은 폴더에서 별개 기능(책 삭제 FK unlink)을 편집했고, 그게 첫 세션의 `git add -A` 에 빨려 들어가 **두 기능이 한 커밋에 줄 단위로 엉켰다**. 정리하며 얻은 세 교정:

1. **SessionStart 훅은 1회성이라 못 막는다.** 훅은 세션 *시작 시* 한 번 git 상태를 보여줄 뿐, 세션 *도중* 일어나는 동시 편집은 감시하지 않는다. 두 세션이 각자 깨끗한 main에서 시작하면 둘 다 "이상 없음"을 받고 서로의 존재를 모른다. git엔 "다른 세션이 이 폴더 쓰는 중"이라는 락이 없다 → 훅은 **hard가 아니라 soft 보조**, 실제 감지·분리는 매 편집 전 *상태 확인 + 판단*의 몫.
2. **공유 트리에선 `git add -A` 금지.** `-A`/`.` 는 "디스크의 모든 변경"을 쓸어담아 옆 세션의 미커밋 작업까지 흡수한다. **변경 파일 경로를 지정해 add** 하라(N-032의 "내 것만 재적용" 원칙의 실천). 같은 파일에 두 세션 변경이 섞이면 경로 add로도 안 갈라지니, 그 전에 worktree로 분리하는 게 근본 해법.
3. **"코드 수정 전 무조건"은 pull이 아니라 _git 상태 확인_.** 흔한 오해가 "병렬 세션이면 수정 전 무조건 `git pull`"인데, pull(=fetch+merge)이 막는 건 *stale base*(옛 main 위 작업)일 뿐, *같은 폴더 동시 편집*은 못 막는다(옆 세션의 미커밋은 원격에 없으니 fetch에 안 잡힌다). 그래서 무조건 해야 하는 건 **상태 확인**(branch/status/worktree, 싸고 빠름)이고, pull은 **새 브랜치 딸 때 한 번**(최신 main 기준 시작)이면 충분하다 — feature 도중 매 수정마다 pull은 불필요. 동시 편집 방어는 pull이 아니라 **worktree 분리**다.

### 일반화 포인트 (면접 답변용)

- **격리의 단위를 정확히 잡아라.** 충돌은 "공유되는 가변 상태"에서 온다. 멀티 세션 작업에서 그 상태는 *워킹 트리*다. 브랜치는 그 트리가 가리키는 포인터일 뿐이라, 트리를 공유하면 브랜치를 나눠도 소용없다.
- **낙관적 잠금(read-before-write)** 으로 lost update를 막는 건 DB 버전 컬럼·ETag·`If-Match` 와 같은 사상. 도구의 "modified since read" 차단이 그 구현체.
- **정책은 두 층에서 강제** — soft(CLAUDE.md: 작업 전 git 상태 확인 후 분리 *판단*) + hard(SessionStart 훅: 매 세션 git 상태 자동 표시·경고). [N-004](#n-004-claude-code-훅으로-워크플로-강제--가이드soft-vs-훅hard)의 soft/hard 역할 분담과 같은 구조다. 단 "다른 세션이 *진짜* 떠있는지"는 git만으론 단정 못 해, 훅은 정보·경고까지(판단은 모델 몫).
- **방어 도구를 위협 모델에 맞춰라.** `git pull`은 *stale base*용, `worktree`는 *동시 편집*용, *경로 지정 add*는 *남의 미커밋 흡수*용 — 셋은 서로 다른 위협을 막는다. "무조건 pull"처럼 한 도구를 만능으로 착각하면, 그 도구가 안 막는 위협(동시 편집)에 그대로 노출된다. 막으려는 게 뭔지 먼저 정하고 도구를 고른다.
- **재발이 곧 강제 수준 신호.** 규칙을 *문서화*(soft)한 직후 같은 함정에 또 빠졌다면, 그 규칙은 soft로 부족하다는 신호일 수 있다 — 단, 훅으로 hard화할 수 있는지는 "기계가 판정 가능한가"에 달렸다(동시 세션 존재는 git이 단정 못 해 hard화가 어렵다 → 습관·체크리스트로 메운다).

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

## N-035. 제휴(어필리에이트) 수익 모델 — 귀속 신원·정산 분리, 왜 알라딘만 / 다나와는 안 되나

**한 줄 요약**: 제휴 링크는 **내 계좌가 아니라 내 식별키(알라딘 TTBKey)** 를 싣고 다닌다. 제휴사는 그 키로 매출을 "내 공로"로 **쿠키 귀속**시켜 적립하고, 입금받을 **계좌는 내가 제휴 관리자에 따로 등록**해야 지급된다(클릭이 계좌를 알려주는 게 아니다). 그리고 *코드로 검색·구매를 붙이는 제품*엔 **알라딘이 거의 유일하게 맞다** — 교보·YES24는 개인용 상품 API·직접 제휴가 없어 데이터·딥링크를 못 얻기 때문. "다나와식 가격비교"는 그 데이터 벽 + **도서정가제(새 책 할인 캡)** 로 새 책 비교가치가 사라져 책엔 잘 안 맞는다.

### 자세한 설명

**① 돈이 들어오는 메커니즘.** 흐름은 `클릭(링크에 ttbkey 포함) → 제휴사가 쿠키로 내 키에 귀속 → (유효기간 내) 구매 → 내 제휴 계정에 수수료 적립 → 내가 등록한 정산 계좌로 지급`. 핵심은 **신원(키)과 정산(계좌)의 분리**: 제휴사는 클릭으로 "누구 공로"(키)만 알고, "어디로 입금"(계좌)은 내가 관리자에서 별도 등록·세금정보(개인=주민번호 원천징수 3.3% / 사업자=사업자번호) 제출해야 안다. 또 지급이 **현금이 아니라 적립금**이거나 **최소 정산액**이 걸린 경우가 많아, 약관 확인이 필수.

**② 우리 측 지표 ≠ 실제 매출.** 우리가 집계하는 `clickCount`(N-033)는 **분석용 프록시**일 뿐, 실제 수수료는 **제휴사 리포트의 귀속 구매 건수**다. 클릭해도 안 사거나·쿠키 만료·봇 클릭이면 우리 카운트는 올라도 귀속은 0. "몇 번 눌렸나"(우리)와 "몇 건 귀속됐나"(제휴사)는 다른 숫자이고, 돈은 후자.

**③ 왜 알라딘만 코드 제품에 맞나.** 제휴 수익을 *프로그램으로* 내려면 두 가지가 필요하다 — (a) 상품 데이터(검색·메타·재고·가격), (b) ISBN별 제휴 딥링크. **알라딘 OpenAPI는 검색 결과에 ttbkey 박힌 구매링크까지 한 묶음으로** 준다. 반면 **교보·YES24는 개인용 상품 API가 없고**(제휴도 B2B 제안 또는 애드픽·텐핑 같은 **네트워크 경유**만), 그 네트워크는 배너·링크 도구 위주라 *책 검색 API*를 안 준다. 그래서 "검색→표시→구매" UX를 코드로 짜는 우리에겐 알라딘이 사실상 유일. (현금화 다변화는 **쿠팡 파트너스** 직접 제휴 병행이 현실적.)

**④ "다나와식 가격비교"가 책엔 왜 안 맞나.** 가격비교 사이트는 *각 판매처의 실시간 가격·재고 데이터*를 B2B 피드·대규모 크롤링으로 모은다. 우리는 ③의 데이터 벽 때문에 교보·YES24의 가격·재고를 못 가져온다 → 나란히 비교표를 못 만든다. 게다가 **도서정가제**로 **새 책은 할인이 최대 15%(가격 10%+적립 5%)로 묶여 어디서 사든 값이 거의 같다** → "최저가 비교"의 가치 자체가 새 책에선 소멸. 진짜 가격차·비교가치는 **중고책**(알라딘 중고마켓 등, 정가제 밖)에서 나온다. 그래서 현실 버전은 "최저가 비교"가 아니라 **구매처 선택권 제공(검색 이동 링크) + 중고 비교**.

### 일반화 포인트 (면접 답변용)

- **제휴의 본질은 "귀속(attribution)"** — 누가 그 매출을 만들었나를 키+쿠키로 추적하는 시스템. 결제정보(계좌)는 그와 분리된 정산 채널에서 별도로 다룬다. "링크가 내 계좌를 안다"는 오해는 신원·정산을 합쳐 본 것.
- **외부 수익 통합의 제약은 "API가 열려 있나"가 가른다.** 같은 도메인(도서)이라도, 개발자 친화 API(알라딘·쿠팡 파트너스)가 있는 곳만 코드로 통합 가능. API 없는 곳은 B2B 계약·중개 네트워크를 거쳐야 해 비용이 급증.
- **남의 성공 모델을 베끼기 전에 "전제 조건"을 확인하라.** 다나와는 *데이터 피드 확보 + 가격이 실제로 다름*이 전제. 책은 둘 다(데이터 벽 + 정가제) 깨져서 그 모델이 안 선다. 모델보다 *그 모델이 서는 조건*이 옮겨지는지를 본다.
- **규제(도서정가제)가 제품 차별화 축을 바꾼다.** 가격 경쟁이 법으로 막히면 "최저가"는 차별점이 못 된다 → 차별화를 다른 축(구매처 편의·중고·큐레이션)으로 옮긴다.

### 코드 위치

- `src/main/java/com/booktimer/book/AladinBookSearchClient.java` — 검색 결과에 제휴 구매링크(ttbkey 포함) 동봉
- `src/main/java/com/booktimer/web/BookController.java` — `GET /books/{id}/buy`(클릭 집계 후 제휴 링크로 리다이렉트, N-033)

### 관련 노트

- [N-033. 분석용 클릭 추적은 GET 리다이렉트 — CSRF 면제와 오픈 리다이렉트 트레이드오프](#n-033-분석용-클릭-추적은-get-리다이렉트--csrf-면제와-오픈-리다이렉트-트레이드오프) — 이 수익의 "클릭"을 우리가 집계하는 그 엔드포인트

---

## N-036. Safe Browsing은 서버가 아니라 도메인 평판·휴리스틱으로 차단 — TLD 평판이 신규 사이트 오탐을 키운다

> **한 줄 요약**: Chrome의 "위험한 사이트" 차단은 내 서버 응답이 아니라 **Google Safe Browsing**이 내린 도메인 판정이다. 신규 도메인 + 평판 낮은 TLD(`.click` 등) + 로그인/자격증명 수집이 겹치면 정상 사이트도 **피싱으로 오탐**된다. 해제는 코드 수정이 아니라 **Search Console 검토 요청**이고, 근본 예방은 평판 좋은 TLD다.

### 무엇이 일어났나

OAuth 동의 화면을 게시한 직후, 구글 로그인 콜백(`booktimer.click/login/oauth2/code/google?...`)에서 Chrome이 빨간 전체화면 "위험한 사이트"를 띄웠다. 서버는 200으로 정상 응답 중이었다 — 차단은 **브라우저가** Safe Browsing 목록을 보고 막은 것.

### 왜 — Safe Browsing의 작동 축

- Safe Browsing은 **URL/도메인을 평판·머신러닝 휴리스틱으로 평가**한다. "내 서버가 뭘 응답하느냐"가 아니라 "이 도메인이 위험해 보이느냐"가 기준. 그래서 **내 코드를 고쳐도 직접 풀리지 않는다.**
- 오탐을 키우는 신호의 결합:
  - **TLD 평판** — `.click`·`.zip`·`.top` 등 저가·피싱 악용이 잦은 TLD는 기본 의심도가 높다. **TLD 자체가 위협 신호**로 쓰인다.
  - **도메인 신규성** — 평판 이력이 없는 갓 등록 도메인은 보수적으로 의심.
  - **자격증명 수집 + 사칭 외형** — 로그인 폼·비밀번호 입력에 더해 URL에 `accounts.google.com`이 들어가니 "구글 로그인 사칭"으로 보였다.
- 즉 동의 화면 게시는 **원인이 아니라 타이밍 우연**. 게시로 트래픽/크롤이 늘며 평가가 앞당겨졌을 뿐.

### 어떻게 푸나

1. **Google Search Console** 속성 등록(Route 53 본인 존이라 DNS TXT 인증이 쉬움) → **보안 문제** 리포트에서 분류 사유 확인.
2. 사이트가 깨끗함을 확인하고 **검토 요청** → 보통 며칠 내 해제.
3. 현재 분류는 `transparencyreport.google.com/safe-browsing/search?url=<도메인>`에서 직접 조회.
4. **근본 예방**: 평판 좋은 TLD(`.com`, `.app` — `.app`은 HSTS preload로 HTTPS 강제라 평판이 좋다)로 이전. 단 도메인 재구매·인증서·DNS·**OAuth 리디렉션 URI 재등록**이 따라오는 큰 작업이라, 1회성이면 검토 요청으로 풀고 재발하면 이전을 결정.

### Q&A 대비

- *"코드를 고치면 되나?"* → 아니다. 서버 응답과 무관한 도메인 평판 판정이다. 검토 요청이 정공법.
- *"왜 하필 게시 후에?"* → 게시가 원인이 아니라 우연. 신규 `.click` 도메인 + 로그인이 본질적 유발 요인.
- *"왜 `.app`이 더 안전한가?"* → `.app`은 레지스트리 차원에서 HTTPS를 강제(HSTS preload)해 중간자·사칭 여지가 작아 평판이 높다.

### 관련 노트

- **N-021** (HTTPS·TLS 종료, ALB) — 도메인·인증서·TLS 종료의 인프라 맥락(같은 `booktimer.click` 도메인).
- 함정 기록: [troubleshooting.md T-027](troubleshooting.md#t-027-구글-로그인-중-chrome-위험한-사이트-차단--safe-browsing이-신규-click-도메인-오탐).

---

## N-037. SNS로 확장해도 도메인 데이터는 새로 저장하지 않는다 — 새로 필요한 건 '관계 + 공개범위', 기존 데이터는 조회 주체만 바뀐다

> **한 줄 요약**: "남이 내 독서 기록을 보려면 DB에 저장해야 하나?"의 답은 **이미 저장돼 있다**. 정규화된 1:N 스키마에선 "누가 무엇을 얼마나"는 이미 자식 테이블(`book`/`reading_session`)에 `user_id`로 들어 있고, 남의 걸 보여주는 건 **데이터 추가가 아니라 조회 주체(`where user_id`)를 바꾸는 것**이다. SNS가 **새로** 저장해야 하는 건 도메인 데이터가 아니라 ① **관계**(팔로우/친구)와 ② **공개 범위**(누구에게 보일지)뿐이며, 그 순간 **IDOR/공개범위 체크가 보안 경계**가 된다.

### 배경 — 흔한 오해

기능을 SNS로 확장한다고 하면 "사람들이 서로 무슨 책을 읽는지 보려면 그 데이터를 어딘가에 저장해야 하지 않나?"라는 생각이 자연스럽게 든다. 하지만 이건 **저장(write)과 조회(read)를 혼동**한 것이다.

- **이미 저장돼 있다**: 정규화된 RDBMS에서 "사용자가 가진 책"은 `book`(소유자 `user_id`, `status`로 읽고싶음/읽는중/완독), "얼마나 읽었나"는 `reading_session`(`book_id` + `duration_seconds`)에 들어 있다. 사용자 한 명이 책 500권·측정 1만 번을 해도 `users` row는 한 줄 그대로고(N의 책임은 자식 테이블이 진다), 늘어나는 건 자식 테이블의 **행 수**다.
- **공유 = 조회 주체 변경**: 지금은 화면이 "내 것"만 본다(`where user_id = 나`). 남의 잔디/책별 시간을 보여주는 건 같은 쿼리에서 **주체를 `= 그 사람`으로 바꾸는 것**일 뿐, 새 컬럼·새 테이블이 필요하지 않다.

### 그래서 SNS가 *진짜로* 새로 저장해야 하는 것

도메인 데이터(독서 기록)는 그대로 두고, **지금 스키마에 없는 두 가지**만 새 Flyway 버전으로 더한다:

| 새로 저장할 것 | 왜 필요한가 | 형태 예 |
|---|---|---|
| **관계** — 누가 누구를 팔로우/친구 | "내 피드에 누구 걸 띄울지" 결정하려면 관계가 있어야 한다 | `follow(follower_id, followee_id)` 새 테이블 |
| **공개 범위** — 기록을 누구에게 보일지 | 모두가 모두 걸 볼 순 없다. 비공개/팔로워만/전체 | `book`·세션·프로필에 `visibility` 컬럼 또는 설정 테이블 |

→ 결정 트리: **"화면에 보여주기만" 하는 기능이면 DB 안 건드림(읽기)**, **"새로 저장할 게 생기는" 기능이면 Flyway 새 버전.** SNS는 후자지만, 그 대상이 "독서 기록"이 아니라 "관계 + 공개범위"라는 게 핵심.

### 보안 경계 — 여기서부터 IDOR

`where user_id`만 갈아끼우는 순간, **남의 `user_id`를 넣으면 비공개 기록까지 다 보이는 사고(IDOR)**가 가장 흔한 함정이 된다. 그래서:

- 조회 시 **공개 범위를 반드시 확인**한다(요청자가 그 데이터를 볼 권한이 있나 — 전체공개? 팔로워? 본인?).
- 노출 항목은 **화이트리스트**로 — "이 필드들만 남에게 보인다"를 명시(이메일·내부 id 등 누출 방지).
- 이 판단은 **코드보다 먼저 설계**에서 못 박아야 한다. 공개 범위·관계 모델은 한번 코드/스키마에 굳으면 되돌리기 어렵다(기존 데이터의 기본 공개값 마이그레이션까지 얽힘).

### Q&A 대비

- *"남이 보려면 그 데이터를 새로 저장해야 하나?"* → 아니다. 독서 데이터는 이미 `book`/`reading_session`에 있다. 보여주는 건 조회 주체(`where user_id`)를 바꾸는 것.
- *"그럼 SNS는 DB를 아예 안 건드리나?"* → 건드린다. 단 추가하는 건 독서 기록이 아니라 **관계 테이블 + 공개범위 컬럼**.
- *"`users` 테이블이 비대해지지 않나?"* → 안 된다. 1:N은 부모 row가 아니라 자식 테이블+FK가 떠안는다(정규화). 늘어나는 건 자식 테이블의 행 수뿐.
- *"공유에서 제일 위험한 건?"* → IDOR. 주체만 바꿔치기하면 비공개가 새는 구조라, 조회 시 공개범위 확인이 보안 경계.

### 관련 노트

- **N-019** (DB 제약 = 무결성의 마지막 방어선) — 권한 확인은 앱 계층(공개범위 체크)이 첫 방어선이라는 같은 결.
- **N-026** (OAuth find-or-create, 권한 가정의 함정) — "신뢰 경계를 코드가 지킨다"는 같은 사고.
- **N-017** (SSR→SPA 전환) — 공개 프로필 페이지의 렌더 위치·SEO 맥락.
- 작업 계획: [plan.md](../plan.md) "SNS 기능" 항목의 💡 블록(저장 대상 = 관계+공개범위).

---

## N-038. 온보딩 게이트는 단일 진입점에 두면 인터셉터가 필요 없다 + 시드값 vs 사용자 초기값 분리 + NOT NULL 컬럼은 신규=기본·기존=백필

> **한 줄 요약**: "신규 가입자만 첫 설정 화면으로 강제"하는 게이트는, 로그인 후 **착지점이 단일(`/`)이면 그 컨트롤러 한 곳에서 `if (!onboarded) redirect`** 로 충분하다 — 전역 인터셉터·필터는 과설계다. 그리고 가입 시 자동 시드된 값과 사용자가 직접 정하는 초기값은 **다른 책임**이라 분리해야 하며(초기값 적용 시 누적 기준일도 리셋), 온보딩 여부 같은 **NOT NULL 플래그를 추가할 때는 신규 행=기본값 / 기존 행=백필**로 의미를 갈라야 기존 사용자가 휩쓸리지 않는다.

### 배경

가입하면 타이머 잔여가 무조건 1시간(증가값)으로 시드되던 것을, 사용자가 첫 진입 때 **초기값(시작 잔여)+증가값+상한**을 직접 정하도록 온보딩 단계를 넣으며 마주친 세 가지 설계 판단.

### 1. 게이트는 단일 진입점이면 인터셉터가 필요 없다

"온보딩 안 한 사용자는 본 화면 대신 온보딩으로 보낸다"는 가드를 어디에 둘지가 갈린다.

- **전역 인터셉터/필터** — 모든 인증 요청을 가로채 `!onboarded`면 `/onboarding`으로. *강력하지만* 허용목록(온보딩 자신·정적·로그아웃·POST들) 관리가 늘고, **기존 테스트가 대거 깨진다**(인증 페이지를 때리는 모든 테스트가 리다이렉트됨).
- **단일 진입점 가드(채택)** — 로그인 후 착지점이 항상 `/`(폼 로그인·OAuth 둘 다 기본)이므로, **대시보드 컨트롤러 한 곳**에서 `if (!user.isOnboarded()) return "redirect:/onboarding"`. 신규 사용자는 어차피 `/`를 거쳐 유입되니 실제 흐름을 100% 덮고, **블래스트 반경이 그 컨트롤러 테스트 하나**로 좁아진다.
- 트레이드오프: 비온보딩 사용자가 `/books` 같은 URL을 **직접 입력**하면 안 걸린다(엣지). 첫 사용자는 그쪽으로 갈 링크가 없으므로 v1에선 수용. 진짜 강제가 필요해지면 그때 인터셉터로 승격.
- 교훈: **"어디서 강제하나"는 위협/흐름 모델에 맞춰라.** 모든 경로를 다 막는 건, 모든 경로로 들어올 수 있을 때만 값을 한다. 입구가 하나면 입구만 지켜도 된다(과설계 회피).

### 2. 자동 시드값과 사용자 초기값은 다른 책임 — 분리 + 기준일 리셋

가입 시 `startFor`가 잔여를 `min(증가값, cap)`으로 **자동 시드**한다(README "1일차 = 1증가값"). 이건 "값을 안 정한 사용자도 일단 굴러가게" 하는 기본값이다. 사용자가 **직접 고르는 초기값**은 별도 동작(`applyInitialSetup`)으로 분리했다 — 한 메서드에 욱여넣으면 "기본 시드인지 사용자 선택인지" 의미가 흐려진다.

- 초기값 적용 시 **`lastAccrualDate`를 today로 리셋**하는 게 핵심: 가입~온보딩 사이에 하루가 지났다면, 리셋 안 하면 그 경과분이 사용자가 정한 초기값에 누적돼 섞인다. "사용자가 정한 값이 *지금 이 순간*의 시작점"이 되려면 기준일을 온보딩 시점으로 당겨야 한다(N-001 Lazy 누적과 맞물림).
- 불변식은 도메인이 지킨다: 초기값 > cap이면 `min`으로 클램프(remaining ≤ cap 유지). 컨트롤러는 추가로 "초기값 ≤ 상한"을 친절한 검증 에러로 안내(이중 방어).

### 3. NOT NULL 플래그 추가 — 신규=기본값, 기존=백필

온보딩 여부(`users.onboarded boolean not null`)처럼 **기존 행에도 채워야 하는 NOT NULL 컬럼**을 더할 때, 신규와 기존의 의미가 다르다.

- **신규 가입자**: `false`(아직 온보딩 안 함) — 엔티티 기본값 + 컬럼 `default false`.
- **기존 사용자**: 이미 서비스를 쓰고 있으니 온보딩을 강요하면 안 된다 → 마이그레이션에서 `update ... set onboarded = true`로 **백필**.
- 한 Flyway 스크립트(V6)에서 `add column ... default false` 직후 `update set true`로 둘을 가른다. 신규 환경(테스트 H2)엔 기존 행이 없어 update가 0행이라 무해.
- 일반화: **기능 플래그/상태 컬럼을 도입할 때 "이미 존재하던 데이터는 어느 상태로 볼 것인가"를 반드시 정하라.** 기본값만 두면 기존 사용자가 신규처럼 취급돼 엉뚱한 흐름(여기선 온보딩 강제)에 휩쓸린다. [[n-023-ddl-auto-update의-한계]]의 "스키마는 Flyway가 단일 소스"와 같은 결.

### Q&A 대비

- *"왜 인터셉터 안 썼나?"* → 입구가 `/` 하나라 거기서만 막으면 실제 흐름을 다 덮는다. 전역 가드는 허용목록 관리·테스트 파손 비용이 크고 이득은 엣지(직접 URL 입력)뿐.
- *"초기값 적용할 때 왜 날짜를 리셋?"* → 안 하면 가입~온보딩 경과분이 사용자가 정한 시작값에 누적돼 섞인다. 기준일을 온보딩 시점으로 당겨야 "지금이 시작점"이 된다.
- *"기존 사용자는 온보딩 다시 하나?"* → 아니다. 마이그레이션에서 `onboarded=true`로 백필해 제외. 신규만 `false`로 게이트에 걸린다.

### 관련 노트

- [[n-001-누적-카운터-일일-리셋--배치-스케줄러-vs-lazy-계산]] — `lastAccrualDate` 리셋이 맞물리는 Lazy 누적 모델.
- [[n-023-ddl-auto-update의-한계--스키마-드리프트와-마이그레이션flyway]] — NOT NULL 컬럼/백필은 Flyway가 단일 소스.
- [[n-012-인증-주체--도메인-엔티티--principal로-도메인-user를-다시-잇고-접속을-lazy-누적-트리거로]] — 게이트가 principal→User 재조회 위에서 동작.

---

## N-039. 제약을 뒤늦게 강화하려면 기존 위반 데이터부터 백필한다 (backfill)

> **한 줄 요약**: 운영 중인 테이블에 **유니크·NOT NULL 같은 제약을 뒤늦게** 걸면, 기존 데이터가 이미 그 규칙을 어기고 있어(중복·NULL) 제약 추가가 **실패**한다. 그래서 제약을 걸기 *전에* 위반 행을 규칙에 맞게 메우는 **백필(backfill)** 이 선행돼야 한다 — 순서는 항상 **① 백필 → ② 제약**.

### 백필이란

**이미 존재하는 행(과거 데이터)의 빈/잘못된 값을, 새 규칙에 맞게 뒤늦게 채워 넣는** 작업. 새로 들어올 데이터는 코드(검증)·DB(제약)가 막지만, **제약이 생기기 전부터 쌓여 있던 데이터**는 아무도 그 규칙을 강요한 적이 없어 위반 상태일 수 있다. 제약을 거는 순간 그 과거 위반이 마이그레이션을 깨뜨리므로, 먼저 메워야 한다.

### 배경 — 닉네임 유니크화

닉네임을 SNS 검색 키·프로필 핸들로 쓰려면 유니크해야 하는데, 지금껏 유니크 제약이 없어 중복이 가능했다.
- `nickname`은 V1부터 `NOT NULL` → **NULL은 없고 중복만** 백필 대상(컬럼마다 위반 종류가 다름을 확인하는 게 핵심 — NOT NULL이면 NULL을, 유니크면 중복을 푼다).
- 백필 규칙: 같은 닉 그룹에서 **가장 먼저 가입한(낮은 id) 행은 유지**, 이후 중복은 **`-{id}` 접미사**로 유일화(id는 PK라 유일 보장). 영향받은 사용자는 이후 직접 바꿀 수 있다.

```sql
-- ① 백필: 중복 행만 -{id} 로 유일화 (낮은 id 유지)
update users set nickname = concat(nickname, '-', id)
where id in (select id from (
    select id, row_number() over (partition by nickname order by id) as rn from users
) ranked where ranked.rn > 1);
-- ② 그제서야 제약
alter table users add constraint uk_users_nickname unique (nickname);
```

### 짚어둘 점

- **순서를 어기면 실패**: ②를 먼저 실행하면 기존 중복 때문에 제약 추가가 거부된다. 한 마이그레이션 안에서 UPDATE→ALTER 순서로 둔다.
- **크로스-다이얼렉트**: self-update(`update users ... (select ... from users)`)는 MySQL이 "target table" 오류를 낸다 → **중첩 파생테이블**로 한 번 감싸 materialize하면 MySQL 8·H2 양쪽에서 돈다. (운영 MySQL, 테스트 H2 모두 통과해야 하므로.)
- **신규 환경은 무해**: 새 DB(테스트 H2)는 행이 없어 백필 UPDATE가 0행 — 제약만 선다.
- **앱 검증 ≠ DB 제약**: 백필+제약은 무결성의 마지막 방어선이고, 사용자에겐 그 전에 앱이 친절히 막아야 한다([[n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다]]). 닉네임도 `existsByNickname` 사전 검사 + 유니크 제약 이중.
- **부작용 — 테스트 픽스처가 깨진다**: 같은 값을 여러 행에 재사용하던 기존 테스트가 새 유니크 제약에 걸려 실패한다 → [troubleshooting T-028](troubleshooting.md#t-028-유니크-제약-추가가-같은-값을-쓰던-기존-테스트-픽스처를-깨뜨린다).

### Q&A 대비

- *"백필이 뭐냐?"* → 제약을 강화하기 전, 그 규칙을 어기고 있던 **기존 데이터를 규칙에 맞게 뒤채우는** 것. 안 하면 제약 추가가 기존 위반 때문에 실패한다.
- *"왜 NULL은 안 채웠나?"* → `nickname`이 이미 NOT NULL이라 NULL이 애초에 없었다. 위반 종류는 컬럼 제약에 따라 다르다(여기선 중복만).
- *"왜 낮은 id를 살리고 뒤에 접미사?"* → 먼저 쓰던 사람의 닉을 보존하는 게 자연스럽고, id가 PK라 `-{id}`가 충돌 없이 유일하다.

### 관련 노트

- [[n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다]] — 제약은 마지막 방어선, 앱 사전검증과 역할 분담.
- [[n-023-ddl-auto-update의-한계--스키마-드리프트와-마이그레이션flyway]] — 스키마/백필은 Flyway가 단일 소스.
- [[n-038-온보딩-게이트는-단일-진입점에-두면-인터셉터가-필요-없다--시드값-vs-사용자-초기값-분리--not-null-컬럼은-신규기본기존백필]] — NOT NULL 컬럼 추가 시 신규=기본·기존=백필(같은 백필 결).

---

## N-040. mock 단위테스트는 DB 제약(FK·유니크)을 검증하지 못한다

> **한 줄 요약**: 리포지토리를 mock으로 둔 단위테스트는 **호출 여부·순서만** 검증하고 **실제 DB를 안 탄다**. 그래서 FK·유니크 같은 **DB 제약 위반은 mock 경계 밖**이라 잡히지 않는다 — 특히 **삭제 경로(부모 삭제 전 자식 정리)** 처럼 제약이 핵심인 로직은 **실제 스키마 통합 테스트**를 한 개라도 둬야 한다.

### 어쩌다 만났나

회원 탈퇴(`AccountService.purge`)가 세션·타이머는 지웠지만 **book을 안 지웠다**. `book.user_id`는 FK(cascade 없음)로 users를 참조하므로, 책 가진 사용자는 탈퇴 시 `userRepository.delete(user)`에서 **FK 위반**이 난다. 그런데 기존 `AccountServiceTest`는 **통과**했다 — mock 리포지토리엔 FK가 없어 "book 삭제를 안 해도" 아무 일도 안 일어나기 때문이다.

### 왜 mock은 못 잡나 — 테스트가 보는 경계

```java
// mock 단위테스트: "호출했나/순서 맞나"만 본다 — 실제 DELETE도, FK도 없다
var ordered = inOrder(sessionRepository, timerRepository, userRepository);
ordered.verify(sessionRepository).deleteByUser(user);
ordered.verify(userRepository).delete(user);   // mock이라 FK 검사 자체가 없음
```

- mock은 **협력자와의 상호작용(interaction)** 을 검증하는 도구다. "내가 이 리포 메서드를 이 순서로 불렀다"는 보장하지만, **그 호출들이 실제 DB에서 무결성을 만족하는지**는 알 수 없다.
- 그래서 "book 삭제를 빼먹었다"는 **누락**은 mock 테스트에선 *애초에 검증 대상이 아니다* — 빠진 호출은 검증하지 않으니 초록불.

### 보강 — 삭제 경로엔 실제 스키마 통합 테스트

```java
@SpringBootTest @Transactional   // 실제 빈 + H2(엔티티 파생 스키마, FK 있음)
class AccountDeletionIntegrationTest {
    @Test void deleteAccount_withBooks_succeeds() {
        // 책 가진 사용자 저장 → 탈퇴 → 예외 없이 끝나고 계정이 사라지는지
        // book FK가 안 정리되면 여기서 제약 위반으로 빨개진다(쿼리 시 flush 강제)
    }
}
```

- 메인 테스트 스키마는 `ddl-auto`(엔티티 파생)라 **FK가 실제로 생성**된다 → 통합 테스트면 FK를 진짜로 탄다.
- 통합 테스트는 느리니 전부 만들 필요는 없다. **제약이 본질인 경로**(유저/부모 삭제, 유니크 충돌 등)에만 표적으로 1개.

### 일반화

- **mock은 "행동(누구를 어떻게 불렀나)"을, 통합 테스트는 "상태(DB가 규칙을 지키나)"를 검증한다.** 둘은 대체재가 아니라 역할 분담([[n-009-계층별-테스트-전략--도메인-단위슬라이스서비스-mock-테스트-피라미드]]).
- **새 FK(연관)를 추가하면 유저/부모 삭제 경로를 반드시 점검한다.** follow(V9)를 추가하며 purge에 follow 정리를 넣다가, 같은 패턴으로 book 정리가 빠져 있던 걸 발견했다 — "`deleteByUser`가 정의돼 있다 ≠ 호출된다."

### Q&A 대비

- *"mock 테스트가 통과했는데 왜 운영에서 깨지나?"* → mock은 호출만 검증하고 실제 DB 제약(FK)을 안 탄다. 누락된 자식 삭제는 mock 경계 밖이라 안 보인다 — 실제 스키마 통합 테스트라야 잡힌다.
- *"그럼 다 통합 테스트로 짜야 하나?"* → 아니다. 단위(mock)는 빠르고 분기·상호작용 검증에 좋다. 제약이 핵심인 경로에만 통합 테스트를 표적으로 얹는다.

### 관련 노트

- [[n-009-계층별-테스트-전략--도메인-단위슬라이스서비스-mock-테스트-피라미드]] — 계층별 무엇을 mock하고 무엇을 실제로 둘지.
- [troubleshooting T-029](troubleshooting.md#t-029-유저-삭제-경로에서-fk-자식-정리-누락--mock-단위테스트는-못-잡는다) — 이 노트가 나온 구체 함정(절차).
- [troubleshooting T-023](troubleshooting.md#t-023-읽은-적-있는-책-삭제가-reading_session-fk-미정리로-부모-삭제-실패) — 같은 "부모 삭제 전 자식 정리" 뿌리.

---

## N-041. 외부 검색 API의 "필드 한정" 옵션은 문서대로 동작하지 않을 수 있다 — 결과를 신뢰 말고 후필터로 불변식을 강제

> **한 줄 요약**: 외부 검색 API의 "제목만/저자만" 같은 **필드 한정 파라미터**(알라딘 `QueryType=Title`)는 문서와 달리 다른 필드까지 매칭해 돌려줄 수 있다. 내 코드가 파라미터를 정확히 보냈는데도 결과가 이상하면 **외부 API 동작이 문서와 다른 것**을 의심하고, 노출 전에 **내가 의도한 불변식(이 필드에 검색어가 있다)을 결과에 한 번 더 강제(후필터)** 한다.

### 어쩌다 만났나

책 검색을 **제목 기준**으로 분리(알라딘 `QueryType=Title`)했는데도, "모기"를 제목으로 검색하면 저자 "모기 겐이치로"의 책(제목엔 모기 없음)이 위에 떴다. 코드는 정확히 `QueryType=Title`을 보내고 있었다 — 즉 **알라딘 `Title` 검색이 문서("제목만")만큼 엄격하지 않고 저자도 매칭**했다.

### 디버깅 — "내 코드 의심"을 소거법으로 좁히기

결과가 이상하면 보통 내 코드부터 의심하지만, 여기선 단계적 소거로 **외부 API**가 범인임을 특정했다:
1. 파라미터 값 매핑 enum의 `from()`이 **절대 기본값(Keyword)을 반환하지 않음**(폴백=Title) → 우리는 항상 Title/Author만 보낸다, Keyword를 보낼 경로가 없다.
2. **기존 Keyword 검색이 한글로 정상 동작**했음 → URL 인코딩·전송 계층 무관(같은 기계로 ASCII 값 하나만 바뀜).
3. ∴ 남는 건 외부 API가 받은 `QueryType=Title`을 비엄격하게 해석한다는 것.

> 교훈: "결과가 이상함 ≠ 내 코드 버그". 내 책임 경계(보낸 파라미터)와 외부 책임 경계(API 동작)를 갈라서, 내 쪽이 증명되면 외부를 의심한다.

### 해결 — 결과에 불변식을 강제(후필터)

외부 API 동작을 못 믿으니, **노출 직전에 내가 원하는 성질을 결과에 직접 강제**한다:

```java
// 고른 기준 필드(제목/저자)에 검색어가 실제로 든 결과만 남긴다
String needle = normalize(query);                       // 공백 제거 + 소문자(Locale.ROOT)
results.stream().filter(r -> {
    String field = (type == AUTHOR) ? r.author() : r.title();
    return field != null && normalize(field).contains(needle);
}).toList();
```

- **정규화 후 contains**가 핵심 — 원문 그대로 `contains`나 `equals`는 "Clean Code"↔"cleancode"(공백)·대소문자 차이로 **정상 결과를 과도하게 떨군다**. 너무 느슨하지도(저자 누출) 너무 빡빡하지도(정상 누락) 않은 중간을 고른다.
- **방어적이라 어느 쪽이든 안전**: 외부 API가 `Title`을 무시하든·비엄격이든, 후필터가 최종 불변식을 보장한다.

### 트레이드오프 / 한계

- **페이지네이션 과대 집계**: 외부 API가 준 `totalResults`(필터 전)로 페이지 수를 계산하면, 필터로 많이 걸러질수록 "N페이지"가 실제보다 많게 보인다. 표시만 헐겁고 동작은 무해 — 정확히 하려면 필터 후 재계산하거나 API 호출을 더 정밀하게.
- **거짓 음성(false negative)**: 외부 API가 형태소·로마자 등으로 매칭한 정상 결과를, 단순 substring 후필터가 떨굴 수 있다. 한국어 정확 검색어에선 드물어 수용 가능하지만, 후필터는 "정밀도↑ 재현율↓" 쪽으로 기운다는 걸 인지하고 정규화 강도를 조절한다.

### Q&A 대비

- *"파라미터를 맞게 보냈는데 왜 결과가 틀리나?"* → 외부 API의 필드 한정 옵션이 문서만큼 엄격하지 않을 수 있다. 내 전송이 증명되면 외부 동작을 의심하고, 결과를 후필터로 강제한다.
- *"왜 그냥 contains가 아니라 정규화 후 contains인가?"* → 공백·대소문자 차이로 정상 결과를 떨구지 않으려고. 매칭은 사용자 기대(어느 정도 일치)에 맞춰 느슨하게.
- *"후필터의 비용은?"* → 페이저 과대집계·드문 거짓 음성. 외부 API를 못 고치는 상황에서 사용자 의도를 보장하는 실용적 절충.

### 관련 노트

- [[n-035-제휴어필리에이트-수익-모델--귀속-신원정산-분리-왜-알라딘만--다나와는-안-되나]] — 같은 알라딘 OpenAPI를 쓰는 다른 축(제휴).
- [troubleshooting T-030](troubleshooting.md#t-030-알라딘-querytypetitle이-문서와-달리-저자까지-매칭--결과를-신뢰-말고-후필터) — 이 노트가 나온 구체 함정(절차).

---

## N-042. flex-basis는 주축(main axis) 크기다 — 컨테이너 방향(row↔column)을 바꾸면 같은 `flex` 단축속성이 가로↔세로로 뒤바뀐다

> **한 줄 요약**: `flex: 1 1 160px`의 `160px`(flex-basis)는 "가로폭"이 아니라 **주축(main axis) 크기**다. 컨테이너가 `flex-direction: row`면 주축=가로라 폭 160px이지만, `column`으로 바꾸면 주축=세로라 **높이 160px**로 뜻이 뒤바뀐다. 넓은 셀렉터에 박아둔 `flex` 규칙을 더 구체적인 셀렉터에서 방향만 바꾸면, basis가 조용히 가로↔세로로 바뀌어 칸이 길쭉해지는 함정이 생긴다.

### 어쩌다 만났나

책 검색 폼을 세로 배치로 바꿨다(라디오 위 + 입력칸/버튼 아래). `.search-row`를 `flex-direction: column`으로 두고 입력칸은 `width: 100%`만 줬는데, 검색 입력칸이 textarea처럼 **세로로 길쭉**(~160px 높이)해졌다. 입력칸엔 height를 준 적이 없었다.

범인은 **상속(셀렉터 캐스케이드)된 flex 규칙**이었다:

```css
.book-search-form input[type=text] { flex: 1 1 160px; ... }  /* 원래 row 배치용: 폭 기준 160px */
.search-row { flex-direction: column; }                       /* 나중에 세로로 바꿈 */
.search-row input[type=text] { width: 100%; ... }             /* flex는 안 건드림 → 위 규칙 유효 */
```

`.search-row`가 column이 되면서, 살아있던 `flex: 1 1 160px`의 `160px`가 **세로(높이) 기준**으로 적용돼 입력칸이 그만큼 키가 커졌다(거기에 `flex-grow: 1`까지 있어 남는 세로 공간을 더 먹음).

### 개념 — flex 단축속성과 주축

`flex: <grow> <shrink> <basis>`는 셋을 한 번에 정한다:
- **flex-grow**: 남는 주축 공간을 얼마나 나눠 가질지.
- **flex-shrink**: 모자랄 때 얼마나 줄어들지.
- **flex-basis**: 주축 방향의 **시작 크기**. `width`/`height`가 아니라 **"주축 크기"** — 그래서 방향에 따라 의미가 갈린다.

| flex-direction | 주축(main) | flex-basis가 정하는 것 | 교차축(cross) 크기 |
|---|---|---|---|
| `row` (기본) | 가로 | **width** | height (보통 내용/`align-items`) |
| `column` | 세로 | **height** | width |

즉 같은 `flex: 1 1 160px`라도 row에선 "폭 160px부터", column에선 "높이 160px부터"다. **basis는 width의 동의어가 아니다.**

### 해결 — 방향 바뀐 셀렉터에서 flex를 리셋

```css
.search-row input[type=text] {
    flex: none;          /* = 0 0 auto. 상속된 1 1 160px(높이로 먹던) 무력화 */
    width: 100%;
    box-sizing: border-box;
    padding: 12px 14px;
}
```

`flex: none`(=`flex: 0 0 auto`)으로 grow·shrink·basis를 모두 끄면, 입력칸 높이는 다시 내용(폰트+패딩)으로 결정돼 한 줄 높이로 돌아온다. 폭은 `width: 100%`가 맡는다.

### 일반화 포인트 (면접 답변용)

- **flex의 grow/shrink/basis는 전부 "주축" 기준이다.** 컨테이너 방향을 바꾸면 자식의 flex 규칙 의미가 통째로 90° 회전한다 — 방향을 바꿀 땐 자식의 `flex`도 같이 점검.
- **CSS 버그는 "그 요소에 직접 쓴 것"만 보면 못 잡는다** — 넓은 셀렉터(`.book-search-form input`)가 더 구체적/나중 셀렉터의 의도와 충돌하는 **캐스케이드 상호작용**이 흔한 원인. "이 값 어디서 왔지"는 계산된 스타일(computed)과 매칭된 규칙 전부를 봐야 한다.
- 일반적 처방: 레이아웃 컨텍스트(방향)를 바꾸는 셀렉터에선, 물려받은 sizing 단축속성을 **명시적으로 리셋**(`flex: none` 등)해 의도치 않은 잔재를 끊는다.

### Q&A 대비

- *"width를 안 줬는데 왜 높이가 생겼나?"* → 준 적 없는 건 height고, 높이는 상속된 `flex-basis: 160px`가 column 컨테이너에서 주축(세로) 크기로 적용된 것. basis는 width가 아니라 주축 크기다.
- *"`flex-basis: 0`이나 `width`로 안 되나?"* → basis를 0으로 두면 grow가 살아 여전히 세로로 늘어난다. grow·shrink까지 끄려면 `flex: none`이 깔끔하다. `width`는 교차축이라 높이 문제를 못 푼다.
- *"왜 처음 row 땐 멀쩡했나?"* → row에선 basis 160px가 가로폭 기준이라 의도대로였다. 방향을 column으로 바꾼 순간 같은 규칙이 세로로 재해석된 것.

### 관련 노트

- (아직 없음 — CSS 레이아웃 첫 노트)

---

## N-043. Rate Limiting — 요청 속도 제한으로 남용·과부하·비용을 막는다 (토큰 버킷, 429)

**한 줄 요약**: Rate Limiting은 "일정 시간(윈도우) 동안 한 주체(사용자/IP/API키)가 보낼 수 있는 요청 횟수에 상한"을 두는 것. 신고·로그인처럼 남용 표적이 되는 기능에 붙여 스팸·과부하·비용 폭주를 막는다. 한도를 넘으면 HTTP **429 Too Many Requests**를 돌려준다. 실무 표준 알고리즘은 평소 여유를 두면서 순간 폭주(burst)도 어느 정도 허용하는 **토큰 버킷**.

### 자세한 설명

SNS 5단계로 사용자 신고(report) 기능이 들어왔다(커밋 #127). 신고는 **남용 표적 1순위**다 — 악의적 사용자가 특정 대상을 신고로 연타하면 ① 멀쩡한 사용자를 "신고 폭격"으로 묻고(자동 차단 로직이 있으면 더 위험), ② DB·서버에 부하를 주고, ③ 신고가 메일/푸시 같은 유료 외부 호출을 트리거하면 요금까지 샌다. 그래서 "한 사람이 짧은 시간에 무한정 누르지 못하게" 상한을 거는 게 Rate Limiting이다.

개념적 동작:
```
요청 도착 →
  "이 주체가 최근 <윈도우> 동안 <한도> 회를 넘겼나?"
    넘었으면   → 거부 (429 Too Many Requests, 보통 Retry-After 헤더로 "언제 다시 와라")
    안 넘었으면 → 허용 + 카운트 +1
```

**무엇당(per-key) 셀지가 핵심 설계 결정**이다: 사용자 ID당 / IP당 / (대상,신고자) 쌍당 등. 로그인 전 기능은 보통 IP, 로그인 후는 사용자 ID 기준.

**대표 알고리즘 3가지**:

| 방식 | 한 줄 | 트레이드오프 |
|---|---|---|
| 고정 윈도우(Fixed Window) | "매 분 0~59초에 N회" | 단순. 경계(앞 분 끝 + 다음 분 시작)에 순간 2배 몰림 가능 |
| 슬라이딩 윈도우(Sliding Window) | "지금 기준 최근 60초에 N회" | 경계 문제 완화. 계산/저장이 약간 더 듦 |
| 토큰 버킷(Token Bucket) | 버킷에 토큰이 일정 속도로 차고, 요청마다 1개 소비 | 평소 여유 + 순간 burst 허용. 실무 최다 |

**상태(카운터)를 어디 두나**: 단일 인스턴스면 인메모리로 충분하지만, 무상태·다중 인스턴스(N-029)면 카운터를 공유 저장소(Redis 등)에 둬야 한다 — 인메모리면 인스턴스마다 따로 세서 실효 한도가 인스턴스 수만큼 새고, 인스턴스 교체 시 리셋된다(세션 외부화와 같은 사상).

### 일반화 포인트 (면접 답변용)

- Rate Limiting은 **방어선의 한 층**이다. N-026의 "로그인 brute-force를 IP 잠금으로 막는다"가 바로 Rate Limiting의 특수 사례 — 일반화하면 "남용 가능한 모든 엔드포인트에 요청 속도 상한". 입력 검증(validation)·DB 제약(constraint)과는 다른 축의 방어다(N-019).
- **429 vs 403 vs 503 구분**: 429는 "너무 자주 = 잠시 후 다시"(일시적, Retry-After), 403은 "권한 없음"(영구적), 503은 "서버가 지금 못 받음". 클라이언트의 재시도 전략이 달라지므로 상태코드를 정확히 골라야 한다.
- **per-key 선택이 곧 정책**: IP당이면 NAT/공용 와이파이 뒤 여러 사용자가 한도를 공유해 오탐, 사용자당이면 계정을 새로 만들어 우회 가능. 위협 모델에 맞춰 키를 고른다.
- **상한은 트레이드오프**: 너무 빡빡하면 정상 사용자(연타·새로고침)를 막고, 너무 느슨하면 남용을 못 막는다. 정상 사용 패턴을 기준으로 잡고 모니터링으로 조정한다.

### Q&A 대비

- *"왜 신고에 굳이 제한을 거나?"* → 신고는 인증된 사용자도 악용할 수 있는 "쓰기 트리거"라서. 한 명이 대상 하나를 수백 번 신고하면 자동 차단·운영 큐를 오염시킨다. 정상 사용자는 같은 대상을 분당 몇 번 이상 신고할 일이 없으니 상한이 정상 사용을 거의 안 건드린다.
- *"앞단(ALB/Nginx)에서 막으면 되지 왜 앱에서?"* → 인프라 레벨은 IP당 거친 보호엔 좋지만 "사용자 ID당", "(신고자,대상) 쌍당" 같은 도메인 의미의 키로는 못 센다. 도메인 규칙이 섞인 제한은 앱에서.
- *"인메모리로 충분하지 않나?"* → 인스턴스가 하나면 OK. 다중 인스턴스/무중단 배포(N-029, N-030)면 인스턴스마다 따로 세서 한도가 새고 교체 때 리셋된다 → 공유 저장소 필요.

### 코드 위치

- `src/main/java/com/booktimer/security/RateLimitService.java` — 사용자별 카운터(인메모리 `ConcurrentHashMap`, `Clock` 주입). 키 = `action:userId`.
- `src/main/java/com/booktimer/security/RateLimitAction.java` — 액션별 한도/윈도우(`FOLLOW` 30/분 · `SEARCH` 20/분 · `REPORT` 10/시간).
- 적용: `FollowController`·`ReportController`·`SearchController`가 진입 시 `allow(action, me.getId())` 체크.
- 대비: `security/LoginAttemptService` — 같은 인메모리 패턴이지만 키가 **IP**(미인증 경로라). 이 서비스는 인증 후라 **userId**.

> ⚠️ **이 프로젝트의 실제 선택은 위 "대표 알고리즘" 표·429와 조금 다르다(SSR 실용 절충)**:
> - 알고리즘은 **토큰 버킷이 아니라 고정 윈도우(Fixed Window)** — 가장 단순하고 이 규모에 충분. (burst 정교 제어가 필요해지면 토큰 버킷/슬라이딩으로.)
> - 초과 응답이 **429가 아니다** — 폼 기반 SSR이라: 팔로우·신고는 **조용히 드롭**(스팸에 굳이 피드백 안 줌), 검색은 결과 없이 **안내 문구**(`rateLimited` 플래그). 429+Retry-After는 API/SPA에 더 맞다.
> - 상태가 **인메모리=인스턴스별**이라 다중 인스턴스/롤링 배포(N-029·N-030) 중 분산 우회 가능 — 강한 보장은 공유 저장소(Redis)·앞단 WAF와 함께(plan.md backlog). (LoginAttemptService와 동일 한계.)
> - 커밋: [#127](https://github.com/Goospel/booktimer/pull/127) 신고 + [#128](https://github.com/Goospel/booktimer/pull/128) 레이트리밋(SNS 5단계).

### 관련 노트

- [N-026. OAuth find-or-create의 함정 + brute-force 미방어](#n-026-oauth-find-or-create의-함정email_verified--spring-security가-막아주지-않는-것brute-force) — IP 기반 로그인 잠금 = Rate Limiting의 특수 사례
- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — "여러 층의 방어"라는 같은 사상
- [N-029. 인메모리 세션은 인스턴스가 죽으면 사라진다](#n-029-인메모리-세션은-인스턴스가-죽으면-사라진다--세션-외부화와-무상태-앱-서버) — 카운터를 인메모리에 두면 같은 함정(다중 인스턴스에서 한도가 샘)

---

## N-044. CSRF 숨김필드는 세션을 lazy 생성한다 — 큰 페이지·하단 폼이면 응답 버퍼 커밋 후라 실패

**한 줄 요약**: Spring Security + Thymeleaf에서 `th:action`이 붙은 폼은 렌더 시 **CSRF 토큰 숨김 input**을 자동으로 끼워 넣는데, 토큰 저장소가 세션 기반(`HttpSessionCsrfTokenRepository`)이면 **그 순간 HTTP 세션을 새로 만든다**. 그런데 응답은 출력이 버퍼 크기를 넘으면 렌더 도중 **커밋(flush)** 되고, 커밋 뒤에는 세션을 못 만든다 → 폼이 페이지 **하단에만** 있고 본문이 크면 `IllegalStateException: Cannot create a session after the response has been committed`로 500. 해결은 **렌더 전에 토큰을 미리 만들어** 세션을 확정하는 것.

### 자세한 설명

세 가지 사실이 겹쳐서 터진다:

1. **CSRF 숨김필드 = 세션 쓰기**: `th:action`은 Spring의 `RequestDataValueProcessor`로 `<input type="hidden" name="_csrf" .../>`를 자동 주입한다. 토큰을 세션에 보관하므로, 세션이 아직 없으면 **렌더하다가 `request.getSession()`을 호출**해 만든다(= lazy).
2. **응답은 도중에 커밋된다**: 서블릿 응답엔 출력 버퍼가 있고, 누적 출력이 버퍼를 넘기면 **그 시점에 헤더+앞부분이 클라이언트로 flush**(커밋)된다. 커밋 후엔 헤더를 못 바꾸고 **세션 생성(Set-Cookie 필요)도 불가** → 예외.
3. **그래서 "첫 폼의 위치"에 우연히 의존**: 페이지에 폼이 여러 개면, **가장 먼저 렌더되는 폼**이 세션을 만든다. 그게 본문 앞쪽이면(버퍼 커밋 전) 무사하지만, 큰 본문(예: 371칸짜리 잔디 그래프) 뒤 **맨 아래 폼**이 첫 폼이면 이미 커밋된 뒤라 실패.

이 프로젝트에선 "측정엔 책 필수"로 바꾸며 **책 0권 사용자에게서 상단 측정-시작 폼이 사라지자**, 세션을 만드는 첫 폼이 맨 아래 로그아웃 폼으로 밀려 드러났다(T-033). *코드를 바꾼 게 아니라, 우연히 가려져 있던 잠복 버그가 노출된 것*.

### 해결 — 렌더 전에 토큰 선확정

컨트롤러에서 렌더 시작 전에 토큰을 강제로 로드하면 세션이 그때(응답 커밋 전) 생긴다. 이후 렌더 중 숨김필드는 **이미 있는** 토큰을 읽기만 하므로 세션을 새로 안 만든다:

```java
Object csrf = request.getAttribute(CsrfToken.class.getName());
if (csrf instanceof CsrfToken token) {
    token.getToken();   // Security 필터가 넣어둔 deferred 토큰을 강제 materialize → 세션 생성
}
```

대안과 비교:
- **버퍼 크기 키우기**(`response.setBufferSize`) — 페이지가 더 커지면 또 터짐. 미봉책.
- **폼을 페이지 앞쪽에 두기** — 레이아웃이 우연한 동작을 떠받치게 됨. 깨지기 쉬움.
- **CSRF 저장소를 쿠키 기반으로**(`CookieCsrfTokenRepository`) — 세션 의존이 사라져 근본적이나, 보안 모델·기존 설정 변경 폭이 큼.
- → **토큰 선확정**이 가장 국소적이고 폼 위치·페이지 크기와 무관해 견고.

### 일반화 포인트 (면접 답변용)

- **"응답 커밋"은 되돌릴 수 없는 경계다**: 한번 flush되면 상태코드·헤더·쿠키(=세션 생성)를 못 바꾼다. *렌더 도중 헤더를 바꾸려는 모든 시도*(세션 생성, 리다이렉트, 상태코드 변경)는 커밋 전에 끝나야 한다.
- **lazy 생성은 "언제 처음 쓰이나"에 동작이 묶인다**: 세션·토큰·커넥션처럼 lazy하게 만들어지는 자원은 *첫 사용 시점*이 곧 생성 시점이라, 페이지 구조·실행 순서 같은 우연에 결과가 좌우될 수 있다. 안정성을 원하면 **명시적으로 일찍 확정**한다(eager).
- **"안 건드린 코드가 깨졌다"의 정체**: 한쪽(시작 폼)을 지웠더니 무관해 보이던 다른 쪽(로그아웃 폼 CSRF)이 터진 건, 둘이 *공유 자원(세션)의 lazy 생성*을 통해 암묵적으로 연결돼 있었기 때문. 숨은 결합은 "무엇이 그 자원을 처음 만들었나"를 따라가면 보인다.

### Q&A 대비

- *"왜 평소엔 멀쩡했나?"* → 앞쪽 폼이 버퍼 커밋 전에 세션을 만들어줬다. 그 폼이 없어진 사용자에게서만 첫 세션 생성이 커밋 후로 밀렸다.
- *"왜 line 143(로그아웃 폼)이라고 나왔나?"* → 세션을 만들려다 실패한 게 그 폼의 CSRF 숨김필드라서. 진짜 원인은 그 폼이 아니라 "그게 첫 세션 생성 지점이 된 것".
- *"테스트로 어떻게 잡았나?"* → 컨트롤러 MockMvc가 실제 템플릿을 렌더하므로 끝단에서 500이 났다(N-009). 순수 서비스 테스트만으론 못 봤을 것.

### 코드 위치

- `src/main/java/com/booktimer/web/DashboardController.java` — 렌더 전 `CsrfToken#getToken()` 선확정.
- `src/main/resources/templates/dashboard.html` — 큰 잔디 그래프 + 하단 로그아웃 폼(`th:action="@{/logout}"`). "측정 책 필수"로 책 0권 시 상단 시작 폼이 사라지는 분기.

### 관련 노트

- [N-011. Spring Security 폼 로그인 — CSRF 판단](#n-011-spring-security-폼-로그인--userdetailsservice--passwordencoder-자동-조립-csrf-판단) — CSRF 토큰이 왜·어떻게 폼에 실리는가
- 자매 트러블슈팅 **T-033** (같은 버그의 재발 방지 절차)

---

## N-045. Spring Data에서 "최신 N건"은 Pageable로 limit — 파생 메서드 이름으로 못 쓰는 정렬+개수 제한을 @Query에 얹는다

**한 줄 요약**: "가장 최근에 읽은 책 1권"처럼 **정렬 후 앞에서 N개만** 필요할 때, JPQL `@Query`에는 `LIMIT` 절을 직접 못 쓴다(JPQL 표준에 LIMIT 없음). 대신 메서드 시그니처에 **`Pageable` 파라미터**를 받고 호출부에서 `PageRequest.of(0, 1)`을 넘기면 Spring Data가 DB 방언에 맞는 `LIMIT`/`FETCH FIRST`로 변환한다. 반환은 `List<T>`로 받아 `isEmpty()` 체크 후 첫 원소를 쓴다.

### 자세한 설명

`reading_session`에서 "이 유저가 가장 최근에 시작한, 책이 연결된 세션의 책 id"가 필요했다(드롭다운 자동 선택용). 세 가지 선택지:

1. **파생 쿼리 메서드** (`findTopBy...OrderBy...`) — `findTopByUserAndBookIsNotNullOrderByStartedAtDesc` 처럼 **메서드 이름만으로** 정렬+1건이 된다(`Top`/`First` 키워드). 이름이 짧으면 깔끔하지만, 조건이 복잡해지면 이름이 비대해지고 `s.book.id`만 골라 받는(프로젝션) 게 어렵다.
2. **`@Query` + `Pageable`** (택함) — JPQL로 `select s.book.id ... order by s.startedAt desc`를 명시하고, limit은 `Pageable`로 분리. 프로젝션(`s.book.id`만)과 정렬을 쿼리에 또렷이 쓰면서 개수 제한은 호출부가 정한다.
3. **전부 가져와 자바에서 자르기** — `findByUser(...).stream()....limit(1)`. 행이 많으면 불필요하게 다 읽어 비효율. DB가 할 일을 앱이 떠안음.

택한 형태:

```java
@Query("select s.book.id from ReadingSession s where s.user = :user and s.book is not null order by s.startedAt desc")
List<Long> findRecentlyReadBookIds(@Param("user") User user, Pageable pageable);

// 호출부
List<Long> recent = sessionRepository.findRecentlyReadBookIds(user, PageRequest.of(0, 1));
Long recentBookId = recent.isEmpty() ? null : recent.get(0);
```

### 일반화 포인트 (면접 답변용)

- **JPQL엔 LIMIT이 없다**: `LIMIT`/`OFFSET`은 표준 SQL이 아니라 방언(MySQL `LIMIT`, Oracle `FETCH FIRST`, H2 등)마다 다르다. JPA가 이를 추상화한 게 `Pageable` — "몇 번째 페이지의 몇 건"을 넘기면 방언별 구문으로 번역한다. 그래서 **페이징이 필요 없어도 "앞에서 N건"을 위해 `Pageable`을 빌려 쓴다**.
- **`Top`/`First` 키워드 vs `Pageable`의 분담**: 고정 개수(항상 1건)면 메서드 이름의 `findFirst`/`findTop3`이 간결하다. 개수가 **호출 시점에 달라지거나**, 쿼리를 `@Query`로 명시(프로젝션·조인 페치 등)하고 싶으면 `Pageable`이 맞다.
- **반환 타입 선택**: 1건이어도 `Optional<T>`가 아니라 `List<T>`로 받는 게 안전하다 — `@Query`+`Pageable`은 결과가 0건일 수 있고, 단일 객체로 받으면 `NonUnique`/`NoResult` 처리가 애매해진다. `List`로 받아 `isEmpty()`로 분기.

### Q&A 대비

- *"왜 `findFirstBy...` 안 쓰고 `@Query`?"* → `s.book.id`만 뽑는 **프로젝션**과 `book is not null` 조건을 쿼리에 또렷이 두고 싶었고, 개수 제한은 직교 관심사라 `Pageable`로 분리했다. 파생 이름으로도 가능하지만 이름이 길어진다.
- *"`PageRequest.of(0, 1)`의 0과 1?"* → 0번째 페이지(첫 페이지), 페이지 크기 1 → 정렬 후 맨 앞 1건.
- *"정렬을 `Pageable`의 `Sort`로 안 넣은 이유?"* → 정렬 기준이 고정(`startedAt desc`)이라 쿼리에 박는 게 의도가 분명하다. 호출부가 정렬을 바꿀 일이 없으면 쿼리에 두는 편이 읽기 쉽다.

### 코드 위치

- `src/main/java/com/booktimer/session/ReadingSessionRepository.java` — `findRecentlyReadBookIds(User, Pageable)`.
- `src/main/java/com/booktimer/web/DashboardModel.java` — `PageRequest.of(0, 1)`로 호출, 최근 읽은 책을 드롭다운에 미리 선택(`recentBookId`).

### 관련 노트

- [N-009. 계층별 테스트 전략](#n-009-계층별-테스트-전략--도메인-단위--슬라이스--서비스-mock-테스트-피라미드) — 이 쿼리는 컨트롤러 통합 테스트(MockMvc+H2)로 끝단 검증했다.

---

## N-046. 식별자 3분할 — 로그인/공개핸들/표시이름은 각자 다른 축이고, 공개 핸들을 뭘로 두느냐는 보안 동치다

**한 줄 요약**: 사용자를 가리키는 문자열은 사실 **세 가지 직교한 역할**(① 로그인·내부 식별 ② 공개 핸들=검색·URL ③ 표시 이름)을 한다. 한 컬럼이 여럿을 겸하면 한 쪽 요구가 다른 쪽을 망가뜨린다(이메일이 로그인+공개 핸들을 겸하면 "로그인 식별자가 공개"라는 약점). BookTimer는 **login_id(로그인+공개핸들·불변·유니크) / nickname(표시·가변·중복허용) / email(연락·복구·비공개)**로 쪼갰다. 핵심 통찰: **공개 핸들을 nickname에 두든 login_id에 두든 "어떤 핸들 하나는 공개"라는 사실은 같다 — 보안상 동치**다. 진짜 약점은 "공개되는 게 *로그인 식별자*냐"가 아니라 "공개되는 핸들이 *연락 채널(email)*과 묶여 있느냐"였다.

### 자세한 설명

처음 설계는 "login_id는 비공개, 공개 핸들은 nickname"이었다. 그런데 "검색을 닉네임 말고 아이디로(인스타·X처럼)"라는 요구가 들어오자 login_id가 공개 검색 핸들이 됐다 — 초기 전제("login_id 비공개")의 정반대. 패닉할 일처럼 보이지만 따져보면 보안 본질은 안 변한다:

- 원래 걱정: "내 **이메일**이 홈페이지에 공개됐는데 그 이메일로 로그인한다" = 공개된 식별자가 **연락/복구 채널과 동일**.
- 해법의 본질: 로그인·공개 핸들을 **email에서 떼어낸다**. email은 비공개 속성(복구용)으로 강등.
- 그 공개 핸들을 nickname에 둘지 login_id에 둘지는 **부차적**이다 — 어느 쪽이든 "공개 핸들 하나"는 존재하고, 그게 알려져도 인증(비밀번호)을 못 뚫는다(X의 `@handle`이 공개여도 안전한 것과 동일).

즉 "공개 핸들 = login_id"로 옮긴 건 보안을 약화시키지 않고, **email을 식별/공개에서 완전히 분리**한다는 목표를 그대로 달성한다.

### 일반화 포인트 (면접 답변용)

- **한 식별자에 여러 역할을 겹치면 충돌한다**: 로그인 식별자는 "안정·유일", 공개 핸들은 "검색·URL 친화", 표시 이름은 "자유·중복 OK". 한 컬럼이 둘 이상을 겸하면 한 요구를 만족시키려다 다른 쪽을 깨뜨린다(email=로그인+공개 → "로그인 표적 공개").
- **불변/가변의 분담**: 공개 핸들(login_id)은 **불변**이어야 URL·@멘션·외부 링크가 안 깨진다. 사람이 바꾸고 싶은 욕구는 **표시 이름(nickname)**이 흡수한다(중복 허용·자유 변경). X/인스타가 `@handle`(불변·유일)과 display name(가변·중복)을 나눈 이유.
- **"무엇이 공개되냐"보다 "공개되는 게 무엇과 묶였냐"**: 공개 핸들 자체는 위험이 아니다(알려져도 인증을 못 뚫음). 위험은 그 공개 핸들이 *복구 채널(email)*이나 *권한 부여 키*와 동일할 때 생긴다.

### Q&A 대비

- *"login_id가 공개면 처음 목표(이메일 안 쓰기)가 무의미한 거 아냐?"* → 아니다. 목표는 "이메일을 식별자에서 빼기"였고 그건 달성됐다. email은 여전히 비공개·복구용. 공개되는 건 login_id뿐이고 이건 X @핸들처럼 공개 전제 식별자다.
- *"그럼 그냥 nickname으로 로그인하면 되지 왜 login_id를 또 만들어?"* → nickname은 중복 허용·수정 자유라 **안정 유일 식별자**가 못 된다. 로그인·URL은 안 변하고 유일한 키가 필요해서 login_id가 따로 있다.

### 코드 위치

- `src/main/java/com/booktimer/user/User.java` — `loginId`(불변·유니크), `nickname`(가변·중복), `email`(비공개).
- `claude-docs/login-id-design.md` §2·§🔁전환 — 식별 모델 표와 보안 동치 논증.

### 관련 노트

- [N-037. SNS 확장 — 새로 필요한 건 관계+공개범위](#n-037-sns로-확장해도-도메인-데이터는-새로-저장하지-않는다--새로-필요한-건-관계--공개범위-기존-데이터는-조회-주체만-바뀐다) — 식별/관계/속성의 분리 사고.
- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — nickname 유니크를 떼고 login_id로 옮긴 결정의 토대.

---

## N-047. 불변 식별자는 대리키(surrogate PK) 위에서 도메인 규칙으로 강제한다 — DB가 막아주지 않는다

**한 줄 요약**: "login_id는 한번 정하면 영원히 불변"은 **DB 제약이 아니라 도메인 규칙**이다. PK가 의미 없는 대리키(auto-increment `id`)라서 모든 FK가 그 `id`를 참조하므로, login_id를 바꿔도 무결성은 안 깨진다 — 즉 DB는 login_id 변경을 막을 이유가 없다(UPDATE 가능). 불변성은 **엔티티 메서드가 "이미 설정됐으면 던진다"로 스스로 지킨다**(`assignLoginId`가 `loginId != null`이면 `IllegalStateException`).

### 자세한 설명

`User`의 PK는 `@GeneratedValue(IDENTITY) Long id` — 비즈니스 의미 0인 대리키다. 이게 두 가지를 동시에 가능케 한다:

1. **nickname·login_id를 마음대로 바꿔도 FK가 안 깨진다** — follow/block/report/reading_session이 전부 `user.id`(대리키)를 참조하지 login_id를 참조하지 않으니까. (자연키를 PK로 썼다면 값 변경이 FK 연쇄 갱신을 일으킨다.)
2. **그래서 "불변"은 DB가 강제할 수 없다** — DB 관점에선 login_id는 그냥 유니크 컬럼이라 UPDATE가 자유롭다. 불변성은 *정책*이지 *무결성*이 아니다.

정책은 정책을 둘 곳(도메인 엔티티)에서 지킨다:

```java
public void assignLoginId(String raw) {
    if (this.loginId != null)                 // 이미 정해졌으면
        throw new IllegalStateException(...);  // 재설정 거부 = 불변
    this.loginId = normalizeLoginId(raw);
}
```

세터를 안 열고 "한 번만 채우는" 메서드만 노출하면, 호출부 어디서도 login_id를 못 바꾼다.

### 일반화 포인트 (면접 답변용)

- **무결성(integrity) vs 정책(policy)**: DB 제약은 "데이터가 모순되지 않게"를 지킨다(유니크·FK·NOT NULL). "한 번 정하면 못 바꾼다" 같은 **업무 규칙**은 모순이 아니라 정책이라 DB가 강제할 동기가 없다 → 도메인이 진다. (N-019의 "검증 vs 제약"과 같은 층 나눔의 연장.)
- **대리키가 값 변경의 자유를 준다**: 자연키(이메일·login_id)를 PK로 쓰면 그 값은 사실상 불변이어야 한다(FK 연쇄 때문). 대리키를 쓰면 자연 식별자를 자유롭게 바꾸거나(닉네임) 정책으로 묶을 수(login_id) 있다 — PK 선택이 곧 변경 가능성의 설계다.
- **불변은 "세터를 안 만들기"가 아니라 "두 번째 호출을 거부하기"**: 한 번은 채워야 하므로 완전 불변(final)이 아니라 "최초 1회만 허용"이다. `if (field != null) throw`가 그 관용구.

### Q&A 대비

- *"login_id를 unique로 걸었으니 DB가 불변도 지켜주는 거 아냐?"* → 아니다. unique는 "값이 겹치지 않게"일 뿐 "값을 못 바꾸게"가 아니다. 다른 값으로 UPDATE는 unique를 위반하지 않는다.
- *"PK를 login_id로 했으면 불변이 공짜였을 텐데?"* → FK가 login_id를 참조하게 돼서 "사실상 불변"이 강제됐겠지만, 대신 닉네임 같은 다른 자연키 변경도 다 빡빡해지고 인덱스·조인이 무거워진다. 대리키+도메인 규칙이 유연성·성능 면에서 낫다.

### 코드 위치

- `src/main/java/com/booktimer/user/User.java` — `id`(대리키), `assignLoginId`(불변 가드).
- `src/test/java/com/booktimer/user/UserTest.java` — `assignLoginId_immutable_onceSet`(재설정 시 ISE).

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 무결성 vs 검증/정책의 층 나눔.
- [N-046. 식별자 3분할](#n-046-식별자-3분할--로그인공개핸들표시이름은-각자-다른-축이고-공개-핸들을-뭘로-두느냐는-보안-동치다) — 불변/가변의 역할 분담(login_id vs nickname).

---

## N-048. 유니크 사전확인은 정규화한 값으로, 그리고 엔티티를 바꾸기 *전에* 한다 — JPA auto-flush가 미영속 자기자신을 오탐한다

**한 줄 요약**: "값을 정규화해 저장하는 컬럼(소문자 login_id)"의 중복을 사전 확인할 때 두 가지를 지켜야 한다. ① **정규화한 값으로 조회**해야 한다(`Reader`로 검사하고 `reader`로 저장하면 우회됨). ② **엔티티에 그 값을 채우기 전에** `existsBy...`를 호출해야 한다 — 채운 뒤 조회하면 JPA의 **auto-flush**가 아직 커밋도 안 된 그 엔티티를 DB에 내보내, "이미 존재한다"고 **자기 자신을 오탐**한다.

### 자세한 설명

온보딩에서 login_id 중복을 막는 코드. 순진하게 짜면:

```java
user.assignLoginId(raw);                       // user.loginId = "reader" (in-memory)
if (userRepository.existsByLoginId(user.getLoginId()))   // ❌ 위험
    throw new LoginIdAlreadyExistsException(...);
```

`@Transactional` 안에서 `existsByLoginId`는 **쿼리**다. Hibernate의 기본 `FlushMode.AUTO`는 "쿼리 결과에 영향 줄 수 있는 변경은 쿼리 전에 flush"한다 → 방금 `assignLoginId`로 바꾼 `user`가 DB로 나가고, `existsByLoginId("reader")`가 **바로 그 user**를 발견해 true. 결국 정상 입력인데도 "이미 쓰는 아이디"로 거부된다.

해결 — **검사를 assign 앞에 두고, 정규화는 상태를 안 바꾸는 정적 메서드로 분리**:

```java
String normalized = User.normalizeLoginId(raw);   // 검증+소문자화, 엔티티 안 건드림
if (userRepository.existsByLoginId(normalized))    // user.loginId는 아직 null → 자기 오탐 없음
    throw new LoginIdAlreadyExistsException(normalized);
user.assignLoginId(raw);                           // 이제 확정
```

정규화 로직(소문자화·형식·예약어)을 `assignLoginId`에서 `static String normalizeLoginId`로 빼내 **단일 출처**로 공유한다 — 사전확인용 값과 실제 저장값이 같은 규칙을 타게.

### 일반화 포인트 (면접 답변용)

- **정규화 컬럼의 유니크는 "정규화 후" 비교다**: 대소문자 무시·trim 같은 정규화를 저장 시 하면, 중복 검사도 **같은 정규화를 거친 값**으로 해야 한다. 안 그러면 `Reader`/`reader`가 둘 다 통과해 DB 유니크 제약에서야 터진다(500).
- **auto-flush는 "읽기 전에 쓰기를 내보낸다"**: 트랜잭션 안에서 엔티티를 바꾼 뒤 쿼리하면, 그 변경이 이미 DB에 반영된 것처럼 보인다(아직 커밋 전인데). "내가 방금 만든/바꾼 걸 내 쿼리가 도로 본다"는 함정. → **부작용(엔티티 변경) 전에 조회**하면 깔끔히 피한다.
- **검증과 변이를 분리**(normalize=순수함수 / assign=변이): 순수 정규화 메서드를 따로 두면 "조회용으로 미리 정규화"가 가능해지고, 같은 규칙을 두 곳이 공유한다. 부작용을 미루는 설계가 이런 순서 의존 버그를 원천 차단.
- **3중 방어는 유지**: 사전확인(UX·친절한 에러)이 자기 오탐을 피하더라도, DB 유니크 제약(동시 가입 레이스의 최후 방어, N-019)은 그대로 둔다.

### Q&A 대비

- *"왜 그냥 assign 뒤에 검사하면 안 돼?"* → auto-flush로 미영속 자기자신이 조회돼 정상 입력을 거부한다. 검사는 변이 전에.
- *"flush를 끄면(MANUAL) 되지 않나?"* → 되지만 트랜잭션 전역 flush 모드를 만지는 건 부작용이 크다(다른 쿼리의 일관성). 순서를 바꾸는 국소 해법이 안전하다.
- *"정규화를 서비스에서 또 하면 중복 아닌가?"* → 그래서 도메인의 `normalizeLoginId` **하나**를 서비스(사전확인)와 `assignLoginId`(저장)가 같이 쓴다. 규칙 단일 출처.

### 코드 위치

- `src/main/java/com/booktimer/user/User.java` — `normalizeLoginId`(정적·순수), `assignLoginId`(변이).
- `src/main/java/com/booktimer/user/OnboardingService.java` — `complete`에서 정규화→`existsByLoginId`→`assignLoginId` 순서.

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 사전확인(UX)+DB 제약(무결성) 3중 방어.
- [N-040. mock 단위테스트는 DB 제약을 검증 못 한다](#n-040-mock-단위테스트는-db-제약fk유니크을-검증하지-못한다) — 이런 flush·제약 상호작용은 실제 H2 통합테스트로만 잡힌다.

---

## N-049. 운영 통계는 새 저장 없는 읽기 집계 — Flyway 무변경, 시간창 집계는 Clock 주입으로 결정화

**한 줄 요약**: 운영 대시보드의 통계(가입자·활성·총 독서시간)는 기존 테이블을 `count`/`sum`으로 요약할 뿐이라 **새로 저장하는 게 없다** → Flyway(스키마)를 안 건드린다(N-037). 단 "최근 7일 활성" 같은 **시간창(window) 집계**는 "지금"에 의존하므로, 시스템 시계를 직접 부르지 말고 **`Clock`을 주입**해 윈도 경계를 테스트에서 고정한다(N-010).

### 자세한 설명

관리자 통계 카드 7종(가입자 수·온보딩 완료·최근 7일 활성·책/세션 수·총·평균 독서시간)을 얹으면서 "통계 테이블을 새로 만들어야 하나?" 싶지만 — 아니다. 전부 **이미 있는 행을 다른 각도로 세는 읽기**다:

```java
long totalUsers   = userRepository.countByRole(Role.USER);          // count(*)
long activeUsers  = sessionRepository.countActiveUsersSince(since);  // count(distinct user) where startedAt >= since
long totalSeconds = sessionRepository.sumAllDurationSeconds();       // coalesce(sum(duration), 0)
```

새 저장이 없으니 마이그레이션도 없다. 새 Flyway 버전이 필요한 건 "새로 **저장**할 게 생길 때"(관계·플래그)뿐이고, 통계는 그 반대편(읽기)이다(N-037).

시간창만 주의. "최근 7일 활성"의 cutoff는 "지금"에 달렸다:

```java
Instant since = clock.instant().minus(Duration.ofDays(7));  // ✅ 주입된 Clock
// Instant.now() 직접 호출이면 테스트가 7일 경계 근처 데이터에서 흔들린다
```

테스트는 주입 `Clock` 기준 상대 시각으로 데이터를 심어 경계를 못 박는다 — `now-1d`(활성), `now-30d`(비활성)면 7일 창이 둘을 확실히 가른다.

### 일반화 포인트 (면접 답변용)

- **읽기는 스키마를 안 늘린다**: "보여주기"는 조회 주체(`where`)의 변경일 뿐이다. 통계·목록·드릴다운 전부 읽기 → Flyway 0. 마이그레이션은 "새 저장"의 신호다(N-037).
- **집계는 DB에서 한 방에**: `count(distinct ...)`·`sum(...)`을 쿼리로 내린다. 앱에서 전건 로딩 후 `Set`으로 세거나 루프 합산하면 메모리·N+1. DB가 제일 잘하는 일을 뺏지 말 것.
- **시간 의존은 주입으로 결정화**: `clock.instant()` 한 줄로 "지금"을 외부화하면, 시간창·만료·"오늘" 계산이 테스트에서 고정된다(N-010). `Instant.now()`/`new Date()` 직접 호출은 테스트 불가능 코드.
- **집계엔 빈 데이터 경계가 늘 있다**: 평균 = 총합/인원인데 인원이 0이면 0 나눗셈. `coalesce(sum, 0)`·`totalUsers==0 ? 0 : ...`처럼 0/빈 경계를 항상 먼저 막는다.

### Q&A 대비

- *"통계 기능인데 왜 Flyway를 안 만들어?"* → 새로 저장하는 게 없으니까. 기존 테이블을 세는 읽기라 스키마 무관(N-037).
- *"활성 사용자 테스트가 가끔 깨질 수 있나?"* → `Instant.now()`를 쓰면 7일 경계 근처에서 깨진다. `Clock` 주입 + 상대 시각이면 결정적.
- *"distinct를 앱에서 Set으로 세도 되지 않나?"* → 전건 로딩은 메모리·N+1 비용. `count(distinct user)`로 DB에 맡겨라.

### 코드 위치

- `src/main/java/com/booktimer/admin/AdminStatsService.java` — `summary()`, cutoff = `clock.instant().minus(7d)`, 0명 평균 가드.
- `src/main/java/com/booktimer/session/ReadingSessionRepository.java` — `countActiveUsersSince`(distinct), `sumAllDurationSeconds`(coalesce).
- `src/main/java/com/booktimer/user/UserRepository.java` — `countByRole`·`countByRoleAndOnboarded`.

### 관련 노트

- [N-037. SNS 확장해도 독서 데이터는 새로 저장 안 함](#n-037-...) — "읽기=DB 안 건드림, 새 저장=Flyway 새 버전"의 원본 규칙.
- [N-010. 테스트 가능한 시간 — Clock 주입](#n-010-테스트-가능한-시간--clock-주입--절대-시점-vs-유저-타임존-오늘) — 시간 의존을 주입으로 결정화.

---

## N-050. 운영 화면 PII 최소노출은 층이다 — 안 싣기가 가리기보다 우선, 마스킹은 표시일 뿐 비노출이 아니다

**한 줄 요약**: 운영자만 보는 화면이라도 개인정보(PII)는 최소로 노출한다. 도구는 **서로 다른 층**에 있다: ① **DTO에서 아예 제외**(비밀번호 해시 — 클라이언트로 안 내려감 = 진짜 비노출), ② **표시 마스킹**(`g***@gmail.com` — 가공해 보여줌), ③ **검색 표면 축소**(email로는 검색 못 하게). 핵심 함정: **마스킹은 "표시"지 "비노출"이 아니다** — 클릭 토글로 원문을 보여주려면 원문이 응답 DOM에 실리므로, 가린 건 CSS/JS뿐이다. 진짜 비노출은 서버가 **아예 안 내려야** 한다.

### 자세한 설명

관리자 사용자 목록·드릴다운은 PII가 한가득(email·가입 메타·독서 기록)이다. "어차피 ADMIN만 본다"로 풀면 안 되고, **닿은 사람에게도 최소만** 준다(심층 방어). 층별로 다른 도구를 쓴다:

- **비밀번호 해시 — DTO에서 제외**: `record AdminUserRow`/`AdminUserDetail`에 해시 필드를 아예 안 둔다. 엔티티→DTO 매핑 단계에서 누락되니 네트워크로 **나가지 않는다**. 표시할 게 아니면 가릴 필요조차 없이 빼는 게 정답.
- **email — 표시 마스킹**: `EmailMask.mask`로 local part 첫 글자만 남기고 `g***@gmail.com`. 도메인은 남겨(가입 분포 파악) 식별엔 부족하게. 단 "클릭 시 전체"를 주려면 **원문도 함께 내려야** 하고, 그 순간 "가렸지만 실렸다"가 된다(`<details>` 토글은 CSS 표시 전환일 뿐). ADMIN 전용이라 1차는 허용하되, 더 엄격히 하려면 원문은 별도 인증 fetch로 미뤄야 한다(과설계라 보류 — 설계 메모에 한계로 명시).
- **검색 — 키 축소**: 목록 검색은 login_id/nickname만 부분일치, **email은 검색 키에서 제외**. 노출·열거 표면을 줄인다.

### 일반화 포인트 (면접 답변용)

- **"최소 노출"은 단일 스위치가 아니라 층이다**: 저장(그대로 둠) ≠ 전송(DTO에서 제외) ≠ 표시(마스킹) ≠ 검색(키 제한). 각 층에서 **따로** 줄인다 — 한 곳만 손대면 다른 층으로 샌다.
- **안 싣기 > 가리기**: 클라이언트로 내려간 값은 가려도 본 것이다(DOM·네트워크 탭·캐시). 진짜 비밀은 **서버 경계에서 멈춘다**. 가릴지 뺄지는 "이걸 보여줄 일이 있나"로 가른다 — 없으면(비번 해시) DTO에서 빼고, 가끔 필요하면(email) 마스킹+토글.
- **인가 ≠ 데이터 최소화**: `hasRole("ADMIN")`로 "누가 닿느냐"를 막는 것과, 닿은 사람에게 "얼마나 보여주냐"는 **별개 방어**다. 둘을 겹쳐야 심층 방어(N-019의 층위 사고의 연장).
- **마스킹 규칙은 식별불가 + 유용성 균형**: 무엇을 남기고 가릴지에 의도가 있다 — local은 가리고 도메인은 남긴다(분포는 유용, 개인 식별엔 부족). 비정상 입력(null·'@' 없음)은 `***` 안전값.

### Q&A 대비

- *"ADMIN만 보는데 왜 마스킹까지?"* → 심층 방어 + 어깨너머·로그·스크린샷 노출을 줄인다. 인가는 "누가 닿느냐", 최소화는 "닿은 뒤 얼마나 보느냐" — 다른 축.
- *"마스킹했으니 안전한가?"* → 토글로 원문을 내리면 DOM에 원문이 있다 = **표시만** 가린 것. 진짜 비노출이면 서버가 안 내려야 한다.
- *"비번은 왜 마스킹이 아니라 제외?"* → 운영자도 비밀번호 해시를 볼 이유가 0이다. 표시할 게 아니면 DTO에서 빼는 게 정답 — 가릴 필요조차 없다.

### 코드 위치

- `src/main/java/com/booktimer/admin/EmailMask.java` — local 첫 글자만, 도메인 보존, 비정상은 `***`.
- `src/main/java/com/booktimer/admin/AdminUserRow.java`·`AdminUserDetail.java` — record에 passwordHash 필드 없음(전송 제외).
- `src/main/java/com/booktimer/admin/AdminUserService.java` — `listUsers`가 login_id/nickname만 검색(email 제외).
- `claude-docs/admin-data-lookup-design.md` §3 — "클릭 시 전체는 DOM에 원문이 실린다" 한계 명시.

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 층위(심층) 방어 사고의 원본.
- [N-037. SNS 확장 시 새로 저장할 건 관계·공개범위뿐](#n-037-...) — "보여주기"의 보안 경계는 조회 주체·공개범위.

---

## N-051. 상태 의존 불변식은 단순 NOT NULL이 아니라 조건부 CHECK로 — 생성 순서(지연 채움)와 충돌 없이 무결성을 박는다

**한 줄 요약**: "이 컬럼은 항상 값이 있어야 한다"를 무심코 `NOT NULL`로 박으면, **그 값을 나중에 채우는 생성 경로**와 충돌한다. login_id를 NOT NULL로 만들려다, OAuth 사용자는 row를 **먼저 INSERT**하고 온보딩에서 login_id를 정한다는 걸 발견했다(그 창의 null은 정상). 진짜 박고 싶은 규칙은 "항상"이 아니라 **"어떤 상태가 되면"** — `onboarded = true ⟹ login_id IS NOT NULL`. 이런 **상태 의존(부분 함수 종속) 불변식**은 `NOT NULL`이 아니라 **조건부 CHECK 제약**(`check (onboarded = false or login_id is not null)`)으로 표현한다.

### 자세한 설명

login_id 도입의 마지막 단계는 "모든 정식 계정은 login_id가 있다"를 DB로 보장하는 것이었다. 직관은 `alter ... modify login_id ... not null`. 그런데 생성 경로가 둘이고 **채우는 시점이 다르다**:

- **로컬 가입**: `register(...)`가 가입 시점에 login_id를 확정 → INSERT 시 이미 채워짐.
- **OAuth 가입**: `provision → registerOAuth`가 **login_id=null인 row를 먼저 INSERT**하고, 사용자가 **온보딩에서** login_id를 고른다. login_id는 **불변**이라(N-047) X처럼 가입 시 자동 핸들을 박아 NOT NULL을 만족시키는 것도 설계와 충돌한다.

즉 "항상 NOT NULL"은 **거짓 불변식**이었다 — OAuth의 프로비저닝~온보딩 사이엔 null이 정상이다. 실제로 보장하고 싶은 건 **온보딩이 끝난(=정식 계정) 뒤엔 반드시 있다**는 조건부 규칙. 이건 관계형 용어로 **부분 함수 종속**(어떤 행에만 적용되는 NOT NULL)이고, 표준 도구가 **CHECK 제약**이다:

```sql
alter table users add constraint ck_users_login_id_when_onboarded
    check (onboarded = false or login_id is not null);   -- onboarded ⟹ login_id IS NOT NULL
```

`A ⟹ B`는 불 논리로 `not A or B` = `onboarded = false or login_id is not null`. onboarded가 false면(아직 정식 아님) login_id가 null이어도 통과, true면 login_id가 반드시 있어야 통과.

### 일반화 포인트 (면접 답변용)

- **"항상 있어야 한다"인지 "언제부터 있어야 한다"인지 구분**: 전자는 `NOT NULL`, 후자는 **조건부 CHECK**. 생성과 동시에 못 채우는 값(지연 채움·다단계 온보딩·외부 콜백 후 확정)은 거의 항상 후자다.
- **제약은 데이터 생성 *순서*를 안다**: 컬럼 제약을 정하기 전에 "이 값을 누가, 언제 채우나"를 모든 경로에서 따져야 한다. 한 경로라도 "나중에 채움"이면 무조건 NOT NULL은 그 경로의 INSERT를 깬다.
- **조건부 CHECK = 상태 머신의 불변식을 DB에 박기**: "pending 상태엔 비어도 되고 active가 되면 필수"는 흔한 패턴(주문 결제완료⟹결제수단, 발행글⟹본문). 앱 검증에만 두지 말고 DB CHECK로 최후 방어선을 친다(N-019의 연장).
- **불변(immutable) 제약은 NOT NULL 타이밍을 더 좁힌다**: 값이 불변이면 "나중에 자동값 박고 나중에 교체"가 불가하므로, 지연 채움 경로는 반드시 null 창을 갖는다 → 단순 NOT NULL이 원천 봉쇄된다.

### Q&A 대비

- *"왜 login_id를 NOT NULL로 안 했나?"* → OAuth는 row를 먼저 만들고 온보딩에서 login_id를 정한다. 그 사이 null이 정상이라 NOT NULL은 OAuth 가입 INSERT를 깬다. 진짜 규칙은 "온보딩 끝나면 필수"라 조건부 CHECK로 박았다.
- *"CHECK가 MySQL에서 진짜 강제되나?"* → MySQL 8.0.16+부터 강제(이전엔 파싱만 하고 무시). H2도 지원. 버전 확인이 전제다.
- *"앱에서 검증하면 되지 왜 DB까지?"* → 앱 검증은 첫 방어선(UX), DB 제약은 최후 방어선(무결성). 버그·직접 SQL·동시성으로 앱을 우회해도 DB가 막는다(N-019).
- *"테스트는 어떻게?"* → 메인 스위트는 Hibernate가 스키마를 생성해 CHECK가 없으니(엔티티에 안 박음) 무영향. Flyway 스키마를 격리 H2에 적용하는 전용 테스트(FlywayMigrationTest)가 3경계(거부/허용 두 종)를 검증한다 — DB 제약은 mock으로 못 잡으니 실제 스키마 통합테스트가 필수(N-040).

### 코드 위치

- `src/main/resources/db/migration/V15__user_login_id_when_onboarded_check.sql` — `ck_users_login_id_when_onboarded` CHECK.
- `src/test/java/com/booktimer/migration/FlywayMigrationTest.java` — 온보딩+null 거부 / 온보딩전 null 허용 / 정상 허용 3경계.
- `src/main/java/com/booktimer/user/OAuthUserProvisioningService.java`·`UserRegistrationService.registerOAuth` — login_id 없이 먼저 INSERT하는 지연 채움 경로.
- `claude-docs/login-id-design.md` §7 PR-5 — 충돌 발견·해법 기록.

### 관련 노트

- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 앱(UX)+DB(무결성) 다층 방어. CHECK도 같은 최후 방어선.
- [N-039. 제약을 뒤늦게 강화하려면 기존 위반 데이터부터 백필](#n-039-제약을-뒤늦게-강화하려면-기존-위반-데이터부터-백필한다-backfill) — 제약 강화의 *순서*(여기선 wipe 그린필드라 위반 행 0).
- [N-040. mock 단위테스트는 DB 제약을 검증하지 못한다](#n-040-mock-단위테스트는-db-제약fk유니크을-검증하지-못한다) — CHECK 검증은 실제 스키마 통합테스트로.
- [N-047. 불변 식별자는 대리키 위에서 도메인 규칙으로 강제](#n-047-불변-식별자는-대리키surrogate-pk-위에서-도메인-규칙으로-강제한다--db가-막아주지-않는다) — 불변성이 지연 채움 null 창을 강제하는 이유.

---

## N-052. 계정 열거(account enumeration) — 존재 여부를 응답으로 흘리지 않기, 그리고 가입이 까다로운 이유

**한 줄 요약**: 가입·로그인·비번찾기에서 "이미 있는 계정"과 "없는 계정"에 **다른 응답**을 주면, 공격자가 어떤 이메일/아이디가 등록됐는지 *열거*할 수 있다. 저항의 핵심은 **존재 여부와 무관하게 동일 응답**. 그런데 가입은 유니크를 강제해야 해서 특히 까다롭고, 식별자가 **공개냐 비공개냐**로 해법이 갈린다.

### 자세한 설명

**계정 열거란**: 시스템 응답의 차이(에러 메시지 / 상태코드 / **응답 시간**)로 특정 식별자가 등록돼 있는지 알아내는 것. 그 자체가 피해는 아니지만, **표적형 무차별 대입**(존재 확인된 계정만 공략)·피싱·프라이버시 침해의 **선행 단계**다.

**흔한 누출 지점**:
- 로그인: "비밀번호가 틀렸습니다" vs "그런 계정 없습니다" → 어느 쪽인지로 존재 확인.
- 비밀번호 찾기: "메일 보냄" vs "등록되지 않은 이메일".
- **가입: "이미 가입된 이메일입니다"** ← BookTimer가 갖고 있던 누출.

**BookTimer 맥락 — 식별자가 공개냐 비공개냐가 갈림길**: login_id 도입(N-046)으로 식별자가 3분할됐다 — `login_id`(공개 @핸들), `email`(비공개 연락/복구), `nickname`(표시). 가입은 login_id·email **둘 다 유니크**를 강제하는데, 노출 정책은 정반대다:
- **login_id = 공개 핸들** — `/u/{handle}`로 이미 조회 가능하다. "사용 중" 노출이 **무해**하고, 오히려 **UX상 필요**(다른 아이디를 골라야 하니까). 열거가 의미 없다 → 그대로 필드 에러로 알린다.
- **email = 비공개 속성** — "이미 가입된 이메일"이라고 알려주는 순간 그게 곧 **열거**다. 이게 진짜 누출.

**왜 가입이 특히 까다로운가**: 로그인·비번찾기는 그냥 "동일한 모호 응답"을 주면 끝이지만, **가입은 유니크를 강제**해야 한다 — 그런데 거부하면 곧 "존재한다"는 누출이다. 정석 해법은 **"조용히 수락하고 메일로 통지"**(이미 계정이 있으면 '로그인하세요' 메일을 보냄) — 공격자에겐 항상 같은 "메일 확인하세요" 응답이라 구분 불가. 단 이건 **메일 발송 인프라가 전제**다.

**BookTimer의 선택(인프라 제약)**: 메일 발송 인프라가 없어 통지를 못 한다 → 차선으로 **가입 성공과 동일한 응답(`redirect:/login?registered`)으로 흡수**(계정은 안 만듦). 응답만 같아 이메일 존재 여부를 응답으로 구분할 수 없다. 검사 순서도 **login_id 먼저(공개·실행가능한 에러)·email 마지막(흡수)**으로 둬, 둘 다 충돌해도 사용자는 고칠 수 있는 login_id 에러를 받는다.
- **트레이드오프**: 잊고 재가입한 *정직한* 사용자는 "성공"처럼 보인 뒤 로그인 단계에서야 알게 된다. 이게 **열거 저항의 표준 비용**이다(메일 통지가 있으면 더 친절히 처리 가능).

### 일반화 포인트 (면접 답변용)

- **열거 저항 = 존재/부재에 식별 불가능한 동일 응답** — 메시지·상태코드·**타이밍** 셋 다 같아야 한다. 존재할 때만 BCrypt를 돌리면 응답이 느려져 **타이밍 사이드채널**로 새므로, 없는 계정에도 더미 해시를 검증해 시간을 평탄화한다(N-025의 BCrypt 비용과 연결).
- **무엇이 비공개냐가 무엇을 숨길지를 정한다** — 공개 식별자(프로필 URL로 이미 노출된 핸들)는 열거를 막을 이유가 없다. 비공개 속성(연락 이메일)만 숨긴다.
- **가입의 열거 저항은 통지 채널(메일 인프라)에 의존** — "조용히 수락"의 완성형은 "대신 메일로 알림"이 있어야 성립한다. 인프라 제약이 보안 설계의 도달 가능 범위를 좌우한다.
- **위협 모델 대비 비용** — 친구 한정·소규모면 완벽한 열거 저항이 항상 ROI는 아니다. "어디까지 막을지"는 노출 부담과 사용자 혼란의 저울질.
- **(관련 패턴) 비밀번호 없는 파괴적 동작의 재확인** — OAuth 계정은 비번 재인증이 없어 되돌릴 수 없는 동작(탈퇴)이 무방비가 된다. 본인 **공개 @핸들을 타이핑**하게 하는 서버사이드 게이트로 막는다(GitHub "저장소 이름 입력" 패턴). `confirm()` 같은 클라이언트 확인은 우회 가능하므로 **검증은 서버에서**.

### Q&A 대비

- *"왜 login_id 중복은 알려주고 email 중복은 안 알려주나?"* → login_id는 공개 핸들이라 이미 조회 가능(열거 무의미)이고 다른 걸 골라야 하니 **알려야** 한다. email은 비공개라 "있다"는 사실 자체가 누출이라 **흡수**한다.
- *"그럼 잊고 재가입한 사용자는?"* → 성공처럼 보이고 로그인 단계에서 실패해 알게 된다. 열거 저항의 표준 비용. 메일 발송이 있으면 '이미 계정 있음' 통지로 더 친절히 가능.
- *"타이밍으로도 열거되나?"* → 그렇다. 존재할 때만 해시 검증을 돌리면 응답이 느려 구분된다 → 없는 경로에도 더미 해시 검증으로 시간 평탄화.
- *"로그인은 안 새나?"* → "아이디 또는 비밀번호가 올바르지 않습니다" **한 메시지**로 어느 쪽이 틀렸는지 구분 안 한다(이미 그렇게 둠).

### 코드 위치

- `src/main/java/com/booktimer/user/UserRegistrationService.java` — `register`에서 **login_id(형식→유니크) 먼저·email 마지막** 검사 순서.
- `src/main/java/com/booktimer/web/SignupController.java` — `EmailAlreadyExistsException`·`DataIntegrityViolationException`을 `redirect:/login?registered`로 흡수(가입 성공과 동일 응답).
- `src/main/java/com/booktimer/user/AccountService.java` — `deleteSocialAccount(email, confirmHandle)`(비번 없는 탈퇴의 @핸들 타이핑 게이트), `AccountDeletionConfirmationException`.
- `claude-docs/troubleshooting.md` **T-012** — 옛 동작(이메일 필드 에러)→열거 완화로 정정한 기록.

### 관련 노트

- [N-046. 식별자 3분할 — 공개 핸들을 뭘로 두느냐는 보안 동치](#n-046-식별자-3분할--로그인공개핸들표시이름은-각자-다른-축이고-공개-핸들을-뭘로-두느냐는-보안-동치다) — **무엇이 공개/비공개냐**가 무엇을 숨길지(열거 저항 대상)를 정한다.
- [N-026. OAuth find-or-create(email_verified) + Spring Security가 안 막는 것(brute-force)](#n-026-oauth-find-or-create의-함정email_verified--spring-security가-막아주지-않는-것brute-force) — 열거는 **표적형 brute-force의 선행 단계**.
- [N-025. 로그인 지연의 범인은 보통 DB가 아니라 BCrypt × 작은 vCPU](#n-025-로그인-지연의-범인은-보통-db가-아니라-bcrypt--작은-vcpu) — 존재 시에만 도는 해시가 **타이밍 열거**로 새는 연결.
- [N-019. DB 유니크 제약은 무결성의 마지막 방어선](#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다) — 유니크는 **여전히 강제**한다(노출만 안 할 뿐), 앱+DB 다층.

---

## N-053. OAuth 자동 계정 연결(find-or-create)의 양방향 위협 — verified email은 한 방향만 막고, 미검증 로컬 가입이 pre-hijacking을 연다

**한 줄 요약**: 소셜 로그인이 "검증된 이메일로 기존 계정을 찾아 붙이는"(find-or-create) 자동 연결은 **두 방향**으로 작동한다. `email_verified` 체크는 그중 *역방향*(가짜 소셜이 기존 로컬 계정을 탈취)만 막는다. *정방향*(공격자가 **검증 안 된 로컬 가입**으로 피해자 이메일에 계정을 미리 심어두고, 나중에 진짜 주인이 소셜 로그인하며 그 계정에 올라타는 **account pre-hijacking**)은 **로컬 가입 측 이메일 미검증**이라는 다른 끝에서 열린다. 자동 연결이 안전하려면 **양쪽 진입 경로의 이메일 신뢰 수준이 같아야** 한다 — 한쪽만 verified면 비대칭이 갭을 만든다.

### 자세한 설명

BookTimer는 한 사람이 같은 이메일로 두 경로(로컬 가입 / 구글 로그인)를 탈 수 있다. 두 방향의 동작을 코드로 보면 **비대칭**이다:

| 방향 | 동작 | 근거 |
|---|---|---|
| 구글 가입 → 같은 이메일로 **로컬 가입** | **거부**(계정 안 생김, 열거 완화로 성공처럼 흡수) | `existsByEmail` 유니크 강제 (N-052) |
| 로컬 가입 → 그 이메일로 **구글 로그인** | **허용**(기존 로컬 계정에 그대로 로그인) | `provision`의 find-or-create — `findByEmail(email).orElseGet(...)` |

후자가 곧 **자동 계정 연결**이다. 이건 비정상이 아니라 표준 동작이다("Sign in with Google"이 기존 계정에 붙는 그 경험). 신뢰 근거는 **provider가 이메일 소유를 보증**(`email_verified == true`)했다는 것 — `provision`은 이게 아니면 거부한다(N-026).

**그런데 자동 연결은 두 방향 모두에서 악용될 수 있다**:

1. **역방향 — 가짜 소셜이 기존 로컬을 탈취**: 공격자가 피해자 이메일을 주장하는 소셜 신원으로 로그인 → find-or-create가 기존 로컬 계정을 찾아 붙여줌 → 피해자 계정 탈취. **이건 `email_verified` 체크가 막는다**(미검증 이메일 주장은 거부). N-026이 닫은 구멍.

2. **정방향 — 미검증 로컬 가입이 여는 pre-hijacking**:
   - ① 공격자가 **피해자의 이메일로 로컬 계정을 미리 만든다**(비밀번호는 공격자가 앎). BookTimer 로컬 가입은 **이메일 검증이 없어** 가능하다.
   - ② 나중에 피해자가 그 이메일로 **구글 로그인** → find-or-create가 **공격자가 만든 로컬 계정을 찾아** 그곳에 로그인시킨다.
   - ③ 피해자는 공격자 소유 계정에서 활동하고, 공격자도 아는 비번으로 같이 들어와 데이터를 본다.
   - **`email_verified`는 이걸 못 막는다** — OAuth 쪽 이메일은 진짜 검증됐다. 문제는 **반대편(로컬 가입)이 검증을 안 했다**는 것.

핵심: 자동 연결은 "이메일 소유자 == 계정 주인"을 전제하는데, 그 전제는 **계정이 만들어지는 모든 경로**가 이메일을 검증해야 성립한다. OAuth만 verified고 로컬이 unverified면 전제가 한쪽에서 깨진다.

**근본 처방 — 가입 시 이메일 인증으로 양쪽 신뢰 수준을 맞춘다**: 로컬 가입이 이메일 소유 증명(인증 코드/링크)을 요구하면, 공격자가 피해자 이메일로 계정을 미리 못 만든다 → find-or-create가 찾을 수 있는 건 "정당하게 검증된 본인 계정"뿐 → 자동 연결의 전제가 양방향 모두 참이 된다.

대안과 트레이드오프:
- **가입 이메일 인증**(정석) — 미검증 계정 자체를 없앤다. **메일 발송 인프라 전제**(N-052의 silent-success 통지와 같은 선결조건).
- **자동 연결 끄기** — OAuth가 기존 로컬 이메일을 만나면 거부하고 "이 이메일은 비번 로그인으로" 안내. UX 마찰↑.
- **provisional 표식** — 미검증 로컬 계정이면 OAuth 연결 시 소유권 재증명 요구. 상태 머신 복잡도↑.

BookTimer는 메일 인프라가 없어 지금은 **보류 박스**(메일 붙이는 날 N-052 통지와 함께 닫을 갭)다. 친구 한정·소규모라 위협 모델상 ROI가 낮은 것도 보류 근거.

### 일반화 포인트 (면접 답변용)

- **자동 계정 연결의 안전성은 "가장 약한 진입 경로의 이메일 신뢰 수준"으로 결정된다** — 한 경로라도 미검증이면 그 경로가 pre-hijacking 입구가 된다. 방어를 한 곳(OAuth)에만 두고 다른 곳(로컬 가입)을 비우면 비대칭 갭이 생긴다.
- **같은 메커니즘(find-or-create)이 두 방향으로 악용될 수 있다** — 한 방향만 막은 방어(`email_verified`)를 "다 막았다"로 착각하기 쉽다. 위협을 *방향별로* 분해해야 빈칸이 보인다.
- **pre-hijacking은 "계정 생성 시점"과 "계정 장악 시점"이 분리된 공격** — 공격자가 먼저 씨를 심고(미검증 가입), 피해자의 정상 행동(소셜 로그인)이 방아쇠가 된다. 그래서 "지금 로그인이 안전한가"만 보면 안 잡히고, "이 계정이 *어떻게 생겼나*"의 출처를 따져야 한다.
- **인프라 제약이 보안 도달 범위를 정한다(반복 주제)** — 이메일 검증·열거 통지 둘 다 메일 발송이 전제. 메일 인프라 부재가 여러 보안 결정의 공통 천장이다(N-052와 동일 구조).

### Q&A 대비

- *"②(소셜이 기존 로컬에 로그인)는 버그 아닌가?"* → 아니다. verified email 기반 자동 연결은 표준 동작이다. 버그는 그 동작이 아니라 **반대편 로컬 가입이 이메일을 검증 안 한다**는 점 — 자동 연결의 전제가 한쪽에서 깨진다.
- *"`email_verified` 체크가 있는데 왜 아직 위험한가?"* → 그건 *역방향*(가짜 소셜→기존 로컬 탈취)만 막는다. *정방향*(미검증 로컬을 진짜 소셜이 끌어안음)은 OAuth 이메일이 진짜 검증됐으니 그 체크에 안 걸린다. 구멍은 로컬 가입 쪽에 있다.
- *"이메일 인증을 붙이면 정확히 뭐가 닫히나?"* → 공격자가 피해자 이메일로 계정을 *미리 못 만든다*. 그러면 find-or-create가 찾는 계정은 항상 검증된 본인 것 → 양방향 전제가 참.
- *"왜 지금 안 고치나?"* → 이메일 인증은 메일 발송 인프라가 전제(현재 부재). N-052의 열거 통지와 같은 선결조건이라 함께 보류. 친구 한정 규모라 위협 ROI도 낮다.

### 코드 위치

- `src/main/java/com/booktimer/user/OAuthUserProvisioningService.java` — `provision`의 find-or-create(`findByEmail(...).orElseGet(registerOAuth)`)와 `email_verified` 거부(역방향만 방어).
- `src/main/java/com/booktimer/user/UserRegistrationService.java` — `register`(로컬 가입)에 **이메일 검증 단계 없음** = 정방향 갭의 위치.
- `src/main/java/com/booktimer/security/BookTimerOidcUserService.java` — OIDC 어댑터가 `provision`을 호출하는 진입점.

### 관련 노트

- [N-026. OAuth find-or-create의 함정(email_verified)](#n-026-oauth-find-or-create의-함정email_verified--spring-security가-막아주지-않는-것brute-force) — **역방향**(가짜 소셜→기존 계정 탈취)을 닫은 노트. 이 노트는 그 방어가 *못 닫는 정방향*을 드러낸다.
- [N-052. 계정 열거 — 가입이 까다로운 이유](#n-052-계정-열거account-enumeration--존재-여부를-응답으로-흘리지-않기-그리고-가입이-까다로운-이유) — 같은 **메일 인프라 부재**가 천장. 가입 측 이메일 미검증이 열거 완화(흡수)와 pre-hijacking 갭을 동시에 만든다.
- [N-046. 식별자 3분할 — 무엇이 공개/비공개냐](#n-046-식별자-3분할--로그인공개핸들표시이름은-각자-다른-축이고-공개-핸들을-뭘로-두느냐는-보안-동치다) — email을 신원(연결 키)으로 쓰는 설계 선택이 이 위협의 토대.

---

## N-054. 외부 API가 채워주는 식별자라도 집계 키로 쓰려면 적재 시점 정규화가 필요하다 — 빈 값·표기 차이가 group-by를 오염시킨다

**한 줄 요약**: 외부 데이터 소스(검색 API 등)가 내려주는 식별자를 그대로 저장해 `group by`/`distinct` 집계 키로 쓰면, ① 빈 값(`""`)이 *서로 다른* 레코드를 하나로 뭉치고 ② 표기 차이(하이픈·공백)가 *같은* 대상을 쪼갠다. "제공자가 채워주니 깨끗하겠지"는 함정 — 키로 쓰는 값은 **적재 단일 통로에서** 정규화해 한 표기로 모아야 한다.

### 자세한 설명

BookTimer의 책 동일성 키는 `book.isbn13`이다. 이게 단순 표시용이 아니라 **인기 카운트의 group-by 키**로 쓰인다 — `select b.isbn13 ... where b.isbn13 in :isbns group by b.isbn13`. 즉 "같은 isbn13 = 같은 책"이라는 전제로 사람 수를 센다.

문제는 이 값을 알라딘 검색 응답에서 **가공 없이 그대로** 저장하고 있었다는 것:

- **빈 값 오염(서로 다른 게 뭉침)**: 알라딘은 ISBN13이 없는 책(구간, 일부 절판본 등)에 `""`(빈 문자열)를 내려줄 수 있다. 이걸 그대로 저장하면 ISBN 없는 *서로 다른* 책 수십 권이 전부 `isbn13=""` 한 그룹이 되어 "이 책 N명이 읽음"이 엉뚱하게 합산된다.
- **표기 차이 오염(같은 게 쪼개짐)**: `978-89-...`(하이픈)와 `9788989...`(숫자열)는 *같은 책*인데 group-by 키로는 다른 값이라 카운트가 갈린다.

핵심 통찰: **집계 키의 신뢰도는 "값이 어디서 왔나"가 아니라 "한 대상이 항상 한 표기로 들어오나"에 달렸다.** 외부 제공값도 정규화 없이는 그 불변식이 안 선다.

처방은 두 층:
1. **적재 시점 정규화(앞으로 들어올 것)** — `Isbn.normalize`(하이픈·공백 제거 후 빈 값이면 `null`)를 **`Book` 생성자 단일 통로**에 박았다. 검색 등록·수동 등록·미래 경로가 전부 이 생성자를 지나므로 누락이 구조적으로 불가능하다(choke point). 정규화 로직을 순수 정적 메서드로 빼 단위 테스트로 경계값을 고정.
2. **백필(이미 들어온 것)** — 적재 정규화는 *이후* 데이터만 고친다. 기존 행은 여전히 오염돼 있으므로 마이그레이션(`V16`)으로 같은 규칙을 한 번 돌린다(`nullif(trim(replace(replace(isbn13,'-',''),' ','')),'')`). 이건 N-039(제약 강화 전 기존 위반 데이터 백필)와 같은 "앞으로 + 과거" 두 축 처리다.

빈 값을 `null`로 모으는 게 왜 중요한가 — `null`은 group-by에서 자연 제외되고(`in :isbns`에 NULL은 매칭 안 됨, 서비스도 blank 키 선제외), "ISBN 미상"끼리 가짜로 합쳐지지 않는다. `""`는 *값*이라 한 그룹이 되지만 `null`은 *부재*라 집계에서 빠진다 — 이 의미 차이가 정합성을 가른다.

의식적으로 **하지 않은 것**: ISBN10→13 변환과 개정판/세트 동일성(다른 ISBN인데 같은 작품). 전자는 현재 적재 경로(알라딘 `isbn13`만 받음)에 ISBN10이 들어올 길이 없어 노출이 없고, 후자는 정규화가 아니라 work-레벨 dedup(fuzzy 매칭)이라 별도 과제다. "정규화"와 "동일성 추론"을 섞지 않는 게 범위를 작게 유지하는 선.

### 일반화 포인트

- 외부 소스(써드파티 API, 업로드 CSV, 사용자 입력)에서 온 값을 **집계·조인·유니크 키**로 쓸 거면, 저장 직전 정규화가 사실상 필수다. 표시용 컬럼은 원본 보존이 나을 수 있지만 **키는 한 표기**여야 한다.
- 정규화는 **단일 적재 통로**(생성자/팩토리/서비스 한 곳)에 두어 경로 추가 시 자동 적용되게 한다 — 호출부마다 깜빡하면 새 오염 경로가 생긴다.
- 부재는 `""`가 아니라 `null`로. 빈 문자열은 "같은 빈 값"으로 뭉치는 함정이 있고, `null`은 집계에서 자연 제외된다.
- 정규화를 켜면 항상 **백필**을 함께 생각하라 — 코드는 미래만, 마이그레이션이 과거를 맡는다(N-039).

### Q&A 대비

- **Q. isbn13은 알라딘이 주는 정형 데이터인데 왜 정규화가 필요한가?** → 제공자가 채워줘도 (a) ISBN 없는 책엔 `""`를 주고 (b) 하이픈 포함 여부가 일정치 않을 수 있다. 표시용이면 무해하지만 우린 이걸 **group-by 키**로 써서 "같은 책"을 판정하므로, 한 책이 한 표기로 들어온다는 불변식이 깨지면 카운트가 직접 틀어진다.
- **Q. 왜 빈 값을 `null`로? `""` 그대로 두면 안 되나?** → `""`는 *값*이라 group-by에서 한 그룹이 되어 ISBN 없는 서로 다른 책이 합쳐진다. `null`은 *부재*라 집계에서 빠진다. 의미가 다르다.
- **Q. 정규화를 어디에 두나?** → 적재 단일 통로(여기선 `Book` 생성자). 모든 등록 경로가 통과하므로 미래 경로도 자동 커버. 호출부에 흩으면 새 경로에서 누락된다.
- **Q. 코드만 고치면 끝 아닌가?** → 아니다. 코드는 이후 데이터만 정규화한다. 이미 저장된 오염 행은 마이그레이션 백필로 따로 고쳐야 한다(N-039의 "앞으로 + 과거").
- **Q. ISBN10→13, 개정판/세트는?** → 의식적 보류. ISBN10은 현재 적재 경로에 들어올 길이 없고, 개정판/세트는 정규화가 아니라 동일성 추론(fuzzy)이라 별도 과제. 범위를 작게.

### 코드 위치

- `Isbn.normalize` — `src/main/java/com/booktimer/book/Isbn.java` (순수 정적, 하이픈·공백 제거 후 빈 값→null)
- 적재 통로 — `Book` 생성자에서 `this.isbn13 = Isbn.normalize(isbn13)` (`book/Book.java`)
- 백필 — `src/main/resources/db/migration/V16__book_isbn13_normalize.sql`
- 집계 키 사용처 — `BookRepository.followScopePopularity`/`followScopeReaders` (`b.isbn13 in :isbns ... group by`)
- 테스트 — `IsbnTest`(경계값), `BookTest.register_normalizesIsbn13`(생성자 와이어링)

### 관련

- [N-039](#n-039-제약을-뒤늦게-강화하려면-기존-위반-데이터부터-백필한다-backfill) — 코드는 미래·마이그레이션은 과거(백필). 같은 "앞으로 + 과거" 두 축.
- [N-048](#n-048-유니크-사전확인은-정규화한-값으로-그리고-엔티티를-바꾸기-전에-한다--jpa-auto-flush가-미영속-자기자신을-오탐한다) — 키 비교는 정규화한 값으로(소문자 login_id). 여기선 group-by 키, 거기선 유니크 키 — 같은 "키는 정규화 후 비교" 원칙.
- [N-041](#n-041-외부-검색-api의-필드-한정-옵션은-문서대로-동작하지-않을-수-있다--결과를-신뢰-말고-후필터로-불변식을-강제) — 외부 검색 API 결과를 그대로 신뢰하지 말고 우리 불변식을 강제. 같은 알라딘 응답 다루는 신뢰 경계.
- [N-037](#n-037-sns로-확장해도-도메인-데이터는-새로-저장하지-않는다--새로-필요한-건-관계--공개범위-기존-데이터는-조회-주체만-바뀐다) — 인기 카운트가 "기존 book을 집계만"하는 맥락. 그 집계의 키가 isbn13.

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
| 2026-06-03 | N-032 보강 (같은 함정 두 번째 실전 사고 — SessionStart 훅은 1회성이라 도중 동시 편집 못 잡음 / 공유 트리 `git add -A` 금지·경로 지정 add / "수정 전 무조건"은 pull이 아니라 git 상태 확인, pull=stale 방지(브랜치 생성 시), 동시 편집 방어=worktree / 방어 도구를 위협 모델에 맞추기) |
| 2026-06-03 | N-035 (제휴 수익 모델 — 링크는 계좌가 아니라 식별키(TTBKey)를 싣고 쿠키 귀속, 정산 계좌는 별도 등록·적립금 vs 현금 / clickCount(우리)≠귀속매출(제휴사) / 코드 제품엔 알라딘만 맞음(교보·YES24는 상품 API·직접제휴 부재, 네트워크 경유) / 다나와식 가격비교는 데이터 벽+도서정가제로 새 책 비교가치 소멸→구매처 선택권+중고 비교가 현실) |
| 2026-06-03 | N-017 보강 (BookTimer 실제 판단 — 전환 트리거 0개(API 미안정+htmx로 인터랙션 해소+모바일/포트폴리오 동기 없음) / SEO=제휴 수익 직결이라 SSR이 매출 채널, 순수 CSR은 불리 → 옮긴다면 SSR-가능(Next.js)로 / "SPA=SEO 포기"는 거짓 이분법) |
| 2026-06-03 | N-036 (Safe Browsing "위험한 사이트"는 서버 응답이 아니라 도메인 평판·휴리스틱 판정 — 신규+저평판 TLD(.click)+로그인/OAuth 콜백이 피싱 오탐 유발, 코드 수정 무관 / 해제=Search Console 검토요청, 근본=.com·.app(HTTPS 강제) 이전, T-027) |
| 2026-06-03 | N-037 (SNS 확장해도 독서 데이터는 새로 저장 안 함 — 이미 book/reading_session에 user_id로 있음, 공유=조회 주체(`where user_id`) 변경 / 새로 저장할 건 도메인 데이터가 아니라 ①관계 ②공개범위뿐 → 그 순간 IDOR/공개범위 체크가 보안 경계 / "보여주기"=읽기=DB 안 건드림, "새 저장"=Flyway 새 버전, 1:N은 자식 테이블이 떠안아 users는 안 비대해짐) |
| 2026-06-03 | N-038 (온보딩 게이트는 단일 진입점(/)이면 인터셉터 불필요 — 컨트롤러 한 곳 redirect로 충분, 전역 가드는 허용목록·테스트파손 비용↑ 이득은 엣지뿐 / 자동 시드값 vs 사용자 초기값 분리·초기값 적용 시 lastAccrualDate 리셋(경과분 안 섞이게) / NOT NULL 플래그는 신규=기본값·기존=백필(V6)로 의미 분리 — 기존 사용자 안 휩쓸리게) |
| 2026-06-04 | N-039 (제약을 뒤늦게 강화하려면 기존 위반 데이터부터 백필(backfill) — 순서는 ①백필→②제약, 어기면 기존 위반으로 실패 / 위반 종류는 컬럼 제약 따라(NOT NULL→NULL, 유니크→중복) / 닉네임 유니크화는 중복만 -{id}로(낮은 id 유지) / self-update는 중첩 파생테이블로 MySQL·H2 공통, 신규 H2는 0행 무해 / 부작용: 같은 값 쓰던 테스트 픽스처 파손 T-028) |
| 2026-06-04 | N-040 (mock 단위테스트는 DB 제약(FK·유니크)을 검증 못 함 — 호출/순서만 보고 실제 DB 안 탐, 삭제 누락은 mock 경계 밖이라 초록불 / 삭제 경로(부모 전 자식 정리)는 실제 스키마 통합테스트 1개 표적으로 / mock=행동·통합=상태 역할 분담 N-009, 새 FK 추가 시 삭제 경로 점검, T-029·자매 T-023) |
| 2026-06-04 | N-041 (외부 검색 API의 "필드 한정" 옵션(알라딘 QueryType=Title)은 문서대로 안 동작할 수 있음 — 저자까지 매칭 / 내 전송 증명(폴백·기존 동작)되면 외부 동작 의심 = 소거법 / 노출 전 결과에 불변식 강제(후필터, 정규화 후 contains — 느슨도 빡빡도 아니게) / 트레이드오프: 페이저 과대집계·드문 거짓음성(정밀도↑재현율↓), T-030) |
| 2026-06-04 | N-042 (flex-basis는 width가 아니라 주축(main axis) 크기 — 컨테이너를 row→column으로 바꾸면 상속된 `flex: 1 1 160px`의 160px가 폭→높이로 뒤바뀌어 입력칸이 세로로 길쭉 / 해결은 방향 바뀐 셀렉터에서 `flex: none` 리셋 / CSS 버그는 캐스케이드 상호작용(넓은 셀렉터 잔재)을 봐야 잡힘) |
| 2026-06-04 | N-043 (Rate Limiting — 시간당 요청 횟수 상한으로 남용/과부하/비용 방어, 신고처럼 남용 표적인 쓰기 트리거에 필수 / per-key(사용자·IP·쌍) 선택이 곧 정책 / 고정·슬라이딩 윈도우 vs 토큰 버킷(burst 허용, 실무 표준) / 초과 시 429+Retry-After(403·503과 구분) / 다중 인스턴스면 카운터 공유 저장소 N-029 / N-026 brute-force IP 잠금의 일반화, N-019 다층 방어) |
| 2026-06-04 | N-044 (CSRF 숨김필드(`th:action`)는 세션 기반 토큰 저장소면 렌더 중 세션을 lazy 생성 / 큰 본문으로 응답 버퍼가 커밋된 뒤 하단 폼이 첫 세션 생성을 시도하면 "Cannot create a session after the response has been committed" 500 / 평소엔 앞쪽 폼이 먼저 세션 만들어 가려져 있다가 그 폼이 사라지자 노출 — lazy 생성은 "첫 사용 위치"에 동작이 묶임 / 해결=렌더 전 `CsrfToken#getToken()` 선확정(버퍼 키우기·폼 앞배치는 미봉책) / 응답 커밋은 되돌릴 수 없는 경계, T-033) |
| 2026-06-05 | N-045 (Spring Data에서 "최신 N건"은 Pageable로 limit — JPQL엔 LIMIT 없음(방언 차이를 Pageable이 추상화), `@Query`+`Pageable` 파라미터에 `PageRequest.of(0,1)` 넘김 / `findTop`/`findFirst` 파생 이름 대안과 분담(고정 개수=이름, 가변·프로젝션·명시쿼리=Pageable) / 1건이어도 `List<T>`로 받아 isEmpty 분기 / 측정 드롭다운 "최근 읽은 책" 자동선택에 적용) |
| 2026-06-05 | N-046 (식별자 3분할 — 로그인·내부식별/공개핸들(검색·URL)/표시이름은 직교한 3역할, 한 컬럼이 겸하면 충돌(email=로그인+공개 → 표적 노출) / login_id(불변·유니크·공개핸들)·nickname(가변·중복·표시)·email(비공개·복구)로 분리 / **공개 핸들을 nickname에 두든 login_id에 두든 "핸들 하나는 공개"라 보안 동치** — 진짜 약점은 공개되는 게 *로그인 식별자*냐가 아니라 *연락채널(email)*과 묶였냐 / 불변·가변 분담=X @handle vs display name, login_id PR-2) |
| 2026-06-05 | N-047 (불변 식별자는 대리키(surrogate PK) 위에서 도메인 규칙으로 강제 — "login_id 한번 정하면 불변"은 무결성 아닌 정책이라 DB가 안 막음(unique는 겹침 방지지 변경 방지 아님, UPDATE 자유) / 대리키 PK라 FK가 `id` 참조 → login_id·nickname 바꿔도 FK 무사 = 값 변경의 자유, 자연키 PK면 사실상 불변 강제 / 불변=세터 제거 아니라 "두 번째 호출 거부"(`if field!=null throw ISE`) / 무결성 vs 정책 층 나눔 N-019 연장, login_id PR-2) |
| 2026-06-05 | N-048 (유니크 사전확인은 정규화값으로·변이 *전에* — 정규화 저장 컬럼(소문자 login_id)은 검사도 정규화 후 값으로(Reader/reader 우회 방지) / `@Transactional`서 엔티티 바꾼 뒤 `existsBy` 조회하면 auto-flush가 미커밋 자기자신을 내보내 **자기 오탐**(정상입력 거부) → 조회를 assign 앞에 / 정규화를 `static normalizeLoginId`(순수)로 빼 사전확인·저장이 단일 규칙 공유, 검증·변이 분리가 순서의존 버그 차단 / DB 유니크는 레이스 최후방어로 유지 N-019, login_id PR-2) |
| 2026-06-05 | N-049 (운영 통계 = 새 저장 없는 읽기 집계 → Flyway 무변경, N-037의 "읽기=DB 안 건드림" / 집계는 DB에서 한 방(count·count distinct·coalesce(sum,0)), 앱 전건 로딩·Set 카운트 금지 / 시간창("최근 7일 활성")은 `clock.instant().minus(7d)`로 — Clock 주입해 윈도 경계 테스트 결정화 N-010, Instant.now() 직접호출은 테스트불가 / 평균=총합/인원, 인원 0이면 0(0 나눗셈 가드), 집계엔 빈 경계 상존 / 관리자 통계 카드) |
| 2026-06-05 | N-050 (운영 화면 PII 최소노출은 *층*이다 — 저장(그대로)≠전송(DTO 제외)≠표시(마스킹)≠검색(키 제한), 각 층 따로 줄임 / **안 싣기 > 가리기**: 비번 해시는 record DTO에서 아예 제외(전송 안 됨=진짜 비노출), email은 마스킹(g***@, 도메인 보존) / **마스킹은 표시일 뿐**: 클릭 토글로 원문 주려면 DOM에 원문 실림 = CSS만 가림, 진짜 비노출은 서버가 안 내려야(별도 fetch는 과설계 보류) / email 검색 제외로 열거 표면 축소 / 인가(hasRole)≠데이터 최소화, 심층방어 N-019 / 관리자 데이터 조회) |
| 2026-06-05 | N-052 (계정 열거 = 존재/부재에 다른 응답 주면 식별자 등록 여부가 샘 → 저항=동일 응답(메시지·코드·타이밍) / 공개 식별자(login_id 핸들, /u/{handle}로 이미 노출)는 열거 무의미·UX상 알려야, 비공개(email)만 숨김 / **가입이 까다로운 건 유니크 강제 때문** — 거부=존재 누출, 정석은 "조용히 수락+메일 통지"인데 **메일 인프라 전제** / BookTimer는 메일 없어 가입 성공과 동일 redirect로 흡수(계정 미생성, login_id 먼저·email 마지막 검사), 잊고 재가입은 로그인서 인지(표준 비용) / 타이밍 사이드채널은 더미 해시로 평탄화 N-025 / 관련 패턴: 비번 없는 파괴적 동작(OAuth 탈퇴)은 공개 핸들 타이핑 서버게이트(GitHub repo-name, JS confirm 우회가능) / 무엇이 비공개냐가 숨길 대상 정함 N-046, brute-force 선행 N-026, 유니크는 여전히 강제 N-019) |
| 2026-06-05 | N-053 (OAuth 자동 계정 연결(find-or-create)은 양방향 위협 — `email_verified`는 *역방향*(가짜 소셜→기존 로컬 탈취)만 막고, *정방향*(공격자가 **미검증 로컬 가입**으로 피해자 이메일에 계정 선점→진짜 주인이 소셜 로그인하며 올라탐 = **account pre-hijacking**)은 로컬 가입 측 이메일 미검증이라는 다른 끝에서 열림 / 자동 연결 안전성=가장 약한 진입 경로의 이메일 신뢰 수준, 한쪽만 verified면 비대칭 갭 / 근본 처방=가입 이메일 인증으로 양쪽 신뢰 수준 맞춤(메일 인프라 전제 — N-052와 같은 천장, 보류 박스) / ②(소셜이 기존 로컬에 로그인)는 버그 아닌 표준, 버그는 반대편 로컬 미검증, N-026 역방향 방어가 못 닫는 정방향) |
| 2026-06-05 | N-054 (외부 API가 채워주는 식별자도 group-by/집계 키로 쓰려면 적재 시점 정규화 필수 — ① 빈 값 `""`이 *서로 다른* 레코드를 한 그룹으로 뭉치고 ② 표기 차이(하이픈·공백)가 *같은* 대상을 쪼갬, "제공자가 주니 깨끗"은 함정 / 키 신뢰도는 "값 출처"가 아니라 "한 대상이 항상 한 표기로 들어오나" / 처방 2층: 적재 단일 통로(`Book` 생성자)에서 `Isbn.normalize`(하이픈·공백 제거→빈 값 null) = 미래 경로 자동커버 choke point + 마이그레이션 백필(V16)로 과거 행 = N-039의 앞으로+과거 / 부재는 `""` 아닌 `null`로 — `""`는 값이라 한 그룹, `null`은 부재라 집계 자연제외(`in`에 NULL 미매칭) / ISBN10→13·개정판/세트는 정규화 아닌 동일성 추론이라 의식적 보류 — 범위 작게 / 키는 정규화 후 비교 N-048, 외부결과 불신 N-041, 집계 맥락 N-037, isbn 정규화 PR #164) |
| 2026-06-05 | N-051 (상태 의존 불변식은 단순 NOT NULL이 아니라 조건부 CHECK로 — "항상 값 있음"을 NOT NULL로 박으면 그 값을 *나중에 채우는* 생성 경로와 충돌(OAuth는 row 먼저 INSERT·온보딩에서 login_id 확정→그 창 null 정상, login_id 불변이라 자동 핸들도 불가) / 진짜 규칙은 "항상"이 아니라 "어떤 상태가 되면" = 부분 함수 종속 `onboarded ⟹ login_id IS NOT NULL` → `check (onboarded=false or login_id is not null)`(A⟹B = not A or B) / 컬럼 제약은 생성 *순서*를 안다 — 한 경로라도 지연 채움이면 무조건 NOT NULL이 그 INSERT 깸 / MySQL 8.0.16+·H2 CHECK 강제, 메인 스위트는 Hibernate 생성이라 CHECK 없어 무영향→Flyway 격리 통합테스트로 3경계 검증 N-040 / 상태머신 불변식의 DB화, N-019·N-039·N-047 연장, login_id PR-5) |
