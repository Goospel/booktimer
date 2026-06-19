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
- [T-029. 유저 삭제 경로에서 FK 자식 정리 누락 — mock 단위테스트는 못 잡는다](#t-029-유저-삭제-경로에서-fk-자식-정리-누락--mock-단위테스트는-못-잡는다)
- [T-030. 알라딘 `QueryType=Title`이 문서와 달리 저자까지 매칭 — 결과를 신뢰 말고 후필터](#t-030-알라딘-querytypetitle이-문서와-달리-저자까지-매칭--결과를-신뢰-말고-후필터)
- [T-031. Thymeleaf `th:if="${!flag}"`에서 flag가 null이면 SpringEL이 터진다 — 모델에 항상 boolean을 넣어라](#t-031-thymeleaf-thifflag에서-flag가-null이면-springel이-터진다--모델에-항상-boolean을-넣어라)
- [T-032. Thymeleaf 함정 2종 — `th:each`+`th:replace` 우선순위 역전 & 파라미터 fragment의 인라인 렌더 NPE](#t-032-thymeleaf-함정-2종--theachthreplace-우선순위-역전--파라미터-fragment의-인라인-렌더-npe)
- [T-033. 큰 페이지에서 폼이 하단에만 있으면 CSRF 숨김필드가 응답 커밋 후 세션 생성 → 500](#t-033-큰-페이지에서-폼이-하단에만-있으면-csrf-숨김필드가-응답-커밋-후-세션-생성--500)
- [T-034. 생성자 2개(주입 + 테스트용)인 빈은 주입 생성자에 `@Autowired` 필수 — 없으면 no-arg 탐색 실패](#t-034-생성자-2개주입--테스트용인-servicebin은-주입-생성자에-autowired-필수--없으면-no-arg-탐색-실패nosuchmethodexception)
- [T-035. author `display` 규칙이 UA의 `display:none`을 이겨 `<details>`·`[hidden]`이 안 숨겨진다 (cascade origin: author > UA)](#t-035-author-display-규칙이-ua의-displaynone을-이겨-detailshidden이-안-숨겨진다-cascade-origin-author--ua)
- [T-039. 실시간 시계 통합 테스트는 자정·타임존 경계에서 플레이키 — 고정 클락을 주입하라](#t-039-실시간-시계-통합-테스트는-자정타임존-경계에서-플레이키--고정-클락을-주입하라)
- [T-043. preview_screenshot이 환경에 따라 타임아웃(렌더러는 정상) — preview_inspect/eval computed-style로 시각 검증 대체](#t-043-preview_screenshot이-환경에-따라-타임아웃렌더러는-정상--preview_inspecteval-computed-style로-시각-검증-대체)
- [T-044. GitHub branch protection PUT — 4개 최상위 키 필수(422) + PowerShell 파이프로 JSON 넘기면 400](#t-044-github-branch-protection-put--4개-최상위-키-필수422--powershell-파이프로-json-넘기면-400)
- [T-045. ECS 오토스케일링 워크플로가 service-linked role 자동 생성 권한 부족으로 실패 — CloudShell에서 직접 1회 생성](#t-045-ecs-오토스케일링-워크플로가-service-linked-role-자동-생성-권한-부족으로-실패--cloudshell에서-직접-1회-생성)
- [T-046. MockMvc nullValue 모델 단언은 속성이 없어도 통과한다 — 폴백은 실제 반대값으로 RED 검증](#t-046-mockmvc-nullvalue-모델-단언은-속성이-없어도-통과한다--폴백은-실제-반대값으로-red-검증)
- [T-047. 외부 API를 http로 호출하면 CDN(CloudFront)이 https로 301 → RestClient가 미추적해 응답이 HTML이라 JSON 파싱 실패(운영 알라딘 검색 0건)](#t-047-외부-api를-http로-호출하면-cdncloudfront이-https로-301--restclient가-미추적해-응답이-html이라-json-파싱-실패운영-알라딘-검색-0건)
- [T-048. gh pr merge --squash는 PR 제목이 아니라 커밋 메시지를 squash subject로 쓴다 — PR 제목만 정정하면 main 커밋 제목이 어긋난다](#t-048-gh-pr-merge---squash는-pr-제목이-아니라-커밋-메시지를-squash-subject로-쓴다--pr-제목만-정정하면-main-커밋-제목이-어긋난다)
- [T-049. head에 작은 스크립트/마크업을 추가했더니 특정 큰 페이지만 500(IllegalStateException) — 응답 버퍼 임계 + CSRF 세션](#t-049-head에-작은-스크립트마크업을-추가했더니-특정-큰-페이지만-500illegalstateexception--응답-버퍼-임계--csrf-세션)
- [T-050. CSS transform: perspective()로 격자 캔버스를 기울이면 클릭 좌표가 어긋나 탭-투-플레이스가 깨진다](#t-050-css-transform-perspective로-격자-캔버스를-기울이면-클릭-좌표가-어긋나-탭-투-플레이스가-깨진다)
- [T-051. 워크트리 안에서 연 세션이 gh pr merge --delete-branch를 하면 로컬 정리가 'main is already used by worktree'로 실패한다](#t-051-워크트리-안에서-연-세션이-gh-pr-merge---delete-branch를-하면-로컬-정리가-main-is-already-used-by-worktree로-실패한다)
- [T-052. 헤드리스 preview에서 WebGL+RAF 앱(Phaser 등)은 screenshot/renderer.snapshot이 타임아웃 — eval 상태/픽셀 검증으로 우회](#t-052-헤드리스-preview에서-webglraf-앱phaser-등은-screenshotrenderersnapshot이-타임아웃--eval-상태픽셀-검증으로-우회)
- [T-053. Alpine 편집 위젯에서 Phaser scene/game을 x-data 속성에 저장하니 팔레트 추가가 먹통 — reactive Proxy 오염, 클로저로 분리](#t-053-alpine-편집-위젯에서-phaser-scenegame을-x-data-속성에-저장하니-팔레트-추가가-먹통--reactive-proxy-오염-클로저로-분리)
- [T-054. defer Phaser를 파싱 즉시 인라인 스크립트가 참조해 class가 TDZ에 빠지고 캔버스가 안 뜬다](#t-054-defer-phaser를-파싱-즉시-인라인-스크립트가-참조해-class가-tdz에-빠지고-캔버스가-안-뜬다)
- [T-055. Phaser moveAbove는 a가 이미 b 위면 no-op이라 z-order는 setDepth로 박는다](#t-055-phaser-moveabove는-a가-이미-b-위면-no-op이라-z-order는-setdepth로-박는다)
- [T-056. 전역 button{width:100%} 규칙이 flex 자식 버튼으로 새 풀폭 세로 스택, 컴포넌트에 width:auto로 상쇄](#t-056-전역-buttonwidth100-규칙이-flex-자식-버튼으로-새-풀폭-세로-스택-컴포넌트에-widthauto로-상쇄)
- [T-057. PowerShell 5.1 Set-Content -Encoding utf8가 BOM 포함 UTF-8을 생성해 커밋 메시지 앞에 BOM 붙음](#t-057-powershell-51-set-content--encoding-utf8가-bom-포함-utf-8을-생성해-커밋-메시지-앞에-bom-붙음)
- [T-058. SES 프로덕션 액세스 거부 — 케이스 '사례 해결'은 승인이 아니라 요청 포기, '사례 다시 열기'로 상세 보강해 재요청](#t-058-ses-프로덕션-액세스-거부--케이스-사례-해결은-승인이-아니라-요청-포기-사례-다시-열기로-상세-보강해-재요청)
- [T-059. Thymeleaf `<script>` 안 이중 대괄호 `[[` 표기 — 배열 of 배열·주석 속 공백 `[[ ]]`도 인라인 식으로 파싱됨, object 배열로 교체](#t-059-thymeleaf-script-안-이중-대괄호--표기--배열-of-배열주석-속-공백--도-인라인-식으로-파싱됨-object-배열로-교체)
- [T-060. `@free-pure-core` 블록 순수함수 제거 시 하니스 destructure 목록 미갱신 → `ReferenceError` FAIL](#t-060-free-pure-core-블록-순수함수-제거-시-하니스-destructure-목록-미갱신--referenceerror-fail)
- [T-067. Phaser 캔버스를 CSS `transform: rotate()`로 돌리면 포인터 hit-test가 깨진다 — 카메라 회전을 쓸 것](#t-067-phaser-캔버스를-css-transform-rotate로-돌리면-포인터-hit-test가-깨진다--카메라-회전을-쓸-것)
- [T-068. 카메라 강제 회전(`cam.setRotation`)은 기기를 거꾸로 들면 방향이 반대 — 순수 반응형이 정답](#t-068-카메라-강제-회전camsetrotation은-기기를-거꾸로-들면-방향이-반대--순수-반응형이-정답)
- [T-069. 모바일 가로 첫 로드에서 마을 왼쪽 치우침 — `cam.setBounds`가 centering 음수 scrollX 클램핑](#t-069-모바일-가로-첫-로드에서-마을-왼쪽-치우침--cambounds가-centering-음수-scrollx-클램핑)

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
- 등록 서비스에서 **저장 전 `existsByEmail` 사전 확인** → 있으면 도메인 예외(`EmailAlreadyExistsException`). 컨트롤러가 잡아 500이 아닌 정상 응답으로 처리.
- **레이스 대비 이중 방어**: 동시 가입(둘 다 사전확인 통과 후 insert)은 컨트롤러에서 `DataIntegrityViolationException`도 함께 catch. (사전확인=흔한 경로, DB제약=마지막 방어선 — 둘 다 필요. [learning-notes.md N-019](learning-notes.md#n-019-db-유니크-제약은-무결성의-마지막-방어선이지-사용자-검증의-첫-방어선이-아니다))
- ⚠️ **갱신(2026-06-05, PR #159 계정 열거 완화)**: 위 두 예외를 더는 **이메일 필드 에러로 노출하지 않는다** — email은 login_id 도입 후 비공개 속성이라 "이미 가입됨"을 드러내면 계정 열거가 된다. 컨트롤러는 이를 **가입 성공과 동일한 `redirect:/login?registered`로 흡수**(계정 미생성, 응답만 동일). 즉 이 항목의 "친절한 필드 에러"는 **login_id 중복(공개 핸들)에만** 해당하고, 이메일 중복은 무응답차이 처리다.
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

## T-029. 유저 삭제 경로에서 FK 자식 정리 누락 — mock 단위테스트는 못 잡는다

**증상**: 책을 1권이라도 등록한 사용자가 **회원 탈퇴하면 FK 위반으로 실패**할 수 있다(`book.user_id` → `users` 참조). 그런데 기존 `AccountServiceTest`(mock)는 **통과**해서, 코드 리뷰·CI 어디서도 안 잡혔다.

**원인**: `AccountService.purge`가 세션·타이머는 지웠지만 **book을 안 지웠다**. `book`은 `fk_book_user`(cascade 없음)로 users를 FK 참조하므로, 자식(book)을 먼저 지우지 않으면 `userRepository.delete(user)`가 제약 위반으로 실패한다. `BookRepository.deleteByUser`는 **정의돼 있었지만 아무도 호출하지 않았다**(미사용 데드 메서드).
- **왜 mock 테스트가 못 잡았나**: 단위테스트가 `inOrder(...).verify(repo).deleteByUser(...)`로 **호출만 검증**하고 실제 DB를 안 탄다. mock 리포지토리는 FK가 없으니 "book 삭제 안 해도" 아무 일도 안 난다. **DB 제약은 mock 경계 밖**이라 누락이 보이지 않는다.

**해결 / 예방**:
- 삭제 순서를 FK 방향대로: **세션 → 타이머 → 팔로우 → 책 → 유저**. 자식이 부모보다 먼저, 그리고 `reading_session.book_id`가 book을 참조하므로 **세션은 book보다 먼저**.
  ```java
  private void purge(User user) {
      sessionRepository.deleteByUser(user);
      timerRepository.deleteByUser(user);
      followRepository.deleteByFollower(user);
      followRepository.deleteByFollowee(user);
      bookRepository.deleteByUser(user);   // ← 빠져 있던 자식 정리
      userRepository.delete(user);
  }
  ```
- **삭제 경로는 mock만으로 끝내지 말고 실제 스키마 통합 테스트를 1개라도 둔다.** 책을 가진 사용자를 만들고 탈퇴가 예외 없이 끝나는지 H2로 검증(`AccountDeletionIntegrationTest`)하면 FK 누락이 즉시 빨개진다.
- **일반화 — 새 FK(연관)를 추가할 때마다 유저/부모 삭제 경로를 점검한다.** follow(V9)를 추가하며 purge에 follow 정리를 넣다가, 같은 패턴으로 book 정리가 빠져 있던 걸 발견했다. "deleteByUser가 정의돼 있다 ≠ 호출된다."
- 자매 함정: [T-023](#t-023-읽은-적-있는-책-삭제가-reading_session-fk-미정리로-부모-삭제-실패) — 책 삭제 시 reading_session FK 미정리(같은 "부모 삭제 전 자식 정리" 뿌리). 개념: [learning-notes.md N-040](learning-notes.md#n-040-mock-단위테스트는-db-제약fk-유니크을-검증하지-못한다).

---

## T-030. 알라딘 `QueryType=Title`이 문서와 달리 저자까지 매칭 — 결과를 신뢰 말고 후필터

**증상**: 책 검색을 **제목 기준**으로 골라도, 검색어가 저자명에 든 책이 결과에 섞였다. 예) "모기"를 **제목**으로 검색했는데 저자 "모기 겐이치로"의 책(제목엔 모기 없음)이 위에 떴다.

**원인**: 알라딘 ItemSearch의 `QueryType` 문서값은 `Keyword`(제목+저자, 기본) / `Title`(제목만) / `Author`(저자만)다. 우리는 정확히 `QueryType=Title`을 보냈는데도 저자 매칭이 섞여 나왔다 — **외부 API의 `Title` 검색이 문서("제목만")만큼 엄격하지 않다**(저자도 매칭). 코드(클라이언트가 보내는 파라미터)는 멀쩡했고, 문제는 외부 API의 실제 동작이 문서와 달랐던 것.
- 디버깅 함정: "결과가 이상하다 → 내 코드가 틀렸다"로 의심을 좁히기 쉽지만, 여기선 **파라미터는 정확**했다. `from()`이 절대 `Keyword`를 안 만들고(폴백=Title), 기존 Keyword 검색이 한글로 정상 동작했으니 인코딩도 무관 → 소거법으로 **외부 API 동작**이 범인임을 특정.

**해결 / 예방**:
- **받은 결과를 우리가 한 번 더 거른다** — 고른 기준 필드(제목/저자)에 검색어가 실제로 든 결과만 남긴다. 외부 API가 `Title`을 무시하든 비엄격이든 **어느 쪽이든 보장**된다.
  ```java
  // BookService.search 후처리
  String needle = normalize(query);                 // 공백 제거 + 소문자(Locale.ROOT)
  results.stream().filter(r -> {
      String field = (type == AUTHOR) ? r.author() : r.title();
      return field != null && normalize(field).contains(needle);
  }).toList();
  ```
- **정규화 후 contains** — "Clean Code"↔"cleancode"처럼 공백·대소문자 차이로 정상 결과가 누락되지 않게(naive `equals`/원문 contains는 과도하게 거른다).
- **알려진 한계**: 페이저가 필터 *전* `totalResults`로 페이지 수를 계산해 과대 집계될 수 있다(표시만 헐겁고 동작은 무해). 정확히 하려면 필터 후 재계산.
- **일반화**: 외부 검색/필터 API의 "필드 한정" 옵션은 문서를 곧이곧대로 믿지 말고, 노출 전에 **우리가 의도한 불변식(이 필드에 검색어가 있다)을 한 번 더 강제**한다. 개념: [learning-notes.md N-041](learning-notes.md#n-041-외부-검색-api의-필드-한정-옵션은-문서대로-동작하지-않을-수-있다--결과를-신뢰-말고-후필터로-불변식을-강제).

---

## T-031. Thymeleaf `th:if="${!flag}"`에서 flag가 null이면 SpringEL이 터진다 — 모델에 항상 boolean을 넣어라

**증상**: 검색 결과 화면에 레이트리밋 안내(`rateLimited`) 플래그를 더한 뒤, **정상 검색 경로**의 기존 테스트들이 `TemplateProcessingException: Exception evaluating SpringEL expression: "!rateLimited and ..."`로 깨졌다. 정작 레이트리밋 케이스(플래그를 `true`로 넣는 경로)는 멀쩡했다.

**원인**: 템플릿에 `th:if="${!rateLimited and ...}"`를 썼는데, 정상 경로에선 컨트롤러가 `rateLimited`를 **모델에 안 넣어 null**이었다. Thymeleaf의 표준 표현식(SpringEL)은 **`!null`(null에 부정 연산)을 평가 못 해 예외**를 던진다 — OGNL/일부 템플릿 엔진의 "null=false 관대 처리"와 다르다. 즉 "값을 안 넣음 = false로 취급"이 아니라 **에러**다.

**해결 / 예방**:
- **모델에 항상 boolean을 넣는다** — 플래그를 쓰는 컨트롤러의 *모든* 경로에서 `model.addAttribute("rateLimited", false|true)`. 한 경로에서만 넣고 다른 경로에서 빠지면 null이 샌다.
  ```java
  // 정상 경로도 명시적으로 false
  model.addAttribute("results", searchService.search(me, q));
  model.addAttribute("rateLimited", false);   // ← 없으면 th:if="${!rateLimited}"가 null로 터짐
  ```
- 대안(널-세이프 표현식): `th:if="${rateLimited == true}"` / `th:if="${rateLimited != true}"` — null과 비교는 안전(`null == true` → false). 단, 모델에 항상 넣는 쪽이 의도가 더 분명.
- **일반화**: SSR 템플릿의 boolean 분기는 "안 넣으면 false"를 가정하지 말 것. 컨트롤러가 플래그를 **항상** 채우거나, 템플릿을 null-safe(`== true`)로 쓴다. 새 분기 플래그를 더할 땐 그 플래그를 세팅하는 경로가 **하나라도 빠지지 않았는지** 본다.

**확장 (2026-06-06) — null만이 아니다: `and`/`or`로 묶는 순간 String 등 non-boolean 피연산자도 같은 이유로 터진다.**
단독 `th:if="${b.purchaseLink}"`는 잘 동작한다 — Thymeleaf가 `th:if` 한 표현식의 **결과값**에 자기 truthiness(문자열은 비어있지 않으면 참)를 적용하기 때문. 그런데 `th:if="${b.purchaseLink and !self}"`처럼 **boolean 연산자(`and`/`or`/`!`)로 감싸면** 이제 SpringEL이 *연산자 단계*에서 피연산자를 boolean으로 강제하고, `purchaseLink`(String)는 boolean으로 못 바뀌어 `SpelEvaluationException`을 던진다(증상: 그 행을 렌더하는 모든 테스트가 한꺼번에 깨짐). 즉 truthiness는 **단독 `th:if` 한정**이고, boolean 연산자 안에선 안 통한다.
- **해결**: 문자열은 명시 술어로 boolean화한다 — `th:if="${!#strings.isEmpty(b.purchaseLink) and !self}"`. (null/빈 문자열 모두 false 처리되어 원래 truthiness 의도도 보존.)
- **한 줄 규칙**: `${문자열}`을 `and`/`or`/`!`와 섞지 말 것. 섞을 땐 `#strings.isEmpty(...)`·`!= null`·`== true` 같은 **명시적 boolean 술어**로 바꾼다. (null 사례=위 본문, String 사례=이 확장 — 뿌리는 "boolean 연산자는 boolean 피연산자만 받는다" 하나다.) 발견: 남의 책방에만 구매 버튼을 띄우려 `self` 조건을 더하다 밟음.

---

## T-032. Thymeleaf 함정 2종 — `th:each`+`th:replace` 우선순위 역전 & 파라미터 fragment의 인라인 렌더 NPE

**증상**: 재사용 행을 fragment로 빼서 목록을 그렸더니 `TemplateProcessingException: ... SpringEL "r.nickname" ... EL1007E: Property or field 'nickname' cannot be found on null` — 컨트롤러 MockMvc 테스트가 200 대신 500. 두 가지 다른 원인이 같은 "파라미터가 null" 증상으로 나타났다.

**원인 ①  같은 요소에 `th:each`+`th:replace`**:
```html
<!-- ✗ replace가 each보다 먼저 실행됨 -->
<li th:each="r : ${rows}" th:replace="~{::row(${r})}"></li>
```
Thymeleaf 속성 **우선순위는 숫자가 작을수록 먼저**다 — `th:insert`/`th:replace`=**100**, `th:each`=**200**. 그래서 같은 요소면 **replace가 each보다 먼저** 돌아 루프 변수 `r`이 아직 없다(null) → fragment에 null이 넘어간다.

**원인 ②  파라미터 있는 fragment를 body에 인라인 정의**:
```html
<!-- 이 정의는 호출도 되지만, 전체 페이지 렌더 때 '여기 그 자리'에서도 한 번 그려진다 -->
<li th:fragment="row(r)"> ... ${r.nickname} ... </li>
```
`th:fragment`로 정의한 요소가 템플릿 본문에 있으면, fragment 호출과 **별개로 전체 페이지를 렌더할 때 그 자리에서도 한 번 렌더**된다. 이때는 파라미터 `r`이 바인딩 안 돼 **null** → `${r.nickname}`에서 NPE.

**해결 / 예방**:
- **①**: `th:each`와 `th:replace`를 **같은 요소에 두지 말 것**. `th:block`으로 each를 감싸 분리한다:
  ```html
  <th:block th:each="r : ${rows}">
      <li th:replace="~{::row(${r})}"></li>
  </th:block>
  ```
- **②**: 파라미터 fragment를 본문에 둘 거면 **null-가드**를 건다(인라인 렌더 시 아무것도 안 그림):
  ```html
  <th:block th:fragment="row(r)">
      <span th:if="${r != null and r.self}">…</span>   <!-- r==null이면 전부 스킵 -->
  </th:block>
  ```
  더 깔끔한 길은 재사용 fragment를 **별도 `fragments.html`** 로 빼서 본문 인라인 렌더 자체를 없애는 것. 또는 그냥 **인라인 복제**(이 코드베이스의 `search.html`처럼 행 마크업을 루프 안에 직접 쓰는 방식)도 작은 중복이면 충분.
- **일반화**: 템플릿 SpEL의 "... on null"은 대개 **변수 바인딩 타이밍** 문제다. 컨트롤러 MockMvc 테스트가 *실제 템플릿을 렌더*하므로 이런 버그를 끝단에서 잡아준다(순수 서비스 테스트만으론 못 봄). 인기 카운트 drill-down(`book-readers.html`) 만들다 둘 다 밟음. 자매 함정 **T-031**(같은 SpEL null 계열).

---

## T-033. 큰 페이지에서 폼이 하단에만 있으면 CSRF 숨김필드가 응답 커밋 후 세션 생성 → 500

**증상**: 대시보드(`/`)를 그리던 중 500.
```
TemplateProcessingException: ... SpringActionTagProcessor (template: "dashboard" - line 143)
Caused by: java.lang.IllegalStateException: Cannot create a session after the response has been committed
  at SpringWebMvcThymeleafRequestDataValueProcessor.getExtraHiddenFields(...)
```
에러가 가리키는 line 143은 **맨 아래 로그아웃 폼**(`<form th:action="@{/logout}">`). 평소엔 멀쩡하다가, **특정 사용자(책 0권·진행 세션 없음)** 에서만 터졌다.

**원인 — CSRF 숨김필드는 세션을 lazy 생성하는데, 큰 페이지면 그 시점에 응답이 이미 커밋됨**:
- Spring Security의 `th:action`은 폼에 CSRF 숨김 input을 자동 주입한다(`getExtraHiddenFields`). 토큰이 `HttpSessionCsrfTokenRepository`라 **세션이 없으면 그 순간 새로 만든다**.
- 대시보드는 **독서 잔디 그래프(53주×7 ≈ 371칸 div)** 로 출력이 커서, 렌더 도중 **응답 버퍼가 commit(flush)** 된다. 커밋 후엔 `request.getSession()`이 `IllegalStateException`을 던진다.
- 그동안 안 터진 이유: 페이지 **앞쪽**(잔디 그래프 위)의 측정 시작/종료 폼이 먼저 렌더되며 그때 세션을 만들어줬다. 그런데 "측정엔 책 필수" 변경으로 **책 0권 사용자에게선 시작 폼이 사라져**, CSRF가 세션을 만드는 첫 지점이 맨 아래 로그아웃 폼으로 밀렸다 → 그땐 이미 커밋.

**해결 / 예방**:
- **렌더 전에 CSRF 토큰을 선확정**해 세션을 미리 만든다(폼 위치·페이지 크기와 무관해짐). 컨트롤러에서:
  ```java
  Object csrf = request.getAttribute(CsrfToken.class.getName());
  if (csrf instanceof CsrfToken token) {
      token.getToken();   // lazy 토큰 강제 로드 → 세션을 렌더 시작 전에 생성
  }
  ```
- **교훈**: "응답 커밋 후 세션 생성" 류 500은 **폼이 페이지 하단에만 있고 본문이 큰** 화면의 잠복 버그다. *첫 CSRF 폼이 어디서 렌더되는가*에 우연히 의존하던 것 — 폼을 옮기거나 지우면 드러난다. 근본 해결은 토큰 선확정. (버퍼 크기 키우기·폼을 앞에 두기는 미봉책.)
- **재발 트리거(#247)**: 코드량을 안 늘려도, 본문에 **표준 `<!-- … -->` 주석**이 길게 들어가면 같은 500이 난다 — Thymeleaf가 그 주석을 출력에 **그대로 실어**(T-036) 응답이 더 일찍 버퍼 commit 경계를 넘기기 때문. 개발용 주석은 **파서 수준 `<!--/* … */-->`**(출력에서 제거)로 쓰면 버퍼도 안 키우고 내부 주석 유출도 막는다.
- **개념**: learning-notes **N-044**(CSRF 숨김필드의 lazy 세션 생성 ↔ 응답 버퍼 커밋 타이밍), **N-062**(같은 함정 — 관리자 홈 카드 추가가 트리거).

---

## T-034. 생성자 2개(주입 + 테스트용)인 `@Service`/빈은 주입 생성자에 `@Autowired` 필수 — 없으면 no-arg 탐색 실패(NoSuchMethodException)

**증상**: 멀쩡하던 컨텍스트가 갑자기 **대량 실패**(`@SpringBootTest`·`@DataJpaTest` 등 컨텍스트 로드하는 테스트가 한꺼번에). 단위테스트는 통과.
```
BeanCreationException → BeanInstantiationException
  Caused by: java.lang.NoSuchMethodException: com.booktimer.quote.QuoteService.<init>()
```
NoSuchMethodException이 가리키는 건 **no-arg 생성자**(`<init>()`)인데, 그 빈엔 no-arg가 아예 없다.

**원인 — 생성자가 둘 이상인데 `@Autowired`가 없으면 Spring이 주입 대상을 못 정한다**:
- 격언을 DB로 옮기며 `QuoteService`에 생성자를 둘 뒀다: `public QuoteService(QuoteRepository)`(운영 주입)와 package-private `QuoteService(QuoteRepository, Random)`(테스트용 Random 주입 이음새).
- Spring은 **생성자가 정확히 1개면** 그걸 자동으로 쓰지만, **2개 이상이면 `@Autowired` 표시가 없는 한 어느 것도 "주입 생성자"로 못 고른다** → 마지막 폴백인 **기본(no-arg) 생성자**를 찾고, 없으니 `NoSuchMethodException`.
- 직전 버전(JSON 적재)에선 public 생성자가 **no-arg**였어서 우연히 폴백과 맞아 통과했다 — 주입받는 생성자로 바뀌며 드러났다.

**해결 / 예방**:
- **주입받을 생성자에 `@Autowired`를 명시**한다(테스트용 보조 생성자와 공존할 때 특히):
  ```java
  @Autowired
  public QuoteService(QuoteRepository repository) { this(repository, new Random()); }

  QuoteService(QuoteRepository repository, Random random) { ... } // 테스트용 — Spring은 안 봄
  ```
- **교훈**: "테스트용 보조 생성자를 추가했더니 컨텍스트가 대량으로 깨졌다"면 생성자 다중성을 의심하라. `NoSuchMethodException: <init>()`(no-arg)는 "Spring이 주입 생성자를 못 골라 폴백했다"는 신호. 컴파일·단위테스트는 멀쩡하고 **컨텍스트 로드 테스트만** 무더기로 깨지는 패턴(T-022·T-020과 같은 "대량 실패 = 컨텍스트 로드 실패" 부류).

---

## T-035. author `display` 규칙이 UA의 `display:none`을 이겨 `<details>`·`[hidden]`이 안 숨겨진다 (cascade origin: author > UA)

**증상**: 직접 추가 폼을 `<details class="manual-add">`로 접었는데, **배포 후 라이브에서 접어도 폼이 계속 보였다**. JS로 재보니 `details.open === false`(닫힘)인데 `form.offsetHeight === 135`(보임), `getComputedStyle(form).display === "flex"`. (스크린샷은 접힘처럼 보여 처음엔 "정상"으로 넘길 뻔 — **스크린샷 캐시에 속음**.)

**원인 — cascade는 특정성보다 origin을 먼저 본다 (author > UA)**:
- 브라우저 UA 스타일시트가 닫힌 `<details>`의 자식을 숨긴다: `details:not([open]) > *:not(summary) { display: none }`(또는 동등 메커니즘).
- 그런데 author 규칙 `.book-manual-form { display: flex }`(검색 폼과 공용 selector)가 그 자식(form)을 직접 타깃해 `display:flex`를 준다.
- **CSS cascade 우선순위는 `!important` 제외하면 origin(author > UA)을 특정성보다 먼저 적용한다.** 그래서 특정성이 아무리 낮은 author 규칙이라도 UA의 `display:none`을 항상 이긴다 → 닫혀도 폼이 늘 보임.
- #189에서 `li.hidden=true`(UA `[hidden]{display:none}`)가 `.book-row{display:flex}`(author)에 져서 하나도 안 숨겨지던 것과 **완전히 같은 뿌리**.

**해결 / 예방**:
- 닫힘 상태를 author 규칙으로 **다시 숨긴다**(특정성을 충돌 규칙보다 높여):
  ```css
  .manual-add:not([open]) .book-manual-form { display: none; }   /* (0,3,0) > .book-manual-form (0,1,0) */
  ```
- 일반 규칙: **UA의 `display:none`에 기대 숨기는 메커니즘(`<details>` 접힘, `[hidden]` 속성, `<template>` 등)에서 그 요소의 `display`를 author CSS로 건드리면 깨진다** — author가 origin상 늘 이기므로. 숨겨야 할 상태를 author 규칙으로 **명시 재숨김**하거나, 그 요소에 `display`를 직접 주지 말 것.
- **검증 함정**: UI 토글·접힘은 **스크린샷만 믿지 말고 라이브 DOM(`offsetHeight`/`getComputedStyle`)을 직접 측정**하라. 여기선 스크린샷이 캐시돼 "접힘"으로 보였으나 실제 DOM은 펼쳐져 있었다. (서버 렌더 `th:open` 자체는 정상이었음 — 무결과 URL 서버 HTML에 `open="open"` 확인.)
- 개념(특정성 vs cascade origin)은 자매 함정 #189(이 항목이 그 미기록분까지 포섭).

---

## T-037. 신형 Gemini `AQ.` API 키는 `x-goog-api-key` 헤더로 401 — `?key=` 쿼리파라미터로만 통한다

**증상**: 책BTI 성향 서술이 운영에서 안 켜지고 늘 폴백(사실만 표시). CloudWatch에 앱은 정상 기동인데
`Gemini 서술 생성 실패` 로그 + Gemini가 `401 ACCESS_TOKEN_TYPE_UNSUPPORTED`. SSM 키 주입·배선·IAM은 다 정상이고
키도 AI Studio에서 갓 발급한 진짜 키인데 거부당함.

**원인 — Google의 API 키 세대 교체(2026) + 인증 채널별 호환성 차이**:
- Google이 키를 구형 `AIza…`(Traffic key)에서 신형 `AQ.…`(Authentication key)로 옮기는 중인데, **일부 계정은
  `AQ.` 키만 발급**된다(재발급해도 계속 `AQ.`). 처음엔 "잘못된 키"로 오해하기 쉬우나 AI Studio가 발급한 정상 키다.
- 같은 `AQ.` 키라도 **인증을 어디에 싣느냐로 결과가 갈린다**:
  | 방식 | 결과 |
  |---|---|
  | `x-goog-api-key: AQ…` 헤더 | ❌ 401 `ACCESS_TOKEN_TYPE_UNSUPPORTED` |
  | `Authorization: Bearer AQ…` | ❌ 401 `UNAUTHENTICATED`(OAuth 토큰 자리라 거부) |
  | `?key=AQ…` 쿼리파라미터 | ✅ 200 정상 |
- 구형 `AIza` 키는 헤더·쿼리 둘 다 됐어서, "헤더가 더 안전"이라고 헤더로 짜뒀다가 신형 키에서 조용히 깨진 것.

**해결 / 예방**:
- 어댑터를 **`?key=` 쿼리파라미터 방식**으로 호출(`GeminiReadingPersonalityNarrator.buildEndpoint`).
  `AIza`·`AQ.` 둘 다 호환되니 이 방식이 안전한 기본값. URL에 키가 실리므로 **catch에서 URL·요청을 로그에 남기지 말 것.**
- **키 검증은 반드시 쿼리파라미터로**: `curl -s "https://generativelanguage.googleapis.com/v1beta/models?key=본인키&pageSize=1"`
  → 모델 목록 JSON이면 정상. **헤더(`-H "x-goog-api-key: ..."`)로 테스트하면 멀쩡한 AQ 키도 401**이라 "키가 죽었다"고 오진하게 된다.
- curl `-H` 함정: `-H "AQ.키"`처럼 **헤더 이름 없이 값만** 주면 curl이 무효 헤더로 무시 → `403 PERMISSION_DENIED
  "unregistered callers"`(=자격증명 자체가 안 감). 헤더는 `-H "이름: 값"` 형식 필수 — 이걸로 한참 헤맸음.
- 배경: ECS 시크릿은 태스크 시작 시 주입되므로 SSM 갱신만으론 안 바뀜 — `--force-new-deployment` 필요(T-011).

---

## T-038. 세션 타임아웃을 프로퍼티로 못 늘린다(Spring Session JDBC) — `SessionRepositoryCustomizer` 빈으로

**증상**: 책 읽다 "측정 종료"를 누르니 로그아웃, ~50분 비웠다 오니 재로그인. 세션이 30분 만에 끊김.
`server.servlet.session.timeout=30d`를 application.properties에 넣어도 **여전히 30분**(테스트가 `expected: 720H but was: 30M`로 포착).

**원인 — Boot 4 + Spring Session JDBC "프로퍼티 무동작" 함정(T-014·T-021 자매)**:
- 세션 외부화(Spring Session) 이후 세션 기본 만료시간(maxInactiveInterval)은 서블릿 컨테이너가 아니라 **Spring Session
  저장소**가 들고 있어, 컨테이너용 프로퍼티 `server.servlet.session.timeout`이 저장소에 **연결되지 않는다**(기본 30분 유지).
- 게다가 독서 타이머는 **클라이언트(JS)에서만** 돌아 읽는 동안 서버 요청이 0 → 서버가 "비활성"으로 보고 30분에 끊는다(개념 **N-057**).

**해결 / 예방**:
- 타임아웃: `SessionRepositoryCustomizer<JdbcIndexedSessionRepository>` 빈으로 저장소에 직접
  `setDefaultMaxInactiveInterval(Duration.ofDays(30))`(`WebConfig`). 프로퍼티는 의도 문서화·컨테이너 기본값용으로만 남김.
- 브라우저 닫아도 유지: 쿠키 Max-Age도 `DefaultCookieSerializer.setCookieMaxAge(seconds)`로 영속화(기본 -1=세션 쿠키라 창 닫으면 소멸).
- **검증 필수**(이 함정은 "넣었으니 됐겠지"로 놓치기 쉬움 — 효과를 반드시 테스트):
  - `sessionRepository.createSession().getMaxInactiveInterval()`이 30일인지(프로퍼티만 넣으면 30분이라 Red로 포착).
  - 로그인 응답 `Set-Cookie: SESSION=...`에 `Max-Age=2592000`이 실리는지.
- 같은 "프로퍼티 무동작→명시 빈" 계열: **T-014**(forward headers), **T-021**(쿠키 SameSite/Secure). 개념은 **N-057**.

---

## T-039. 실시간 시계 통합 테스트는 자정·타임존 경계에서 플레이키 — 고정 클락을 주입하라

**증상**: 코드와 무관한 기존 테스트가 어느 날 갑자기 CI에서 깨진다. 로컬 커밋 땐 통과했는데(`./gradlew test` 게이트도 통과) 머지 후 CI가 `546 tests, 3 failed`로 **배포를 skip**. 깨진 건 "오늘 부채 = 목표 − 오늘 읽은 초" / "오늘 수동입력이 오늘 부채를 줄인다" 같은 **"오늘" 기준** 통합 테스트.

**원인 — `@SpringBootTest`가 운영 `Clock` 빈(`Clock.systemUTC()`, 실시간)을 그대로 쓴다**:
- 테스트가 `clock.instant()`("지금")으로 "오늘 세션"을 만든다(예: `now − 30분 ~ now`). **자정 직후**(예: 06-08 00:0x KST) 실행되면 `now − 30분`이 **전날**로 넘어가, 세션이 유저 TZ 기준 어제 날짜로 묶여(N-010) "오늘 부채"가 안 줄어든다 → 단언 실패.
- **타임존 경계**도 같이 터질 수 있다: 한 테스트가 설정 변경으로 유저 tz를 `America/New_York`로 바꾸면, 목표 변경 이력의 effectiveDate가 그 tz로 계산돼(예: NY 06-16) 테스트의 SEOUL `today()`(06-17)와 어긋난다 — 두 tz의 달력 날짜가 다른 순간에만 발현.
- 즉 **테스트가 "실행 시각"에 의존**해 자정/월말/tz 경계에서만 깨지는 잠복 플레이키. 커밋 게이트는 그 경계가 아닌 시각에 돌면 통과시켜 못 잡는다.

**해결 / 예방**:
- 시간 의존 통합 테스트는 운영 시계 대신 **고정 클락을 주입**한다(`TimeConfig` javadoc의 지침). 클래스마다 nested `@TestConfiguration`으로:
  ```java
  @org.springframework.boot.test.context.TestConfiguration
  static class FixedClockConfig {
      @org.springframework.context.annotation.Bean
      @org.springframework.context.annotation.Primary
      java.time.Clock fixedClock() {
          return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
      }
  }
  ```
  (`@Primary`라 `@Autowired Clock`·모든 컴포넌트가 고정 시각을 쓴다. nested `@TestConfiguration`은 `@SpringBootTest`가 자동 등록.)
- **고정 시각 고르기**: ① 자정에서 충분히 떨어진 **한낮**(±30분 세션이 같은 날) ② 테스트가 여러 tz를 쓰면 그 tz들이 **모두 같은 달력 날짜**가 되는 순간. 예) `09:00Z` = 18:00 KST = 05:00 EDT → SEOUL·America/New_York 둘 다 같은 날짜. ③ 월말·주말·DST 경계도 피하면 안전.
- 새 시간 의존 통합 테스트를 추가할 땐 처음부터 고정 클락으로. 운영 코드는 안 건드린다(테스트 결정성 문제).

---

## T-040. Gemini 2.5-flash가 HTTP 200인데 `parts[0].text`가 빈 문자열 — thinking이 출력 예산을 삼킨다

**증상**: LLM 서술이 "한 번씩" 안 나온다(빈 화면/폴백). 키도 멀쩡(T-037 해결됨)하고 네트워크 예외도 없는데(catch 로그 안 찍힘) 어쩔 때만 결과가 빈다. 재시도하면 나오기도 한다.

**원인 — "성공인데 알맹이 없음"은 예외가 아니다**:
- `gemini-2.5-flash`(및 2.5 계열)는 **thinking이 기본 ON**이다. 요청에 `maxOutputTokens`를 안 주고 `responseMimeType=application/json`만 주면, 모델의 thinking 토큰이 출력 예산을 소진해 응답이 **HTTP 200**이면서 `candidates[0].content.parts[0].text`가 **빈 문자열**(또는 parts 자체가 빔)로 온다(`finishReason`이 `MAX_TOKENS`인 경우도).
- 빈 본문은 `try/catch`에 안 걸린다(200이라 정상 응답). 그래서 "키/네트워크 문제 아닌데 왜 비지?"로 헤맨다. 파싱이 빈 text→빈 결과로 폴백시키는 건 올바르지만, **근본은 요청 쪽**이다.

**해결 / 예방** (`generationConfig`에 두 필드 추가):
```java
genConfig.put("maxOutputTokens", 2048);                       // 출력 상한(서술 한 문단엔 충분)
genConfig.putObject("thinkingConfig").put("thinkingBudget", 0); // 2.5-flash thinking 비활성
```
- `thinkingBudget=0` → 2.5-flash의 thinking을 꺼 출력 예산이 onto 서술로 온전히 간다. `maxOutputTokens`로 상한도 명시(비용·지연).
- **검증**: `buildRequestBody`가 두 필드를 싣는지 정적 단위 테스트로 단언(네트워크 없이). 외부 응답은 형식·내용까지 불신(N-041) — "200=성공"이 아니라 "쓸 수 있는 본문이 왔나"로 본다.
- 외부 호출의 빈/지연 응답은 화면을 깨면 안 되므로, 호출자에서 **직전 캐시(stale) 폴백**도 함께(serve-stale-on-error, N-060).

---

## T-041. Thymeleaf `#temporals.format(Instant, …)`는 서버 기본 타임존으로 찍는다 — 표시 시각은 뷰에서 유저 TZ로 변환

**증상**: 책BTI 분석 카드의 "분석 시각"이 한국 사용자에게 **9시간 이르게** 표시됐다(실제 17:43이 08:43으로). 저장값은 정상, 화면 표기만 어긋남. 로컬·테스트(한국 TZ)에선 안 보이고 **프로덕션(UTC 컨테이너)에서만** 틀려 늦게 발견된다.

**원인 — `Instant`는 타임존이 없어, 포맷터가 어딘가의 존을 빌려 쓴다**:
- 시각을 `Instant`(절대 시점, TZ 무관)로 저장하는 건 옳다. 문제는 **표시**다.
- 템플릿이 `#temporals.format(entry.generatedAt(), 'yyyy-MM-dd HH:mm')`로 `Instant`를 바로 찍으면 Thymeleaf가 **서버 JVM 기본 타임존**으로 변환한다. ECS 컨테이너는 보통 **UTC**라 한국 사용자에게 9시간 밀려 보인다.

**해결 / 예방**:
- **표시 변환을 뷰 모델로 끌어와 유저 TZ를 명시 적용**한다(템플릿의 암묵 변환 금지):
  ```java
  // PersonalityView — zone = ZoneId.of(user.getTimezone()) 주입
  public String formatTime(Instant t) {
      return (t == null) ? "" : TIME_FORMAT.format(t.atZone(zone));
  }
  ```
  템플릿: `th:text="${view.formatTime(entry.generatedAt())}"`.
- **교훈**: 저장은 `Instant`로, **표시는 반드시 "누구의 타임존이냐"를 정해 변환**한다(역할 분리). "로컬은 맞는데 운영만 N시간 틀림"은 거의 항상 *서버 기본 TZ로 Instant를 찍은* 신호 — 변환 지점에 유저 TZ가 있는지 본다.
- **개념**: learning-notes **N-010**(절대 시점 ≠ 민간 날짜/시각, 변환엔 누구의 타임존이 필요). 테스트 쪽 자매 함정은 T-039(고정 클락). (PR #247)

---

## T-042. 마우스 드래그 캐러셀이 손을 안 따라온다 — 컨테이너 `scroll-behavior: smooth`가 `scrollLeft` 직접 대입까지 애니메이션화

**증상**: `scroll-snap` 캐러셀에 마우스 드래그를 직접 구현(`pointermove`마다 `track.scrollLeft = startLeft - dx`)했는데, 끌어도 콘텐츠가 손을 1:1로 안 따라오고 **끊기거나 제자리로 튀어** 보인다. 디버깅으로 `track.scrollLeft = 150` 직후 읽으면 **`0`** (대입이 안 먹은 듯).

**원인 — `scroll-behavior: smooth`가 `scrollLeft` IDL 대입까지 "스크롤"로 보고 애니메이션화**:
- CSSOM-View 스펙상 `scrollLeft`/`scrollTop` 대입도 스크롤 동작이라, 컨테이너 computed `scroll-behavior`가 `smooth`면 **즉시 반영이 아니라 부드럽게 애니메이션**된다(Chromium 구현). 그래서 대입 직후 읽으면 아직 0.
- 더해서 `scroll-snap-type: x mandatory`가 진행 중인 비-스냅 위치를 가장 가까운 스냅으로 **되당긴다**(150은 스냅점이 아니라 0으로 회귀).
- 두 효과가 겹쳐 드래그가 손을 못 따라오고 끊겨 보인다. (탄력 스냅을 위해 넣어둔 `smooth`가 드래그를 깨는 자가당착.)

**해결 / 예방**:
- 드래그가 **확정되는 순간** 인라인으로 `track.style.scrollBehavior = 'auto'`(즉시 반영), **놓을 때** `track.style.scrollBehavior = ''`로 CSS `smooth` 복원:
  ```js
  // 드래그 시작(임계 넘김) 시:
  track.style.scrollBehavior = 'auto';   // 손 따라 1:1
  // 드래그 종료(pointerup/cancel) 시:
  track.style.scrollBehavior = '';       // CSS smooth 복원 → 놓는 순간 mandatory 스냅이 부드럽게 카드 중앙으로(탄력)
  ```
- **확인법**: 같은 `el.scrollLeft = N`이 `auto`에선 즉시(읽으면 그 자리), 종료 후 `getComputedStyle(el).scrollBehavior`가 다시 `smooth`.
- **교훈**: 즉시 스크롤이 필요한 호출만 콕 집어 `auto`로(전역 CSS는 그대로 둠). `scrollTo({behavior:'instant'})`도 대안. "대입했는데 0"은 거의 항상 smooth-scroll 애니메이션 중인 신호.
- **개념**: learning-notes **N-065**(중앙 정렬 캐러셀의 4가지 클라이언트 함정 — 이 함정이 ④번). (PR #267)

---

## T-043. preview_screenshot이 환경에 따라 타임아웃(렌더러는 정상) — preview_inspect/eval computed-style로 시각 검증 대체

**증상**: 디자인 작업에서 `preview_screenshot`이 `timed out after 30s. The preview window may be stuck`로 계속 실패한다. 그러나 같은 서버에 `preview_eval`은 정상 응답(`document.readyState === "complete"`, `getComputedStyle` 값 반환)하고 `preview_console_logs`에 에러 0 — **렌더러는 멀쩡하고 캡처 단계만 멈춘 것**.

**원인**: 이 환경에서 스크린샷 캡처(헤드리스 렌더러 → 이미지 인코딩) 경로가 행(hang)한다. 페이지 로드·DOM·JS 실행과는 독립이라, 페이지가 정상 렌더돼도 이미지만 못 받는다. (재현: #287 랜딩 디자인 리프레시 — landing/책장/기록 목업 모두 eval은 되는데 screenshot만 타임아웃.)

**해결 / 대체 검증** (스크린샷 없이 시각 변경을 정밀 확인):
- **색·폰트·크기**는 `preview_inspect`(또는 `preview_eval`+`getComputedStyle`)로 computed-style을 **값으로** 단언 — 스크린샷 눈대중보다 정확(read_me도 "색·폰트는 inspect로" 권장).
  ```js
  // 예: 토큰 적용·폰트 로드 확인
  getComputedStyle(document.body).backgroundColor   // "rgb(243, 238, 228)" = #F3EEE4
  getComputedStyle(document.body).fontFamily.split(',')[0]  // "Gowun Dodum"
  await document.fonts.ready; document.fonts.check("16px 'Gowun Dodum'")  // true
  ```
- **레이아웃·정렬·대비**는 `getBoundingClientRect`·boundingBox로 지오메트리 측정(#276에서도 같은 우회 — 버튼 width/우측 정렬을 픽셀로).
- **사용자에게 시각 공유**가 필요하면 별도 채널(인라인 위젯 등)로 시안을 렌더하거나, 실제 브라우저에서 직접 확인을 안내(`bootRun`/정적 목업 URL).

**교훈**: "스크린샷이 안 된다 ≠ 변경이 안 됐다." 렌더러 생존(eval 응답·console 무에러)을 먼저 분리 확인하고, 시각 속성은 **DOM 측정으로 대체**한다. 이 우회는 #269·#276·#287에서 반복 — 디자인/UI 검증의 기본기로 둔다.

**관련**: T-035(UI 토글은 스크린샷 캐시 말고 라이브 DOM 측정 — "스크린샷 불신"의 자매), learning-notes **N-068**(CSS 토큰 무파괴 리프레시 — 이 검증으로 회귀 0 확인).

---

## T-044. GitHub branch protection PUT — 4개 최상위 키 필수(422) + PowerShell 파이프로 JSON 넘기면 400

**증상**: main branch protection을 `gh api -X PUT .../branches/main/protection`로 적용하려는데 두 단계에서 막힘.
- 1차: 키를 일부만 보내면 `422 Validation Failed` (또는 누락 키 관련 오류).
- 2차: 키를 다 채운 JSON을 **PowerShell here-string으로 만들어 `| gh api --input -`** 파이프로 넘겼더니 `400 {"message":"Problems parsing JSON"}`.

**원인**:
- ① **4개 최상위 키 필수**: protection PUT은 `required_status_checks` / `enforce_admins` / `required_pull_request_reviews` / `restrictions` **네 키를 null이라도 모두 명시**해야 한다(GitHub API 규약). 하나라도 빠지면 422.
- ② **PowerShell 파이프 인코딩**: PowerShell 5.1에서 문자열을 네이티브 stdin으로 파이프하면 UTF-16(BOM)·CRLF 등이 섞여 `gh`(→GitHub)가 JSON 파싱에 실패 → 400. 내용은 맞는데 바이트가 틀린 케이스(T-026 한글 커밋 깨짐과 같은 뿌리).

**해결**:
- 본문을 **UTF-8 파일**로 쓰고 `gh api -X PUT ... --input <file>.json` 으로 넘긴다(파이프 금지). Write 도구로 파일 생성 → `--input`이 안전.
- 4개 키를 모두 포함:
  ```json
  {
    "required_status_checks": { "strict": true, "contexts": ["test"] },
    "enforce_admins": true,
    "required_pull_request_reviews": { "required_approving_review_count": 0 },
    "restrictions": null
  }
  ```
  (+ `allow_force_pushes`/`allow_deletions` `false`는 선택). `contexts`의 체크 이름은 **실제 워크플로 job 이름과 정확히 일치**해야 게이트가 헛돌지 않음 — `gh api repos/<o>/<r>/commits/<sha>/check-runs --jq '.check_runs[].name'`로 실측 후 등록.
- 적용 후 readback으로 확정: `gh api repos/<o>/<r>/branches/main/protection --jq '{checks: .required_status_checks.contexts, admins: .enforce_admins.enabled, ...}'`.

**닭-달걀 주의**: `contexts:["test"]`가 실재하려면 그 체크를 만드는 워크플로(ci.yml)가 **main에 먼저 안착**해야 한다. protection을 먼저 켜면 ci.yml PR이 (아직 없는) test 체크를 기다리다 막힐 수 있음 — ci.yml 머지 → protection 순서.

**교훈**: GitHub API의 "부분 업데이트 같은데 전체 키 필수"인 PUT은 422를, PowerShell의 "내용 맞는데 바이트 틀림"은 400을 던진다. 둘 다 **파일+`--input`**으로 한 번에 회피. (PR #298 — PR CI 게이트 + branch protection 도입)

**관련**: T-026(PowerShell 5.1 한글 커밋 깨짐 — 파일 우회 같은 뿌리), learning-notes **N-070**(required check + paths-ignore 머지 영구 블록 함정 — 같은 PR).

---

## T-045. ECS 오토스케일링 워크플로가 service-linked role 자동 생성 권한 부족으로 실패 — CloudShell에서 직접 1회 생성

**증상**: `Ensure ECS service autoscaling` 워크플로(`autoscaling-config.yml`)를 처음 실행하니 `register-scalable-target` 스텝에서 ~17초 만에 실패:
```
ValidationException ... User is missing the following permissions: iam:CreateServiceLinkedRole
```
바로 앞 `before describe` 스텝(권한 OK)은 통과했는데 register에서만 막혔다.

**원인**: ECS 오토스케일링이 처음 켜질 때 AWS는 `AWSServiceRoleForApplicationAutoScaling_ECSService`라는 **service-linked role**(오토스케일링이 ECS를 실제 조정할 때 쓰는 내부 역할)을 자동 생성하려 한다. 이 계정엔 그게 아직 없어서 생성을 시도했고, 워크플로 OIDC 역할(`githubActionsDeployRole`)엔 그 생성 권한(`iam:CreateServiceLinkedRole`)이 없어 거부됐다. ⚠️ `AccessDenied`가 아니라 **`ValidationException`** 형태로 온다(권한 누락인데 검증 예외 메시지라 헷갈림 — 메시지 본문의 "missing ... iam:CreateServiceLinkedRole"이 진짜 단서).

**해결 (권장 = 직접 생성, 최소권한)**: 워크플로 역할에 IAM 생성 권한을 더 주기보다, 관리자 자격(CloudShell)으로 그 role을 **직접 한 번** 만든다. 이후엔 이미 존재하니 워크플로가 생성 시도조차 안 해 추가 권한이 필요 없다:
```bash
aws iam create-service-linked-role --aws-service-name ecs.application-autoscaling.amazonaws.com
# 이미 있으면 "has been taken" — 무시(있으면 그걸로 충분)
```
그 뒤 실패한 실행을 Re-run하면 register→put이 통과한다(BookTimer는 이 방법으로 Min2/Max4/CPU70 적용 성공).

**대안**: 워크플로 역할 정책에 `iam:CreateServiceLinkedRole`(Resource를 그 role ARN으로 제한)을 더해도 되지만, OIDC 배포 역할에 IAM 쓰기 권한을 주는 셈이라 보안상 직접 생성이 낫다.

**교훈**: "한 작업이 여러 AWS 서비스에 걸치면 권한 경계가 넓어진다"(N-073)의 실제 사례 — 오토스케일링은 `ecs`·`application-autoscaling`·`cloudwatch`에 더해 **첫 1회는 `iam`(service-linked role)**까지 닿는다. 부수 리소스를 자동 생성하는 API는 "그 생성 권한"도 호출자에게 요구한다. (PR #322 후속, 실제 첫 점등에서 발생)

**관련**: learning-notes **N-073**(ECS 오토스케일링 권한 경계 — 왜 `ecs:UpdateService`를 넘어서나), deploy-aws.md §12-1b(절차·해결 스니펫), **N-030**(무중단 배포는 `ecs:UpdateService`로 충분했던 대조).

---

## T-046. MockMvc nullValue 모델 단언은 속성이 없어도 통과한다 — 폴백은 실제 반대값으로 RED 검증

**증상**: 책장 공개여부 필터(PR #327)를 TDD로 짤 때, 컨트롤러에 `visFilter` 모델 속성을 아직 안 넣은 미구현 상태인데도 폴백 검증 테스트가 **Red가 아니라 통과**했다. `visibility=garbage`면 폴백으로 `visFilter`가 null이어야 함을 `model().attribute("visFilter", nullValue())`로 단언했는데, 속성 자체가 없는데도 초록 — "실패해야 할 테스트"가 안 실패해 폴백 미구현을 못 잡을 뻔했다.

**원인**: MockMvc `model().attribute(name, matcher)`는 모델 맵에서 `get(name)`을 꺼내 matcher에 넘긴다. **속성이 아예 없으면 `get`이 `null`을 돌려주고**, Hamcrest `nullValue()`는 그 null과 매칭한다 → "속성 부재"와 "속성=null"을 구분하지 못해 둘 다 통과한다. 즉 `nullValue()` 단언은 "값이 null"이 아니라 "없거나 null"을 본다.

**해결 / 예방**:
- null/폴백 동작을 RED로 박으려면 `nullValue()` 말고 **실제 효과(반대값)로 단언**하라. 예: `visibility=garbage`(폴백→전체)가 아니라 `visibility=PRIVATE`로 "비공개 책만 남는지"를 검증하면, 미구현 시 전체가 나와 확실히 Red가 된다(본 PR에서 invalid 테스트를 PRIVATE 양방향으로 교체).
- 굳이 "명시적 null"을 봐야 하면 `model().attributeExists(name)`로 존재를 먼저 못 박고 값을 검사.
- 일반 교훈: 폴백·엣지보다 **양방향 동작**을 박는 게 distinct 실패를 잡는다 — 같은 불변식이라도 한 방향(전체로 떨어짐)만 보면 미구현이 새어 나간다(N-055 정신).

**관련**: learning-notes **N-055**(null-state가 새지 않는지 양방향으로 단언), T-030(같은 books 검색 후필터 — 외부/엣지 동작은 결과를 직접 단언).

---

## T-047. 외부 API를 http로 호출하면 CDN(CloudFront)이 https로 301 → RestClient가 미추적해 응답이 HTML이라 JSON 파싱 실패(운영 알라딘 검색 0건)

**증상**: 운영(Fargate)에서 알라딘 도서 검색이 제목·저자·출판사 **전부 0건**("검색 결과가 없습니다"). 같은 TTBKey로 PC 브라우저 직접 호출은 정상(21건). CloudWatch에 `AladinBookSearchClient … JsonParseException: Unexpected character ('<')`가 반복.

**원인**: 알라딘이 앞단 **CloudFront로 `http://`→`https://` 301 리다이렉트**를 강제. 앱은 `http://`로 호출했고 Spring `RestClient`가 **3xx를 안 따라가** 301 응답 본문(`<html>…301 Moved…CloudFront</html>`)을 그대로 받음 → `parse()`가 JSON으로 읽다 `<`에서 깨져 빈 결과 → 0건. **브라우저는 3xx를 자동 추적**해 https에서 JSON을 받아 정상이라 "내 PC는 되고 서버만 0건"으로 보였다. 코드·키·직전 기능 PR과 무관한 **외부(알라딘)가 http를 https로 막기 시작한 변경**(우리가 안 바꿔도 깨짐).

**해결 / 예방**:
- 외부 엔드포인트는 **처음부터 `https://`로** 호출한다(301 자체가 사라짐 + 평문→TLS). `ENDPOINT`/`LOOKUP_ENDPOINT` 두 상수 전환으로 끝(PR #329).
- 진단법: 응답 본문이 `<`로 시작(JSON 아님)하면 HTML/리다이렉트를 의심하고 `curl -sS -D - <url>`로 **상태줄·`Location` 헤더**를 본다(`HTTP/1.1 301` + `Location: https://`면 확정). **서버 출처에서 재현**하려면 CloudShell(AWS IP)에서 curl — 브라우저(내 PC)는 추적해 버려 재현 안 됨.
- "브라우저는 되는데 서버만 안 됨"은 리다이렉트 자동추적 차이일 수 있다(N-074).

**관련**: learning-notes **N-074**(브라우저 vs 서버 리다이렉트 추적 차이 / 외부 의존은 시간이 지나며 바뀐다), N-021(HTTPS는 앞단에서 termination), T-030·N-041(같은 알라딘 — 외부 API가 문서·관행과 다르게 동작).

---

## T-048. gh pr merge --squash는 PR 제목이 아니라 커밋 메시지를 squash subject로 쓴다 — PR 제목만 정정하면 main 커밋 제목이 어긋난다

**증상**: PR 제목을 `gh pr edit --title`로 정정한 뒤(이번엔 노트 번호 충돌로 학습노트 `N-074`→`N-075`) `gh pr merge --squash`로 머지했는데, main의 squash 커밋 **제목이 옛 제목(`…(N-074)`)** 으로 박혔다. PR 페이지 제목·실제 파일 내용(learning-notes·changelog)은 `N-075`로 맞는데 **커밋 제목만** 어긋남.

**원인**: `gh pr merge --squash`는 `--subject` 미지정 시 squash 커밋 제목을 **PR 제목이 아니라 브랜치 커밋 메시지**(단일 커밋이면 그 제목, 복수면 첫/HEAD 커밋)에서 가져온다. 웹 UI의 squash 기본(= PR 제목)과 달라서, **PR 제목만 `gh pr edit`로 고치면 무력화**된다 — 내 브랜치 첫 커밋(`docs: …(N-074)`)이 그대로 subject가 됐다.

**해결 / 예방**:
- 머지 시 최종 제목을 **명시**한다: `gh pr merge --squash --subject "docs: … (N-075)" --body "…"`. PR 제목·커밋 메시지 어느 쪽에도 안 의존해 가장 확실.
- 또는 PR 제목을 고칠 때 **커밋 메시지도 함께** 맞춘다(`git commit --amend`/새 커밋) — squash가 커밋 메시지를 보므로.
- **사후엔 못 고친다**: main 커밋 제목 수정은 history rewrite = force push인데 main force push는 금지(T-002). 그러니 **머지 전에** 맞춰야 한다. 영향은 *제목뿐* — 파일 내용·changelog가 맞으면 기능 문제는 없다.

**관련**: T-026(한글 메시지는 인라인 말고 파일로 — 같은 "메시지가 의도대로 안 실린다" 계열), T-002(main force-push 금지라 머지된 커밋 제목은 사후 수정 불가), N-070(같은 머지 게이트 맥락).

---

## T-049. head에 작은 스크립트/마크업을 추가했더니 특정 큰 페이지만 500(IllegalStateException) — 응답 버퍼 임계 + CSRF 세션

**증상**: GA4 fragment(`head`에 gtag 몇백 바이트)를 전 템플릿에 추가(#338)했더니 **`/personality`만** 500. 다른 32개 페이지는 정상. 예외는 `IllegalStateException: Cannot create a session after the response has been committed`, Thymeleaf `th:action`(SpringActionTagProcessor) 처리 중. 텍스트 충돌도 없고, 그 PR이 personality를 건드린 적도 없어 헷갈린다.

**원인**: personality는 서술·기록 카드·인라인 `<style>`로 응답이 커서 **버퍼(기본 ~8KB)가 렌더 도중 커밋**되기 직전이었다. head에 더해진 몇백 바이트가 버퍼를 임계 너머로 밀어, 맨 아래 CSRF 폼이 렌더될 때 토큰이 세션을 새로 만들려다(`getSession(true)`) 커밋 후라 실패. 개념·메커니즘은 learning-notes **N-077**.

**해결 / 예방**:
- **근본 수정**: 그 컨트롤러 GET 핸들러에서 렌더 전에 CSRF 토큰을 선확정한다 — `Object csrf = request.getAttribute(CsrfToken.class.getName()); if (csrf instanceof CsrfToken t) t.getToken();`(`DashboardController`가 이미 쓰던 패턴). 세션 생성을 응답 커밋 전으로 당긴다.
- **진단 격리**: "head에 X 추가 → 특정 페이지만 500"이면 그 페이지를 **추가 전 버전으로 되돌려 테스트** → 통과하면 그 추가가 방아쇠(버퍼 임계 확정). 범인은 추가가 아니라 *세션 선확정 누락*.
- **예방 스캔**: `th:action` 폼이 맨 아래 있는 **큰 SSR 페이지**는 같은 잠재 버그. `DashboardController`처럼 토큰 선확정을 미리 넣어둔다. (전역 버퍼 크기 증가는 다른 페이지 부작용 위험이라 차선.)
- **익명 폼 페이지는 페이지 크기와 무관하게 취약 (#350, 4번째 재발)**: 로그인·회원가입·비번재설정처럼 **비로그인 = 세션 없음** 상태의 폼 페이지는 CSRF가 *매 요청* 세션을 새로 만들어야 해서, 큰 페이지가 아니어도 응답 커밋 타이밍만 맞으면 깨진다(작은 `/login`마저 운영에서 빈 화면이 됐고, GA4 head가 임계로 민 방아쇠). 증상이 **빈 화면**(500 페이지조차 안 보임)인 이유 = 이미 커밋된 응답 뒤에 error.html이 덧붙어 **중첩·잘린 HTML**이 chunked로 나가 브라우저가 렌더 못 함(운영 `curl`이 `transfer closed`로 드러냄). 로그아웃 직후에야 `/login`을 봐서 "로그아웃하면 깨진다"로 체감됐을 뿐 `/login` 자체가 깨진 상태였다. dashboard/personality(로그인=세션 有)보다 더 위험 → **익명 폼 GET 핸들러엔 선확정을 기본 장착**(login·signup·password 일괄). 단위 테스트는 MockMvc로 commit-후-500을 재현 못 하니(폼이 어차피 세션 생성) **GET이 렌더 전 `getToken()`을 호출하는가**로 못 박는다.

**관련**: N-077(메커니즘·개념), N-078(이 회귀가 semantic merge conflict로 드러남), T-002(머지 후 사후 수정 불가라 머지 전 검증 중요), changelog #340·#350.

---

## T-050. CSS transform: perspective()로 격자 캔버스를 기울이면 클릭 좌표가 어긋나 탭-투-플레이스가 깨진다

**증상**: 정원 캔버스(격자)에 `transform: perspective()`로 3D 기울임을 주면, 셀을 탭했을 때 배치가 엉뚱한 셀에 꽂히거나 안 먹는다. 반응형·터치에서 특히 불안정.

**원인**: 클릭 hit-test는 변환된 **시각 위치** 기준인데, 격자 인덱스 계산 로직은 **원래 좌표계**를 가정한다 → 둘이 분리돼 클릭→셀 매핑이 어긋난다.

**해결 / 예방**:
- 격자 클릭 UI엔 perspective/3D 변환을 쓰지 않는다. 깊이감은 그라데이션 + 발밑 그림자 + inset 그림자로 '암시'(좌표계는 불변 유지).
- 진짜 아이소메트릭이 필요하면 DOM 격자가 아니라 캔버스(PixiJS 등)로 좌표를 직접 계산한다.

**관련**: 정원 무대화 A0에서 채택, changelog #346.

---

## T-051. 워크트리 안에서 연 세션이 gh pr merge --delete-branch를 하면 로컬 정리가 'main is already used by worktree'로 실패한다

**증상**: 별도 워크트리(`.claude/worktrees/<task>`)에서 작업한 브랜치를 `gh pr merge <n> --squash --delete-branch`로 머지하면 `failed to run git: fatal: 'main' is already used by worktree at '...'`로 끝난다. 원격 머지 자체는 성공했는데도 에러로 보여 "머지가 안 됐나" 헷갈린다.

**원인**: `--delete-branch`의 후처리는 머지한 로컬 브랜치를 지우려고 다른 브랜치(main)로 전환을 시도하는데, main이 **이미 다른 워크트리(메인 폴더)에 체크아웃**돼 있어 "같은 브랜치 동시 체크아웃 금지"에 걸린다. 머지(원격 GitHub)와 로컬 정리(git)는 **별개 단계**라, 정리만 실패하고 머지는 이미 끝나 있다.

**해결 / 예방**:
- 에러에 속지 말고 `gh pr view <n> --json state,mergeCommit`로 **머지 성공(MERGED)부터 확인**한다.
- 원격 브랜치는 `git push origin --delete <branch>`로 직접 삭제, `git fetch --prune`로 추적 정리.
- 로컬 main 갱신은 **메인 워크트리에서** `git -C <main-worktree> merge --ff-only origin/main`(그 폴더가 깨끗할 때만 — 다른 세션 점유 주의).
- 자기 워크트리·로컬 브랜치 제거는 **그 세션을 빠져나온 뒤** 메인에서 `git worktree remove <path>` → `git branch -d <branch>`(현재 점유 폴더는 자기 발밑이라 세션 중 제거 불가).

**관련**: 워크트리 격리 개념 N-032, 머지 후 정리 순서 T-005, squash subject T-048, changelog #347.

---

## T-052. 헤드리스 preview에서 WebGL+RAF 앱(Phaser 등)은 screenshot/renderer.snapshot이 타임아웃 — eval 상태/픽셀 검증으로 우회

**증상**: Phaser(또는 WebGL 캔버스 + `requestAnimationFrame` 루프) 위젯을 띄운 preview 페이지에서 `preview_screenshot`이 30초 타임아웃(`window may be stuck`)으로 실패한다. `Phaser.Game.renderer.snapshot(cb)`의 콜백조차 안 돌아온다. 그런데 `preview_console_logs`엔 에러가 0이고, 엔진은 정상 부팅(`Phaser vX (WebGL | Web Audio)`)했다.

**원인**: 이 헤드리스 렌더러는 WebGL 프레임버퍼 readback(픽셀 떠오기)을 못 하거나, RAF 루프가 계속 도는 캔버스에서 캡처가 idle 프레임을 못 잡는다. **렌더러는 살아 있고(앱은 정상 동작) 캡처 단계만 막힌다** — T-043(스크린샷 타임아웃인데 렌더러 정상)의 WebGL판. CSS-only 페이지는 `preview_inspect`/`getComputedStyle`로 우회됐지만(T-043), 캔버스는 DOM 속성이 없어 그 길도 안 통한다.

**해결 / 예방**:
- **스크린샷에 의존하지 말고 엔진 상태를 `preview_eval`로 단언**한다. 게임/씬을 `window.__scene`·`window.__game`으로 노출(목업 한정)하고:
  - 텍스처 적재: `scene.textures.get(key).getSourceImage().width > 0`(디코드 성공) — SVG→텍스처 POC를 픽셀 없이 확정(N-081).
  - 객체 타입/좌표: 게임오브젝트 `type`(`Image` vs `Text` 폴백 분기)·`x/y`(좌표 복원 정확도)·`exportPlacements()`(왕복 보존).
  - 로직 경로: `addPlant()`/`removePlant()`/`isOutsideWorld()` 같은 순수·상태 함수를 직접 호출해 결과 단언(드래그 dragend 거두기·팔레트 추가·중복 거부).
- **픽셀이 꼭 필요하면** `renderer.snapshot`을 시도하되, 안 돌아오면 환경 한계로 보고 실 브라우저 수동 게이트로 넘긴다 — 실제 제스처·시각 품질은 어차피 헤드리스로 못 잡는다(계획에 "실 브라우저 수동 게이트" 명시).
- **순수 코어는 캔버스 밖으로 빼 node로 단언**한다(`@free-pure-core` 마커 → `.preview/*.test.mjs`) — 좌표 수학은 렌더러와 무관하니 헤드리스 한계를 안 탄다.
- `preview_eval` 안에서 `location.href=...`로 **이동시키면 그 eval 컨텍스트가 끊긴다**(`Inspected target navigated`) → navigate와 측정을 **별도 eval 호출로 분리**하고 사이에 로드 대기를 둔다.

**관련**: 스크린샷 불신 자매 T-043(CSS판)·T-035, SVG→텍스처 POC N-081, "200/부팅=성공 아님, 쓸 결과가 왔나로 봄" N-041, changelog #356.

---

## T-053. Alpine 편집 위젯에서 Phaser scene/game을 x-data 속성에 저장하니 팔레트 추가가 먹통 — reactive Proxy 오염, 클로저로 분리

**증상**: 정원 "꾸미기"(자유배치 Phase 1)에서 ✏️ 진입 후 **팔레트 식물을 클릭해도 정원에 안 들어간다**(에러 토스트도 없음). 드래그·거두기 등 다른 조작도 함께 죽는다. `.preview` POC 목업과 `free-pure.test.mjs`는 통과했는데 실배포만 깨졌다.

**원인**: `garden.html`의 Alpine 컴포넌트가 Phaser 인스턴스를 **반응 속성**에 저장했다(`this.scene = new GardenScene(...)`, `this.game = new Phaser.Game({ scene: this.scene })`). Alpine 3가 `x-data` 속성을 reactive Proxy로 감싸, Proxy scene을 Phaser에 넘기니 내부 순환참조가 깨졌다(개념·일반화 N-082). mock은 Phaser를 평범한 `const`에 담아 Proxy가 없어 멀쩡 → **헤드리스 검증의 사각**: 구현 세션이 "실제 클릭은 수동 게이트"로 미룬 바로 그 경로에 버그가 숨었다.

**해결 / 예방**:
- Phaser `scene`/`game`을 컴포넌트 팩토리의 **클로저 변수**(`let scene, game`)로 옮기고 메서드는 클로저를 참조. 반응 표시 상태(`placedKeys`)만 `this.*` 유지(N-082).
- **검증을 실클릭까지**: reactive Proxy 오염은 **실제 Alpine 마운트 경로**에서만 터지므로 순수 코어 단언·eval POC만으론 못 잡는다. `preview_eval`로 `Alpine.$data(el)`을 얻어 `mountPhaser()`·`addFromPalette()`를 호출하고 `plantObjs.length` 증가를 단언(또는 reactive vs closure 대조 목업)해 "POC 통과 = 실사용 OK"의 공백을 메운다.

**관련**: 개념·일반화 N-082, 헤드리스 캡처 한계 T-052, SVG 텍스처 POC N-081, changelog Phase 1(#356) 핫픽스.

---

## T-054. defer Phaser를 파싱 즉시 인라인 스크립트가 참조해 class가 TDZ에 빠지고 캔버스가 안 뜬다

**증상**: 정원 "꾸미기"(자유배치)에서 ✏️ 진입 후 **팔레트 식물을 눌러도 정원에 안 들어가고, 캔버스(하늘/잔디/흙 배경)조차 안 그려진다**(빈 초록 박스 = `.garden-phaser` div의 CSS 배경만 보임). Phase 1(#356)부터 실배포 내내 깨져 있었는데 #358 reactive Proxy 수정(T-053/N-082)으로도 안 고쳐졌다 — **별개의 두 번째 결함**이었다. 콘솔: `ReferenceError: Phaser is not defined`(인라인 스크립트 줄) + `Cannot access 'GardenScene' before initialization`(mountPhaser).

**원인**: `garden.html`이 htmx·Alpine·**Phaser를 모두 `<head>`에 `defer`로 로드**하는데, 본문의 인라인 `<script>`(`defer` 아님)는 **파싱 중 즉시 실행**돼 `defer` 스크립트들보다 **먼저** 돈다(스펙: 비-defer 인라인은 파서를 막고 즉시 실행, defer 외부 스크립트는 파싱 완료 후). 그 인라인 안 최상위 `class GardenScene extends Phaser.Scene`가 **아직 로드 안 된 `Phaser`를 평가 시점에 참조** → `Phaser is not defined`로 던진다. 그 줄이 던져 `GardenScene` 렉시컬 바인딩이 **TDZ(초기화 안 됨)** 로 남는다. `myGarden`은 **함수 선언이라 호이스팅**돼 살아 있어 Alpine·팔레트·버튼은 정상으로 보이지만, ✏️ 클릭 시 `mountPhaser`의 `new GardenScene()`이 TDZ 에러로 죽어 **Phaser 게임·캔버스가 0개** 생성된다. **mock·헤드리스 repro가 Phaser를 `<head>`에 동기(`defer` 없이) 로드해 이 순서를 가렸다** — 그래서 #358 closure 검증도, `@free-pure-core` 단언도 통과한 채 실배포만 깨졌다(T-053과 같은 "헤드리스 사각"의 다른 얼굴 = 로드 순서판).

**해결 / 예방**:
- **Phaser 의존 클래스 정의를 파싱 시점에서 빼라.** `class GardenScene extends Phaser.Scene`를 `function ensureGardenScene(){ if (GardenScene) return; GardenScene = class extends Phaser.Scene {…} }`로 감싸 **mountPhaser(사용자 클릭 = `defer` 로드 완료 후)** 시점에 1회 평가. Phaser는 `defer` 유지(비차단 로드·보기 전용 방문 성능 무손), `myGarden`이 Alpine 전에 준비되는 의도 보존.
- **검증 하니스는 production의 스크립트 로드 속성(`defer`)을 그대로 복제하라.** mock이 외부 라이브러리를 동기 로드하면 "파싱 시점 참조" 버그를 통째로 가린다(이 트랩의 뿌리). `.preview` 하니스를 Phaser `defer`로 맞춰야 RED(캔버스 미마운트·팔레트 클릭 무반응)가 재현되고, 수정 후 GREEN(캔버스 마운트·`placedKeys` 0→1)이 의미를 가진다.
- **실배포 직접 진단이 빨랐다.** Chrome 확장으로 실계정 페이지를 열어 `read_console_messages`로 두 에러를, `javascript_tool`로 `#garden-phaser canvas` 부재를 1분 내 확정 — 헤드리스 추정보다 실 브라우저 콘솔이 로드 순서 버그엔 직격.

**관련**: 헤드리스 사각의 자매 T-053(같은 정원·다른 원인)·N-082, 헤드리스 캡처 한계 T-052, defer/TDZ/mock-masking 개념 후보 learning-notes, changelog(#364).

---

## T-055. Phaser moveAbove는 a가 이미 b 위면 no-op이라 z-order는 setDepth로 박는다

**증상**: 정원 "꾸미기" 변형 툴바에서 식물을 선택하고 **⬇(맨 뒤로)를 눌러도 아무 변화가 없다.** 맨뒤로 보낸 식물이 계속 다른 식물 위에 남고, 겹친 자리를 탭하면 **또 그 식물이 선택**돼(앞에 와야 할 식물이 안 눌림) "화살표가 작동을 안 한다"로 보인다. ⬆(맨 앞으로)는 동작하는데 ⬇만 먹통이라 더 헷갈린다.

**원인**: `GardenScene.sendToBack`이 `this.children.moveAbove(obj, this.bg)`로 식물을 "배경 바로 위 = 식물 중 맨 뒤"에 두려 했는데, **이게 no-op**이다. Phaser `DisplayList.moveAbove(A, B)`는 "A를 B 바로 위로" 옮기지만 **A가 이미 B보다 위(높은 index)면 아무것도 안 한다.** 식물(A)은 항상 배경(B) 위에 있으니 매번 무동작 → 맨뒤로가 영영 안 먹는다. (실측: `children.bringToTop(obj)`로 식물을 최상위(index 5)로 올린 뒤 `moveAbove(obj, bg)` 호출해도 index 5→5 불변.) ⬆는 `children.bringToTop`을 써 우연히 동작했지만, display-list 재정렬 방식 자체가 'A가 이미 위면 무동작'·`setDepth` 정렬에 덮임 등 취약하다.

**해결 / 예방**:
- **z-order는 display-list 재정렬(`moveAbove`/`bringToTop`)에 맡기지 말고 `setDepth`로 직접 박아라.** 논리적 순서 배열(`plantObjs`)을 단일 출처로 두고, 변경 때마다 `restack()`이 배경 `setDepth(0)`·식물 `setDepth(1..n)`·선택 오버레이 `setDepth(아주 큰 값)`을 일괄 부여. `bringToFront`/`sendToBack`은 배열 재배열 + `restack()`만, `spawn`/`remove`도 `restack()`. depth가 렌더·입력(hit-test) 순서를 결정하므로 "탭하면 맨 앞 식물 선택"도 자동으로 맞는다.
- **z 검증은 `getIndex`(display index)가 아니라 `.depth`로.** Phaser는 depth 정렬을 **렌더 프레임에** 수행하는데, 헤드리스/비가시 탭은 rAF가 throttle돼 정렬이 안 돌아 `children.getIndex`가 옛 순서를 준다. 단언은 객체의 `.depth`를 직접 보거나 `children.depthSort()`를 강제한 뒤 본다(실 브라우저는 매 프레임 정렬돼 문제없음).
- **진단은 실 브라우저 scene 상태 측정으로.** Chrome 확장 `javascript_tool`로 실계정 페이지의 scene을 잡아 `sendSelectedToBack` 전후 식물 depth·"탭 top"을 비교하면 "버튼이 안 먹는다"의 진위를 객관 확인할 수 있다(시각 추정보다 빠르고 정확).

**관련**: 같은 정원 Phaser 위젯 T-053·T-054·T-052, 변형·레이어 출하 changelog(#363), 본 수정 changelog(#365).

---

## T-056. 전역 button{width:100%} 규칙이 flex 자식 버튼으로 새 풀폭 세로 스택, 컴포넌트에 width:auto로 상쇄

**증상**: `/garden` 도감 **필터 탭**(전체·⏳시간·🌸장르·🖋️작가·출판사·🔍숨은 레시피)이 태블릿/PC처럼 **넓은 화면에서 한 줄 pill이 아니라 풀폭 버튼으로 세로로 쌓여** 길게 늘어진다. 모바일(좁은 화면)에선 풀폭 세로 버튼이 모바일 메뉴처럼 자연스러워 **안 보이고, 화면이 넓어질수록만 어색**해진다("모바일은 괜찮은데 PC만 이상").

**원인**: 전역 `button, .btn { width: 100% }`(`app.css`)가 `.garden-tab`(=`<button>`)으로 **상속**되는데, `.garden-tab`은 `padding`·`border-radius`만 자기 값으로 덮고 **`width`는 안 덮어 100%를 그대로 받는다**. `.garden-tabs`가 `display:flex; flex-wrap:wrap`이라 → **100% 너비 flex 자식**은 한 줄을 꽉 채우고 다음 자식이 줄바꿈 → **세로 스택**(flex 자식의 `width:100%`는 flex-basis 100%로 작용해 한 줄에 하나). 작은 화면에선 풀폭 세로가 자연스러워 버그가 가려지고, 컨테이너가 넓어질수록 드러난다.

**해결 / 예방**:
- **전역 `button{width:100%}`는 "폼 제출 버튼" 기준이라, flex 행·인라인에 놓는 컴포넌트 버튼(탭·칩·툴바)엔 거의 항상 틀리다.** 그런 컴포넌트엔 **`width:auto`를 명시**해 누수를 상쇄하라(`.garden-tab{width:auto}` 1줄). padding·radius만 덮고 width를 빠뜨리는 게 전형적 실수.
- **증상 시그니처**: "넓은 화면에서만 버튼이 풀폭으로 세로로 쌓임"이면 십중팔구 이 누수(flex 컨테이너 + width 미설정 `<button>`). 미디어쿼리·`flex-direction`을 의심하기 전에 **전역 button width부터** 본다.
- **반복 함정(같은 뿌리)**: #286(팔로우·언팔 칩이 풀폭, `app.css` 233줄 인라인 주석), 배너 재발송 버튼(421줄 주석), 이번 #368(정원 필터 탭). 새 버튼 컴포넌트를 flex 안에 놓을 때 **`width:auto` 기본 체크**.
- **검증**: 순수 레이아웃이라 preview 정적 하니스(실 마크업 + 진짜 `app.css`, 반응형 컨테이너까지 재현)로 폭별 `offsetTop`(한 줄=모든 탭 동일)·`getBoundingClientRect().width`(내용 너비 < 컨테이너)·`getComputedStyle(tab).width`를 **값으로 단언**(스크린샷 불신 T-043 패턴, screenshot 타임아웃돼도 수치로 확정).

**관련**: 같은 뿌리 #286(칩 풀폭)·`app.css` 233·421줄 인라인 주석, 시각 검증 T-043, 본 수정 changelog(#368).

---

## T-057. PowerShell 5.1 `Set-Content -Encoding utf8`가 BOM 포함 UTF-8을 생성해 커밋 메시지 앞에 BOM 붙음

**증상**: `.commit-msg-tmp`를 `Set-Content -Encoding utf8`로 썼을 때 커밋 메시지 제목 앞에 BOM(`﻿`, `﻿`)이 붙어 git log / GitHub에서 `﻿feat:` 처럼 보이지 않는 글자가 앞에 달린다.

**원인**: PowerShell 5.1의 `-Encoding utf8`은 **UTF-8 with BOM**을 생성한다. PowerShell 7+에서는 `utf8NoBOM`이 기본값이지만 5.1은 다르다. `git commit -F .commit-msg-tmp`가 그 BOM을 첫 글자로 포함해 커밋 제목에 박는다.

**해결 / 예방**:
- `.commit-msg-tmp`에 쓸 때는 **Bash 도구**를 사용한다 (`printf '%s' "$(cat <<'EOF'\n...\nEOF\n)" > .commit-msg-tmp`).
- PowerShell 5.1에서 BOM-less UTF-8을 강제하려면 `[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))`.
- 커밋 후 `git show HEAD --format="%s" | head -c 4 | xxd`로 첫 바이트가 `efbbbf`면 BOM 포함.
- **자매 함정**: T-026(PowerShell 5.1 인라인 한글 깨짐), T-044(JSON here-string 인코딩). `.commit-msg-tmp` 경로를 쓸 때 인코딩을 항상 의식적으로 지정한다.

**관련**: T-026(PowerShell 5.1 한글 커밋 깨짐), T-044(JSON 인코딩), PR #372.

---

## T-058. SES 프로덕션 액세스 거부 — 케이스 '사례 해결'은 승인이 아니라 요청 포기, '사례 다시 열기'로 상세 보강해 재요청

**증상**: SES 샌드박스 해제(프로덕션 액세스) 요청이 진행이 안 되거나 거부됨. AWS 지원 케이스 화면에 "고객 작업 완료"·"해결됨" 같은 상태가 떠 혼란스럽다.

**원인 / 오해**:
- **"사례 해결"(Resolve case) 버튼 = 요청 *포기*(케이스 종료)지 승인이 아니다.** 답답하다고 누르면 요청을 스스로 닫는 셈(실제로 누를 뻔함).
- **"고객 작업 완료"** = 내 답변이 등록돼 공이 AWS로 넘어간 상태(끝/승인 아님).
- AWS가 "추가 정보 요청"을 보냈는데 **상세를 안 채우고 "검토만 해달라"**고 하면 불충분한 정보로 **거부**된다("몇 가지 우려, 보안상 사유 비공개"라는 정형 거부).

**해결 / 예방**:
- **승인 여부의 진짜 신호는 케이스가 아니라 SES 콘솔** — `SES → Account dashboard`에서 "Sandbox"가 사라지고 "Production access"면 승인.
- **거부돼도 끝 아님**: 케이스 상세 우상단 **"사례 다시 열기"(Reopen)** → 답장란에 AWS가 물은 4가지(발송 빈도·수신자 목록 관리·반송/불만/수신거부 처리·메일 예시)를 **구체적으로** 담아 제출 → 보통 24h 내 재심사.
- 진행 중 요청에서 **"사례 해결"은 누르지 말 것**(요청을 닫음). 재요청하려면 "다시 열기".
- 개념(왜 샌드박스가 관문인지·외부 심사 통과 전략·토글 점등 ≠ 실발송)은 N-091.

**관련**: N-091(개념·토글≠실발송), N-067(메일 법적 분리), N-071(검증 도메인), case 178123901400162.

---

## T-059. Thymeleaf `<script>` 안 이중 대괄호 `[[` 표기 — 배열 of 배열·주석 속 공백 `[[ ]]`도 인라인 식으로 파싱됨

**증상**: `garden.html`의 `<script>` 내에서 `[[-1,0],[1,0],...]` 형태의 배열 of 배열을 쓰거나, 주석에 `[[ ]]`(공백 포함)를 썼을 때 서버 500 에러. 메시지: `Could not parse as expression: "-1,0],[1,0],..."` 또는 `Could not parse as expression: " "`.

**원인**: Thymeleaf는 `<script>` 블록 내에서도 `[[...]]`를 인라인 식으로 파싱한다. `[[-1,0],[1,0],...]` 배열 of 배열에서 `[[-1,0]`가 `[[expr]]` 인라인 식의 시작으로 인식된다. **주석 안의 `[[ ]]`도 파싱한다** — 공백만 있어도 `expression: " "` 오류가 난다.

**해결**:
- 배열 of 배열 대신 **object 배열** 사용: `[{dc:-1,dr:0},{dc:1,dr:0},...]`
- 주석에 `[[` 또는 `]]`이 들어가지 않도록 문구를 수정한다.
- BFS 방향 벡터·격자 이웃 좌표처럼 2차원 상수를 `<script>` 인라인 상수로 쓸 때 자주 마주치는 패턴이다.

**관련**: `garden.html @free-pure-core` 블록, T-032(Thymeleaf 함정), PR #384.

---

## T-060. `@free-pure-core` 블록 순수함수 제거 시 하니스 destructure 목록 미갱신 → `ReferenceError` FAIL

**증상**: `node .preview/free-pure.test.mjs`가 `ReferenceError: clampScale is not defined`로 FAIL.

**원인**: `garden.html`의 `@free-pure-core:start/end` 블록은 **단일 출처** — 하니스가 이 블록을 통째로 `new Function()`으로 평가하고, factory return 목록과 destructure 목록에서 해당 함수를 끌어온다. 블록에서 순수함수를 **제거**할 때 **두 줄을 같이 갱신하지 않으면** 남아있는 참조가 `ReferenceError`를 낸다.

**해결**: `@free-pure-core` 블록에서 함수를 추가·제거할 때 항상 하니스의 두 줄을 같이 갱신한다:
1. `factory return { ..., 함수명, ... }` — factory return 목록 (`free-pure.test.mjs` 12번째 줄)
2. `const { ..., 함수명, ... } = factory()` — destructure 목록 (13번째 줄)

**관련**: `garden.html`, `.preview/free-pure.test.mjs`, PR #387.

---

## T-061. `.gitignore` 하니스를 커밋·CI 그물로 승격할 때 — required check는 job 단위라 반드시 같은 job에 스텝 추가

**증상**: gitignore 안에 있는 테스트 하니스를 커밋으로 옮겼는데, CI required check가 그것을 잡지 못한다.

**원인**: GitHub의 required status check는 **job 이름 단위**로 등록된다. 별도 job을 만들면 새 required check로 따로 등록해야 PR 통과 조건이 되는데, 기존 `test` job만 required로 등록된 상태라면 새 job은 **선택사항**이 된다 — 실패해도 머지 차단 안 됨.

**해결**: 기존 required check job(`test`)에 **스텝으로 추가**한다. `npm --prefix frontend test`를 `test` job 안에 넣으면 별도 등록 없이 자동으로 required check에 포함된다.

**적용 예 (BookTimer)**:
```yaml
jobs:
  test:   # ← 이미 required check로 등록된 job
    steps:
      - uses: actions/setup-node@v4      # ← 기존 job에 스텝 추가
      - run: npm --prefix frontend test  # ← 별도 job으로 빼면 required 아님
      - run: ./gradlew test              # ← 기존 스텝
```

**파생 주의**: `paths-ignore`를 두면 문서-only PR에서 job이 스킵돼 required check가 `pending`으로 남아 PR을 영영 못 머지하는 함정도 같은 맥락.

**관련**: PR #388, `ci.yml`.

---

## T-064. 다중 세션 워크트리·브랜치 잔재 누적 — squash 머지로 `git branch --merged`가 머지된 feat/*를 미머지로 분류·고아 워크트리 폴더는 `prune` 미포착

**증상**: `.claude/worktrees/`에 워크트리 폴더 수십 개가 쌓이고(`git worktree list`엔 메인만 보임), 로컬 브랜치도 수십 개 누적. `git branch --merged main`이 `#399~#403`으로 squash 머지된 `feat/garden-*`를 '미머지'로 분류해 머지 여부 판단이 어긋난다.

**원인**:
1. 구현 세션들이 작업·머지 후 `git worktree remove`·`git branch -d`를 안 해 잔재가 누적된다.
2. **squash 머지**는 브랜치의 여러 커밋을 main에 **새 해시 한 개로 합쳐**, 원본 브랜치 커밋이 main 히스토리에 없다 → `--merged`가 "미머지"로 본다(실제론 머지됨). T-048과 같은 squash 특성.
3. `git worktree remove`로 워크트리 **메타(`.git/worktrees/<id>`)는 끊겼는데 디렉토리만 남으면** `git worktree prune`이 안 잡는다(prune은 메타만 청소, 고아 폴더는 대상 아님) → `git -C <고아폴더>`는 상위 메인 `.git`을 찾아 **메인 상태**를 보여줘 폴더 자체 추적이 안 된다(미커밋 점검 시 noise 주의).

**해결**:
- **브랜치**: `git branch --merged main`으로 진짜 머지된 자동브랜치(`claude/*`)는 `-d`로 안전 삭제(미머지면 `-d`가 거부 = 안전장치). squash 머지된 `feat/*`는 `--merged`가 못 잡으니 **main 로그의 PR 번호로 머지 확인 후** `-D`(또는 보존).
- **고아 워크트리 폴더**: `git worktree list`로 **활성 워크트리 0** 확인 → 각 폴더 미커밋(`git -C <d> status --porcelain --untracked-files=no`)·`HEAD`가 `origin/main`에 포함(`git merge-base --is-ancestor HEAD origin/main`)인지 점검 → 손실 위험 0이면 `rm -rf .claude/worktrees/*/`. **삭제 직전 `worktree list` 재확인**(그 사이 다른 세션이 새 워크트리를 만들었으면 중단).

**예방**: 구현 세션이 PR 머지 후 `worktree remove` + `branch -d`로 본인 잔재를 치운다(자기 발밑 워크트리는 세션 종료 후 메인에서 정리 — T-051). 안 그러면 이렇게 수십 개가 쌓여 한 번에 청소해야 한다.

**관련**: N-032(워크트리 격리), T-051(워크트리 세션 머지 후 로컬 정리), T-048(squash 머지 특성), 2026-06-19 청소(`claude/*` 19개 + 고아 폴더 19개 삭제).

---

## T-065. 실 브라우저에서 Phaser 씬 런타임 값(angle/scale/flipX)을 수치로 읽으려 했으나 — 번들 Phaser는 IIFE 클로저·프로덕션 Vue엔 DOM 컴포넌트 핸들 없음 → introspection 불가, 시각 검증으로 대체

**증상**: 걷기 애니(transform 변형)를 실 브라우저로 검증할 때, 캐릭터의 `angle`/`scaleX/Y`/`flipX`/`footY` 값을 JS로 직접 읽어 "포즈가 실제 적용되는가"를 수치로 단언하려 했으나 게임/씬 핸들에 도달할 길이 없다. `window.Phaser.GAMES`는 빈 배열(이후 undefined), 캔버스에서 부모 체인을 올라가도 `__vueParentComponent`가 없고(전 요소 `comps:0`), `window` 전역에도 `Phaser.Game`(`scene.getScene`+`loop`+`canvas`) 형태가 없다.

**원인**:
1. **번들 Phaser는 IIFE/모듈 클로저 스코프** — vite 빌드 `garden.js`가 Phaser를 번들 내부로 감싸, 게임 인스턴스는 그 모듈의 `Phaser.GAMES`에 등록된다. 페이지 전역 `window.Phaser`는 같은 객체가 아니라 핸들이 안 잡힌다.
2. **프로덕션 Vue는 DOM에 컴포넌트 인스턴스를 안 붙임** — `__vueParentComponent`/`__vue__`는 dev 빌드에서만 부착. 프로덕션은 제거돼 컴포넌트의 `setupState`(게임 ref)로 역추적이 불가. 게다가 게임 객체는 N-082 회피로 `markRaw`/클로저에 숨겨져 반응형 트리에도 없다.

**해결**: 핸들 접근을 포기하고 **시각 검증으로 확정**(T-053/054 정신 그대로). ① 순수함수(예: `walkPose`)는 단위 테스트로 수학을 못 박고(결정성), ② 배선은 **시간차 스크린샷·확대 연사**로 움직임/포즈/접지/depth/위상분산/콘솔0을 눈으로 확인. 수치 introspection이 막혔다고 검증을 멈추지 말 것 — 애초에 클라이언트 통합은 실 브라우저가 1차 게이트다. 캐릭터가 필요하면 로컬 DB에 완독 책을 임시 시드(작가 매칭)해 띄우고 **검증 후 삭제로 계정 원상복구**.

**예방**: Phaser/Vue 프로덕션 번들의 런타임 상태를 페이지에서 읽을 수 있다고 가정하지 말 것. 디버그 핸들이 꼭 필요하면 코드에서 `window.__debugGame = game`처럼 **의도적으로 노출**하는 훅을 빌드에 심어야 한다(현재 없음). 평소엔 순수함수 단위 테스트 + 실 브라우저 시각 게이트 조합이 정답.

**관련**: T-053/054(헤드리스 fake-green·실 브라우저 게이트), N-082(reactive Proxy → markRaw/클로저), N-080(시각 검증 파이프라인), PR #409(절차 걷기 애니).

---

## T-066. PowerShell에서 `gh pr create --body "$(cat <<'EOF' ...)"` 파서 오류 — bash heredoc을 PS 인라인 인자로 못 쓴다

**증상**: `gh pr create --body "$(cat <<'EOF' ...)"` 형태로 PR body를 전달하려 하면 PowerShell 5.1이 `Missing file specification after redirection operator` / `The '<' operator is reserved` 파서 오류를 낸다. 실행조차 안 됨.

**원인**: Windows PowerShell 5.1은 bash가 아니라 `<<'EOF'`를 `<`(입력 리다이렉션) 토큰으로 파싱한다. `"$(...)"` 안에서도 같다.

**해결**: PR body를 임시 파일(`.pr-body-tmp.md`)에 `Write` 툴로 쓰고, PowerShell에서 `Get-Content ".pr-body-tmp.md" -Raw`로 읽어 변수에 담아 `gh pr create --body $body`로 전달. 사용 후 `Remove-Item`으로 삭제.

```powershell
$body = Get-Content ".pr-body-tmp.md" -Raw
gh pr create --title "..." --body $body
Remove-Item ".pr-body-tmp.md" -ErrorAction SilentlyContinue
```

**예방**: PowerShell에서 멀티라인 문자열을 CLI 인자로 넘길 때는 항상 파일 경유. PowerShell `@'...'@` here-string은 변수 할당 전용이라 직접 `gh --body @'...'@`로 넘기면 역시 안 됨. T-026(한글 커밋 메시지도 `.commit-msg-tmp`로 파일 경유)과 같은 맥락.

**관련**: T-026(커밋 메시지 파일 경유), PR #410.

---

## T-067. Phaser 캔버스를 CSS `transform: rotate()`로 돌리면 포인터 hit-test가 깨진다 — 카메라 회전을 쓸 것

**증상**: 모바일 portrait에서 캔버스 DOM 또는 부모 요소에 `transform: rotate(90deg)`를 적용하면 렌더는 올바르게 보이나, 드래그·탭 등 포인터 입력이 회전 전 좌표계 기준으로 들어와 엉뚱한 위치에 반응하거나 전혀 반응하지 않는다. 편집 모드에서 식물을 탭해도 선택이 안 되고, 드래그해도 이상한 방향으로 이동한다.

**원인**: Phaser 3(3.80.1 포함) 내부 포인터 hit-test가 `getBoundingClientRect()` 기준 좌표를 쓰는데, CSS `transform: rotate()`는 레이아웃 영역을 변환하지 않아 hit-test 좌표와 시각 좌표가 어긋난다([phaserjs/phaser#7175](https://github.com/phaserjs/phaser/issues/7175)). PR #7278로 수정 진행 중이지만 3.80.x 직격.

**해결**: DOM을 회전하지 말고 **Phaser 카메라를 회전**한다 (`cam.setRotation(Math.PI / 2)`). 캔버스 DOM은 정상 방향 그대로이므로 입력↔렌더 정렬이 유지된다. 팬 제스처처럼 화면 좌표를 직접 쓰는 곳은 `cam.getWorldPoint(x, y)` 경유로 교체하면 회전이 투명하게 보정된다 ([Phaser Discourse #9710](https://phaser.discourse.group/t/.../9710) 해법).

```typescript
// ❌ DOM rotate — 입력 깨짐(Phaser#7175)
// canvas.style.transform = 'rotate(90deg)';

// ✅ 카메라 회전 — 입력 정상
cam.setRotation(Math.PI / 2);

// 팬 보정: 직접 좌표 대신 getWorldPoint 경유
const before = cam.getWorldPoint(pointer.prevPosition.x, pointer.prevPosition.y);
const after  = cam.getWorldPoint(pointer.x, pointer.y);
cam.scrollX -= after.x - before.x;
cam.scrollY -= after.y - before.y;
```

**예방**: Phaser 씬 위에서 시각 변환이 필요하면 항상 Phaser 카메라 API(setRotation·setZoom·setScroll)를 쓴다. DOM transform은 Phaser 입력 파이프라인 밖이라 좌표 불일치를 유발한다.

**관련**: T-050(CSS perspective가 격자 클릭 좌표 깨뜨림 — 같은 뿌리), N-100, PR #411.

---

## T-068. 카메라 강제 회전(`cam.setRotation`)은 기기를 거꾸로 들면 방향이 반대 — 순수 반응형이 정답

**증상**: 세로 모바일에서 `cam.setRotation(Math.PI / 2)`로 강제 가로(CW 90°)를 만들면, 사용자가 폰을 거꾸로 쥐었을 때 콘텐츠가 180° 반전된다. 또한 고정 방향이라 기기의 자동 회전과 충돌하고, DOM UI 래퍼도 함께 회전시켜야 해 복잡도가 늘어난다.

**원인**: 강제 회전은 "세로 = 항상 시계방향 90°" 라고 가정하지만, 세로에도 두 방향(정방향·역방향)이 있다. 고정 회전은 역방향으로 쥔 사용자에게 거꾸로 된 화면을 강요한다. 더불어 DOM 회전 대신 카메라 회전을 써야 한다는 T-067의 교훈을 지키더라도, "강제 회전 자체"가 UX 문제다.

**해결**: 회전을 버리고 **순수 반응형**으로 전환한다.

```typescript
// ❌ 강제 가로 회전 — 역방향 기기에서 반전
// if (portrait) cam.setRotation(Math.PI / 2);

// ✅ 순수 반응형 — 기기 자연 방향 유지, 줌으로 적응
fitCamera(w: number, h: number) {
    const cam = this.cameras.main;
    if (w > 0 && h > 0) {
        const containZ = containZoomFor(w, h, this.cfg.worldW, this.cfg.worldH);
        const plantZ   = initialZoomFor(TARGET_PLANT_CSS, this.plantPx, w, this.cfg.worldW);
        cam.setZoom(clampZoom(Math.min(plantZ, containZ)));
    }
    cam.centerOn(this.cfg.worldW / 2, this.cfg.worldH / 2);
}
```

- `containZoomFor(viewW, viewH, worldW, worldH)` = `Math.min(viewW/worldW, viewH/worldH)` — 뷰포트에 세계 전체가 들어가는 최소 줌.
- `ZOOM_MIN = 0.25`로 완화해 세로 폰(containZ ≈ 0.39)이 충분히 줌아웃 가능하게 한다.
- 팬·핀치·휠을 `readonly` 블록 밖으로 빼 보기 모드에서도 탐색 가능.
- DOM `.village-ui-wrap` 회전 래퍼·portrait 미디어 쿼리 불필요.

**예방**: 화면 방향 적응은 회전이 아니라 줌과 레이아웃으로. `cam.setRotation`은 순수 시각 효과(맵 기울기 등)에만 쓴다.

**관련**: T-067(CSS DOM rotate 금지 — 입력 깨짐), PR #411(강제 회전), PR #413(폐기·순수 반응형).

---

## T-069. 모바일 가로 첫 로드에서 마을 왼쪽 치우침 — `cam.setBounds`가 centering 음수 scrollX 클램핑

**증상**: 모바일(/village 첫 로드, 가로 방향)에서 마을이 왼쪽에 치우쳐 보인다. PC에서는 처음부터 정상.

**원인**: `cam.setBounds(0, 0, worldW, worldH)` + 가로 모바일(뷰포트 너비 < 월드 너비)에서 `cameraCenterScroll`이 음수 scrollX를 계산하는데, Phaser의 `clampToBounds`가 `scrollX < bounds.x(=0)`이면 0으로 강제 클램핑한다.

구체적으로, iPhone XR 가로(896×414) + 월드(1000×800) 기준:
- `containZoom = min(896/1000, 414/800) = 0.5175` (높이 기준 제한)
- `scrollX = 1000/2 − 896/(2×0.5175) = 500 − 865 = −365` → **음수, 클램핑!**
- 결과: `scrollX = 0` → 마을이 캔버스 좌측에 고정

PC(뷰포트≈월드 1000×800): `scrollX = 500 − 500 = 0` → 클램핑 불필요, 정상.
세로 모바일(414×896): `containZoom = 414/1000`, `scrollX = 0` 정확히 → 클램핑 없음.

**해결**: `fitCamera()`에서 centering offset만큼 bounds를 동적 확장 → 음수 스크롤 허용.

```typescript
// ✅ fitCamera() — containZoom에서 뷰포트가 월드보다 넓을 때 음수 스크롤 허용
const offX = Math.max(0, -s.scrollX);
const offY = Math.max(0, -s.scrollY);
cam.setBounds(-offX, -offY, this.cfg.worldW + 2 * offX, this.cfg.worldH + 2 * offY);
cam.setScroll(s.scrollX, s.scrollY);
```

`create()`의 `cam.setBounds(0, 0, worldW, worldH)` 정적 호출 제거 — `fitCamera`가 매 resize마다 갱신.

**예방**: `fitCamera`에서 centering 스크롤 계산 후 bounds를 그 offset 기준으로 맞춘다. `setBounds(0,0,W,H)` 고정은 "월드 > 뷰포트"에서만 올바르고, 월드가 뷰포트보다 작은 방향에서는 centering 음수 스크롤을 차단한다.

**관련**: PR #414(중앙 정렬·containZoom 도입), #415(진짜 원인 확인·수정).

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
| 2026-06-04 | T-029 (유저 삭제 경로에서 FK 자식(book) 정리 누락 — purge가 book 미삭제로 탈퇴 FK 위반, BookRepository.deleteByUser 정의됐으나 미호출 / mock 단위테스트는 호출만 검증·실제 FK 안 타 못 잡음 → 실제 H2 통합테스트로 보강 / 삭제 순서 세션→타이머→팔로우→책→유저, 새 FK 추가 시 삭제 경로 점검, 자매 T-023, N-040) |
| 2026-06-04 | T-030 (알라딘 QueryType=Title이 문서("제목만")와 달리 저자까지 매칭 — "모기" 제목검색에 저자 모기 겐이치로 책 섞임 / 파라미터는 정확, 외부 API 동작이 문서와 불일치 → 결과를 BookService.search에서 후필터(기준 필드에 검색어 든 것만, 공백·대소문자 정규화 contains), 페이저 과대집계는 알려진 한계, N-041) |
| 2026-06-04 | T-031 (Thymeleaf `th:if="${!flag}"`에서 flag가 모델에 없으면 null → SpringEL이 `!null` 평가 못 해 TemplateProcessingException, 정상 경로 테스트만 깨지고 플래그 true 경로는 멀쩡 / "안 넣으면 false" 아님 → 모든 경로에서 boolean 명시 또는 널-세이프 `== true`, 레이트리밋 안내 플래그 추가 중 발견) |
| 2026-06-04 | T-032 (Thymeleaf 함정 2종: ① 같은 요소 `th:each`(우선순위 200)+`th:replace`(100)는 replace가 먼저 돌아 루프변수 null → th:block으로 each 분리 / ② 파라미터 fragment를 본문에 정의하면 전체 페이지 렌더 때 그 자리서도 한 번 그려져 파라미터 null NPE → `th:if="${r!=null}"` 가드 또는 별도 fragments 파일·인라인 복제 / 컨트롤러 MockMvc가 실제 템플릿 렌더라 끝단에서 잡힘, drill-down book-readers.html 만들다 발견, 자매 T-031) |
| 2026-06-05 | T-033 (큰 페이지에서 폼이 하단에만 있으면 CSRF 숨김필드의 lazy 세션 생성이 응답 커밋 후 일어나 500 → 컨트롤러에서 렌더 전 토큰 선확정, N-044) |
| 2026-06-05 | T-034 (생성자 2개(주입+테스트용)인 @Service는 @Autowired 없으면 Spring이 no-arg 탐색 → NoSuchMethodException, 컨텍스트 로드 테스트만 대량 실패 → 주입 생성자에 @Autowired 명시, T-022/T-020 "대량 실패=컨텍스트 로드 실패" 부류) |
| 2026-06-04 | T-033 (큰 페이지(독서 잔디 ~371칸)에서 폼이 하단에만 있으면 `th:action` CSRF 숨김필드의 lazy 세션 생성이 응답 커밋 후라 `IllegalStateException: Cannot create a session after the response has been committed` → 500 / 평소엔 앞쪽 측정 폼이 세션을 먼저 만들어 숨었는데 "측정 책 필수"로 책 0권 사용자에게서 시작 폼이 사라지자 드러남 / 컨트롤러에서 렌더 전 `CsrfToken#getToken()`으로 토큰 선확정=세션 미리 생성, 폼 위치·페이지 크기 무관 / N-044) |
| 2026-06-06 | T-035 (author `display` 규칙이 cascade origin(author > UA)으로 UA의 `display:none`을 이겨 `<details>` 접힘·`[hidden]`이 안 숨겨짐 — `.book-manual-form{display:flex}`가 닫힌 details 자식 숨김을 무력화 → `.manual-add:not([open]) .book-manual-form{display:none}`로 명시 재숨김 / 특정성보다 origin이 먼저, #189 `[hidden]` 함정과 동일 뿌리 / UI 토글은 스크린샷 말고 라이브 DOM offsetHeight 측정으로 검증) |
| 2026-06-06 | T-036 (Thymeleaf 일반 주석 `<!-- -->`은 파싱 후에도 **클라이언트 HTML로 그대로 출력**된다 — 렌더된 HTML을 substring으로 단언하는 MockMvc 테스트가 주석 속 텍스트에 가짜로 걸림. 예: "구매 버튼 미노출"을 `html.doesNotContain("/buy")`로 단언했는데 주석에 `.../books/{id}/buy` 설명이 있어 Red / 해결 ① 단언을 **해석된 실제 값**으로 정밀화(리터럴 `{loginId}` 대신 치환된 `/u/openking/books/`처럼 — 앵커가 진짜 렌더될 때만 나타나는 문자열) ② 출력에서 빼려면 parser-comment `<!--/* */-->` 사용(일반 주석은 의도적으로 클라에 남김 ↔ 파서 주석은 제거됨) / 본 프로젝트는 주석을 클라에 남기는 관행이라 ①로 해결 — PR #199 프로필 구매 버튼 음성 렌더 테스트) |
| 2026-06-07 | T-037 (신형 Gemini `AQ.` API 키는 `x-goog-api-key` 헤더로 401 `ACCESS_TOKEN_TYPE_UNSUPPORTED`·`Authorization: Bearer`도 401 — `?key=` 쿼리파라미터로만 통함 / 일부 계정은 `AQ.` 키만 발급(재발급해도 동일, 정상 키) / 어댑터를 buildEndpoint `?key=`로 전환해 AIza·AQ 호환 / 키 검증은 쿼리파라미터 curl로(헤더 테스트는 멀쩡한 키도 401이라 오진), `-H "값"`만 주면 무효헤더→403 unregistered / 책BTI 라이브 서술 살림) |
| 2026-06-06 | T-031 확장 (null만이 아니다: 단독 `th:if="${stringVar}"`는 Thymeleaf truthiness로 동작하지만 `and`/`or`/`!`로 묶으면 SpringEL이 피연산자를 boolean으로 강제 → String도 boolean화 못 해 `SpelEvaluationException` / `${b.purchaseLink and !self}`가 그 행 렌더 테스트 9개를 한꺼번에 깸 → `${!#strings.isEmpty(b.purchaseLink) and !self}`로 명시 술어화 / 규칙: `${문자열}`을 boolean 연산자와 섞지 말 것 — 본인 책방 구매 버튼 숨기다 발견) |
| 2026-06-07 | T-038 (세션 타임아웃을 `server.servlet.session.timeout` 프로퍼티로 못 늘림 — Boot 4 + Spring Session JDBC에선 만료시간을 서블릿 컨테이너가 아니라 Spring Session 저장소가 들고 있어 프로퍼티가 안 닿음(30분 그대로, 테스트가 720H vs 30M로 포착) / 독서 타이머는 클라이언트(JS)에서만 돌아 읽는 동안 서버 요청 0 → 30분에 끊김(N-057) / 해결: `SessionRepositoryCustomizer<JdbcIndexedSessionRepository>`로 `setDefaultMaxInactiveInterval` 직접 + 쿠키 Max-Age는 `DefaultCookieSerializer.setCookieMaxAge`(기본 -1=세션 쿠키) / 검증 필수: `createSession().getMaxInactiveInterval()`·`Set-Cookie Max-Age` / T-014·T-021 "프로퍼티 무동작→명시 빈" 자매) |
| 2026-06-08 | T-039 (실시간 시계 통합 테스트가 자정·tz 경계에서 플레이키 — `@SpringBootTest`가 운영 `Clock.systemUTC()`를 써서 `now` 기준 "오늘" 데이터가 자정 직후 전날로 넘어가거나(06-08 00:0x KST CI에서 3개 깨져 배포 skip) tz 변경 시 SEOUL today()와 어긋남 / 해결: 클래스별 nested `@TestConfiguration` `@Primary Clock.fixed(...)` — 한낮+대상 tz 모두 같은 날짜인 시각(예 09:00Z=18:00 KST=05:00 EDT) / 운영 코드 무변경, `TimeConfig` javadoc 지침) |
| 2026-06-08 | T-040 (Gemini 2.5-flash가 HTTP 200인데 `parts[0].text` 빈 문자열 — thinking 기본 ON이라 `maxOutputTokens` 미설정 시 thinking이 출력 예산 소진 → 본문 빔(`finishReason=MAX_TOKENS`일 수도), 200이라 catch에 안 걸려 "키·네트워크 멀쩡한데 왜 비지"로 헤맴 / 해결: `generationConfig`에 `maxOutputTokens`=2048 + `thinkingConfig.thinkingBudget=0`(thinking 비활성), `buildRequestBody` 정적 단위테스트로 두 필드 단언 / "200=성공" 아니라 "쓸 본문이 왔나"로 봄(N-041), 호출자엔 stale 캐시 폴백 동반(N-060) / T-037 키 문제와 구분되는 별개 빈응답 원인) |
| 2026-06-08 | T-033 보강 (#247 — 긴 표준 `<!-- -->` 주석이 출력에 실려(T-036) 본문을 키우면 같은 버퍼 commit/CSRF 500을 유발 → 개발 주석은 파서 수준 `<!--/* */-->`로) |
| 2026-06-08 | T-041 (Thymeleaf `#temporals.format(Instant)`가 서버 기본 TZ로 찍음 → 한국 사용자에게 9시간 어긋남, 뷰 모델에서 유저 TZ로 `atZone` 변환 / N-010 개념) |
| 2026-06-09 | T-033 보강 (#265 — 페이지 전용 인라인 `<head><style>`가 누적돼 응답 버퍼(8KB) 넘으면 같은 commit/CSRF 500 — `personality.html`이 7956B까지 커져 캐러셀 CSS 추가로 초과, `th:action` refresh 폼 렌더에서 `SpringActionTagProcessor` → `CsrfRequestDataValueProcessor.getExtraHiddenFields` → 세션 생성이 commit 후라 `IllegalStateException`. **MockMvc 통합 테스트가 끝단에서 잡음**(`get_ready_rendersNarrative` 등 2개 Red) / 해결: 페이지 전용 CSS도 `app.css`로 빼 인라인 본문을 비운다(T-033 주석 케이스·#247과 같은 뿌리=본문 비대화, 원인만 CSS) — 컨트롤러 토큰 선확정(T-033 본체)으로도 막히지만 버퍼 자체를 줄이는 게 근본) |
| 2026-06-09 | T-042 (마우스 드래그 캐러셀이 손을 안 따라옴 — 컨테이너 `scroll-behavior:smooth`가 `scrollLeft` 직접 대입까지 애니메이션화(CSSOM 스펙)+`scroll-snap mandatory`가 진행 중 위치 되당김 → `scrollLeft=150` 직후 읽으면 0 / 해결: 드래그 확정 시 인라인 `scrollBehavior='auto'`(즉시 추적)·놓을 때 `''` 복원(탄력 스냅 유지), 또는 `scrollTo({behavior:'instant'})` / "대입했는데 0"=smooth 애니메이션 중 신호, N-065 ④번 함정) |
| 2026-06-10 | T-043 (preview_screenshot이 환경따라 타임아웃(`window may be stuck`)인데 렌더러는 정상 — `preview_eval`은 응답·console 에러 0이라 캡처 단계만 행 / 시각 변경 검증은 스크린샷 없이 `preview_inspect`·`getComputedStyle`로 색·폰트·크기를 *값으로* 단언(눈대중보다 정확)·`getBoundingClientRect`로 레이아웃·`document.fonts.check`로 폰트 로드 / "스크린샷 안 됨 ≠ 변경 안 됨" — 렌더러 생존 먼저 분리 확인 후 DOM 측정 대체, #269·#276·#287 반복 / 스크린샷 불신 자매 T-035, 디자인 토큰 검증 N-068, PR #287) |
| 2026-06-11 | T-044 (GitHub branch protection PUT은 4개 최상위 키(`required_status_checks`/`enforce_admins`/`required_pull_request_reviews`/`restrictions`)를 null이라도 모두 보내야 함 — 누락 시 422 / PowerShell 5.1에서 JSON을 here-string 파이프로 넘기면 인코딩 깨져 400 `Problems parsing JSON` → UTF-8 파일+`--input` 사용(T-026 한글 커밋과 같은 뿌리) / `contexts` 체크 이름은 check-runs로 실측 후 등록·ci.yml 머지 후 protection 켜는 닭-달걀 순서, N-070, PR #298) |
| 2026-06-12 | T-045 (ECS 오토스케일링 워크플로 첫 실행이 `register-scalable-target`에서 `ValidationException: missing iam:CreateServiceLinkedRole`로 실패 — 첫 점등 시 AWS가 service-linked role `AWSServiceRoleForApplicationAutoScaling_ECSService`를 자동 생성하려는데 OIDC 역할에 생성 권한 없어 거부(AccessDenied 아닌 ValidationException이라 헷갈림) / 권장 해결=워크플로 역할에 IAM 권한 더하기보다 CloudShell에서 `aws iam create-service-linked-role --aws-service-name ecs.application-autoscaling.amazonaws.com` 직접 1회 생성(최소권한·이후 존재하니 Re-run 통과) / 부수 리소스 자동 생성 API는 그 생성 권한도 호출자에 요구, N-073·N-030, PR #322 후속) |
| 2026-06-12 | T-046 (MockMvc `model().attribute(name, nullValue())`가 속성 부재여도 통과 — TDD RED에서 폴백 미구현인데 초록, "속성 없음"과 "속성=null"을 못 가림 / null·폴백은 nullValue() 말고 실제 반대값(visibility=PRIVATE로 비공개만 남는지)으로 단언해야 Red, N-055 양방향, PR #327) |
| 2026-06-12 | T-047 (운영 알라딘 검색 전부 0건 — 알라딘 CloudFront가 http→https 301 강제하는데 RestClient가 미추적해 응답 본문이 리다이렉트 HTML('<html>') → parse()가 JsonParseException('<')로 빈 결과 / 브라우저는 3xx 자동추적해 정상이라 "PC는 되고 서버만 0건" / 해결=ENDPOINT https로(301 제거+TLS), 진단=curl -D -로 301·Location 확인·로그 '<', 서버출처 재현은 CloudShell / N-074, T-030, PR #329) |
| 2026-06-12 | T-048 (gh pr merge --squash는 --subject 미지정 시 PR 제목이 아니라 브랜치 커밋 메시지(단일=그 제목, 복수=첫/HEAD 커밋)를 squash subject로 씀 — 웹 UI squash 기본(=PR 제목)과 달라 gh pr edit로 PR 제목만 정정하면 무력화, 이번 N-074→N-075 정정이 main 커밋 제목엔 N-074로 박힘 / 해결=gh pr merge --squash --subject/--body 명시 또는 커밋 메시지 동기화(amend), 사후엔 main force push 금지(T-002)라 불가 → 머지 전에 / 영향은 제목뿐(파일·changelog는 정확) / 한글 메시지 경로 T-026, N-070) |
| 2026-06-13 | T-049 (head에 작은 스크립트(GA4 #338) 추가가 응답 버퍼 임계 근처였던 큰 페이지(personality)만 500 — 버퍼 커밋 후 맨 아래 CSRF 폼(th:action)이 세션 생성 못 해 IllegalStateException / 다른 32템플릿 정상·그 PR이 personality 안 건드림이라 헷갈림 / 해결=컨트롤러 GET서 렌더 전 CsrfToken.getToken() 선확정(DashboardController 패턴), 세션 생성을 커밋 전으로 / 진단=그 페이지를 추가 전으로 격리 테스트해 방아쇠 확정(범인은 추가 아닌 선확정 누락) / 예방=th:action 폼 맨 아래 큰 페이지에 토큰 선확정 미리 / 개념 N-077, semantic conflict N-078, PR #340) |
| 2026-06-14 | T-050 (CSS `transform: perspective()`로 격자 캔버스를 기울이면 셀의 화면 클릭 좌표가 원근 변환과 어긋나 탭-투-플레이스(칸 탭→배치)가 엉뚱한 셀에 꽂히거나 안 먹는다 — 반응형·터치에서 특히 불안정 / 원인=클릭 hit-test는 변환된 시각 위치 기준인데 격자 인덱스 로직은 원래 좌표계를 가정 → 둘이 분리됨 / 해결=격자 클릭 UI엔 perspective/3D 변환을 쓰지 말고 깊이는 그라데이션+발밑 그림자+inset 그림자로 '암시'(좌표계 불변 유지) / 진짜 아이소메트릭이 필요하면 DOM 격자가 아니라 캔버스(PixiJS 등)로 좌표를 직접 계산 / 정원 무대화 A0에서 채택, PR #346) |
| 2026-06-14 | T-051 (워크트리 세션이 gh pr merge --delete-branch 하면 로컬 정리가 `fatal: 'main' is already used by worktree`로 실패 — 원격 머지는 성공, --delete-branch 후처리가 로컬 브랜치 지우려 main 전환 시도하나 main이 다른 워크트리 점유라 거부 / 해결=gh pr view로 MERGED 확인 → git push origin --delete로 원격 브랜치 삭제 → 메인 워크트리서 ff-only pull → 세션 종료 후 worktree remove+branch -d(자기 발밑 폴더는 세션 중 제거 불가) / N-032, T-005, T-048, PR #347). T-050 본문·목차 누락도 함께 복원(#346이 누적표에만 추가) |
| 2026-06-15 | T-049 보강 (#350 — 익명 폼 페이지(login/signup/password)는 비로그인=세션 없음이라 CSRF가 매 요청 세션을 새로 만들어, 페이지 크기와 무관하게 commit-후-500에 취약 / 작은 /login마저 운영서 **빈 화면**(이미 커밋된 응답 뒤에 error.html이 덧붙어 중첩·잘린 HTML이 chunked로 나가 브라우저 렌더 실패, curl `transfer closed`로 확정) / 로그아웃 직후에만 /login을 봐서 "로그아웃하면 깨진다"로 체감됐을 뿐 /login 자체가 깨진 상태 / 해결=익명 폼 GET 핸들러 3곳(LoginController·SignupController·PasswordResetController)에 CsrfToken 선확정 일괄 장착 / 단위테스트는 getToken() 호출 검증(MockMvc는 작은 페이지 commit-후-500 재현 불가) / 4번째 재발, N-077) |
| 2026-06-15 | T-052 (헤드리스 preview에서 WebGL+RAF 앱(Phaser)은 screenshot/renderer.snapshot이 30s 타임아웃 — 렌더러 readback/idle 프레임 캡처 불가, 단 엔진은 정상 부팅·console 에러 0(캡처만 막힘=T-043 WebGL판) / 캔버스는 DOM 속성 없어 inspect 우회도 불가 → window.__scene/__game 노출 후 preview_eval로 텍스처 getSourceImage().width>0(디코드)·게임오브젝트 type(Image vs Text)·좌표·exportPlacements 왕복·addPlant/removePlant/isOutsideWorld 로직경로 단언 = 픽셀 없이 확정 / 순수 코어는 @free-pure-core 마커로 빼 node .test.mjs로(렌더러 무관) / eval 안 location.href 이동은 그 컨텍스트 끊김(navigated) → navigate·측정 별도 eval로 분리 / 실제 제스처·시각 품질은 실 브라우저 수동 게이트 / 정원 Phaser 자유배치 전환서, T-043·T-035, N-081, PR #356) |
| 2026-06-15 | T-053 (Alpine 편집 위젯이 Phaser scene/game을 x-data 속성(this.scene)에 저장 → reactive Proxy 오염으로 팔레트 추가·드래그 전부 먹통, 에러도 없음 / .preview POC·free-pure.test.mjs는 통과한 채 실배포만 깸=헤드리스 검증 사각(수동 게이트로 미룬 실클릭에 버그) / 해결=scene·game을 클로저 let 변수로 빼고 반응 상태(placedKeys)만 this.*, 그리고 preview_eval로 Alpine.$data(el).mountPhaser()·addFromPalette() 호출해 plantObjs 증가 단언=실클릭 경로 자동검증 / 개념·일반화 N-082, 헤드리스 한계 T-052, PR Phase1 핫픽스) |
| 2026-06-15 | T-054 (정원 꾸미기가 #356부터 실배포 내내 먹통 — htmx·Alpine·Phaser를 모두 defer로 로드하는데 본문 인라인 `<script>`(defer 아님)는 파싱 즉시 실행돼 그 안 최상위 `class GardenScene extends Phaser.Scene`가 아직 없는 Phaser를 참조→`Phaser is not defined`로 던지고 GardenScene이 TDZ로 남아 mountPhaser의 new가 죽어 캔버스 0·추가 무반응 / myGarden은 함수 선언이라 호이스팅돼 팔레트는 떠 보임 / mock·헤드리스가 Phaser를 동기 로드해 가림=#358 closure 수정과 별개 결함 "아직도 안 됨"의 정체 / 해결=클래스 정의를 ensureGardenScene()로 감싸 mountPhaser(클릭=defer 로드 후) 시점 1회 평가, Phaser는 defer 유지 / 검증 하니스도 production처럼 Phaser defer로 맞춰 RED재현 / 진단=Chrome 확장 실계정 콘솔 두 에러+canvas 부재 / 자매 T-053, T-052, PR #364) |
| 2026-06-15 | T-055 (정원 꾸미기 ⬇'맨 뒤로' 먹통 — sendToBack의 children.moveAbove(obj,bg)가 no-op: Phaser moveAbove(A,B)는 A가 이미 B 위면 무동작인데 식물은 늘 배경 위라 매번 무동작, 맨뒤로 보낸 식물이 계속 위에 남아 탭하면 또 선택 / ⬆ bringToTop은 우연히 동작=비대칭 / 해결=z-order를 plantObjs 순서 단일출처로 setDepth로 직접 박는다(restack: 배경0·식물1..n·선택테두리 최상단, bringToFront/sendToBack/spawn/remove에서 호출) — depth가 렌더·입력순서 결정 / 검증은 getIndex 말고 .depth로(헤드리스 rAF throttle로 display정렬 지연, depthSort() 강제) / 진단=Chrome 확장으로 실계정 scene depth·탭top 측정 / 자매 T-053·T-054, PR #365) |
| 2026-06-15 | T-056 (전역 button{width:100%}가 .garden-tab(=button)으로 상속되는데 .garden-tab이 padding·radius만 덮고 width를 안 덮어 100% 상속 → .garden-tabs가 flex-wrap이라 풀폭 pill이 각자 한 줄씩 세로로 쌓임(태블릿/PC만 어색, 모바일은 풀폭 세로가 자연스러워 가려짐) / 전역 button{width:100%}는 폼 제출 버튼 기준이라 flex 행·인라인 컴포넌트 버튼(탭·칩·툴바)엔 거의 틀림 → 그런 컴포넌트에 width:auto 명시로 상쇄(.garden-tab{width:auto} 1줄) / 증상 시그니처="넓은 화면에서만 버튼이 풀폭 세로 스택"이면 미디어쿼리 의심 전에 전역 button width부터 / 같은 뿌리 #286(칩 풀폭, 233줄)·배너 버튼(421줄) 반복 / 검증=preview 하니스로 offsetTop·width 값 단언, 자매 시각검증 T-043, PR #368) |
| 2026-06-16 | T-057 (PowerShell 5.1 Set-Content -Encoding utf8가 UTF-8 with BOM 생성 → 커밋 메시지 첫 글자에 BOM(﻿)이 박혀 git log/GitHub에 `﻿feat:` 표시 / PS 7+는 utf8NoBOM 기본, 5.1은 BOM 붙음 / 해결=Bash 도구 printf 사용 또는 [System.IO.File]::WriteAllText(path,content,UTF8Encoding.new($false)) / git show HEAD --format="%s" | head -c 4 | xxd로 efbbbf면 BOM / T-026·T-044 같은 뿌리(PS 5.1 인코딩), PR #372) |
| 2026-06-17 | T-058 (SES 프로덕션 액세스 거부 — AWS 지원 케이스 "사례 해결"(Resolve)=요청 포기(케이스 종료)지 승인 아님(답답해도 누르지 말 것)·"고객 작업 완료"=공이 AWS로 넘어감(끝 아님) / AWS "추가 정보 요청"에 상세 없이 "검토만 해달라" 하면 불충분으로 거부("우려 있음, 보안상 사유 비공개" 정형) / 승인 진짜 신호=케이스 아니라 SES 콘솔 Account dashboard "Production access" / 거부돼도 "사례 다시 열기"로 AWS가 물은 4가지(발송 빈도·목록 관리·반송/불만/수신거부·메일 예시) 구체 담아 재요청→보통 24h 재심사 / 개념·토글≠실발송 N-091, N-067·N-071, case 178123901400162) |
| 2026-06-18 | T-059 (Thymeleaf `<script>` 안 이중 대괄호 `[[..` — 배열 of 배열 `[[-1,0],...]`·주석 속 공백 `[[ ]]` 모두 인라인 식 파싱 오류 / 해결=object 배열 `[{dc:-1,dr:0},...]`·주석 문구 수정 / `garden.html @free-pure-core` nearestFreeCell 방향 벡터 / T-032·PR #384) |
| 2026-06-18 | T-060 (`@free-pure-core` 블록 순수함수 제거 시 하니스 factory return·destructure 양쪽 갱신 안 하면 `ReferenceError` FAIL — PR #387) |
| 2026-06-18 | T-061 (gitignore 하니스를 CI 그물로 승격 시 required check는 job 단위 — 별도 job 아닌 기존 `test` job에 스텝 추가해야 자동 포함·paths-ignore 없어야 문서 PR도 통과 — PR #388) |
| 2026-06-18 | T-062 (Vite 번들 `garden.js`를 `static/garden/`에 커밋하지 않으면 bootRun에서 404 — `<script type="module" th:src="@{/garden/garden.js}">`는 서버가 정적 자원을 서빙해야 하므로 `src/main/resources/static/garden/garden.js`가 없으면 브라우저에서 404·정원 캔버스 마운트 실패·콘솔 "Failed to load module script" / 해결=`npm --prefix frontend run build` 후 산출물을 git add·commit까지 해야 bootJar에 포함 / CI stale 게이트(`git diff --exit-code src/main/resources/static/garden`)가 소스 변경 후 재빌드·재커밋 누락을 PR 차단으로 방지 / N-098, PR #391) |
| 2026-06-18 | T-063 (Vite 빌드 산출물 stale — 소스 TS를 수정했는데 `npm run build`를 안 하면 배포된 `garden.js`가 이전 버전 / 증상=로컬 `bootRun`에서 수정이 반영 안 됨 / 해결=소스 변경 후 반드시 `npm --prefix frontend run build` 재실행 후 `git add src/main/resources/static/garden/garden.js`·커밋 / CI 게이트 예방: `git diff --exit-code src/main/resources/static/garden`이 0이 아니면 빌드 실패 → PR 머지 차단 / N-098, T-062, PR #391) |
| 2026-06-19 | T-064 (다중 세션 워크트리·브랜치 잔재 누적 — squash 머지로 `git branch --merged`가 머지된 `feat/*`를 미머지로 분류·고아 워크트리 폴더는 `prune` 미포착(메타만 청소) / 청소: `claude/*` `--merged`는 `-d` 안전, `feat/*`는 main 로그 PR로 머지확인 후 `-D`, 고아 폴더는 활성0·미커밋0·`HEAD@origin/main` 점검 후 `rm -rf .claude/worktrees/*/`(삭제 직전 list 재확인) / 예방: 구현 세션이 머지 후 `worktree remove`+`branch -d` / N-032·T-051·T-048) |
| 2026-06-19 | T-065 (실 브라우저에서 Phaser 씬 런타임 transform 값 수치 introspection 불가 — 번들 Phaser는 IIFE 클로저(`window.Phaser.GAMES` 빈/undefined)·프로덕션 Vue엔 `__vueParentComponent` 없음 → 페이지서 게임/씬 핸들 도달 X / 해결=순수함수 단위테스트 + 실 브라우저 시각 검증(시간차 스크린샷·확대 연사)으로 확정, 캐릭터는 완독 책 임시 시드 후 삭제 / 예방=디버그 필요 시 빌드에 `window.__debugGame` 의도 노출 / T-053/054·N-082·N-080·PR #409) |
| 2026-06-19 | T-066 (PowerShell `gh pr create --body "$(cat <<'EOF' ...)"` 파서 오류 — Windows PowerShell 5.1에서 bash heredoc `<<'EOF'`를 `"$(...)"` 안에 쓰면 `<`를 리다이렉션으로 파싱해 `Missing file specification`·`The '<' operator is reserved` 파서 오류 / 해결=PR body를 임시 파일(`.pr-body-tmp.md`)에 `Write`로 쓰고 `Get-Content ".pr-body-tmp.md" -Raw`를 변수에 받아 `gh pr create --body $body`로 전달, 사용 후 삭제 / 예방=PowerShell에서 멀티라인 문자열을 CLI 인라인 인자로 넘길 때 항상 파일 경유·`@'...'@` here-string은 할당 전용(인자로 직접 못 넘김) / T-026(한글 커밋 file 경유)과 같은 맥락 / PR #410) |
| 2026-06-19 | T-067 (Phaser 캔버스를 CSS `transform: rotate()`로 돌리면 포인터 hit-test가 깨진다 — `cam.setRotation()` + 팬 `getWorldPoint` 교체로 입력 정렬 유지 / T-050·N-100·PR #411) |
| 2026-06-19 | T-068 (`cam.setRotation` 강제 가로 회전은 기기를 거꾸로 들면 방향 반대 — 세로에도 방향이 있어 고정 회전 부적합 / 해결=`fitCamera`+`containZoomFor` 순수 반응형, `ZOOM_MIN=0.25`, 팬·핀치·휠 보기/편집 공통 분리, DOM 회전 래퍼 제거 / T-067·PR #413) |
| 2026-06-20 | T-069 (모바일 가로 첫 로드 마을 왼쪽 치우침 / 진짜 원인=`cam.setBounds(0,0,W,H)`가 containZoom 상태 centering 음수 scrollX를 클램핑 / 해결=`fitCamera`에서 centering offset만큼 bounds 동적 확장, `create()` 정적 setBounds 제거 / PR #415) |