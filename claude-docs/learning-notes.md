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

## 🔄 누적 갱신

| 일자 | 추가 항목 |
|---|---|
| 2026-05-30 | 초안 + N-001 (누적 카운터 일일 리셋: Lazy 계산) |
| 2026-05-31 | N-002 (Gradle toolchain + foojay), N-003 (Spring Boot 4 starter 네이밍) |
| 2026-05-31 | N-004 (Claude Code 훅 워크플로 강제), N-006 (PowerShell 5.1 native stderr 함정) |
| 2026-05-31 | N-007 (Boot 4 autoconfigure/test-slice 모듈화 — 패키지 이동) |
| 2026-06-01 | N-008 (JPA Auditing — 리스너/스위치 분리, @DataJpaTest 슬라이스 함정) |
