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
