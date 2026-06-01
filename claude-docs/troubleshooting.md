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
