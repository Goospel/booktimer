# 트러블슈팅 — 작업 중 만난 함정과 해결법

> "이렇게 하지 마라" 형 실전 트랩 기록. 같은 실수 두 번 반복 방지.
> 개념 이해는 [learning-notes.md](learning-notes.md), 프로젝트 규칙은 [../CLAUDE.md](../CLAUDE.md) 참고.

## 📑 목차

- [T-001. 확인 질문과 실행을 병렬로 보내 의도와 다르게 머지됨](#t-001-확인-질문과-실행을-병렬로-보내-의도와-다르게-머지됨)
- [T-002. 실수 머지된 PR — main force-push로 이력 되돌리기](#t-002-실수-머지된-pr--main-force-push로-이력-되돌리기)
- [T-003. `git show > 파일` 리다이렉트가 한글/UTF-8 파일을 깨뜨림](#t-003-git-show--파일-리다이렉트가-한글utf-8-파일을-깨뜨림)
- [T-004. gradlew stderr가 `$EAP=Stop` 훅을 죽임](#t-004-gradlew-stderr가-eapstop-훅을-죽임)
- [T-005. PR 머지 후 feat 브랜치에서 pull → 군더더기 merge 커밋](#t-005-pr-머지-후-feat-브랜치에서-pull--군더더기-merge-커밋)
- [T-006. Spring Boot 4: `@DataJpaTest` import 경로 변경](#t-006-spring-boot-4-datajpatest-import-경로-변경)
- [T-007. `@DataJpaTest` 슬라이스에서 auditing이 안 돌아 createdAt이 null](#t-007-datajpatest-슬라이스에서-auditing이-안-돌아-createdat이-null)
- [T-008. `redirectedUrlPattern("**/login")`이 상대경로 리다이렉트에 매칭 실패](#t-008-redirectedurlpatternlogin이-상대경로-리다이렉트에-매칭-실패)
- [T-009. Fargate 콜드스타트가 헬스체크 grace를 못 넘겨 ECS 태스크 무한 재시작](#t-009-fargate-콜드스타트가-헬스체크-grace를-못-넘겨-ecs-태스크-무한-재시작)
- [T-010. ECS 안정화 대기 중 수동 update-service 끼워넣어 워크플로 배포가 경쟁·실패](#t-010-ecs-안정화-대기-중-수동-update-service-끼워넣어-워크플로-배포가-경쟁실패)
- [T-011. Fargate 태스크가 SSM 시크릿 pull 실패 — 퍼블릭 IP라도 서브넷에 IGW 라우트 없으면 인터넷 도달 불가](#t-011-fargate-태스크가-ssm-시크릿-pull-실패--퍼블릭-ip라도-서브넷에-igw-라우트-없으면-인터넷-도달-불가)
- [T-012. 가입 시 중복 이메일이 처리 안 된 DataIntegrityViolationException → 500 whitelabel (prod만)](#t-012-가입-시-중복-이메일이-처리-안-된-dataintegrityviolationexception--500-whitelabel-prod만)
- [T-013. `aws logs --max-items 1`이 페이지네이션 토큰 None을 변수에 섞어 "stream does not exist"](#t-013-aws-logs---max-items-1이-페이지네이션-토큰-none을-변수에-섞어-stream-does-not-exist)
- [T-014. `forward-headers-strategy=framework`가 Boot 4에서 무동작 → OAuth redirect_uri가 http](#t-014-forward-headers-strategyframework가-boot-4에서-무동작--oauth-redirect_uri가-http)
- [T-015. ddl-auto=update가 기존 컬럼 NOT NULL을 못 풀어 prod 500 / 사설 RDS 수동 ALTER도 막힘](#t-015-ddl-autoupdate가-기존-컬럼-not-null을-못-풀어-prod-500--사설-rds-수동-alter도-막힘)
- [T-016. flyway-core만 추가하면 Flyway 빈이 안 생긴다 (Boot 4 autoconfig 모듈 분리)](#t-016-flyway-core만-추가하면-flyway-빈이-안-생긴다-boot-4-autoconfig-모듈-분리)
- [T-017. 공유 인메모리 H2가 순서 의존 테스트 버그를 가린다 — 클래스패스 변경이 폭로](#t-017-공유-인메모리-h2가-순서-의존-테스트-버그를-가린다--클래스패스-변경이-폭로)
- [T-018. Spring Security 7(Boot 4)에서 `AntPathRequestMatcher` 제거됨](#t-018-spring-security-7boot-4에서-antpathrequestmatcher-제거됨)
- [T-019. Boot 4에서 `NoResourceFoundException`이 `ResponseStatusException` 비-상속 → 핸들러가 안 잡힘](#t-019-boot-4에서-noresourcefoundexception이-responsestatusexception-비-상속--핸들러가-안-잡힘)
- [T-020. Boot 4에서 raw `spring-session-jdbc`만으론 세션 외부화가 조용히 무동작 → 스타터 필요](#t-020-boot-4에서-raw-spring-session-jdbc만으론-세션-외부화가-조용히-무동작--스타터-필요)
- [T-021. Spring Session 쿠키엔 `server.servlet.session.cookie.*`가 무동작 → 명시 CookieSerializer 빈](#t-021-spring-session-쿠키엔-serverservletsessioncookie가-무동작--명시-cookieserializer-빈)
- [T-022. SSR(웹MVC) 앱엔 `ObjectMapper` 빈이 없어 주입 실패 → 자체 생성](#t-022-ssr웹mvc-앱엔-objectmapper-빈이-없어-주입-실패--자체-생성)
- [T-023. 직접 추가한 책 삭제가 500 — reading_session FK 미정리로 부모 삭제 실패, 좁은 catch가 못 잡음](#t-023-직접-추가한-책-삭제가-500--reading_session-fk-미정리로-부모-삭제-실패-좁은-catch가-못-잡음)
- [T-027. 구글 로그인 중 Chrome "위험한 사이트" 차단 — Safe Browsing이 신규 `.click` 도메인 오탐](#t-027-구글-로그인-중-chrome-위험한-사이트-차단--safe-browsing이-신규-click-도메인-오탐)
- [T-028. 유니크 제약 추가가 같은 값을 쓰던 기존 테스트 픽스처를 깨뜨린다](#t-028-유니크-제약-추가가-같은-값을-쓰던-기존-테스트-픽스처를-깨뜨린다)

---

## T-001. 확인 질문과 실행을 병렬로 보내 의도와 다르게 머지됨

**증상**: PR 머지 여부를 묻는 질문(AskUserQuestion)과 머지 실행(gh pr merge)을 같은 턴에 병렬로 보냈더니, 사용자가 "아직 머지 안 함"을 골랐는데도 답을 받기 전에 머지가 이미 실행돼 버렸다.

**원인**: 독립적인 도구 호출을 병렬로 묶는 습관 때문. 하지만 "실행"은 "사용자 답변"에 **의존**하므로 병렬로 보내면 안 된다 — 답을 받기 전에 실행이 끝난다.

**해결 / 예방**:
- **사용자 결정에 의존하는 액션은 절대 그 질문과 같은 턴에 실행하지 않는다.** 질문 → 답 수신 → 그 다음 턴에 실행.
- 특히 되돌리기 어려운 액션(머지, push, 삭제)은 답을 확인하고 단독으로 실행.

---

## T-002. 실수 머지된 PR — main force-push로 이력 되돌리기

**증상**: PR이 의도와 다르게 main에 머지됨. 커밋 이력을 깨끗하게 되돌리고 싶음.

**해결 절차** (협업자 없는 경우에만 안전):
1. **먼저 브랜치 보호 확인** (읽기 전용):
   - `gh api repos/<owner>/<repo>/branches/main --jq '.protected'`
   - `gh api repos/<owner>/<repo>/rulesets`
   - protected가 true거나 ruleset이 있으면 force-push가 막힌다 → 추측 말고 먼저 확인.
2. 잃으면 안 되는 내용은 **보존 브랜치**로 먼저 백업: `git branch <backup> <머지커밋>`
3. main을 머지 이전으로 reset: `git reset --hard <이전커밋>`
4. force-push: `git push --force-with-lease origin main` (`--force-with-lease`로 안전하게)
5. 보존 내용은 깨끗한 새 브랜치에서 재커밋 → 새 PR.

**주의**:
- **GitHub의 PR "merged" 기록과 PR 번호는 못 지운다.** 커밋 이력만 정리되고 PR #N은 닫힌 채 남는다.
- 협업자가 이미 pull 했다면 force-push는 위험 — 이 절차는 단독 작업 전제.
- 이 프로젝트는 main 직접 push가 훅으로 차단됨 → 사용자 명시 승인 시 `ALLOW_MAIN_PUSH` 토큰 부착 (CLAUDE.md 참고).

---

## T-003. `git show > 파일` 리다이렉트가 한글/UTF-8 파일을 깨뜨림

**증상**: `git show <branch>:path > file.md`로 파일을 추출했더니 BOM(`0xFF 0xFE`)이 붙고 null 바이트가 잔뜩 생김(UTF-16). 파일 크기도 비정상(4306 vs 원본 2949 bytes).

**원인**: PowerShell의 `>` 리다이렉트는 기본 인코딩이 **UTF-16 LE**. native 명령(git)의 UTF-8 출력을 UTF-16으로 다시 써버린다 (T-026 한글 커밋 메시지 깨짐과 같은 뿌리).

**해결**:
- 파일을 **있는 그대로 복원**할 땐 리다이렉트 대신 git의 파일 출력 기능을 쓴다:
  - `git checkout <branch> -- <path>` (워킹트리에 정확한 바이트로 복원)
  - 또는 `git restore --source=<branch> <path>`
- 굳이 리다이렉트가 필요하면 `git show ... | Out-File -Encoding utf8 file`.
- 검증: 복원 후 `git diff --quiet <branch> -- <path>` 로 바이트 동일 확인.

---

## T-004. gradlew stderr가 `$EAP=Stop` 훅을 죽임

**증상**: 커밋 테스트 게이트 훅이 테스트 통과/실패와 무관하게 항상 커밋을 차단(exit 1). 차단은 되는데 이유가 틀린 가짜 동작.

**원인**: PowerShell 5.1에서 `$ErrorActionPreference='Stop'`일 때, native 명령(gradlew)이 stderr로 출력하면(JDK 경고 등) 종료코드 0이어도 `NativeCommandError`로 승격되어 스크립트가 그 줄에서 죽는다.

**해결**: native 호출을 `cmd.exe /c "... >nul 2>nul"`로 격리하고 `$LASTEXITCODE`만 판정에 사용. 상세는 [learning-notes.md N-006](learning-notes.md#n-006-powershell-51--native-stderr-가-eapstop-과-만나-스크립트를-죽이는-함정).

---

## T-005. PR 머지 후 feat 브랜치에서 pull → 군더더기 merge 커밋

**증상**: PR(squash) 머지 직후, 아직 feat 브랜치에 체크아웃된 상태로 `git pull origin main`을 했더니 원치 않는 merge 커밋이 생기고 이력이 지저분해졌다.

**원인**: squash 머지는 main에 **새 단일 커밋**을 만든다. feat 브랜치의 원래 커밋들과는 별개 히스토리라, feat 브랜치에서 `pull`하면 두 갈래가 합쳐지며 merge 커밋이 끼어든다.

**해결 / 예방** — 머지 후 정리는 **순서**를 지킨다:
1. `git checkout main` (먼저 main으로 전환)
2. `git pull origin main` (또는 `git fetch && git reset --hard origin/main`)
3. `git branch -d <feat>` 로 머지된 브랜치 삭제
- 이미 꼬였다면: `git checkout main` 후 `git reset --hard origin/main`로 원격에 맞춤(로컬 군더더기 커밋 폐기).
- 핵심: **feat 브랜치에서 절대 pull 하지 않는다.** pull은 항상 main에서.

---

## T-006. Spring Boot 4: `@DataJpaTest` import 경로 변경

**증상**: `@DataJpaTest` 슬라이스 테스트가 컴파일조차 안 됨.
```
error: package org.springframework.boot.test.autoconfigure.orm.jpa does not exist
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
@DataJpaTest  symbol: class DataJpaTest
```
의존성(`spring-boot-starter-data-jpa-test`)은 `build.gradle`에 분명히 있는데도 "package does not exist".

**원인**: Spring Boot 4에서 autoconfigure/테스트-슬라이스가 **모듈별 패키지로 이동**했다. `@DataJpaTest`는 더 이상 umbrella 패키지에 없고 data-jpa 모듈 패키지로 옮겨졌다. import 경로가 옛날 그대로라 못 찾는 것(의존성 문제가 아니라 import 경로 문제). N-003(starter 네이밍 변경)과 같은 뿌리.

- ❌ 옛: `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest`
- ✅ 신: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`

**해결 / 예방**:
- import만 신 경로로 교체하면 끝.
- 클래스가 어느 패키지/jar에 있는지 **추측 말고 jar에서 직접 확인**:
  ```bash
  for j in $(find ~/.gradle/caches/modules-2 -name 'spring-boot*.jar'); do \
    unzip -l "$j" 2>/dev/null | grep -q 'DataJpaTest.class' && { echo "$j"; unzip -l "$j" | grep DataJpaTest; }; done
  ```
  → `spring-boot-data-jpa-test-4.0.6.jar` 안 `org/springframework/boot/data/jpa/test/autoconfigure/DataJpaTest.class` 확인.
- 다른 슬라이스(`@WebMvcTest` 등)도 Boot 4에선 모듈별 패키지일 수 있으니 같은 방법으로 확인.

**확인된 추가 사례** (같은 뿌리 — 마주칠 때마다 누적):

| 애너테이션 | ❌ 옛 (3.x) | ✅ 신 (4.x) | jar |
|---|---|---|---|
| `@DataJpaTest` | `...boot.test.autoconfigure.orm.jpa` | `...boot.data.jpa.test.autoconfigure` | `spring-boot-data-jpa-test` |
| `@AutoConfigureMockMvc` | `...boot.test.autoconfigure.web.servlet` | `...boot.webmvc.test.autoconfigure` | `spring-boot-webmvc-test` |

---

## T-007. `@DataJpaTest` 슬라이스에서 auditing이 안 돌아 createdAt이 null

**증상**: JPA auditing(`@CreatedDate`/`@LastModifiedDate`)을 붙였는데 `@DataJpaTest` 슬라이스 테스트에서 저장 후 `createdAt`/`updatedAt`이 `null`. 엔티티에 `@EntityListeners(AuditingEntityListener.class)`도, 베이스에 애너테이션도 다 있는데 안 채워진다.

**원인**: `@DataJpaTest`는 JPA 관련 빈만 최소로 올리는 슬라이스라, 메인 앱의 일반 `@Configuration`에 있는 `@EnableJpaAuditing`을 **자동으로 로드하지 않는다**. auditing 리스너를 켜는 스위치가 슬라이스 밖에 있어 꺼진 상태로 도는 것 (애너테이션은 표식일 뿐, 스위치가 따로 — N-008).

**해결 / 예방**:
- 슬라이스 테스트에 `@EnableJpaAuditing`을 가진 설정을 명시적으로 끌어온다:
  ```java
  @DataJpaTest
  @Import(JpaConfig.class)   // @EnableJpaAuditing 보유 — 없으면 createdAt null
  class AuditingTest { ... }
  ```
- `@EnableJpaAuditing`은 별도 `@Configuration`(예: `JpaConfig`)에 두면 메인은 컴포넌트 스캔으로, 슬라이스는 `@Import`로 재사용 가능 — 메인 클래스에 직접 달면 슬라이스에서 끌어오기 번거롭다.
- 검증: insert 후 `getCreatedAt()`이 non-null인지 단언하는 테스트를 두면 회귀로 잡힌다.

---

## T-008. `redirectedUrlPattern("**/login")`이 상대경로 리다이렉트에 매칭 실패

**증상**: Spring Security formLogin 설정 후, 미인증 요청이 로그인으로 리다이렉트되는지 MockMvc로 검증.
```java
mvc.perform(get("/"))
   .andExpect(status().is3xxRedirection())
   .andExpect(redirectedUrlPattern("**/login"));  // 실패
```
상태는 302로 정상인데 단언만 실패:
```
AssertionError: Redirected URL '/login' does not match the expected URL pattern '**/login'
```

**원인**: Spring Security의 기본 로그인 진입점(`LoginUrlAuthenticationEntryPoint`)은 **상대경로** `/login`으로 리다이렉트한다(`http://...` 절대 URL이 아님). `redirectedUrlPattern`은 Ant 경로 패턴 매칭이라 `**/login`이 `/login` 한 조각짜리 경로엔 안 맞는다(`**`가 앞 세그먼트를 요구). 동작은 옳고 **단언 표현만 틀린** 케이스 — 프로덕션 문제로 오인하기 쉽다.

**해결 / 예방**:
- 상대경로 리다이렉트는 패턴 대신 **정확 매칭**을 쓴다: `redirectedUrl("/login")`.
- 실패하면 프로덕션부터 의심하지 말고 **실제 리다이렉트 URL을 먼저 확인**한다(테스트 리포트의 AssertionError 메시지에 `Redirected URL '...'`로 찍힌다). 동작이 옳은데 단언이 틀린 경우가 많다.
- 절대 URL(`http://host/...`)이나 다단 경로에만 `redirectedUrlPattern`을 쓴다.

---

## T-009. Fargate 콜드스타트가 헬스체크 grace를 못 넘겨 ECS 태스크 무한 재시작

**증상**: ECS Fargate 첫 배포에서 서비스가 안정화되지 않고 태스크가 계속 떴다 죽었다 반복. 서비스 이벤트에 `(port 8080) is unhealthy ... (reason Health checks failed)` → `has begun draining` → `has stopped 1 running tasks` 루프. 워크플로의 "서비스 안정화 대기"도 끝나지 않음.

**원인**: 앱 로그를 보면 **컨테이너는 정상 기동**(RDS 연결 성공, `Started ... in 99.6 seconds`). 문제는 **콜드스타트가 ~100초**(Fargate 0.25 vCPU=256은 CPU가 적어 Spring Boot 부팅이 느림)인데, ECS 서비스의 **헬스체크 유예(`health-check-grace-period-seconds`)를 120초**로 줬다는 것. 앱이 준비되자마자 유예가 끝나, ALB 타깃그룹이 "healthy 2회 연속(≈60초)"을 채우기 전에 ECS가 태스크를 비정상으로 보고 종료 → 새 태스크 → 또 반복.

> 진단 포인트: `stoppedReason`이 아니라 **서비스 이벤트의 "unhealthy ... Health checks failed"** + **앱 로그가 정상 기동을 보이는데도 죽는 것**이 "타이밍 문제(grace)"의 신호. (이때 본 `CannotPullContainerError ...not found`는 이미지 push 전 옛 태스크의 사유라 무관 — 사유의 시점을 구분할 것.)

**해결 / 예방**:
- 유예를 콜드스타트보다 넉넉히: `aws ecs update-service --health-check-grace-period-seconds 300 --force-new-deployment ...`. (update-service는 grace를 명시 안 한 배포에선 기존값을 유지하므로 한 번 늘리면 이후 배포에도 적용됨.)
- 근본: **콜드스타트 단축** — 태스크 CPU를 0.5 vCPU(512)로 올리면 부팅이 크게 빨라진다. (느린 시작 = 작은 CPU 신호.)
- ALB 타깃 헬스체크는 컨테이너가 아니라 **HTTP 응답**으로 판정 — 경로(`/actuator/health`)가 인증 없이 200을 주는지, 포트(8080)가 맞는지도 함께 확인.

---

## T-010. ECS 안정화 대기 중 수동 update-service 끼워넣어 워크플로 배포가 경쟁·실패

**증상**: GitHub Actions의 ECS 배포 잡(`amazon-ecs-deploy-task-definition`, `wait-for-service-stability: true`)이 도는 중에, 디버깅하려고 CloudShell에서 `aws ecs update-service`를 별도로 실행. 이후 워크플로 배포 잡이 25분 만에 **실패**(run이 red)했는데, 정작 **서비스 자체는 정상 안정화**됨(앱 접속 잘 됨).

**원인**: 두 번의 배포가 **경쟁**했다. 워크플로가 만든 배포(PRIMARY)가 안정화되길 기다리는 동안, 수동 `update-service`가 **새 배포로 교체**해버려 워크플로가 추적하던 배포가 무효화됨. 워크플로의 안정화 대기는 자기 배포가 stable 되는 걸 끝내 못 보고 타임아웃/실패 처리. → **run 색만 red, 실제 서비스는 멀쩡**.

**해결 / 예방**:
- **워크플로의 배포 대기 중에는 같은 서비스에 수동 `update-service`를 하지 않는다.** 한 서비스의 배포는 한 주체(파이프라인)만 건드린다.
- 디버깅이 필요하면 **워크플로 run을 먼저 취소**(`gh run cancel`)하고 수동 작업을 하거나, 반대로 수동 개입을 멈추고 워크플로 하나만 끝까지 둔다.
- 배포 성공 판정은 **워크플로 색이 아니라 서비스 상태/실접속**으로 확인(둘이 갈릴 수 있음 — 이 케이스가 그 예). 깨끗한 green 기록이 필요하면 개입 없이 재트리거.

---

## T-011. Fargate 태스크가 SSM 시크릿 pull 실패 — 퍼블릭 IP라도 서브넷에 IGW 라우트 없으면 인터넷 도달 불가

**증상**: 새 배포가 안정화 안 되고 12분+ stuck. 서비스 이벤트에 새 태스크 시작 실패가 반복:
```
ResourceInitializationError: unable to pull secrets or registry auth:
unable to retrieve secrets from ssm: The task cannot pull secrets from AWS
Systems Manager. There is a connection issue between the task and AWS Systems
Manager Parameter Store. Check your task network configuration. ...
operation error SSM: GetParameters, https response error StatusCode: 0,
RequestID: , canceled, context deadline exceeded
```
한편 **옛 태스크는 계속 running**이라 사이트는 멀쩡(롤링 배포 안전장치) → "문제 없어 보임"의 착시. 그리고 **재시도하다 어쩌다 성공해 green이 되기도** 함(비결정적).

**원인**: 태스크가 SSM Parameter Store 엔드포인트에 **네트워크로 도달하지 못함**(`context deadline exceeded` = 타임아웃, 권한 아님 — AWS가 직접 "network configuration 확인"이라 안내). 서비스는 `assignPublicIp=ENABLED`였지만 — **퍼블릭 IP는 서브넷 라우트테이블에 `0.0.0.0/0 → IGW`가 있을 때만 인터넷에 나간다.** 서비스에 물린 서브넷 2개 중:
- `subnet-071…`: 명시적 RTB 연결 없음 → VPC **main RTB**(IGW 라우트 보유) 사용 → **정상**
- `subnet-018…`: 별도 RTB에 **`local` 경로만**, IGW 없음 → 인터넷 차단 → **여기 뜨는 태스크는 SSM 도달 실패**

→ 네트워크 설정은 서비스 레벨로 동일한데, **태스크 배치가 두 서브넷 사이 비결정적**이라 좋은 서브넷(071)에 걸리면 성공·나쁜 서브넷(018)에 걸리면 실패. "전엔 됐는데 지금 안 됨 / 됐다 안 됐다"의 정체.

> 진단 포인트: ① 서비스 이벤트의 `ResourceInitializationError ... ssm ... context deadline exceeded` (권한이면 AccessDenied가 뜸 — 메시지로 권한 vs 네트워크 구분) ② `describe-services` 의 `networkConfiguration.awsvpcConfiguration` 으로 서브넷/SG/assignPublicIp 확인 ③ **각 서브넷의 라우트테이블을 비교** — `0.0.0.0/0 → igw-…` 유무가 갈림. SG egress(443/all)도 함께 확인.

**해결 / 예방**:
- 나쁜 서브넷의 RTB에 IGW 라우트 추가(2-AZ 유지):
  ```bash
  aws ec2 create-route --route-table-id <rtb-018> \
    --destination-cidr-block 0.0.0.0/0 --gateway-id <igw-…>
  ```
  `"Return": true` 후 `describe-route-tables`로 `0.0.0.0/0 → igw` 추가 확인. 즉시 발효.
- 대안: 서비스를 좋은 서브넷만 쓰게 `update-service --network-configuration`(단일 AZ가 됨), 또는 SSM·ECR·CloudWatch용 **VPC 엔드포인트**(퍼블릭 IP 없이 사설로 도달 — 더 안전하지만 설정 추가).
- 셋업 시 예방: **서비스에 물리는 서브넷이 전부 인터넷 egress(IGW 또는 NAT)를 갖는지** 확인. 퍼블릭/프라이빗 서브넷 혼재가 비결정적 실패의 흔한 원인.
- 개념: [learning-notes.md N-018](learning-notes.md#n-018-퍼블릭-ip--인터넷-접근--서브넷-라우트테이블이-진짜-관문).

---

## T-012. 가입 시 중복 이메일이 처리 안 된 DataIntegrityViolationException → 500 whitelabel (prod만)

**증상**: 배포된 앱에서 회원가입(POST `/signup`) 시 **Whitelabel Error Page, 500 Internal Server Error**. 로컬 테스트(H2)는 전부 통과하는데 prod(MySQL)만 터진다. ("아까는 404, 지금은 500"처럼 증상이 오락가락 — 같은 이메일로 재시도할 때만 500.)

**원인**: `User`에 이메일 유니크 제약(`uk_users_email`)은 있는데, `UserRegistrationService.register`가 **저장 전 중복 확인 없이** 바로 `save` → 이미 가입된 이메일이면 MySQL이 제약 위반 → `DataIntegrityViolationException`이 컨트롤러에서 처리되지 않고 그대로 500으로 새어 나감.
- **왜 테스트는 통과하나**: 통합 테스트가 `@Transactional`이라 매 테스트가 롤백돼 **DB에 중복이 남지 않는다**. prod는 영속되니 한 번 가입한 이메일로 재시도하면 충돌. → "H2 테스트 그린인데 prod만 터지는" 전형(상태 누적 차이).

> 진단: whitelabel은 스택을 가리므로 **CloudWatch 로그**에서 실제 예외를 본다. 로그 그룹 `/ecs/booktimer`의 최신 스트림 → 스택트레이스가 `UserRegistrationService.register(...:54)` → `SimpleJpaRepository.save` → `com.mysql.cj.jdbc.ClientPreparedStatement.executeUpdate`(users INSERT 실패)를 가리킴. NOT NULL이 폼 검증으로 다 차 있으면, 실패할 제약은 unique 하나뿐 → 중복 이메일 확정.

**해결 / 예방**:
- 등록 서비스에서 **저장 전 `existsByEmail` 사전 확인** → 있으면 도메인 예외(`EmailAlreadyExistsException`). 컨트롤러가 잡아 **이메일 필드 에러**로 변환(폼 재렌더, 500 아님).
- **레이스 대비 이중 방어**: 동시 가입(둘 다 사전확인 통과 후 insert)은 컨트롤러에서 `DataIntegrityViolationException`도 함께 catch해 같은 친절한 에러로. (사전확인=흔한 경로, DB제약=마지막 방어선 — 둘 다 필요. [learning-notes.md N-019](learning-notes.md#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다))
- **테스트 함정 인지**: `@Transactional` 통합 테스트는 유니크 충돌 같은 "상태 누적" 버그를 못 잡는다. 중복 케이스는 한 테스트 안에서 **사전 데이터를 저장한 뒤** 같은 키로 시도해 재현(롤백돼도 그 트랜잭션 안에선 보임).

---

## T-013. `aws logs --max-items 1`이 페이지네이션 토큰 None을 변수에 섞어 "stream does not exist"

**증상**: CloudWatch 로그 스트림 이름을 변수에 담아 쓰는데 실패.
```bash
STREAM=$(aws logs describe-log-streams ... --max-items 1 --query 'logStreams[0].logStreamName' --output text)
echo "stream: $STREAM"
# stream: ecs/booktimer/b55b290e...
# None                       ← 두 번째 줄
aws logs get-log-events --log-stream-name "$STREAM" ...
# ResourceNotFoundException: The specified log stream does not exist.
```

**원인**: AWS CLI의 **`--max-items`는 클라이언트측 페이지네이션** 옵션이라, 결과가 더 있으면 **다음 토큰을 `None`(또는 실제 토큰)으로 출력 끝에 한 줄 더 붙인다**. `--output text`에선 이게 스트림명 다음 줄에 `None`으로 찍혀 `$STREAM`이 `"<스트림명>\nNone"`으로 오염 → 존재하지 않는 스트림명이 됨.

**해결 / 예방**:
- 단건만 필요하면 `--max-items`를 **쓰지 말고** 쿼리에서 `[0]`으로 집는다: `--query 'logStreams[0].logStreamName'`. (정렬 `--order-by LastEventTime --descending`로 최신 1건.)
- 굳이 개수를 제한해야 하면 결과 토큰 줄을 분리 처리하거나 `--no-paginate` + 쿼리로 자른다.
- 일반화: `--max-items`가 붙은 AWS CLI 출력은 **마지막 줄이 페이지네이션 토큰일 수 있다** — 스칼라로 캡처할 때 주의.

---

## T-014. `forward-headers-strategy=framework`가 Boot 4에서 무동작 → OAuth redirect_uri가 http

**증상**: ALB(HTTPS 종료) 뒤 배포 후, 구글 로그인 인가요청의 `redirect_uri`가 `http://...`로 만들어져 구글과 mismatch(https만 등록). 프로퍼티 `server.forward-headers-strategy=framework`를 줬는데도 효과 없음.

**원인**: 이 프로퍼티는 `ForwardedHeaderFilter`를 `FilterRegistrationBean`으로 등록하는 자동구성(`ServletWebServerConfiguration`)에 의존한다. Boot 4의 모듈 분리(`spring-boot-web-server` 등) 환경에서 그 조건부 빈이 컨텍스트에 활성화되지 않아 **필터가 안 걸렸다** → X-Forwarded-Proto/Host 무시 → 앱이 자기 주소를 http/localhost로 인식 → redirect_uri도 http.

**해결 / 예방**:
- `ForwardedHeaderFilter`를 **명시 @Bean**(`FilterRegistrationBean`, `Ordered.HIGHEST_PRECEDENCE`)으로 직접 등록(`WebConfig`). 프로퍼티 대신 코드 등록이면 버전·모듈 구성에 무관하게 확실.
- **MockMvc(`@AutoConfigureMockMvc`)는 이 `FilterRegistrationBean`을 필터 체인에 적용하지 않는다** → 동작 검증은 `@SpringBootTest(webEnvironment=RANDOM_PORT)` 실서버 + 실제 HTTP 호출로(헤더 보내 redirect_uri가 https인지 확인).
- 일반화: "맞는 프로퍼티인데 효과가 없다"면 그 프로퍼티가 의존하는 자동구성이 실제로 켜졌는지 의심하고, 핵심 빈은 명시 등록으로 못 박는다.

---

## T-015. ddl-auto=update가 기존 컬럼 NOT NULL을 못 풀어 prod 500 / 사설 RDS 수동 ALTER도 막힘

**증상**: 소셜(OAuth) 신규 사용자 INSERT가 prod에서 `Column 'password_hash' cannot be null` → 500. 엔티티는 `passwordHash`를 nullable로 바꿨는데도.

**원인**: Hibernate `ddl-auto=update`는 **새 컬럼/테이블만 추가**하고 **기존 컬럼의 제약(NOT NULL→NULL, 타입 등)은 변경하지 않는다**. 새 `auth_provider`는 추가됐지만 기존 `password_hash`의 NOT NULL은 그대로 → 엔티티(nullable)와 DB(NOT NULL) 불일치.

**해결 / 예방**:
- 즉효 SQL: `ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL`. 단 사설 RDS라 접속 경로가 함정(아래).
- **사설 RDS 접속 함정**: `PubliclyAccessible:false` + 3306을 ECS 태스크 SG만 허용 + 서브넷이 IGW만(NAT 없음). → **CloudShell VPC 환경은 퍼블릭 IP를 못 받아 인터넷 불가 → `mysql` 클라이언트 설치조차 안 됨** → 수동 접속이 막힌다.
- **우회(채택)**: 앱이 이미 ALTER 권한 보유(같은 배포에서 컬럼 자동 추가가 증거) → **prod 전용 `ApplicationRunner`로 기동 시 1회 멱등 ALTER**(`information_schema`로 nullable 확인 후). 사설 DB 직접 접속 불필요.
- **근본**: Flyway 마이그레이션 도입(기존 DB는 baseline). `ddl-auto=update`를 운영 스키마 변경 수단으로 신뢰하지 말 것(개념: learning-notes N-023).

---

## T-016. flyway-core만 추가하면 Flyway 빈이 안 생긴다 (Boot 4 autoconfig 모듈 분리)

**증상**: `implementation 'org.flywaydb:flyway-core'` 추가 후 컴파일·런은 되는데, `@Autowired Flyway`가 `NoSuchBeanDefinitionException: No qualifying bean of type 'org.flywaydb.core.Flyway'`로 실패. 마이그레이션도 안 돈다.

**원인**: Spring Boot 4는 `spring-boot-autoconfigure` 단일 모듈을 **기술별 모듈**로 분리했다(`spring-boot-jdbc`/`-jpa`/`-flyway`…). `flyway-core`는 **라이브러리**일 뿐이라 `FlywayAutoConfiguration`(= autoconfig 모듈)이 클래스패스에 없으면 빈이 안 만들어진다.

**해결**:
- `implementation 'org.springframework.boot:spring-boot-flyway'` 추가(이게 `flyway-core`를 전이로 끌어옴). MySQL 운영이면 `org.flywaydb:flyway-mysql`도 함께.
- 일반 규칙: **Boot 4에선 "라이브러리 추가 ≠ 자동설정 켜짐"** — 가능하면 스타터/`spring-boot-<tech>` 모듈로 추가. (같은 결: T-006 패키지 이동, N-024.)

**진단 팁**: `./gradlew dependencies`로 `spring-boot-<tech>` autoconfig 모듈이 클래스패스에 있는지 확인.

---

## T-017. 공유 인메모리 H2가 순서 의존 테스트 버그를 가린다 — 클래스패스 변경이 폭로

**증상**: Flyway 의존성 추가(기능과 무관) 후 갑자기 `@DataJpaTest` 슬라이스 3종이 `NULL not allowed for column "CREATED_AT"` / 유니크 위반으로 실패. 전체 스위트에선 멀쩡했는데 **단독 실행하면 실패**.

**원인**: 두 겹.
- (근본) 슬라이스가 `@Import(JpaConfig.class)`(=`@EnableJpaAuditing`)를 빠뜨려 INSERT 시 `created_at`이 안 채워짐(NOT NULL 위반) — **T-007과 동일한 함정**. 원래부터 버그였다.
- (가림막) 테스트 DB가 `jdbc:h2:mem:booktimer;DB_CLOSE_DELAY=-1` — **모든 컨텍스트가 공유하는, JVM 살아있는 동안 유지되는 인메모리 DB**. auditing이 켜진 다른 컨텍스트(@SpringBootTest)가 먼저 돌며 스키마·데이터를 만들어두면 슬라이스가 그 위에 얹혀 우연히 통과했다. **실행 순서에 의존**한 green이었다.
- Flyway를 클래스패스에 올리자 `@DataJpaTest`의 데이터소스 처리/실행 순서가 바뀌며 가림막이 걷혀 진짜 버그가 드러난 것.

**해결 / 예방**:
- 슬라이스 3종(`UserRepositoryTest`/`ReadingTimerRepositoryTest`/`ReadingSessionRepositoryTest`)에 `@Import(JpaConfig.class)` 추가 → 단독·전체 모두 green.
- **교훈**: "전체 스위트 green"은 격리를 보장하지 않는다. **의심되면 테스트를 단독 실행**해 순서 의존을 잡아라. 공유 인메모리 DB(`DB_CLOSE_DELAY=-1`)는 편하지만 이런 의존을 숨긴다.
- 관련: T-007(슬라이스 auditing 미로드 원형), N-008(JPA Auditing), N-024(이번 클래스패스 변경의 맥락).

---

## T-018. Spring Security 7(Boot 4)에서 `AntPathRequestMatcher` 제거됨

**증상**: 커스텀 필터에서 `new AntPathRequestMatcher("/login", "POST")`로 경로를 판정하려 하니 컴파일 실패 — `cannot find symbol: class AntPathRequestMatcher (package org.springframework.security.web.util.matcher)`.

**원인**: `AntPathRequestMatcher`는 Spring Security 6.x에서 deprecated → **7.x(Boot 4 동반)에서 제거**됐다. 대체는 `PathPatternRequestMatcher`(빌더: `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login")`).

**해결**:
- 버전 의존을 피하려면 매처 클래스 없이 **요청에서 직접 판정**: 메서드(`request.getMethod()`) + 컨텍스트 경로 제외 경로(`request.getRequestURI().substring(request.getContextPath().length())`) 비교. 단순 단일 경로 매칭엔 이쪽이 안정적.
- 매처가 필요하면 `PathPatternRequestMatcher`로 마이그레이션.
- 일반 규칙: **Boot 4 = Security 7** — 6.x 기준 블로그/예제의 `AntPathRequestMatcher`·`authorizeRequests`·`antMatchers` 등은 그대로 안 먹는다. (같은 결: T-006 패키지 이동, T-014 forward-headers, T-016 autoconfig 모듈 — "Boot 4 호환성" 묶음.)

---

## T-019. Boot 4에서 `NoResourceFoundException`이 `ResponseStatusException` 비-상속 → 핸들러가 안 잡힘

**증상**: `@ControllerAdvice`에 `@ExceptionHandler(ResponseStatusException.class)`로 404를 보존하려 했는데 안 먹힘 — 없는 리소스(`/favicon.ico`, 오타 경로) 요청이 여전히 catch-all `@ExceptionHandler(Exception.class)`에 잡혀 **500**으로 응답. 컴파일은 통과해서 더 헷갈림(런타임에 조용히 빗나감).

**원인**: 스프링 6.x에선 `NoResourceFoundException extends ResponseStatusException`이었으나, **Spring 7(Boot 4)에선 `extends jakarta.servlet.ServletException implements org.springframework.web.ErrorResponse`** 로 바뀌었다. 더 이상 `ResponseStatusException`의 하위가 아니라서 `@ExceptionHandler(ResponseStatusException.class)`가 매칭하지 못하고, 그 아래 `Exception.class` 핸들러로 떨어진다.

**진단(추측 금지)**: 상속 관계를 추정하지 말고 jar에서 직접 확인.
```bash
jar=$(find ~/.gradle/caches -name 'spring-webmvc-*.jar' | head -1)
unzip -o -q "$jar" 'org/springframework/web/servlet/resource/NoResourceFoundException.class' -d /tmp/x
javap -p /tmp/x/org/.../NoResourceFoundException.class | head -2
# → ... extends jakarta.servlet.ServletException implements org.springframework.web.ErrorResponse
```

**해결**:
- 상태코드를 들고 오는 두 타입을 **둘 다** 잡고, 공통 인터페이스 `ErrorResponse.getStatusCode()`로 코드를 읽어 보존:
  ```java
  @ExceptionHandler({ResponseStatusException.class, NoResourceFoundException.class})
  public String handleStatusException(Exception ex, Model model, HttpServletResponse response) {
      int status = ((ErrorResponse) ex).getStatusCode().value();  // 둘 다 ErrorResponse 구현
      response.setStatus(status);
      ...
  }
  ```
- 더 구체적인 타입이라 catch-all `Exception` 핸들러보다 우선 적용된다.
- 일반 규칙: **catch-all `@ExceptionHandler(Exception.class)`는 프레임워크가 던지는 상태보유 예외(404 등)까지 삼킨다** — 상태 예외를 더 좁은 타입으로 먼저 잡아 코드를 보존할 것. 그리고 "예전엔 이 클래스의 부모였다"는 기억은 메이저 버전업에서 깨질 수 있으니 **실제 상속을 jar로 확인**(같은 결: T-006/T-014/T-016/T-018 "Boot 4 호환성" 묶음). 개념은 N-028.

---

## T-020. Boot 4에서 raw `spring-session-jdbc`만으론 세션 외부화가 조용히 무동작 → 스타터 필요

**증상**: 세션 외부화하려고 `implementation 'org.springframework.session:spring-session-jdbc'`를 추가했는데, **에러 없이** 세션이 여전히 JVM 메모리에 저장된다. `SPRING_SESSION` 테이블에 행이 안 생기고(직접 조회하면 "table not found"), 다른 세션 사용 테스트는 멀쩡히 통과(=세션 저장소가 안 바뀜). **컴파일·기동 다 성공**해서 "되는 줄" 착각하기 쉽다.

**원인**: Boot 4는 autoconfigure를 기술별 모듈로 쪼갰다(같은 뿌리: T-016 Flyway). **raw 라이브러리 `spring-session-jdbc`엔 Boot autoconfig가 없다** → `JdbcIndexedSessionRepository`·스키마 초기화 빈이 안 생기고, Spring Session은 비활성인 채 기본 인메모리 `HttpSession`이 계속 쓰인다. 빈이 "없는" 것뿐이라 **예외도 안 난다**(조용한 무동작).

**진단(추측 금지)**: BOM에서 실제 모듈/스타터 이름을 확인.
```bash
bom=$(find ~/.gradle/caches -path "*spring-boot-dependencies/4.0*" -name "*.pom" | head -1)
grep -ioE "spring-boot[a-z0-9-]*session[a-z0-9-]*" "$bom" | sort -u
# → spring-boot-session, spring-boot-session-jdbc, spring-boot-starter-session-jdbc, ...
```
또 "다른 세션 테스트가 안 깨진다"가 단서 — 진짜로 JDBC였다면 테이블 없이 세션 저장 시 INSERT 실패가 났을 것. 안 깨졌다는 건 **여전히 인메모리**라는 뜻.

**해결**:
- raw 라이브러리 대신 **스타터**를 쓴다 — `org.springframework.boot:spring-boot-starter-session-jdbc`(라이브러리 + `spring-boot-session-jdbc` autoconfig 동봉). 테스트엔 `spring-boot-starter-session-jdbc-test`.
- 스키마는 운영=Flyway(V2), 테스트 H2=`initialize-schema=embedded` 기본 자동. Flyway가 만드는 곳은 `initialize-schema=never`로 두어 CREATE 충돌 방지(FlywayMigrationTest 포함).
- 일반 규칙: **Boot 4에서 "라이브러리만 추가했는데 기능이 조용히 안 켜진다"면 autoconfig 모듈/스타터 분리를 의심**(T-016과 동일). raw 라이브러리 좌표가 아니라 `spring-boot-starter-*`를 쓴다. 개념 N-029.

---

## T-021. Spring Session 쿠키엔 `server.servlet.session.cookie.*`가 무동작 → 명시 CookieSerializer 빈

**증상**: 세션 쿠키에 `SameSite=Lax`(또는 Secure/HttpOnly)를 주려고 `server.servlet.session.cookie.same-site=lax`를 설정했는데 **에러 없이** 안 먹는다. 응답 `Set-Cookie`를 보면 `SESSION=...; Path=/`뿐 — 지정한 속성이 하나도 안 붙음.

**원인**: 세션 외부화(Spring Session JDBC, T-020) 이후 세션 쿠키(`SESSION`)는 서블릿 컨테이너가 아니라 **Spring Session의 `DefaultCookieSerializer`** 가 쓴다. `server.servlet.session.cookie.*` 프로퍼티는 (이 Boot 4 조합에서) 그 직렬화기에 연결되지 않아 무동작이다. `server.forward-headers-strategy`가 무동작이라 `ForwardedHeaderFilter`를 명시 빈으로 등록해야 했던 것(N-022)과 **같은 부류의 함정** — "표준 프로퍼티인데 조용히 안 먹음".

**진단(추측 금지)**: 단언/로그로 응답 `Set-Cookie` 헤더를 직접 본다. MockMvc면:
```java
result.getResponse().getHeaders("Set-Cookie");  // [SESSION=...; Path=/] ← 속성 없음이 곧 증거
```
"프로퍼티 추가 전후로 쿠키가 그대로"면 프로퍼티가 그 쿠키에 안 닿는다는 뜻.

**해결**: 명시 `CookieSerializer` 빈을 등록한다(`WebConfig#cookieSerializer`).
```java
DefaultCookieSerializer s = new DefaultCookieSerializer();
s.setSameSite("Lax");
s.setUseHttpOnlyCookie(true);
s.setUseSecureCookie(secure);   // Secure는 prod(HTTPS)만 — 로컬 http면 쿠키 안 실려 로그인 불가
```
- 빈 타입이 `CookieSerializer`라 Boot의 기본 직렬화기 자동구성이 물러나고(`@ConditionalOnMissingBean`) Spring Session이 이 빈을 쓴다.
- **주의**: 이 함정 때문에 prod의 `secure`/`http-only` 프로퍼티도 SESSION 쿠키엔 안 먹고 있었을 수 있다(세션 외부화 직후 잠재 갭) — 명시 빈이 셋을 한 번에 잡는다. 개념 N-031.

---

## T-022. SSR(웹MVC) 앱엔 `ObjectMapper` 빈이 없어 주입 실패 → 자체 생성

**증상**: 외부 API 응답 파싱용으로 컴포넌트 생성자에 `ObjectMapper`를 주입했더니, **모든 @SpringBootTest가 컨텍스트 로드 실패**한다:
```
Error creating bean 'aladinBookSearchClient' ... Unsatisfied dependency through constructor parameter 1:
No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available
```
한 빈의 의존 하나 때문에 컨텍스트 전체가 못 떠 무관한 테스트까지 죄다 빨개진다(대량 실패 = 컨텍스트 로드 실패 신호).

**원인**: `ObjectMapper` 빈은 **`JacksonAutoConfiguration`** 이 등록하는데, 그건 보통 REST/JSON 경로(`spring-boot-starter-web`의 JSON 메시지 컨버터)와 함께 활성화된다. 이 프로젝트는 **Thymeleaf SSR**이라 JSON 직렬화 경로가 없어 그 autoconfig가 `ObjectMapper` 빈을 만들지 않는다(jackson-databind 라이브러리는 클래스패스에 있어 `new ObjectMapper()`는 되지만 **빈은 없음**). N-024/T-016/T-020과 같은 "라이브러리는 있는데 빈이 없다" 부류.

**진단(추측 금지)**: 대량 컨텍스트 로드 실패면 단위 테스트(컨텍스트 불필요)는 통과하는지 본다 — 통과하면 빈 와이어링 문제다. 결과 XML의 `Caused by ... No qualifying bean of type 'X'`가 범인.

**해결**: 외부 빈에 의존하지 말고 **컴포넌트가 자체 인스턴스를 생성**한다(ObjectMapper는 스레드 안전·재사용 가능).
```java
private final ObjectMapper objectMapper = new ObjectMapper();  // 주입 대신 자체 보유
```
대안으로 `@Bean ObjectMapper objectMapper(){ return new ObjectMapper(); }`를 한 곳에 정의해도 된다. SSR 앱에서 JSON은 국소적이므로 자체 생성이 의존을 줄여 더 단순하다. 일반 교훈: **Boot에서 "당연히 있겠지" 싶은 빈도 SSR/모듈 구성에선 없을 수 있다 — 주입 전에 그 빈이 실제로 등록되는지 확인.**

---

## T-023. 직접 추가한 책 삭제가 500 — reading_session FK 미정리로 부모 삭제 실패, 좁은 catch가 못 잡음

**증상**: "내 책장"에서 책 삭제 시 500. 단, **타이머로 한 번이라도 읽은 책만** 터진다(읽은 적 없는 책은 정상 삭제). "어떤 책은 되고 어떤 책은 500"으로 보여 헷갈린다.

**원인**: `reading_session.book_id → book(id)` FK(V4)에 `ON DELETE` 동작이 없다. `BookService.delete`가 그 책을 가리키는 세션을 정리하지 않고 바로 `bookRepository.delete(book)` → FK 제약 위반. 그런데 컨트롤러(`BookController#delete`)는 `IllegalArgumentException`만 `catch`하므로, 실제로 던져지는 `DataIntegrityViolationException`이 안 잡히고 그대로 500으로 샌다.
- **같은 버그가 환경에 따라 다른 예외로 발현**: 테스트(H2, 세션이 영속성 컨텍스트 안)는 flush 시 ORM이 먼저 잡아 `TransientPropertyValueException`("references an unsaved transient instance")로, 운영(MySQL, 세션이 컨텍스트 밖)은 commit 시 DB FK가 잡아 `DataIntegrityViolationException`으로. 뿌리는 하나(FK 미정리). 개념은 [learning-notes.md N-034](learning-notes.md#n-034-부모-엔티티-삭제와-자식-fk--연결-끊기unlink-vs-함께-삭제cascade-그리고-같은-버그의-두-예외).

**해결 / 예방**:
- 삭제 전에 그 책을 가리키던 세션을 **"책 미지정"으로 푼다**(`book_id = null`): `sessionRepository.unlinkBook(book)` → `bookRepository.delete(book)`. 한 트랜잭션 안에서 UPDATE→DELETE 순서라 commit 시 FK가 만족된다. `AccountService.purge`가 탈퇴 시 FK 순서(세션→타이머→유저)로 정리하는 것과 같은 패턴.
- **세션을 지우지 않는 이유**: 책을 책장에서 빼도 그날 읽은 기록(잔디·누적 시간)은 사실이라 보존해야 한다. `ReadingSession.book`은 원래 nullable("책 미지정 측정 허용")이라 null이 정상 상태.
- 일반화: 부모 삭제 시 자식 FK 정리(연결 끊기 또는 함께 삭제)를 **명시적으로** 설계하고, 컨트롤러 `catch`가 **실제 던져지는 예외 타입**을 포함하는지 본다(좁은 `IllegalArgumentException`만 잡으면 DB 예외가 500으로 샌다 — T-012/T-019와 같은 결).

---

## T-027. 구글 로그인 중 Chrome "위험한 사이트" 차단 — Safe Browsing이 신규 `.click` 도메인 오탐

**증상**: OAuth 동의 화면 게시 후, 구글 로그인 콜백(`booktimer.click/login/oauth2/code/google?...`)에서 Chrome이 빨간 **"위험한 사이트"**(기만적인 사이트/피싱) 차단 화면을 띄운다. 이전엔 안 떴다. 우리 코드/서버는 정상(200) 응답 중이고, 차단은 **브라우저 단(Google Safe Browsing)** 에서 일어난다.

**원인**: 동의 화면 게시와는 **무관**(타이밍 우연). Safe Browsing이 도메인을 **오탐(false positive)** 으로 피싱 분류한 것. 전형적 유발 조합:
- **`.click` TLD** — 피싱 악용이 잦아 평판이 낮은 TLD라 공격적으로 의심받음.
- **갓 등록한 신규 도메인**(평판 이력 없음).
- **로그인 폼 + 비밀번호 입력** + URL에 `accounts.google.com`이 든 OAuth 콜백 → "구글 로그인 사칭 피싱"처럼 보이는 휴리스틱에 걸림.

**해결 / 예방**:
- **Google Search Console**에 `booktimer.click` 속성 등록(Route 53 본인 존이라 **DNS TXT** 인증 쉬움) → **보안 및 수동 조치 → 보안 문제** 리포트에서 사유 확인 → **검토 요청(Request Review)**. 보통 며칠 내 해제.
- 현재 상태 조회: `https://transparencyreport.google.com/safe-browsing/search?url=booktimer.click`.
- 임시(본인 테스트만): 차단 화면 **세부정보 → "계속 이동"**. 일반 사용자에겐 기대 불가 → 검토 요청이 본 해결책.
- **근본 재발 방지**: `.click`은 평판이 근본적으로 낮아 재발 위험. 잦으면 `.com`/`.app`(HTTPS 강제라 평판 양호) 같은 평판 좋은 TLD로 이전(도메인·ACM 인증서·Route53·**OAuth 리디렉션 URI/JS origin 재등록** 동반). plan.md 백로그.
- 개념: [learning-notes.md N-036](learning-notes.md#n-036-safe-browsing은-서버가-아니라-도메인-평판휴리스틱으로-차단--tld-평판이-신규-사이트-오탐을-키운다).

**실제 결말(2026-06-03)**: Search Console 도메인 인증(DNS TXT)을 마치자 **재평가로 자연 해소**됐다 — 검토 요청 버튼을 누를 필요도 없었다.
- Search Console **보안 문제 = "감지된 문제 없음"**, Transparency Report = 활성 위험 등재 없음("데이터를 확인할 수 없음"). 즉 구글 **공식 판정은 처음부터 깨끗**, 빨간 화면은 **클라이언트 측 휴리스틱 오탐**이었다(인증/시간 경과로 풀림).
- 단, Transparency Report에 **2020년 멀웨어 보관처리 이력**이 보였다 — 이 `.click` 도메인은 **이전 소유자 시절 멀웨어로 등재된 적 있는 재활용 도메인**. 그 과거 평판이 오탐을 키운 유력 원인 → TLD/도메인 이전 백로그(plan.md)의 근거.
- 교훈: "Search Console 문제 없음 + Transparency 깨끗"이면 **검토 요청 없이 인증만으로도 풀릴 수 있다**. 막히면 그때 Safe Browsing 오탐 신고(`safebrowsing.google.com/safebrowsing/report_error/`).

---

## T-028. 유니크 제약 추가가 같은 값을 쓰던 기존 테스트 픽스처를 깨뜨린다

**증상**: 닉네임에 유니크 제약(`uk_users_nickname`)을 추가하고 가입 서비스에 `existsByNickname` 사전검사를 넣자, **기존 통합 테스트 하나가 실패**한다(`NicknameAlreadyExistsException`). 코드는 멀쩡한데 테스트만 깨져 헷갈린다 — 그것도 한 테스트만.

**원인**: 그 테스트가 **한 메서드 안에서 두 사용자를 등록하는데, 닉네임을 동일한 하드코딩 값("독서가")으로 재사용**한다(예: IDOR 검증 — A의 책을 B가 못 본다). 지금껏 닉네임은 중복이 허용돼 문제없었지만, **새 유니크 규칙이 "닉은 중복돼도 됨"이라는 픽스처의 숨은 전제를 깨뜨린다**. 두 번째 등록에서 사전검사가 던진다.
- 대부분의 테스트는 **메서드당 한 사용자만** 등록하고 `@Transactional` 롤백으로 격리되므로 **안 깨진다** — 깨지는 건 *한 트랜잭션에서 2명 이상*을 같은 값으로 만드는 케이스뿐이라 눈에 잘 안 띈다.

**해결 / 예방**:
- 픽스처에서 **유니크해야 하는 값을 사용자별로 구분**한다. 가장 간단한 건 이메일 local part 등 이미 유니크한 키에서 파생:
  ```java
  private User register(String email) {
      String nickname = email.substring(0, email.indexOf('@')); // 이메일이 유니크 → 닉도 유니크
      return registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, today());
  }
  ```
- 일반화: **제약을 강화하는 PR은 코드뿐 아니라 "그 값을 재사용하던 테스트 데이터 가정"도 함께 바꾼다.** 전체 스위트를 돌려 깨지는 픽스처를 찾고(여기선 1개), 한 트랜잭션에서 동일 값으로 여러 행을 만들던 곳을 유니크화한다. 백필로 운영 데이터는 메웠어도(N-039) **테스트 픽스처는 별개로 손봐야** 한다.
- 개념: [learning-notes.md N-039](learning-notes.md#n-039-제약을-뒤늦게-강화하려면-기존-위반-데이터부터-백필한다-backfill) — 백필과 같은 뿌리(제약 강화의 파급).

---

## 🔄 누적 갱신

| 일자 | 추가 항목 |
|---|---|
| 2026-05-31 | 초안 + T-001~T-004 |
| 2026-05-31 | T-005 (머지 후 정리 순서) |
| 2026-05-31 | T-006 (Boot 4 @DataJpaTest import 경로) |
| 2026-06-01 | T-007 (@DataJpaTest 슬라이스 auditing 미로드 — createdAt null) |
| 2026-06-01 | T-008 (redirectedUrlPattern("**/login")이 상대경로 리다이렉트 매칭 실패 → redirectedUrl) |
| 2026-06-01 | T-006 보강 (@AutoConfigureMockMvc도 Boot 4 패키지 이동 — 추가 사례 표) |
| 2026-06-01 | T-009 (Fargate 콜드스타트 ~100s가 헬스체크 grace 120s 못 넘겨 태스크 무한 재시작 → grace 300) |
| 2026-06-01 | T-010 (ECS 안정화 대기 중 수동 update-service 경쟁 → 워크플로 run 실패, 서비스는 정상) |
| 2026-06-01 | T-011 (Fargate SSM 시크릿 pull 실패 — 퍼블릭 IP라도 서브넷 RTB에 IGW 없으면 도달 불가, 서브넷 비대칭 비결정적 실패) |
| 2026-06-01 | T-012 (가입 중복 이메일 → 처리 안 된 DataIntegrityViolationException 500, H2 롤백이라 테스트 미검출, CloudWatch 진단) |
| 2026-06-01 | T-013 (aws logs --max-items 1이 None 페이지네이션 토큰을 변수에 섞어 stream not found) |
| 2026-06-02 | T-014 (forward-headers-strategy=framework가 Boot 4 모듈 분리에서 무동작 → ForwardedHeaderFilter 명시 빈 등록, RANDOM_PORT로 검증) |
| 2026-06-02 | T-015 (ddl-auto=update가 기존 NOT NULL 못 풀어 소셜 INSERT 500 / 사설 RDS NAT 없어 CloudShell 접속 막힘 → prod ApplicationRunner 멱등 ALTER, 근본 Flyway) |
| 2026-06-02 | T-016 (flyway-core만으론 Flyway 빈 미생성 — Boot 4 autoconfig 모듈 분리 → spring-boot-flyway 추가) |
| 2026-06-02 | T-017 (공유 인메모리 H2 DB_CLOSE_DELAY=-1가 순서 의존 버그(@Import(JpaConfig) 누락)를 가림 — Flyway 추가가 순서 바꿔 폭로, 단독 실행으로 진단) |
| 2026-06-02 | T-018 (Spring Security 7(Boot 4)에서 AntPathRequestMatcher 제거 → PathPatternRequestMatcher 또는 요청 직접 판정) |
| 2026-06-02 | T-020 (Boot 4 raw spring-session-jdbc만으론 세션 외부화 조용히 무동작 — autoconfig 모듈 분리 → spring-boot-starter-session-jdbc, "다른 세션 테스트 안 깨짐"이 단서) |
| 2026-06-02 | T-019 (Boot 4에서 NoResourceFoundException이 ResponseStatusException 비-상속(ServletException+ErrorResponse)→ @ExceptionHandler가 안 잡혀 404가 500으로, jar로 상속 확인) |
| 2026-06-02 | T-021 (세션 외부화 후 SESSION 쿠키는 DefaultCookieSerializer가 써서 server.servlet.session.cookie.* 프로퍼티 무동작 → 명시 CookieSerializer 빈, Set-Cookie 직접 확인으로 진단, N-022 자매 함정) |
| 2026-06-03 | T-022 (Thymeleaf SSR 앱엔 ObjectMapper 빈이 없어 주입 시 컨텍스트 전체 로드 실패 → 자체 new ObjectMapper(), 대량 실패=컨텍스트 로드 실패 신호, N-024/T-020 부류) |
| 2026-06-03 | T-023 (읽은 적 있는 책 삭제가 reading_session FK 미정리로 부모 삭제 실패 → unlinkBook(book_id=null) 후 삭제, 세션 보존 / 좁은 catch가 DataIntegrityViolationException 놓쳐 500, 테스트는 TransientPropertyValueException로 발현, N-034) |
| 2026-06-03 | T-027 (구글 로그인 콜백에서 Chrome "위험한 사이트" 차단 — Safe Browsing이 신규 .click 도메인+로그인폼+OAuth 콜백을 피싱 오탐, 서버는 정상 / 해결=Search Console 보안문제 검토요청, 근본은 .com·.app TLD 이전, N-036) |
| 2026-06-03 | T-027 보강 (결말 — Search Console 도메인 인증만으로 재평가 자연 해소(검토요청 불필요), 공식 판정은 처음부터 깨끗=클라이언트 휴리스틱 오탐 / Transparency에 2020 멀웨어 보관처리 이력=재활용 .click 도메인의 과거 평판이 원인) |
| 2026-06-04 | T-028 (닉네임 유니크 제약 추가가 한 메서드에서 같은 닉을 두 사용자에 쓰던 IDOR 테스트를 깨뜨림 — 메서드당 1명·롤백이면 안전, 2명 동일값만 파손 / 픽스처를 이메일 기반 유니크 닉으로 / 제약 강화 PR은 테스트 데이터 가정도 바꾼다, N-039) |
