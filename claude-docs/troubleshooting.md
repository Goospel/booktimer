# 트러블슈팅 — 작업 중 만난 함정과 해결법

> "이렇게 하지 마라" 형 실전 트랩 기록. 같은 실수 두 번 반복 방지.
> 개념 이해는 [learning-notes.md](learning-notes.md), 프로젝트 규칙은 [../CLAUDE.md](../CLAUDE.md) 참고.

## 🔁 재발·승격 트래커

> **2회 이상** 재발한 트랩은 참조용 T-### 누적에 더해 **CLAUDE.md(항상 로드)·훅·하드픽스 중 하나로 승격**한다(CLAUDE.md 합의). 카운팅을 모델 기억에 안 맡기게, **같은 트랩군의 T-###를 새로 쓸 때 이 표의 회차·승격상태를 갱신**한다 — "지금 2회+인데 미승격"을 안 놓치는 단일 출처(완벽한 과거 감사가 아니라 살아있는 표). 신규 트랩은 1회라 안 올리고, **재발(2회째)이 되는 순간** 군을 만들어 올린다. 승격은 prose 한 줄보다 **하드픽스(훅·스크립트로 트랩 자체 제거)**가 우선.

> ⚠️ **1회 과거 백필(2026-06-30)**: 이 "살아있는 표"는 forward-only(새 T-###를 쓸 때만 갱신)라, **규칙 도입(2026-06-25, #508) 이전에 이미 2회+ 재발하고 그 뒤 재발이 멈춘 옛 트랩군**은 구조적으로 표에 안 올라간다. 이 사각을 1회 전수 감사(T-001~T-117, 21-에이전트 워크플로)해 진짜 누락 2군 — **csrf-buffer-commit(T-033·T-049)·fk-child-cleanup(T-023·T-029)** — 을 아래에 소급 등재했다. (나머지 후보 대부분은 "표면만 비슷한 별개 트랩"이거나 "재발이 과거에 멈춰 승격 실익 낮음"으로 판정해 제외.) 이후로는 forward-only를 유지한다 — 같은 사각이 또 의심되면 그때 1회 백필 감사를 반복한다.

| 트랩군 | 발생 T-### | 회차 | 승격 상태 |
|---|---|---|---|
| PR 머지 자동화(`pr-merge.sh`) — 백그라운드 hang·미완료·헛폴링·DIRTY-blind·BEHIND 무한대기 | T-083 · T-088 · T-091 · T-094 · T-102 · T-111 · T-141 | 7 | ✅ 하드픽스(`pr-merge.sh`: gh API 브랜치삭제 + `timeout`, T-094) + 절차(머지=완료확인) + CLAUDE.md Git워크플로 DIRTY 진단. **T-102(5회차): 하드픽스 안 쓰고 손수 워처→DIRTY 누락. T-111(6회차): "up-to-date 필수 + `--auto` + BEHIND = 무한대기" — `pr-merge.sh`에 BEHIND `gh pr update-branch` 자동해소 + `--arm`("걸고 떠나기") 모드 추가·표준 경로로 승격, bare `--auto` 단독 금지** **T-141(7회차): gh 전멸 시 --arm 거짓 성공 — PIPESTATUS·빈상태·autoMergeRequest 증거 검증 하드픽스** |
| 파이프라인이 native 명령 실패 exit를 가림(`\| sed`·`\| tee` 뒤 `$?` 판정 → 가짜 성공/GREEN) | T-126 · T-141 | 2 | ✅ `pr-merge.sh`는 PIPESTATUS 하드픽스. 범용 가드(hookify warn)는 보류 — 3회차 나오면 승격 재검토 |
| Vue 섬 번들 stale(산출물 미커밋·CI 사각) | T-063 · T-082 | 2 | ✅ 훅 `require-bundle-build.ps1`(전 10섬) + CI 확장 |
| Service Worker stale 캐시(파일명 고정 자산) | T-071 · T-075 · T-080 | 3 | ✅ 코드 패턴(SW `NETWORK_FIRST` 배열 + `res.ok` 가드 + `CACHE` 버전업) |
| 커밋/git 무한 hang(멀티세션 gradle 데몬·빌드락) | T-078 | 3+ (2026-06-30 45분 freeze 3회차) | ✅ **하드픽스(2026-07-01): 커밋 훅 `gradlew test`에 8분 타임아웃 — 초과 시 `taskkill /T`+`gradlew --stop` 자가복구 후 차단(exit 2)** + CLAUDE.md(bootRun 정리=8080+`--stop`·「무한 hang」절) + 강제정리 절차 |
| 헤드리스/preview 못 보는 클라 로드순서·타이밍 | T-053 · T-054 | 2 | ✅ CLAUDE.md 「🖥️ 프론트 검증 실 브라우저 게이트」(N-082·N-083) |
| PowerShell 한글 깨짐(커밋 메시지·.ps1 스크립트) | T-026 | 2+ | ✅ CLAUDE.md 「🈲 한글 커밋 `.commit-msg-tmp`」 + 훅 `check-commit-message.ps1` |
| 워크트리 머지 `--delete-branch`(로컬 main checkout 충돌) | T-095 | 2 (#511 → #515) | ✅ CLAUDE.md Git워크플로 auto-merge caveat(워크트리에선 `--delete-branch` 빼고 머지 → 원격=gh api·로컬=수동 정리) |
| changelog 멀티세션 동시 append → 같은 위치 머지 충돌 | T-098 | 5+ (2026-06-26 하루, #516·#518·#520·#523) | ✅ 하드픽스(`.gitattributes` `claude-docs/changelog.md merge=union` — 양쪽 새 행 자동 병합으로 rebase 충돌 자체 제거) |
| 전역 `button` 속성 누수(컴포넌트가 명시 안 한 속성을 상속) | T-056 · T-081 · T-099 | width 4+ (이 PR `.pv-hud-dex` 도감 버튼 풀폭) · radius 1 | ✅ 코드 패턴(flex 안 칩·탭·세그먼트엔 `width:auto`·`border-radius:0`로 상쇄, 인라인 주석) — 이번엔 in-code `#286 가드` 주석이 재디버깅을 막아줌(패턴 작동) |
| CSS 주석 속 `*/`(특히 wildcard-slash `.foo-*/.bar-*`)가 주석 조기 종료 → 다음 규칙 침묵 드랍 | T-087 | 3 (#522 .dash-card → #526 .book-*/.record- → 이 PR .oauth-*/.entry-hero) | ✅ **하드픽스 훅 `require-css-comment-safe.ps1`**(3회차에 승격) — 주석 닫는 `*/`가 **양옆 모두 셀렉터문자에 붙은** 경우만 차단(=기존 보류 사유 FP 해소: ` */color`는 앞이 공백·`/*x*/`+개행은 뒤가 공백이라 통과, 잔여 FP는 `/*c*/.sel`류 희귀패턴+우회 토큰 `SKIP_CSS_COMMENT_CHECK`). 보조: 예방 규칙(슬래시→`·`/`과`) + §11 실 브라우저/static-preview 게이트(N-118) |
| 도구 재생성 시 BOM/EOL 미보존 → phantom diff(첫 줄·전체 줄끝) | T-093 · T-103 | 2 | ✅ 코드 패턴(원본 BOM은 바이트로·EOL은 첫 매치로 감지해 쓸 때 보존; `rebuild-troubleshooting-toc.ps1`) |
| 빈 워크트리 폴더가 cwd 점유로 안 지워짐 | T-105 · T-115 | 2 | ✅ 절차(정션 끊기+`worktree prune`+`branch -D`로 실질 정리 후 빈 폴더는 세션 종료 후 `rmdir` / 워크트리 정리는 그 세션 아닌 **메인·다른 세션**에서) |
| CSRF 숨김필드 lazy 세션 ↔ 응답 버퍼 커밋 타이밍(큰 SSR·익명 폼 500) | T-033 · T-049 | 4+ | ✅ prose CLAUDE.md 「🔒 CSRF 폼 세션 선확정」(N-077) + **공유 헬퍼 `CsrfTokenUtil.precommit`로 9곳(기존3+신규6) 통일**. 1회 백필 소급 등재(2026-06-30) → 누락 6곳(Settings·AdminFeedback·Feedback·EmailVerification·Unsubscribe·Onboarding) **수정 완료**. 훅 미채택=컨트롤러↔템플릿 역매핑·POST 재렌더 FP 과다 |
| FK 자식 미정리로 부모 삭제 실패(mock 단위테스트가 못 잡음) | T-023 · T-029 | 2 | ✅ prose CLAUDE.md TDD절(부모 삭제 경로 H2 통합테스트 필수). **1회 백필로 소급 등재(2026-06-30)**. 현행 경로(`AccountService.purge`·`BookService.delete`)는 정리·통합테스트 완료 |
| author `display`(flex/grid)가 UA `[hidden]`/`<details>` `display:none`을 origin 우선으로 덮어 숨김 실패 | (#189) · T-035 · T-123 | 3 | ✅ **하드픽스(전역 `[hidden]{display:none!important}` 리셋을 `app.css` 베이스에 추가)** — `[hidden]` 속성 변형을 앱 전역에서 제거(T-123의 페이지 스코프 `.settings-page [hidden]` 리셋은 이걸로 승격돼 삭제). ⚠️ 닫힌 `<details>` 자식 변형(T-035)은 `[hidden]` 속성이 아니라 UA `details:not([open])` 메커니즘이라 이 리셋 밖 — 개별 `:not([open])` 재숨김 유지 필요 |
| docker exec mysql 한글 시드 INSERT 깨짐(CP949/이중 인코딩/mojibake) | T-085 · T-119 | 3 | ✅ 절차 확립(T-085 보강 — UTF-8 파일 + `docker exec -i … < file.sql` + `--default-character-set=utf8mb4`, `HEX()` 검증; T-119의 `UNHEX()` 주입은 대안). 검증 데이터 셋업 한정·저빈도라 CLAUDE.md/훅 승격은 보류 — 4회차 나오면 재검토 |
| 어필리에이트 클릭 추적 무성 실패(생성 링크·제휴키·옵션 미탑재로 클릭 0) | T-129 · T-131(알라딘·YES24) | 2 | ⚠️ prose([N-146](learning-notes.md)) + **제공자별 가드로 하나씩 닫는 중**: 알라딘=`includeKey=1`+백필(#644), YES24=`isEnabled()`에 템플릿 `{trackingCode}` 포함 검증(순수 URL이면 버튼 숨김). **범용 커밋 가드는 보류** — 제공자마다 추적 관문이 달라(쿠팡=딥링크 shortenUrl / 알라딘=includeKey ttbkey / YES24=linkprice 래퍼) 일반 정적 가드가 부적합. 신규 제휴를 붙일 때 **"링크에 추적자가 실제로 실렸나"를 한 건 까서 실측**하는 걸 감사·리뷰로 강제(3회차 나오면 제공자별 런타임 assert로 승격 재검토) |
| 공허한 테스트 — 통과하는데 겨눈 규칙을 지워도 안 깨진다(검사 키가 남의 문자열과 겹치거나, 매처가 그 실패를 안 매칭하거나, 하니스가 그 동작을 아예 안 돌림) | T-144(정적 마크업 `toContain` × TDS 주입 CSS) · T-145(Mockito `never()`+`anyString()`이 null 미매칭) · T-149(정적 렌더가 effect 미실행 → 부정 카운트 단언 상시 통과) | 3 | ⚠️ **prose 승격 유지**(글로벌 CLAUDE.md·implementer 규약의 「돌연변이 확인」이 담당 — T-144·T-145는 돌연변이 확인이 잡아냈고, **T-149(3회차)는 같은 규약 덕에 작성 전에 사전 회피**됐다. 규칙이 세 번 연속 작동해 새 계단은 여전히 불필요). **hookify warn 후보** `never()`+`anyString()`/`anyLong()` 동시 등장 감지는 보류 유지 — 3회차(T-149)는 그 조합이 아니라 별개 메커니즘(정적 렌더 × 부정 단언)이라 정규식 한 줄로 못 잡는다; Mockito 조합이 재출현하면 그때 승격. ⚠️ 묶는 근거는 도구(vitest/Mockito)가 아니라 **"통과가 증거가 아닌 상태"**와 그 유일한 탐지 수단이 **돌연변이 확인**이라는 점이다 |
| 자동화가 정상 배선인데 산출물 0건 — 확인 스텝이 없어 무성 실패 | T-129 · T-131(제휴 추적 클릭 0) · T-138(백업 cron 0건) | 3 | ⚠️ **부분 승격** — 배포에는 `deploy.yml`의 `Verify live health`(산출물=실서비스 200)가 이미 이 역할을 하고, 제휴는 제공자별 가드로 하나씩 닫는 중. **백업엔 대응물이 없어서 엿새를 몰랐다** → 다음 승격 후보 = 산출물 존재 확인 자동화(백업 성공 시 CloudWatch 커스텀 메트릭 방출 + 36h 미도달 알람). ⚠️ 메커니즘은 군마다 다르다(URL 조립 / cron 권한) — 묶는 근거는 **"배선·상태는 전부 정상으로 보이는데 산출물만 0이고 아무도 안 알려준다"**는 형태와, 그 해법이 하나같이 **산출물을 직접 세는 게이트**라는 점이다 |

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
- [T-034. 생성자 2개(주입 + 테스트용)인 `@Service`/빈은 주입 생성자에 `@Autowired` 필수 — 없으면 no-arg 탐색 실패(NoSuchMethodException)](#t-034-생성자-2개주입--테스트용인-service빈은-주입-생성자에-autowired-필수--없으면-no-arg-탐색-실패nosuchmethodexception)
- [T-035. author `display` 규칙이 UA의 `display:none`을 이겨 `<details>`·`[hidden]`이 안 숨겨진다 (cascade origin: author > UA)](#t-035-author-display-규칙이-ua의-displaynone을-이겨-detailshidden이-안-숨겨진다-cascade-origin-author--ua)
- [T-037. 신형 Gemini `AQ.` API 키는 `x-goog-api-key` 헤더로 401 — `?key=` 쿼리파라미터로만 통한다](#t-037-신형-gemini-aq-api-키는-x-goog-api-key-헤더로-401--key-쿼리파라미터로만-통한다)
- [T-038. 세션 타임아웃을 프로퍼티로 못 늘린다(Spring Session JDBC) — `SessionRepositoryCustomizer` 빈으로](#t-038-세션-타임아웃을-프로퍼티로-못-늘린다spring-session-jdbc--sessionrepositorycustomizer-빈으로)
- [T-039. 실시간 시계 통합 테스트는 자정·타임존 경계에서 플레이키 — 고정 클락을 주입하라](#t-039-실시간-시계-통합-테스트는-자정타임존-경계에서-플레이키--고정-클락을-주입하라)
- [T-040. Gemini 2.5-flash가 HTTP 200인데 `parts[0].text`가 빈 문자열 — thinking이 출력 예산을 삼킨다](#t-040-gemini-25-flash가-http-200인데-parts0text가-빈-문자열--thinking이-출력-예산을-삼킨다)
- [T-041. Thymeleaf `#temporals.format(Instant, …)`는 서버 기본 타임존으로 찍는다 — 표시 시각은 뷰에서 유저 TZ로 변환](#t-041-thymeleaf-temporalsformatinstant-는-서버-기본-타임존으로-찍는다--표시-시각은-뷰에서-유저-tz로-변환)
- [T-042. 마우스 드래그 캐러셀이 손을 안 따라온다 — 컨테이너 `scroll-behavior: smooth`가 `scrollLeft` 직접 대입까지 애니메이션화](#t-042-마우스-드래그-캐러셀이-손을-안-따라온다--컨테이너-scroll-behavior-smooth가-scrollleft-직접-대입까지-애니메이션화)
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
- [T-057. PowerShell 5.1 `Set-Content -Encoding utf8`가 BOM 포함 UTF-8을 생성해 커밋 메시지 앞에 BOM 붙음](#t-057-powershell-51-set-content--encoding-utf8가-bom-포함-utf-8을-생성해-커밋-메시지-앞에-bom-붙음)
- [T-058. SES 프로덕션 액세스 거부 — 케이스 '사례 해결'은 승인이 아니라 요청 포기, '사례 다시 열기'로 상세 보강해 재요청](#t-058-ses-프로덕션-액세스-거부--케이스-사례-해결은-승인이-아니라-요청-포기-사례-다시-열기로-상세-보강해-재요청)
- [T-059. Thymeleaf `<script>` 안 이중 대괄호 `[[` 표기 — 배열 of 배열·주석 속 공백 `[[ ]]`도 인라인 식으로 파싱됨](#t-059-thymeleaf-script-안-이중-대괄호--표기--배열-of-배열주석-속-공백--도-인라인-식으로-파싱됨)
- [T-060. `@free-pure-core` 블록 순수함수 제거 시 하니스 destructure 목록 미갱신 → `ReferenceError` FAIL](#t-060-free-pure-core-블록-순수함수-제거-시-하니스-destructure-목록-미갱신--referenceerror-fail)
- [T-061. `.gitignore` 하니스를 커밋·CI 그물로 승격할 때 — required check는 job 단위라 반드시 같은 job에 스텝 추가](#t-061-gitignore-하니스를-커밋ci-그물로-승격할-때--required-check는-job-단위라-반드시-같은-job에-스텝-추가)
- [T-064. 다중 세션 워크트리·브랜치 잔재 누적 — squash 머지로 `git branch --merged`가 머지된 feat/*를 미머지로 분류·고아 워크트리 폴더는 `prune` 미포착](#t-064-다중-세션-워크트리브랜치-잔재-누적--squash-머지로-git-branch---merged가-머지된-feat를-미머지로-분류고아-워크트리-폴더는-prune-미포착)
- [T-065. 실 브라우저에서 Phaser 씬 런타임 값(angle/scale/flipX)을 수치로 읽으려 했으나 — 번들 Phaser는 IIFE 클로저·프로덕션 Vue엔 DOM 컴포넌트 핸들 없음 → introspection 불가, 시각 검증으로 대체](#t-065-실-브라우저에서-phaser-씬-런타임-값anglescaleflipx을-수치로-읽으려-했으나--번들-phaser는-iife-클로저프로덕션-vue엔-dom-컴포넌트-핸들-없음--introspection-불가-시각-검증으로-대체)
- [T-066. PowerShell에서 `gh pr create --body "$(cat <<'EOF' ...)"` 파서 오류 — bash heredoc을 PS 인라인 인자로 못 쓴다](#t-066-powershell에서-gh-pr-create---body-cat-eof--파서-오류--bash-heredoc을-ps-인라인-인자로-못-쓴다)
- [T-067. Phaser 캔버스를 CSS `transform: rotate()`로 돌리면 포인터 hit-test가 깨진다 — 카메라 회전을 쓸 것](#t-067-phaser-캔버스를-css-transform-rotate로-돌리면-포인터-hit-test가-깨진다--카메라-회전을-쓸-것)
- [T-068. 카메라 강제 회전(`cam.setRotation`)은 기기를 거꾸로 들면 방향이 반대 — 순수 반응형이 정답](#t-068-카메라-강제-회전camsetrotation은-기기를-거꾸로-들면-방향이-반대--순수-반응형이-정답)
- [T-069. 모바일 가로 첫 로드에서 마을 왼쪽 치우침 — `cam.setBounds`가 centering 음수 scrollX 클램핑](#t-069-모바일-가로-첫-로드에서-마을-왼쪽-치우침--camsetbounds가-centering-음수-scrollx-클램핑)
- [T-070. bootRun은 장기 실행 태스크라 Gradle 진행률이 80%대에서 멈춘다(정상) — % 아닌 로그/포트로 ready 판정](#t-070-bootrun은-장기-실행-태스크라-gradle-진행률이-80대에서-멈춘다정상---아닌-로그포트로-ready-판정)
- [T-071. Service Worker + 해시 없는 번들 → cache-first만 쓰면 배포해도 안 묻힘 — garden.js는 network-first 필수](#t-071-service-worker--해시-없는-번들--cache-first만-쓰면-배포해도-안-묻힘--gardenjs는-network-first-필수)
- [T-072. Service Worker scope = sw.js 파일 위치 — static 루트에 없으면 전역 제어 안 됨](#t-072-service-worker-scope--swjs-파일-위치--static-루트에-없으면-전역-제어-안-됨)
- [T-073. 푸시 토글 함수에서 VAPID 체크를 최상단에 두면 OFF(철회) 경로도 막힌다](#t-073-푸시-토글-함수에서-vapid-체크를-최상단에-두면-off철회-경로도-막힌다)
- [T-074. Thymeleaf 산술은 `${}` 안에 넣어야 정수 — `${x} / n`(밖)은 소수로 샌다](#t-074-thymeleaf-산술은--안에-넣어야-정수--x--n밖은-소수로-샌다)
- [T-075. 파일명 고정 자산(pwa-install.js·app.css)을 SW cache-first로 두면 배포 후에도 stale 서빙](#t-075-파일명-고정-자산pwa-installjsappcss을-sw-cache-first로-두면-배포-후에도-stale-서빙)
- [T-076. `inlineDynamicImports:true`를 멀티 input과 함께 쓰면 Rollup 에러 — 페이지별 독립 빌드로 분리](#t-076-inlinedynamicimportstrue를-멀티-input과-함께-쓰면-rollup-에러--페이지별-독립-빌드로-분리)
- [T-077. jsdom에선 scroll-snap 컴포넌트의 scroll 계측이 모두 0 — 실 브라우저 게이트로 위임](#t-077-jsdom에선-scroll-snap-컴포넌트의-scroll-계측이-모두-0--실-브라우저-게이트로-위임)
- [T-078. git/commit 무한 로딩 = 커밋 훅의 gradle test hang(멀티세션 경합) — Claude Code 코어 아님](#t-078-gitcommit-무한-로딩--커밋-훅의-gradle-test-hang멀티세션-경합--claude-code-코어-아님)
- [T-079. Vue 섬 번들이 book-detail 라우트에 가로채여 무한로딩 — 경로변수 숫자 제한](#t-079-vue-섬-번들이-book-detail-라우트에-가로채여-무한로딩--경로변수-숫자-제한)
- [T-080. Service Worker가 에러 응답(500/503)을 캐싱 → 서버 fix 후에도 stale](#t-080-service-worker가-에러-응답500503을-캐싱--서버-fix-후에도-stale)
- [T-081. SPA 전환에서 form 래퍼 제거 → 전역 button width 100%가 flex-row 액션을 풀폭 세로로 깨뜨림](#t-081-spa-전환에서-form-래퍼-제거--전역-button-width-100가-flex-row-액션을-풀폭-세로로-깨뜨림)
- [T-082. 라디오 CSS탭 → JS(v-if) 탭 전환에서 clear·display·active 경로 누락 + 빌드 stale (책방 탭 3종 깨짐)](#t-082-라디오-css탭--jsv-if-탭-전환에서-cleardisplayactive-경로-누락--빌드-stale-책방-탭-3종-깨짐)
- [T-083. gh pr checks --watch가 CI 등록 전 실행되면 "no checks reported"로 즉시 exit 1](#t-083-gh-pr-checks---watch가-ci-등록-전-실행되면-no-checks-reported로-즉시-exit-1)
- [T-084. Phaser `update()`가 매 프레임 덮는 속성에 tween을 걸면 즉시 무효화 — 효과는 독립 오브젝트로 분리](#t-084-phaser-update가-매-프레임-덮는-속성에-tween을-걸면-즉시-무효화--효과는-독립-오브젝트로-분리)
- [T-085. PowerShell `docker exec … mysql -e`로 한글 INSERT 시 CP949로 `?????` 저장 — 한글은 Spring API(JSON) 경유 삽입](#t-085-powershell-docker-exec--mysql--e로-한글-insert-시-cp949로--저장--한글은-spring-apijson-경유-삽입)
- [T-086. Docker 컨테이너 수십 개 누적의 범인은 `bootRun`(테스트 아님) — `working_dir` 라벨로 BookTimer 것만 정리](#t-086-docker-컨테이너-수십-개-누적의-범인은-bootrun테스트-아님--working_dir-라벨로-booktimer-것만-정리)
- [T-087. CSS 주석 속 `*/`가 주석을 조기 종료해 다음 규칙을 침묵 드랍](#t-087-css-주석-속-가-주석을-조기-종료해-다음-규칙을-침묵-드랍)
- [T-088. 백그라운드 PR 머지 태스크를 띄우고 완료 후속(exit 코드 확인)을 안 챙겨 머지 방치](#t-088-백그라운드-pr-머지-태스크를-띄우고-완료-후속exit-코드-확인을-안-챙겨-머지-방치)
- [T-089. 반응형 재현 하니스 mock이 production worst-case(최장 문자열)를 안 담으면 RED가 안 떠 레이아웃 버그를 놓침](#t-089-반응형-재현-하니스-mock이-production-worst-case최장-문자열를-안-담으면-red가-안-떠-레이아웃-버그를-놓침)
- [T-090. Windows preview `launch.json`으로 `gradlew bootRun` 못 띄움 — `cmd /c <절대경로>gradlew.bat -p <절대경로> bootRun`](#t-090-windows-preview-launchjson으로-gradlew-bootrun-못-띄움--cmd-c-절대경로gradlewbat--p-절대경로-bootrun)
- [T-091. `pr-merge.sh`가 머지 성공 후 `git push origin --delete`에서 hang → 백그라운드 머지 안 끝남](#t-091-pr-mergesh가-머지-성공-후-git-push-origin---delete에서-hang--백그라운드-머지-안-끝남)
- [T-092. minified Vue 프로덕션 번들은 `setupState` 키가 숨겨짐 — 루트 `_vnode.component`에서 `subTree` BFS+props 변이](#t-092-minified-vue-프로덕션-번들은-setupstate-키가-숨겨짐--루트-_vnodecomponent에서-subtree-bfsprops-변이)
- [T-093. 워크트리 `npm run build`가 무관 9개 번들을 CRLF-only로 ` M` 표시 — `git diff --numstat`로 감별, 변경 파일만 stage](#t-093-워크트리-npm-run-build가-무관-9개-번들을-crlf-only로--m-표시--git-diff---numstat로-감별-변경-파일만-stage)
- [T-094. Windows `timeout 30 git push --delete`도 hang 못 막음 → `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`](#t-094-windows-timeout-30-git-push---delete도-hang-못-막음--gh-api--x-delete-reposownerrepogitrefsheadsbranch)
- [T-095. 워크트리 `gh pr merge --delete-branch`가 `main is already used by worktree`로 깨짐 — 머지는 성공](#t-095-워크트리-gh-pr-merge---delete-branch가-main-is-already-used-by-worktree로-깨짐--머지는-성공)
- [T-096. 연쇄 PR 폴링 미머지 종료(TIMEOUT/OPEN/DIRTY)를 머지 완료로 오인 — 다음 브랜치 전 `gh pr view --json state`=MERGED 확인](#t-096-연쇄-pr-폴링-미머지-종료timeoutopendirty를-머지-완료로-오인--다음-브랜치-전-gh-pr-view---json-statemerged-확인)
- [T-097. Git Bash에서 멀티바이트(이모지·한글) `grep`/`sed` 패턴이 조용히 0건 — PowerShell `.Contains/.Replace` 또는 Grep(ripgrep)](#t-097-git-bash에서-멀티바이트이모지한글-grepsed-패턴이-조용히-0건--powershell-containsreplace-또는-grepripgrep)
- [T-098. changelog 멀티세션 동시 append 충돌 → `.gitattributes` `merge=union`](#t-098-changelog-멀티세션-동시-append-충돌--gitattributes-mergeunion)
- [T-099. 전역 `button{border-radius}` 누수 — 명시값 제거 시 전역값이 샌다, `border-radius:0`로 상쇄](#t-099-전역-buttonborder-radius-누수--명시값-제거-시-전역값이-샌다-border-radius0로-상쇄)
- [T-100. 워크트리 frontend `node_modules` 없음 → vite 미해결, `npm ci` (디렉토리 존재 ≠ 패키지 설치)](#t-100-워크트리-frontend-node_modules-없음--vite-미해결-npm-ci-디렉토리-존재--패키지-설치)
- [T-101. content-hash 정적자산 인증 누수 — `@{}` 단일파일 해시 URL이 정확매칭 permitAll에서 빠져 302, 와일드카드로](#t-101-content-hash-정적자산-인증-누수---단일파일-해시-url이-정확매칭-permitall에서-빠져-302-와일드카드로)
- [T-102. auto-merge 후 손수 짠 워처가 DIRTY를 안 봐 침묵 정지 — `pr-merge.sh` 쓰거나 워처에 DIRTY 분기](#t-102-auto-merge-후-손수-짠-워처가-dirty를-안-봐-침묵-정지--pr-mergesh-쓰거나-워처에-dirty-분기)
- [T-103. 스크립트로 파일 재생성 시 ReadAllText + UTF8Encoding(false)가 원본 BOM을 떨어뜨린다](#t-103-스크립트로-파일-재생성-시-readalltext--utf8encodingfalse가-원본-bom을-떨어뜨린다)
- [T-104. squash 머지가 브랜치 커밋 trailer를 메시지 중간으로 밀어 git %(trailers) 구조 조회를 깨뜨린다](#t-104-squash-머지가-브랜치-커밋-trailer를-메시지-중간으로-밀어-git-trailers-구조-조회를-깨뜨린다)
- [T-105. 빈 워크트리 폴더가 `Device or resource busy`로 안 지워짐 — 죽은 세션 좀비 셸이 cwd 점유, cwd 검증 PID만 종료](#t-105-빈-워크트리-폴더가-device-or-resource-busy로-안-지워짐--죽은-세션-좀비-셸이-cwd-점유-cwd-검증-pid만-종료)
- [T-106. auto-merge `--delete-branch`는 비동기 머지라 원격 브랜치가 안 지워진다 — 머지 확인 후 gh API로 삭제](#t-106-auto-merge---delete-branch는-비동기-머지라-원격-브랜치가-안-지워진다--머지-확인-후-gh-api로-삭제)
- [T-107. `git add`와 `git commit`을 한 명령으로 묶으면 PreToolUse 자동수정 훅(목차·번들)이 skip된다 — add는 별도 호출로](#t-107-git-add와-git-commit을-한-명령으로-묶으면-pretooluse-자동수정-훅목차번들이-skip된다--add는-별도-호출로)
- [T-108. `gradlew.bat`이 phantom-modified로 rebase를 막는다 — `.gitattributes eol=crlf`와 커밋된 블롭 EOL 불일치, `--assume-unchanged`로 우회](#t-108-gradlewbat이-phantom-modified로-rebase를-막는다--gitattributes-eolcrlf와-커밋된-블롭-eol-불일치---assume-unchanged로-우회)
- [T-109. vitest include가 test/ 디렉토리만 잡아 src/ 곁 테스트가 조용히 미실행 — include에 src/** 추가](#t-109-vitest-include가-test-디렉토리만-잡아-src-곁-테스트가-조용히-미실행--include에-src-추가)
- [T-110. 정션 둔 워크트리를 `git worktree remove --force`하면 정션 타깃(main node_modules)이 비워진다 — 정션 먼저 끊어라](#t-110-정션-둔-워크트리를-git-worktree-remove---force하면-정션-타깃main-node_modules이-비워진다--정션-먼저-끊어라)
- [T-111. "머지 전 브랜치 최신화 필수" 정책에서 BEHIND인 PR에 `--auto`만 걸면 영영 안 머지된다 — GitHub가 BEHIND 브랜치를 자동 갱신하지 않음](#t-111-머지-전-브랜치-최신화-필수-정책에서-behind인-pr에---auto만-걸면-영영-안-머지된다--github가-behind-브랜치를-자동-갱신하지-않음)
- [T-112. Chrome MCP `resize_window`가 렌더 뷰포트(`innerWidth`)를 못 바꿔 모바일 미디어쿼리 검증이 막힌다 — 폭 N px iframe에 페이지를 로드해 우회](#t-112-chrome-mcp-resize_window가-렌더-뷰포트innerwidth를-못-바꿔-모바일-미디어쿼리-검증이-막힌다--폭-n-px-iframe에-페이지를-로드해-우회)
- [T-113. 도메인 TLD 이전 후 `www.<신규>`를 ALB 301 규칙에서 빠뜨려 검색 유입자가 redirect_uri_mismatch + host-only 세션 분리](#t-113-도메인-tld-이전-후-www신규를-alb-301-규칙에서-빠뜨려-검색-유입자가-redirect_uri_mismatch--host-only-세션-분리)
- [T-114. preview_inspect가 border-radius·padding 등 shorthand CSS를 빈 객체로 반환 — longhand나 eval getComputedStyle로 읽어라](#t-114-preview_inspect가-border-radiuspadding-등-shorthand-css를-빈-객체로-반환--longhand나-eval-getcomputedstyle로-읽어라)
- [T-115. 워크트리에서 작업한 세션이 그 워크트리를 직접 정리하면 최상위 빈 폴더가 안 지워진다(세션 cwd 점유)](#t-115-워크트리에서-작업한-세션이-그-워크트리를-직접-정리하면-최상위-빈-폴더가-안-지워진다세션-cwd-점유)
- [T-116. 순수 마크업/CSS 변경이라 '단위 TDD 무의미'라 본 게 기존 통합 테스트를 놓쳐 CI에서 RED](#t-116-순수-마크업css-변경이라-단위-tdd-무의미라-본-게-기존-통합-테스트를-놓쳐-ci에서-red)
- [T-117. 공유 Vue 컴포넌트에 `<style scoped>`를 넣으면 페이지가 링크하지 않는 별도 번들 CSS가 생성된다](#t-117-공유-vue-컴포넌트에-style-scoped를-넣으면-페이지가-링크하지-않는-별도-번들-css가-생성된다)
- [T-119. PowerShell→`docker exec mysql -e`로 한글 INSERT 시 mojibake — `UNHEX`로 정확한 UTF-8 바이트 주입](#t-119-powershelldocker-exec-mysql--e로-한글-insert-시-mojibake--unhex로-정확한-utf-8-바이트-주입)
- [T-121. WinRT 토스트가 미등록 AppUserModelID면 API 성공해도 화면에 안 뜬다(조용히 드랍)](#t-121-winrt-토스트가-미등록-appusermodelid면-api-성공해도-화면에-안-뜬다조용히-드랍)
- [T-122. 타임아웃/hang 수정의 RED 테스트는 하니스를 outer `timeout`으로 감싸지 않으면 테스트가 세션째 hang한다](#t-122-타임아웃hang-수정의-red-테스트는-하니스를-outer-timeout으로-감싸지-않으면-테스트가-세션째-hang한다)
- [T-123. 커스텀 `display`(flex/grid)를 준 요소를 JS `[hidden]`으로 토글해도 author가 UA `[hidden]{display:none}`을 이겨 안 숨겨진다 (T-035 재발 3회차)](#t-123-커스텀-displayflexgrid를-준-요소를-js-hidden으로-토글해도-author가-ua-hiddendisplaynone을-이겨-안-숨겨진다-t-035-재발-3회차)
- [T-124. `npm install`(무인자)이 vite dist를 불완전하게 남겨 빌드가 `ERR_MODULE_NOT_FOUND`(cli.js 없음) — `npm ci`로 클린 복구](#t-124-npm-install무인자이-vite-dist를-불완전하게-남겨-빌드가-err_module_not_foundclijs-없음--npm-ci로-클린-복구)
- [T-125. Thymeleaf `th:field` 체크박스가 삽입하는 hidden sibling이 CSS 인접 형제 선택자(`+`)를 깨뜨린다](#t-125-thymeleaf-thfield-체크박스가-삽입하는-hidden-sibling이-css-인접-형제-선택자를-깨뜨린다)
- [T-126. 검증 명령을 `| tail`/`| grep`으로 파이프하면 exit code가 가려져 실패가 GREEN으로 보임](#t-126-검증-명령을--tail-grep으로-파이프하면-exit-code가-가려져-실패가-green으로-보임)
- [T-127. 크롬 확장 네트워크 로그의 간헐 503 — 서비스워커 pass-through 내부 fallback 아티팩트(앱 결함 아님)](#t-127-크롬-확장-네트워크-로그의-간헐-503--서비스워커-pass-through-내부-fallback-아티팩트앱-결함-아님)
- [T-128. Yes24 링크프라이스 딥링크, 모바일 UA면 Yes24 게이트가 목적지를 m.yes24 메인으로 치환 (tu에 모바일 URL을 넣어도 우회 불가)](#t-128-yes24-링크프라이스-딥링크-모바일-ua면-yes24-게이트가-목적지를-myes24-메인으로-치환-tu에-모바일-url을-넣어도-우회-불가)
- [T-129. 쿠팡 파트너스 "구매" 링크가 추적 0 — CoupangLinkBuilder 자작 lptag 검색 URL은 정식 추적링크가 아님(딥링크 API 필요)](#t-129-쿠팡-파트너스-구매-링크가-추적-0--coupanglinkbuilder-자작-lptag-검색-url은-정식-추적링크가-아님딥링크-api-필요)
- [T-130. dark-launch 기능 secret을 task-def valueFrom으로 배선하면 SSM 파라미터 미생성 시 ECS 배포가 서킷브레이커 롤백](#t-130-dark-launch-기능-secret을-task-def-valuefrom으로-배선하면-ssm-파라미터-미생성-시-ecs-배포가-서킷브레이커-롤백)
- [T-131. 알라딘 OpenAPI includeKey 미전송으로 응답 link에 TTBKey 없어 제휴 클릭 추적 0](#t-131-알라딘-openapi-includekey-미전송으로-응답-link에-ttbkey-없어-제휴-클릭-추적-0)
- [T-133. 스크립트가 읽는 비밀 파일 경로가 `.gitignore` 무시 경로·문서와 어긋나 조용히 실패 or 비밀 커밋](#t-133-스크립트가-읽는-비밀-파일-경로가-gitignore-무시-경로문서와-어긋나-조용히-실패-or-비밀-커밋)
- [T-134. 외부 API의 "에러처럼 생긴" result 코드가 실은 정상 무데이터일 수 있다 — 비-성공을 뭉뚱그려 fatal 처리 금지](#t-134-외부-api의-에러처럼-생긴-result-코드가-실은-정상-무데이터일-수-있다--비-성공을-뭉뚱그려-fatal-처리-금지)
- [T-135. `preview_screenshot`이 `readyState=complete`인데도 30초 타임아웃 — `preview_inspect`/`eval` + 비교 위젯으로 시각 검증 우회](#t-135-preview_screenshot이-readystatecomplete인데도-30초-타임아웃--preview_inspecteval--비교-위젯으로-시각-검증-우회)

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
- **✅ 실제 결과 (2026-07-06)**: 이 "다시 열기 → 거부 사유를 코드 근거로 조목조목 반박" 경로로 case 178123901400162가 **4차 재요청 만에 승인**됨(1·2차 거부 후). 승인 신호는 예고대로 케이스가 아니라 SES 콘솔 — 발신 한도가 200통/24h → **50,000통/일**로 급증. reopen 재요청은 유효한 정공법(개념 갱신: N-091 후속2).
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

## T-070. bootRun은 장기 실행 태스크라 Gradle 진행률이 80%대에서 멈춘다(정상) — % 아닌 로그/포트로 ready 판정

**증상**: 로컬 `./gradlew bootRun`이 `80% EXECUTING [Nm Ns]`에서 더 안 올라가고 멈춘 듯 보인다. 그러나 브라우저로 접속하면 앱은 완전히 정상 작동. 진행률이 100%/완료되길 기다리면(특히 Claude Code가 foreground로 실행하면) 영영 안 끝나 무한 대기·타임아웃에 걸린다.

**원인**: `bootRun`은 Spring Boot 앱을 띄우고 그 프로세스를 **계속 실행 상태로 유지**하는 장기 실행(long-running) 태스크다. Gradle 진행률 막대는 "태스크 그래프 중 완료된 태스크 비율"인데, 마지막 `bootRun` 태스크가 서버가 살아있는 한 끝나지 않아 그 직전(≈80%, 정확한 값은 태스크 수에 따라 다름)에서 고정된다. 100%는 앱을 종료(Ctrl+C)해야 도달 — **설계상 정상이고 버그 아님**.

**해결**: 진행률 %가 아니라 **로그/포트로 ready 판정**한다.
- 준비 완료 신호 = `Tomcat started on port 8080` + `Started BooktimerApplication in N seconds` 로그(첫 요청 시 뜨는 `DispatcherServlet` 초기화는 lazy라 무관, 끝에 보이는 `.well-known/...com.chrome.devtools.json` 404 WARN도 크롬 자동요청이라 무해).
- Claude Code는 `bootRun`을 **background로 실행**(`run_in_background`)하고 위 로그 또는 8080 LISTEN이 잡히면 진행. foreground(blocking)로 두면 명령이 반환을 안 해 무한 대기에 걸린다.
- 포트 확인(PowerShell): `Get-NetTCPConnection -LocalPort 8080 -State Listen`.
- 검증 끝나면 8080 반납(본인이 띄운 건 본인이 끔 — CLAUDE.md 워크트리 절).

**예방**: long-running 명령(`bootRun`, `npm run dev`, watch류)은 항상 background + ready 신호(로그/포트)로 판정. Gradle %를 완료 신호로 쓰지 말 것.

**관련**: T-063(번들 stale·bootRun 반영), CLAUDE.md 빌드/실행 메모(bootRun·8080 반납).

---

## T-071. Service Worker + 해시 없는 번들 → cache-first만 쓰면 배포해도 안 묻힘 — garden.js는 network-first 필수

**증상**: SW를 배포하고 `garden.js` 소스를 수정해 재배포했는데 기존 사용자에게 구 버전이 계속 보인다. DevTools → Application → Cache Storage에 구 `garden.js`가 살아있고, 네트워크 탭에서 "from ServiceWorker"로 서빙됨.

**원인**: Vite 설정 `entryFileNames: 'garden.js'`로 번들 파일명이 고정(콘텐츠 해시 없음). 일반 HTTP 캐시는 ETag·max-age가 있어 브라우저가 알아서 재검증하지만, SW cache-first는 **캐시 히트 즉시 반환**해 버린다 — 서버에 갱신됐는지 묻지 않는다. 파일명이 바뀌지 않으면 SW는 구 버전을 계속 돌려준다.

**해결**:
1. `garden.js`는 SW `fetch` 핸들러에서 **network-first**로 처리(`fetch(request)` 먼저, 오프라인 시만 캐시 폴백).
2. **`CACHE` 버전 상수** 올리기 + activate에서 구 캐시 전량 삭제(`caches.delete(oldKey)`)를 추가 안전장치로. activate는 SW 새 버전이 설치된 뒤 이전 버전 클라이언트가 모두 닫히면 발동한다.
3. 장기적 해결 = Vite에서 해시 번들명 사용(`entryFileNames: '[name].[hash].js'`). 파일명이 달라지면 cache-first를 써도 이전 파일은 별도 URL이라 히트 안 됨.

**예방**: SW를 붙이기 전에 번들 파일명 전략(해시 vs 고정)을 먼저 결정하라. 고정이면 dynamic(해당 파일만 network-first). 해시가 있으면 SW manifest를 빌드마다 자동 재생성하는 플러그인(vite-plugin-pwa 등)이 필요.

**관련**: N-098(Vite 번들 static·파일명 고정), N-101(PWA 레벨·캐싱 전략), T-072(SW scope), PR L2.

---

## T-072. Service Worker scope = sw.js 파일 위치 — static 루트에 없으면 전역 제어 안 됨

**증상**: SW를 등록했는데 `/dashboard`, `/` 등 일부 페이지에서 fetch 이벤트가 안 잡힌다. DevTools → Application → Service Workers에 scope가 `/garden/`으로 좁혀 표시됨.

**원인**: SW의 기본 scope는 **sw.js가 위치한 경로**(디렉터리)다. `static/garden/sw.js`에 두면 scope=`/garden/`이라 `/garden/*` 요청만 가로채고, `/dashboard`, `/login`, `/`는 제어 밖이다.

**해결**: `static/sw.js`(static 루트 직하)에 두면 scope=`/` 전역 → 모든 경로 fetch를 가로챌 수 있다. BookTimer의 Vite outDir=`static/garden`이라 빌드 산출물이 `static/garden/`에만 들어간다 → sw.js를 빌드 산출물로 관리하면 자동으로 `/garden/` scope에 갇힌다. 손수 `static/sw.js`로 배치(빌드가 건드리지 않음)해야 전역 scope 확보.

**예방**: SW를 새로 만들 때는 배치 위치와 scope를 DevTools → Application → Service Workers에서 확인하라. scope를 명시 지정하려면 `serviceWorker.register('/sw.js', { scope: '/' })`로 쓸 수도 있으나, SW 파일이 실제 그 scope 경로에 접근 가능해야 한다(더 넓은 scope는 서버 헤더 `Service-Worker-Allowed`가 필요).

**관련**: N-101(PWA 레벨·SW 전역 등록), T-071(캐싱 전략·network-first), PR L2.

---

## T-073. 푸시 토글 함수에서 VAPID 체크를 최상단에 두면 OFF(철회) 경로도 막힌다

**증상**: 복귀 알림 "ON→OFF(철회)" 버튼을 눌러도 아무 반응이 없다. 서버에 철회 요청이 전달되지 않는다.

**원인**: `toggleMarketingPush()` 함수 맨 앞에 VAPID 키 존재 여부를 체크하면, **OFF 경로도 동일 게이트를 통과해야** 한다. VAPID 키가 미설정(`not-configured`)이면 경고 후 조기 반환되어 철회조차 불가능해진다.

```typescript
// ❌ 잘못된 패턴 — OFF도 막힌다
async function toggleMarketingPush() {
  const vapidKey = getVapidPublicKey();
  if (!vapidKey || vapidKey === 'not-configured') { // ← OFF도 여기서 막힘
    console.warn('[Push] VAPID 공개키가 설정되지 않았습니다.');
    return;
  }
  if (!marketingPushEnabled.value) { /* ON 로직 */ }
  else { /* OFF 로직 */ }
}
```

**해결**: VAPID 체크를 **ON 분기 안**으로 이동한다. OFF(수신거부)는 VAPID 없이도 동의 API(`/api/push/marketing-consent {enabled:false}`)만 호출하면 충분하다.

```typescript
// ✅ 올바른 패턴
async function toggleMarketingPush() {
  if (!marketingPushEnabled.value) {
    // ON 경로만 VAPID 필요
    const vapidKey = getVapidPublicKey();
    if (!vapidKey || vapidKey === 'not-configured') {
      console.warn('[Push] VAPID 공개키가 설정되지 않았습니다.');
      return;
    }
    /* subscribe + consent API */
  } else {
    // OFF 경로: VAPID 불필요, consent API만
    await fetch('/api/push/marketing-consent', { body: JSON.stringify({ enabled: false }) ... });
    marketingPushEnabled.value = false;
  }
}
```

## T-074. Thymeleaf 산술은 `${}` 안에 넣어야 정수 — `${x} / n`(밖)은 소수로 샌다

**증상**: 책 상세 누적 시간이 `· 누적 11.0944…시간 5.666…분`처럼 소수로 샌다. 그런데 **같은 페이지의 일자별 행과 `/history` 화면은 정수**(`1시간 30분`)로 멀쩡하다.

**원인**: 누적만 나눗셈이 `${}` **밖**에 있었다. `${}` 밖의 산술은 Thymeleaf 자체 산술이 처리해 소수로 평가하고, `${}` 안의 산술은 SpEL이 피연산자 타입(long)대로 정수 나눗셈을 한다.

```html
<!-- ❌ ${} 밖에서 나눗셈 → Thymeleaf 산술 → 5400/3600 = 1.5 -->
th:text="'· 누적 ' + (${totalSeconds} / 3600) + '시간 ...'"
<!-- ✅ 산술 전체를 한 ${...} 안에 → SpEL → 5400L/3600 = 1 -->
th:text="${'· 누적 ' + (totalSeconds / 3600) + '시간 ...'}"
```

일자별 행이 멀쩡했던 이유: 거긴 `${(r.totalSeconds() / 3600) + ...}`처럼 산술까지 `${}` 안이라 SpEL 정수 나눗셈.

**해결**: 나눗셈을 포함한 산술 **전체를 하나의 `${...}` 안**에 넣는다(일자별·history와 통일). MockMvc `content().string(containsString("누적 1시간 30분"))`으로 정수 표기를 RED→GREEN 고정. `#numbers.formatInteger`는 반올림이라 1.5→2가 되어 오답 — 쓰지 말 것.

**개념 상세**: [learning-notes.md](learning-notes.md) N-104.

**예방**: 푸시 토글 함수를 작성할 때 "ON일 때만 필요한 의존성"과 "공통으로 필요한 의존성"을 분기 전에 분리해 생각한다. 특히 수신거부(OFF)는 §50 정보통신망법상 즉시 처리 의무가 있어 막히면 안 된다.

**관련**: N-103(§50 광고성 푸시 요건), N-102(VAPID), PWA L3b.

---

## T-075. 파일명 고정 자산(pwa-install.js·app.css)을 SW cache-first로 두면 배포 후에도 stale 서빙

**증상**: `pwa-install.js`에 `white-space:nowrap`을 추가해 배포(#427)했는데, 일부 기기에서 PWA 설치 칩 라벨이 여전히 두 줄로 보인다. 서버에는 최신 파일이 올라가 있다.

**원인**: Service Worker가 구 `pwa-install.js`를 `shell-v3` 캐시에서 **cache-first**로 영구 서빙하고 있었다. `pwa-install.js`는 Vite 번들이 아닌 수동 파일이지만 **파일명이 고정**(해시 없음)이라 SW 캐시 버전을 올리지 않는 한 구 버전이 계속 살아남는다.

**T-071(garden.js)**과 같은 뿌리: 파일명 고정 → SW cache-first → 배포해도 stale.

**해결**:
1. `sw.js`에 `NETWORK_FIRST` 배열 신설, 파일명 고정 코드·스타일 자산을 모아 network-first 전략으로 통일:
   ```js
   const NETWORK_FIRST = ['/garden/garden.js', '/css/app.css', '/pwa-install.js'];
   // fetch 핸들러에서:
   if (NETWORK_FIRST.includes(url.pathname)) { /* network-first */ }
   ```
2. `CACHE` 버전 상수를 `shell-v3` → `shell-v4`로 올려 activate 시 기존 사용자의 구 캐시를 강제 삭제.

**예방**:
- SW에서 관리할 **파일명 고정 자산이 생기면 `NETWORK_FIRST`에 추가**한다(garden.js·app.css·pwa-install.js 패턴).
- 파일명에 콘텐츠 해시를 붙일 수 없는 자산이라면 반드시 network-first + CACHE 버전 관리.
- 배포 후 "코드는 올라갔는데 화면이 안 바뀜"이면 SW stale 가능성 먼저 의심.
- **신규 정적 파일**을 추가할 때도 마찬가지: SW가 첫 방문 때 캐시하면 그 버전이 stale로 굳는다. `NETWORK_FIRST` 배열에 미리 추가하거나, 개발 중 검증 시 `import('/파일명.js?bust=임의값')`으로 query param을 바꿔 SW 캐시를 bypass한다.

**관련**: T-071(garden.js cache-first stale), N-101(PWA 레벨), PR #430.

---

## T-076. `inlineDynamicImports:true`를 멀티 input과 함께 쓰면 Rollup 에러 — 페이지별 독립 빌드로 분리

**증상**: Vite 멀티빌드를 만들려고 `rollupOptions.input`을 객체(`{ search: '...', history: '...' }`)로 여러 엔트리를 한 빌드에 넣었더니 `inlineDynamicImports is not supported for multiple entry points` 빌드 에러.

**원인**: `inlineDynamicImports`(모든 동적 import를 한 파일로 인라인 = 단일 산출 파일)는 **단일 엔트리 전용** 옵션이다. Vite는 `input` 객체를 멀티 엔트리로 해석하고, 그 조합에서 이 옵션을 거부한다.

**해결**: 페이지별 **독립 빌드**로 분리한다 — `APP` env var로 분기(`cross-env APP=search vite build`), `vite.config.ts`가 `process.env.APP`로 그 페이지의 `input`·`outDir`·`entryFileNames`만 고른다. 각 빌드는 단일 엔트리라 `inlineDynamicImports:true`를 그대로 유지(페이지당 단일 파일 산출). 단계 0의 **Spring resource chain 해시**가 런타임에 파일명을 버저닝하므로 Vite manifest는 불필요.

**예방**: SPA 섬을 페이지마다 늘릴 때 "멀티엔트리 한 빌드"의 유혹을 피하고 페이지별 독립 빌드를 기본으로(Phaser 없는 가벼운 섬은 Vue 중복 번들 비용이 작아 충분). 공유 vendor chunk + manifest는 번들이 정말 클 때만.

**관련**: 선별 SPA 단계 1a(멀티빌드 인프라 첫 도입), T-063(빌드 산출물 수동 커밋), PR #438.

---

## T-077. jsdom에선 scroll-snap 컴포넌트의 scroll 계측이 모두 0 — 실 브라우저 게이트로 위임

**증상**: jsdom 단위테스트에서 캐러셀(scroll-snap) 컴포넌트의 `scrollBy`·`clientWidth`·`scrollLeft`·`offsetWidth`가 전부 0을 반환. `step()`·`sync()`가 의도한 스크롤을 일으켜도 값이 0이라 단언이 항상 통과하거나 항상 실패한다(가짜 신호).

**원인**: jsdom은 **CSS 레이아웃 엔진이 없어** 렌더링·레이아웃 연산을 수행하지 않는다 — 크기·스크롤 계측값이 0으로 고정.

**해결(테스트 전략)**: ① 단위로는 **버튼 존재 + emit 호출 + disabled 토글**만 단언(레이아웃 비의존 로직). ② 실제 스크롤 동작은 **실 브라우저(Chrome 확장) 게이트**로 위임(N-083). 깊은 스크롤 동작 자동화가 꼭 필요하면 jsdom 대신 Playwright·Cypress(headless Chromium=실 레이아웃 엔진).

**예방**: 클라이언트 레이아웃·스크롤·타이밍이 걸린 변경은 jsdom으로 끝내지 말고 실 브라우저 1회를 게이트로(헤드리스-블라인드 회귀 방지).

**관련**: N-083(defer×레이아웃 사각), T-053·T-052(헤드리스 한계), 단계 1c personality 캐러셀.

---

## T-078. git/commit 무한 로딩 = 커밋 훅의 gradle test hang(멀티세션 경합) — Claude Code 코어 아님

**증상**: 구현 세션에서 "git 미커밋 조회" 같은 작업이 무한 로딩에 걸림. esc로 멈추고 머지를 시도해도 Claude Code가 "돌기만 하고" 실제 작업을 안 함. clear로 세션을 날려야 풀림.

**원인(메커니즘)**: Claude Code는 git/gradle을 **자식 프로세스로 실행하고 그 종료를 기다린다**. 그 자식이 hang하면 Claude도 멈춘 것처럼 보인다 — **코어 버그가 아니라 자식 프로세스 hang**. 이 프로젝트는 `git commit`을 `.claude/hooks/require-tests-before-commit.ps1` 훅이 가로채 **`./gradlew test`를 돌리므로**, "git 작업"으로 보여도 실제로 멈춘 건 gradle 테스트일 때가 많다. gradle hang의 흔한 뿌리: **멀티 세션(워크트리 2개·동시 세션)이 같은 gradle 데몬·빌드 락을 동시에 점유**, 데몬 충돌/메모리.

왜 esc·머지로 안 풀렸나: esc는 Claude의 도구 호출만 끊고, 이미 spawn된 gradle 자식·데몬은 즉시 안 죽음 → 다음 커밋도 같은 훅 → 또 hang. **clear(세션 재시작)**가 멈춘 호출 컨텍스트를 정리해 풀린 것.

**감별**: 지금 `git status`가 빠르면(0.x초) git 환경·레포 자체는 정상 = 코어 문제 아님. `.git/index.lock` 없음 + 떠도는 `java`(gradle 데몬) 잔존이 단서.

**해결(hang 시)**:
```powershell
Get-Process java, git -EA SilentlyContinue | Stop-Process -Force
Remove-Item .git\index.lock -EA SilentlyContinue
./gradlew --stop   # gradle 데몬 정리
```

**예방**: 멀티 세션일 땐 **한 세션에서만 커밋/빌드**(gradle은 워크트리를 나눠도 데몬·빌드 캐시를 공유해 경합한다 — N-032). 커밋이 길게 멈추면 단순 esc 대신 위 강제 정리부터. **bootRun 정리는 8080 반납만으로 부족 — 포트로 죽이면 앱 JVM만 가고 gradle 데몬이 남아 다음 게이트와 경합하므로 `./gradlew --stop`까지 한 쌍으로**(CLAUDE.md 「🪢 8080」).

**3회차 재발 + 하드픽스 승격(2026-07-01)**: 2026-06-30 PR-B(#619) 커밋이 이 경합으로 **45분간 무한 freeze**(13:51:40→14:37:10, 다른 세션이 `java` 강제 종료해서야 풀림). bootRun 종료 시 8080만 Stop-Process하고 `--stop`을 안 해 데몬이 남은 게 직접 원인. 회차가 누적돼(2+→3+) **prose를 넘어 하드픽스로 승격**: 커밋 훅 `require-tests-before-commit.ps1`의 `gradlew test`를 **8분 타임아웃**(`BOOKTIMER_TEST_GATE_TIMEOUT_MS`로 조정)으로 감싸 — 초과 시 **프로세스 트리 `taskkill /T` + `gradlew --stop` 자가복구 후 `exit 2`(fail-closed)** 로 차단한다. 이제 무한 freeze는 8분짜리 자기보고 실패로 바뀐다(스모크 테스트: `.claude/hooks/tests/test-require-tests-timeout.sh`). 경합 *빈도* 감소(단일세션 빌드·`--stop`)는 여전히 예방 측 몫.

**관련**: N-032(워크트리 분리), T-070(bootRun 진행률 멈춤=정상), T-051(워크트리 머지 정리), 커밋 훅 require-tests-before-commit.ps1.

---

## T-079. Vue 섬 번들이 book-detail 라우트에 가로채여 무한로딩 — 경로변수 숫자 제한

**증상**: 프로덕션 `/books`(내 책장, Vue 섬)가 SSR 셸("불러오는 중…")만 뜨고 화면이 안 바뀜. `/search` 등 다른 섬은 정상. 네트워크: `/books/books-<hash>.js`(Vue 번들) → **503/500**(앱 커스텀 에러페이지), HTML·CSS는 200.

**원인(메커니즘)**: book-detail `@GetMapping("/books/{id}")`(`@PathVariable Long id`)가 같은 prefix를 쓰는 정적 번들 요청 `/books/books-<hash>.js`(2세그먼트)를 가로챈다. Spring은 `@RequestMapping` 핸들러(우선순위 0)를 기본 정적 리소스 핸들러(`/**`, 최저 우선순위)보다 **먼저** 매칭하므로, 컨트롤러가 번들을 잡아 `id="books-<hash>.js"`→`Long` 변환 실패→예외→500/503. 번들이 실행 안 돼 Vue 마운트·`/api/books`가 안 일어나고 셸이 영영 로딩. (#425 book-detail + #447 books 섬 번들이 둘 다 `/books/` prefix → 머지 후 잠복.)

**감별**:
- 셸은 뜨는데 화면 안 바뀜 = JS 번들 로드 실패 의심 → 네트워크에서 `/<page>/<page>-<hash>.js` 상태 확인.
- **503/500이면(404 아님)** 정적 누락이 아니라 **앱이 그 경로를 컨트롤러로 처리 중** → 같은 prefix `@GetMapping("/<page>/{var}")` 충돌 의심.
- stale 캐시 배제: `git show origin/main:src/main/resources/static/<page>/<page>.js | md5sum`이 요청 URL 해시와 같으면 캐시 아님(서빙 자체 문제).

**해결**: 경로 변수를 정적 자산과 안 겹치게 제한 — 숫자 id면 `@GetMapping("/books/{id:\\d+}")`. 비숫자 경로(번들·소스맵·css)는 기본 정적 핸들러로 폴백→200.

**예방**:
- 섬 번들을 `/<page>/<page>.js`(페이지 셸 라우트와 같은 prefix)에 두는데 그 prefix에 `@GetMapping("/<page>/{var}")`가 있으면 충돌 → 경로변수를 타입에 맞게 제한(`{id:\d+}`)하거나 번들을 다른 prefix로.
- 회귀 테스트로 라우팅을 직접 친다: `GET /<page>/<page>.js`→2xx, `GET /<page>/{비타입값}`→404. **헤드리스/단위테스트는 `/api/*`를 목으로 두고 Vue를 직접 마운트해 실 번들 URL이 라우팅을 안 타 못 잡는다**(N-112).
- 부작용 주의: `{id:\d+}` 제한은 `POST /books/add` 같은 비숫자 POST를 405→404로 바꾼다(매핑 자체가 사라지므로) — 관련 단언이 있으면 갱신.
- **로컬 커밋 게이트가 전체 suite를 항상 돌리진 않는다**(PowerShell 커밋 시 Bash-scoped 훅 미발동 등) → 라우팅 변경은 전체 `./gradlew test` 직접 1회 후 push(CI 재실패 방지).

**관련**: N-112(핸들러 매핑 우선순위·헤드리스 사각), N-083(defer×TDZ 클라이언트 사각), T-062(번들 미커밋 404=누락 ↔ 본 건은 충돌 503), T-077/T-053/T-054(헤드리스 사각), PR #450.

---

## T-080. Service Worker가 에러 응답(500/503)을 캐싱 → 서버 fix 후에도 stale

**증상**: 서버 버그를 고쳐 배포했는데도 일부 사용자에게 같은 깨짐이 지속. 해당 정적 자산(예: Vue 번들 `/books/books-<hash>.js`) 요청이 **fresh인데도 500/503**. 캐시버스터(`?cb=1`)를 붙이면 200 → 오리진은 정상.

**원인(메커니즘)**: `sw.js`의 정적 cache-first 핸들러가 `res.ok` 검사 없이 응답을 캐싱했다:

```js
return fetch(request).then((res) => {
    caches.open(CACHE).then((c) => c.put(request, res.clone())); // ← 500도 캐싱됨
    return res;
});
```

버그 시기(라우트 충돌 #450)에 번들이 낸 500이 캐시(`shell-v5`)에 들어갔고, cache-first(`if (cached) return cached`)는 **재검증 없이 그 500을 영구 서빙** → 오리진을 고쳐도 self-heal 안 됨. 해시 URL은 *내용* stale은 막지만(내용 바뀌면 URL이 바뀜) *캐싱된 에러*는 못 막는다.

**감별**:
- 서버 fix 배포 후에도 깨짐 지속 + fresh 요청 503/500 + **캐시버스터(`?cb=1`) → 200** = SW(또는 CDN) 캐시.
- `via`/`x-cache` 헤더 없으면 CDN 아님 → SW. 확인: `caches.keys()` + `cache.match(url)`의 `.status`(예: `shell-v5`에 500), `navigator.serviceWorker.controller`.

**해결**:
1. **에러 응답 캐싱 금지** — `if (res.ok)` 가드로 2xx만 `put`(재발 방지).
2. **`CACHE` 버전 bump**(`shell-v5`→`v6`) — activate가 옛 캐시를 지우므로(`keys.filter(k => k !== CACHE)`) 새 `sw.js` 배포 시 나쁜 캐시가 purge돼 기존 피해자가 다음 방문에 자동 복구.
3. 개별 사용자 즉시 복구: SW 해제 + `caches.delete()` 후 새로고침.

**예방**: SW 캐시 `put`은 항상 `res.ok` 가드 — cache-first 자산은 에러를 절대 캐시에 남기지 말 것. SW 동작은 단위테스트(전역 목 하니스 과대)보다 배포 후 실 브라우저 게이트(`caches.keys()`·번들 200 확인).

**관련**: T-071/T-075(SW 해시 자산 stale·NETWORK_FIRST), N-101(PWA 레벨), N-112(이 캐싱을 유발한 라우트 충돌), PR #450 후속.

---

## T-081. SPA 전환에서 form 래퍼 제거 → 전역 button width 100%가 flex-row 액션을 풀폭 세로로 깨뜨림

**증상**: SSR→Vue 전환 후 리스트 행 액션 버튼(예: `/books` 공개토글·삭제, 프로필 팔로우·차단)이 한 줄
정렬이 아니라 **각자 풀폭으로 세로로 쌓임**. CSS·클래스명은 전환 전후 동일한데 레이아웃만 깨짐.

**원인**: 전역 `button{width:100%}`(app.css). SSR은 각 액션을 작은 `<form>`으로 감싸 버튼이 그 form 폭에
갇혔는데, Vue(fetch)는 `<form>`을 없애 버튼이 flex-row 컨테이너(`.book-actions`·`.profile-actions`)의
**직계 자식**이 되며 `width:100%`를 컨테이너 폭으로 받아 풀폭 → `flex-wrap`에서 세로 쌓임. (메커니즘 N-113.)

**감별**:
- 같은 행의 `<select>`는 멀쩡(작게)한데 `<button>`만 풀폭 = 전역 `button{width:100%}` 의심(select는 안 받음).
- 실 렌더에서 버튼 `getBoundingClientRect().width`가 컨테이너 폭과 같으면 확정.

**해결**: flex-row 컨테이너의 직계 버튼에 `width:auto` 상쇄(248·761·1213행과 같은 패턴).

```css
.book-actions > button { width: auto; }                  /* books·follow-list·book-readers·block-list·search 공용 */
.profile-follow .profile-actions button { width: auto; } /* profile 팔로우·차단 */
```

`.book-actions`는 `UserRow` 공용 셸을 거쳐 books·follow-list·book-readers·block-list·search를 한 번에 커버.

**예방**: SSR→SPA 전환에서 액션을 감싸던 `<form>`/래퍼를 제거할 때, 그 래퍼가 **전역 폼컨트롤 width를
가두고 있었는지** 확인 — flex-row면 직계 버튼에 `width:auto`. block 컨테이너(`.timer-controls` 등 단일 CTA)는
풀폭이 의도라 무관. 순수 CSS 시각 회귀라 실 렌더(폭 측정·스크린샷)로 검증.

**관련**: N-113(개념·메커니즘), N-112/T-079(같은 #447 books 전환의 라우트 충돌 회귀), N-032(구조 변경이 폭로하는 잠복 회귀).

---

## T-082. 라디오 CSS탭 → JS(v-if) 탭 전환에서 clear·display·active 경로 누락 + 빌드 stale (책방 탭 3종 깨짐)

**증상**: 선별 SPA 전환 후 책방(`/u/{loginId}`) 탭에서 ①책BTI·공개 책장 패널 내용 안 보임 ②하단 `.link-row`(대시보드/내 책장/차단)가 우측 ~25px로 짓눌림 ③활성 탭 밑줄 강조 사라짐 — 셋이 동시 발생.

**원인**: history용 라디오 CSS 탭(`.record-card`/`.record-tab{float:left}` + `<input type=radio>` + `#id:checked ~ .panel{display:block;clear:both}`)을 책방이 재사용하다 Vue `v-if`로 옮기며 라디오를 뺐는데, 거기 묶였던 3가지를 함께 잃음:
1. **패널 display** — `ProfileApp.vue`에서 `tab-panel` 클래스를 뺐으나 `npm build` 누락으로 `profile.js` 산출물이 stale → production은 `tab-panel` 잔존 → `.tab-panel{display:none}` 적중(T-063 빌드 누락 재발).
2. **float clear** — 죽은 `#tab-bti:checked ~ .panel-bti{display:block;clear:both}` 셀렉터를 지우며 `clear:both`까지 삭제 → `.record-tab{float:left}`가 clear 안 됨 → `.record-card` collapse(높이 0) → 다음 형제 `.link-row`(block grid)가 stretch 폭을 못 받아 min-content로 붕괴(개념 N-114).
3. **active 스타일** — 활성 강조가 `:checked + .record-tab`에만 있어 JS의 `.active` 클래스엔 안 먹음.

**감별**:
- 산출물 stale = `git log -- <.vue>` 최신 커밋과 `git log -- <빌드.js>` 최신 커밋이 어긋남. production DOM에 `.vue`엔 없는 옛 클래스(`tab-panel`)가 보이면 확정.
- link-row 짓눌림 = 실 브라우저에서 그 요소만 `width≈min-content`인데 형제 `.card`(block)는 정상 폭. 그 요소를 DOM에서 떼 `width:460px` 고정 div에 넣어 정상화되면 부모 sizing 문제, float 형제 제거 시 펴지면 float collapse 확정.
- active 측정은 `transition: border-color .15s` 탓에 주입 직후엔 옛값을 보니 ~300ms 대기 후 측정(안 그러면 "안 먹는다"고 오판).

**해결**: ①`npm build`로 산출물 재생성(`tab-panel` 잔재 제거) ②`.panel-bti,.panel-shelf{clear:both}` 복원 ③`.record-card .record-tab.active`를 `:checked + .record-tab`와 같은 활성 스타일에 합류.

**예방**: 라디오 CSS 탭을 JS 탭으로 옮길 땐 **display·clear·active(:checked) 세 경로를 전수 이전**한다 — 죽은 `:checked ~` 셀렉터엔 시각 표시 외에 `clear`/`display` 같은 레이아웃 책임이 섞여 있어 무심코 지우면 형제까지 깨진다. `.vue` 수정 뒤 `npm build`+산출물 커밋(T-063). 클라 시각 회귀라 실 브라우저 게이트. 파급은 같은 클래스 재사용처만(history는 라디오+`:checked` 유지로 정상).

**관련**: T-063(프론트 빌드 누락), T-081/N-113(같은 SPA 전환 잠복 회귀·전역 width), N-114(float collapse × block grid), N-032(구조 변경이 폭로하는 잠복 회귀).

---

## T-083. gh pr checks --watch가 CI 등록 전 실행되면 "no checks reported"로 즉시 exit 1

**증상**: PR push 직후 `gh pr checks <PR> --watch`로 CI 통과를 기다리려는데, watch가 대기하지 않고 즉시 `no checks reported on the '<branch>' branch`를 출력하며 exit 1로 끝난다. 뒤에 `&& gh pr merge`를 체이닝했으면 머지가 실행되지 않는다(백그라운드 작업이 "실패"로 종료).

**원인**: push 후 GitHub Actions 워크플로가 *체크 런으로 등록*되기까지 짧은 지연이 있다. 그 사이에 `--watch`가 돌면 감시할 체크가 0이라 "기다림" 없이 바로 끝난다 — `--watch`는 "이미 있는 체크가 끝나길" 기다리지 "체크가 생기길" 기다리지 않는다. 같은 절차라도 등록이 빠르면(직전 PR은 곧장 pending) 통과하고 늦으면 실패해 **비결정적**으로 보인다.

**감별**: `gh pr checks <PR>` 단발 호출이 `no checks reported`면 아직 미등록(머지 충돌로 DIRTY일 때도 체크가 안 붙을 수 있으니 `gh pr view <PR> --json mergeStateStatus`로 DIRTY 여부 먼저 배제).

**⚠️ 자주 재발 — DIRTY 오진 함정**: `no checks reported` 를 "등록 중" 으로 오해하고 sleep-폴링 루프에 들어가면, PR이 DIRTY(충돌) 상태이면 CI가 **영영** 안 붙어서 타임아웃 없이 무한 대기한다. DIRTY면 먼저 `git rebase origin/main` + `git push --force-with-lease` 로 충돌을 풀어야 CI가 돌기 시작한다.

**해결**: watch 전에 반드시 `mergeStateStatus` 를 확인하고, DIRTY면 재base 후 재진행한다. CI 등록 대기가 필요한 경우에만 sleep-폴링:
```bash
for i in $(seq 1 30); do gh pr checks <PR> 2>&1 | grep -q 'no checks' && sleep 20 || break; done
gh pr checks <PR> --watch --interval 30 && gh pr merge <PR> --squash --delete-branch
```
(Bash 도구는 foreground sleep을 막으니 이 루프는 `run_in_background`로 실행)

**완전한 자동화** — DIRTY 진단 + CI 폴링 + 하드 타임아웃(12분) + 원격 브랜치 삭제를 한 번에:
```bash
bash .claude/scripts/pr-merge.sh <PR번호>
```

**예방**: `push → checks --watch → merge`를 한 명령으로 엮을 땐 mergeStateStatus 진단을 가장 먼저 넣는다(DIRTY → 즉시 bail). 워크트리에서 `--delete-branch` 로컬정리가 실패하는 건 별개 트랩 **T-051**(원격 머지는 성공하니 `gh pr view`로 MERGED 확인 후 수동 정리).

**관련**: T-051(워크트리 gh pr merge 로컬정리 실패), T-048(squash subject), N-070(branch protection required check), T-070(bootRun 진행률=장기 태스크 ready 판정).

---

## T-084. Phaser `update()`가 매 프레임 덮는 속성에 tween을 걸면 즉시 무효화 — 효과는 독립 오브젝트로 분리

**증상**: 배회 캐릭터 먹이 반응 애니로 `this.tweens.add({targets: obj, y: ..., scaleY: ...})`를 걸었더니 `update()`가 매 프레임 `walkPose`로 `o.y = py+bobY`, `o.setScale(...)`, `o.setAngle(...)`를 덮어써 tween이 보이지 않는 현상.

**감별**: tween 콜백은 실행되는데 화면에 변화 없음. `update()`에 breakpoint 걸면 tween 값 덮임 확인.

**해결 / 예방**: 캐릭터와 독립된 오브젝트(`this.add.text(...)`)를 `objs`에 넣지 않고 별도 tween으로 처리 → `update()`가 그 오브젝트를 건드리지 않아 충돌 없음. Phaser에서 매 프레임 속성을 직접 세팅하는 `update()` 루프가 있으면, 그 오브젝트에 직접 tween 금지 — 독립 오브젝트로 효과 분리할 것.

**관련**: PR #475.

---

## T-085. PowerShell `docker exec … mysql -e`로 한글 INSERT 시 CP949로 `?????` 저장 — 한글은 Spring API(JSON) 경유 삽입

**증상**: PowerShell에서 `docker exec ... mysql -e "INSERT ... VALUES('한강'...)"`을 실행하면 CP949(ANSI)로 인코딩된 바이트가 MySQL로 전달돼 DB에 `?????`로 저장됨. UTF-8 파일을 `docker cp` 후 stdin으로 넘겨도 PowerShell `Get-Content`의 CRLF 처리나 character set 협상 실패로 동일하게 깨질 수 있음.

**감별**: INSERT 후 `SELECT HEX(col)` 결과가 올바른 UTF-8 HEX(한강=`ED959CEA B095`)가 아닌 다른 바이트열이면 깨진 것.

**해결 / 예방**: 한글이 포함된 데이터는 **Spring Boot API(JSON POST)를 경유**해 삽입 — HTTP 요청은 UTF-8 Content-Type으로 전달되고 서버가 JPA로 올바르게 저장. 크롬 확장 `javascript_tool`로 `fetch('/api/...', {method:'POST', body: JSON.stringify({author:'한강'})})` 형태. 영문 컬럼(code·status 등)만 포함된 INSERT는 docker exec mysql로 직접 가능. 로컬 DB 시드 시 한글 포함 여부 확인 후 경로 선택.

**보강(2026-07-02, 3회차 재발)**: git-bash에서 `docker exec ... mysql -e "INSERT ...한글..."` 인라인도 **이중 인코딩**(UTF-8 바이트가 latin1로 재해석 — `SELECT HEX()`가 `C3AC C2B2…` 패턴)으로 깨진다. API 경유가 곤란한 직접 시드(브라우저 게이트용 픽스처 등)는 **UTF-8 파일 + stdin 파이프**가 동작 확인된 대안: SQL을 도구(Write)로 UTF-8 파일에 쓰고 `docker exec -i <컨테이너> mysql --default-character-set=utf8mb4 … < file.sql`. 검증은 `SELECT HEX(LEFT(col,3))` — 한글 1자가 3바이트(`EAxxxx`~`EDxxxx` 대역)면 정상. 이 군의 3회차(T-085 → T-119(UNHEX 주입 대안) → 이번) — 재발·승격 트래커 등재.

---

## T-086. Docker 컨테이너 수십 개 누적의 범인은 `bootRun`(테스트 아님) — `working_dir` 라벨로 BookTimer 것만 정리

**증상**: `docker ps -a`에 `booktimer-*`·랜덤이름 mysql 컨테이너가 워크트리 수만큼 쌓이고 일부는 검증 후 안 꺼진 Up 좀비·일부는 워크트리 삭제 후 Exited 고아.

**원인**: `./gradlew test`는 H2라 컨테이너를 안 만든다(`spring.docker.compose.enabled=false`) — `bootRun`만 `spring-boot-docker-compose`로 `compose.yaml` MySQL을 자동 기동하고, compose 프로젝트명이 **워크트리 폴더명별로 갈려** 컨테이너가 따로 생기며 Spring은 stop만 하고 rm은 안 해 사라진 워크트리의 것이 고아로 남음.

**해결 / 예방**:
```bash
bash .claude/scripts/docker-cleanup.sh            # 기본: Exited만 (멀티세션 안전)
bash .claude/scripts/docker-cleanup.sh --all      # Up 포함 전부 (먼저 --dry-run 권장)
```
`com.docker.compose.project.working_dir` 라벨이 BookTimer 경로 계열인 것만 지워 타 프로젝트는 보호. 검증용 bootRun 종료 시 8080 반납과 함께 컨테이너도 내린다. 멀티세션 땐 Up 보존 위해 기본 모드 사용.

**관련**: CLAUDE.md 「🪢 다중 세션」·「🛠️ 빌드/실행 메모」.

---

## T-087. CSS 주석 속 `*/`가 주석을 조기 종료해 다음 규칙을 침묵 드랍

**증상**: 특정 클래스만 전부 스타일 미적용·바로 다음 규칙부터 정상. 파일·`fetch`엔 이상 없음. 콘솔 에러 0.

**감별**: `[...document.styleSheets[0].cssRules].some(r=>r.selectorText==='.해당-클래스')`가 false면 파싱 드랍. 캐시는 `?v=` cache-bust로 분리.

**원인**: CSS 주석 안에 `.timer-*/.quick-*` 같은 wildcard-slash 표기가 있으면, `.quick-*/`의 `*/`가 주석 닫는 토큰으로 해석돼 주석이 일찍 닫힘 → 뒤따르는 셀렉터와 규칙이 invalid로 묶여 드랍.

**해결 / 예방**: 주석 안 `*/` 유발 토큰 회피 — `.timer-*/.quick-*` → `.timer-*·.quick-*` (슬래시를 `·` 또는 `과`로). wildcard 클래스를 주석에 나열할 땐 슬래시 금지를 반사적으로. **하드픽스 훅 `require-css-comment-safe.ps1`** 적용(3회차에 승격).

**관련**: 개념 N-118, 시각검증 T-043. **3회차(#522·#526·이 계열)** — 트래커 등재.

---

## T-088. 백그라운드 PR 머지 태스크를 띄우고 완료 후속(exit 코드 확인)을 안 챙겨 머지 방치

**증상**: PR 머지 태스크를 백그라운드로 띄운 후 `exit≠0`(DIRTY 등)으로 종료됐는데 후속을 안 해 PR이 ~40분 OPEN 방치.

**원인**: `pr-merge.sh`의 "12분 하드 타임아웃"은 **스크립트가 도는 동안만** 유효 — exit 후엔 아무도 안 돌아 타임아웃조차 안 걸려 무한 방치처럼 보임.

**감별**: PR이 아직 OPEN인데 백그라운드 태스크는 이미 종료(`exit≠0`이면 사람이 손대야 할 신호).

**해결 / 예방**: ① 호출자 규칙 = 백그라운드 머지 태스크 **완료 알림이 오면 반드시 output 파일을 읽어 exit 코드 확인 후 후속 처리**(0 머지완료→로컬 main 갱신 / 3 DIRTY·rebase 충돌→수동 / 4 CI 실패→원인 / 5 타임아웃→상태 확인). ② `pr-merge.sh <PR> --rebase`로 DIRTY 자동 rebase+force push 후 머지 폴링을 **한 호출 안에서** 이어가 흐름 끊김 자체를 제거.

**관련**: T-083(DIRTY 헛폴링), T-051(워크트리 로컬정리), PR #489.

---

## T-089. 반응형 재현 하니스 mock이 production worst-case(최장 문자열)를 안 담으면 RED가 안 떠 레이아웃 버그를 놓침

**증상**: static-preview mock이 짧은 값(`remainingSeconds=1200` → "20:00", 5글자)라 실제 `01:43:47`(8글자 HH:MM:SS) 오버플로가 재현 안 돼 첫 측정이 "겹침 없음(-38px)"으로 나옴.

**원인**: 숫자·문자열의 폭/줄수처럼 길이에 비례하는 레이아웃은 **가장 긴 케이스에서만** 깨지는데 mock이 짧은 값이라 그 경계를 안 건드림(헤드리스 green = 가짜 green, T-053 부류).

**감별**: element box rect는 컬럼 폭에 맞아 멀쩡해 보이니 텍스트 실폭은 `range.getBoundingClientRect()`/`scrollWidth`로 따로 잰다(block div는 overflow돼도 box는 안 늘어남).

**해결 / 예방**: 재현 mock을 production 최악 케이스로 맞춘다(최대 글자수·최장 문자열·최다 항목) 후 RED 확인 → 수정 → GREEN. 레이아웃 회귀를 static-preview로 잡을 땐 "이 화면에서 가장 넓어질 수 있는 콘텐츠가 뭔가"를 먼저 mock에 박는다.

**관련**: N-055(null-state 누락과 같은 뿌리), N-119(축 전환 함정), N-117(static-preview), T-043, PR #491.

---

## T-090. Windows preview `launch.json`으로 `gradlew bootRun` 못 띄움 — `cmd /c <절대경로>gradlew.bat -p <절대경로> bootRun`

**증상**: `runtimeExecutable: "gradlew.bat"`(상대경로)은 "내부/외부 명령이 아님" 에러로 즉시 실패. `cmd /c gradlew.bat`로 감싸도 cwd가 안 맞아 또 실패.

**원인**: preview가 PATH에서 `.bat`을 찾는데 cwd가 워크트리 루트라는 보장 없음.

**해결 / 예방**:
```json
{
  "runtimeExecutable": "cmd",
  "runtimeArgs": ["/c", "<절대경로>\\gradlew.bat", "-p", "<워크트리 절대경로>", "bootRun"]
}
```
`.bat`은 cmd 셸 경유 필수 + 절대경로로 cwd 의존 제거 + `-p`(gradle `--project-dir`)로 프로젝트 디렉토리 고정. `bootRun`은 무겁다(빌드+MySQL Docker) — SSR fragment 로드순서처럼 정적 mock으론 못 잡는 검증에만 쓰고, 순수 로직·반응성은 N-117 static-preview 우선. `.claude/launch.json`은 gitignore라 커밋 안 됨.

**관련**: N-117(static-preview), T-078(gradle 데몬 경합), T-086(Docker 누적).

---

## T-091. `pr-merge.sh`가 머지 성공 후 `git push origin --delete`에서 hang → 백그라운드 머지 안 끝남

**증상**: PR은 이미 **MERGED**인데 백그라운드 Bash 작업이 계속 "실행 중". 완료 알림조차 안 옴(`do_merge`가 `exit 0`에 도달 못 해 명령 끝의 `| tail`이 프로세스 종료를 영원히 대기).

**감별**: `gh pr view <PR> --json state`가 MERGED + 원격 브랜치는 **미삭제**로 남아 있고 + 머지 직후 시각에 시작된 `git` 좀비 프로세스가 잔존(`Get-Process git | Select StartTime`). `git status`는 빠름(레포·코어 정상, 매달린 건 자식 프로세스, T-078 진단과 동형).

**원인**: `gh pr merge`(자체 토큰)는 됐지만 뒤이은 `git push origin --delete`가 비대화형 백그라운드에서 credential/원격 단계에 멈춤.

**해결 / 예방**: `TaskStop`으로 작업 종료 → 좀비 `git` PID `Stop-Process -Force` + `.git/index.lock` 정리 → 수동 `git push origin --delete <branch>` → 로컬 main 갱신. `do_merge`의 push를 `timeout 30`으로 감싸 hang을 30s로 제한(실패해도 머지는 끝났으니 진행). 백그라운드 머지가 시간 내 완료 알림 없으면 PR 상태부터 직접 확인(MERGED면 스크립트 hang 확정).

**관련**: T-088(exit 후 후속 누락), T-094(T-091의 재발 — `timeout 30` 미봉책 한계 드러남), T-083, T-078.

---

## T-092. minified Vue 프로덕션 번들은 `setupState` 키가 숨겨짐 — 루트 `_vnode.component`에서 `subTree` BFS+props 변이

**증상**: preview 검증용으로 `el.__vue_app__._instance`/`setupState.data`를 건드리려 했으나 `_instance`가 null처럼 보이고 `setupState`는 `Object.keys`가 `[]`·`setupState.data` 접근은 null 반환(빌드 minify로 `<script setup>` 바인딩 키가 숨겨짐).

**감별**: `el._vnode.component`는 살아 있음(루트 instance). 거기서 `subTree`로 자식 vnode 트리를 탈 수 있음.

**해결 / 예방**: 루트(`el._vnode.component`)부터 `subTree`를 BFS로 훑어 **목표 자식 컴포넌트**(예: `props.garden`을 가진 GardenPanel)를 찾고, 그 `inst.props.<객체>`(reactive 프록시·부모 ref와 같은 참조)를 직접 변이 → Vue가 재렌더(`el.__vue_app__`는 존재하나 그 경유는 막힘). 주의: `props`는 읽기전용이라 **재할당(`inst.props.x = ...`)이 아니라 객체 내부 속성 변이**(`g.ownedCharacters = [...]`)로, 부모 reactive 객체를 직접 건드려 reactivity 발동. setupState 대신 트리 BFS+props 변이가 prod 빌드 정공법.

**관련**: N-117(static-preview), N-082(reactive Proxy), T-090(bootRun preview).

---

## T-093. 워크트리 `npm run build`가 무관 9개 번들을 CRLF-only로 ` M` 표시 — `git diff --numstat`로 감별, 변경 파일만 stage

**증상**: history만 고쳤는데 garden·dashboard·books 등 9개 `.js`가 전부 modified로 보여 "안 건드린 번들이 왜?" 혼란. `git add -A` 하면 무관 번들까지 stage.

**원인**: `core.autocrlf=true`라 working tree LF↔CRLF 차이뿐 — 커밋된 블롭은 LF인데 워크트리 새 `node_modules`의 vite가 재생성하며 mtime·줄바꿈 상태가 바뀌어 status는 dirty로 보이나 git 정규화 후 동일.

**감별**: `git diff --numstat <bundle>`이 행을 안 뱉으면(경고만) 내용차 0=CRLF뿐. 실 변경은 numstat에 `+/-` 줄수가 찍힘(예: history.js `5 5`, app.css `68 18`).

**해결 / 예방**: 실 변경 파일만 명시 stage(history.js·app.css·소스·docs), 무관 9개는 add 안 함. 번들 훅 `require-bundle-build.ps1`은 `git diff --exit-code -- src/main/resources/static`(내용 비교)라 CRLF-only는 통과(전체 빌드가 커밋 번들을 결정적으로 재현하면 exit 0). 워크트리에서 프론트 PR 커밋 시 `git add -A` 금지·변경 파일만 명시. **2회차(T-103 재발)**.

**관련**: N-117(static-preview), T-082, T-063, T-103(BOM/EOL 미보존 군).

---

## T-094. Windows `timeout 30 git push --delete`도 hang 못 막음 → `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`

**증상**: PR은 MERGED인데 백그라운드 Bash가 계속 running·완료 알림 없음·출력 0(파이프 풀버퍼링이라 hang 중엔 그간 찍은 로그도 flush 안 됨). `Get-Process git`에 머지 시각 시작된 git이 수십 분째 잔존하고 `Stop-Process`/`taskkill /F`도 "Access denied"(하네스 태스크 트리 소속이라 외부서 못 죽임).

**원인**: git push가 띄운 **자식 프로세스**(git-remote-https·credential helper)가 SIGTERM을 안 받아 `timeout`이 죽이려 해도 살아남고, 그 자식이 stdout 파이프를 쥐고 있어 `timeout`/스크립트가 exit를 못 함. T-091의 `timeout 30` 처방이 실제론 무효였음이 재발로 드러남(사용자 40분 대기).

**해결 / 예방**: **git push를 아예 안 쓴다** — `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`로 원격 ref를 HTTP 삭제(gh는 자체 토큰·자식 프로세스 없음이라 hang 불가). `pr-merge.sh do_merge`를 이 방식으로 교체. 좀비 응급: PR `MERGED`면 머지는 끝난 것 → `TaskStop`으로 태스크 종료. 원격 브랜치가 남았으면 `gh api -X DELETE .../git/refs/heads/<branch>`로 정리.

**관련**: T-091(이 트랩의 1차 — `timeout 30` 미봉책), T-088, T-078. **T-091의 재발**.

---

## T-095. 워크트리 `gh pr merge --delete-branch`가 `main is already used by worktree`로 깨짐 — 머지는 성공

**증상**: `gh pr merge --auto --squash --delete-branch`가 `failed to run git: fatal: 'main' is already used by worktree at '<주 워크트리>'`로 비정상 종료 — 단 **머지·auto-merge 등록 자체는 성공**하고 로컬 `--delete-branch` 단계만 깨진다.

**감별**: `gh pr view <PR> --json state`가 `MERGED`이면 머지는 성공. `git ls-remote --heads origin <branch>`로 원격 브랜치 잔존 확인.

**원인**: `gh pr merge --delete-branch`는 머지 후 로컬 브랜치를 지우려고 기본 브랜치(main)로 `git checkout`을 시도하는데, 워크트리 세션에선 main이 **주 워크트리에 이미 체크아웃**돼 있어 gh의 그 로컬 git 단계가 거부됨. 서버사이드 머지·`--auto` 등록은 그 전에 끝나 영향 없음.

**해결 / 예방**: ① 머지 확인(`state=MERGED`) ② 원격 정리 `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>` ③ 로컬 정리는 main 말고 그 워크트리의 **베이스 브랜치로** `git checkout <base>` 후 `git branch -D <branch>`. 워크트리에서 머지할 땐 `--delete-branch`를 빼고 `gh pr merge <PR> --auto --squash`만 등록.

**관련**: T-094(Windows `git push --delete` hang 동시 회피), #511·#515. **2회차 — 트래커 등재 + CLAUDE.md Git워크플로 auto-merge 절 caveat 승격 완료.**

---

## T-096. 연쇄 PR 폴링 미머지 종료(TIMEOUT/OPEN/DIRTY)를 머지 완료로 오인 — 다음 브랜치 전 `gh pr view --json state`=MERGED 확인

**증상**: PR-1 머지 폴링이 `RESULT: TIMEOUT last=OPEN`(CI는 SUCCESS였으나 다른 세션 머지로 DIRTY라 머지 안 됨)인데 "머지됨"으로 읽고 `git checkout -b <pr2> origin/main` → origin/main에 PR-1이 없어 직전 PR 변경이 PR-2 브랜치에 빠진 채 시작. 빌드·테스트는 통과해 **조용한 회귀**.

**원인**: 폴링 종료를 "머지 완료"와 동일시 — `TIMEOUT`/`OPEN`/`DIRTY`는 미머지 종료인데 다음 단계가 그걸 머지 전제로 진행.

**해결 / 예방**: 다음 단계 브랜치를 따기 전 **반드시 `gh pr view <PR> --json state`가 `MERGED`인지 확인**(폴링 exit·메시지가 아니라 PR 상태가 단일 진실). DIRTY면 `git rebase origin/main`+`push --force-with-lease`로 해결 후 재머지(CLAUDE.md Git워크플로 5번). 연쇄 PR에서 "폴링 끝 ≠ 머지 완료".

**관련**: N-032(워크트리·브랜치 격리), T-083(DIRTY 헛폴링).

---

## T-097. Git Bash에서 멀티바이트(이모지·한글) `grep`/`sed` 패턴이 조용히 0건 — PowerShell `.Contains/.Replace` 또는 Grep(ripgrep)

**증상**: `grep -rl '<span class="emoji">📚</span>' ... | while read f; do sed -i ...; done`이 grep 0건이라 while 루프가 한 번도 안 돌아 **치환 0건인데 에러 없이 성공처럼 종료**.

**원인**: 이 환경 Git Bash가 C 로케일이라 4바이트 이모지 등 멀티바이트를 패턴으로 신뢰성 있게 못 잡음(반면 **Grep 도구=ripgrep은 같은 패턴을 정상 매칭**해 대비로 진단됨).

**해결 / 예방**: 일괄 치환은 PowerShell 리터럴 `.Contains`/`.Replace`로 우회(정규식 아닌 리터럴이라 SVG의 `/`·`"`·`$` 이스케이프 불필요). 쓰기는 BOM 없는 UTF-8(`New-Object System.Text.UTF8Encoding $false` + `[IO.File]::WriteAllText`)로 저장(PS5.1 `Set-Content -Encoding utf8`는 BOM 부착). 검증은 **bash grep 말고 Grep 도구(ripgrep)**로. 멀티바이트(이모지·한글) 검색·치환은 Grep 도구나 PowerShell로, bash `grep`/`sed`에 멀티바이트 패턴을 직접 넣지 말 것.

**관련**: T-026, T-003(PowerShell 5.1 인코딩·멀티바이트 처리 함정 군).

---

## T-098. changelog 멀티세션 동시 append 충돌 → `.gitattributes` `merge=union`

**증상**: 멀티세션이 전부 `claude-docs/changelog.md` 맨 아래에 행을 추가해 항상 같은 위치가 충돌 → PR마다 rebase·force-push 왕복. 2026-06-26 하루에만 #516·#518·#520·#523에서 반복(한 PR이 두 번 DIRTY 나기도).

**원인**: CLAUDE.md가 "changelog 맨 아래 한 줄"을 강제하는데 append-only 로그라 서로 다른 새 행이 파일 끝 같은 hunk에 떨어져 git이 자동 병합 불가 → 멀티세션 활발기엔 구조적으로 불가피.

**해결 / 예방**: `.gitattributes`에 `claude-docs/changelog.md merge=union` — git 내장 union merge 드라이버가 충돌 시 양쪽 hunk를 마커 없이 **둘 다** 보존(append-only에 정확히 맞음, 중복 행 없음). 주의: union은 **양쪽이 같은 행을 수정**하면 둘 다 남겨 중복 가능 — changelog는 각자 새 행만 추가라 안전, 본문을 동시 편집하는 파일엔 부적합. 적용 시점: 이 PR이 main에 들어간 **다음** rebase부터 효과.

**관련**: T-083(DIRTY 진단), T-096(미머지 오인). **멀티세션 충돌 군 5회+**.

---

## T-099. 전역 `button{border-radius}` 누수 — 명시값 제거 시 전역값이 샌다, `border-radius:0`로 상쇄

**증상**: 책장 필터 세그먼트를 각지게(컨테이너 `border-radius:8px`+`overflow:hidden`) 만든 뒤 선택된 active 셀의 초록 하이라이트만 더 둥글어(10px) 겉 박스(8px)와 어긋남.

**원인**: 세그먼트화 때 `.filter-chip`의 명시적 `border-radius`를 **제거**하자 전역 `button, .btn{border-radius:10px}`(app.css §buttons)가 셀에 그대로 상속 → active 셀 배경이 컨테이너보다 둥근 10px라 첫/끝 셀 active 시 코너에 카드배경 틈.

**감별**: static-preview에서 active 셀 `getComputedStyle.borderTopLeftRadius`가 10px(컨테이너 8px와 불일치). 좌상단 곡선 안쪽 hit-test(`elementFromPoint(left+2,top+2)`)가 셀 아닌 컨테이너로 잡힘=초록이 코너 못 채움.

**해결 / 예방**: `.filter-chip`에 `border-radius:0` 명시(전역 10px 상쇄) → 셀 직각 + 컨테이너 `overflow:hidden`이 첫/끝 active를 8px로 클립해 겉 박스와 동심 일치. 전역 `button`의 속성(width·border-radius 등)을 칩·탭·세그먼트로 쓸 때 **명시값을 제거하면 전역값이 샌다** — 기존 명시값을 지울 땐 그 속성을 `0`/`auto`로 끄거나 의도값을 다시 박을 것.

**관련**: **전역 button 누수 군** — width판 T-056·T-081, radius판 이 항목. #523, N-118.

---

## T-100. 워크트리 frontend `node_modules` 없음 → vite 미해결, `npm ci` (디렉토리 존재 ≠ 패키지 설치)

**증상**: `npx vitest run`·`npm run build`가 `vite.config.ts` 로드 단계에서 vite·@vitejs/plugin-vue 미해결로 startup error. 소스·테스트는 멀쩡.

**원인**: `git worktree add`는 **git 추적 파일만** 복제하고 `node_modules`(gitignore)는 안 만든다 → 워크트리 frontend는 의존성 0. 함정: 메인 `frontend/node_modules`가 **빈 디렉토리거나 빌드도구 미설치**면 junction으로 재사용해도 무용 — 디렉토리 존재 ≠ 패키지 설치.

**감별**: `ls node_modules | wc -l`(0이면 빈 껍데기)·`test -d node_modules/vite`로 **핵심 패키지** 존재를 확인.

**해결 / 예방**: 워크트리 frontend에서 `npm ci`(package-lock 기준 완전 설치). junction은 메인이 **완전 설치돼 있을 때만** 빠른 재사용 가치. junction 제거는 `cmd //c rmdir <link>`(reparse point만 제거, `/S` 금지 — 타겟 보존. PowerShell `Remove-Item -Recurse`는 타겟까지 지울 위험). 워크트리에서 프론트 테스트/빌드 전 `test -d frontend/node_modules/vite`로 핵심 패키지 확인 먼저.

**관련**: N-032(워크트리 격리), T-063, T-093(번들 빌드).

---

## T-101. content-hash 정적자산 인증 누수 — `@{}` 단일파일 해시 URL이 정확매칭 permitAll에서 빠져 302, 와일드카드로

**증상**: 미인증 페이지 로드 시 `/pwa-install.js` → 해시 URL로 렌더되는데 SecurityConfig permitAll이 `/pwa-install.js`만 둬서 해시 URL이 `anyRequest().authenticated()`로 떨어짐 → 302 redirect + `RequestCache`에 SavedRequest 저장 → 로그인 성공이 그 .js로 리다이렉트 → 대시보드 대신 깨진 랜딩. 캐시된 세션에선 재현 안 됨.

**원인**: `spring.web.resources.chain`이 `@{/pwa-install.js}`를 `/pwa-install-<md5>.js`로 렌더하는데 인가 매처가 정확 경로만 허용해 해시 변형 URL이 걸림. 루트 단일 파일을 `@{}`로 참조하는 것만 정확매칭에 갇힘.

**감별**: 표적 E2E 도입 첫 실행에서 로그인 setup이 `#dashboard-app` 미도달. 디버그로 최종 URL=`/pwa-install-<hash>.js?continue`(SavedRequest) 실측.

**해결 / 예방**: `permitAll("/pwa-install*.js", "/manifest*.json")` 와일드카드(해시 변형 포함, ant `*`는 세그먼트 내). 회귀가드: `PwaStaticAccessTest`에 가짜 해시 `get("/pwa-install-deadbeef.js")`·`/manifest-deadbeef.json` 미인증 `not(302)` 단언.

**관련**: 개념 N-126, N-108(resource chain 해시), N-055, N-070(인가매처 누락), PR feat/playwright-e2e.

---

## T-102. auto-merge 후 손수 짠 워처가 DIRTY를 안 봐 침묵 정지 — `pr-merge.sh` 쓰거나 워처에 DIRTY 분기

**증상**: auto-merge 등록 후 직접 짠 백그라운드 머지 워처가 `MERGED`/`CLOSED`만 기다려 헛폴링. 멀티세션 중 타 PR 머지로 생긴 `DIRTY`/`CONFLICTING`를 못 알리고 침묵 정지.

**감별**: `gh pr view <PR> --json state,mergeStateStatus,mergeable` — state=OPEN+mergeStateStatus=`DIRTY`+mergeable=`CONFLICTING`이면 충돌(auto-merge 못 돎), `BLOCKED`면 단순 CI 대기(이 둘 구분 필수 — T-083).

**원인**: auto-merge는 DIRTY면 머지 못 함 → 분기 후 타 PR이 같은 파일을 머지하면 충돌이 **사후** 발생하는데, 머지 감시 폴링이 MERGED/CLOSED만 분기하면 DIRTY를 영영 안 잡아 hang처럼 보임.

**해결 / 예방**: **손수 워처를 짜지 말 것 — `.claude/scripts/pr-merge.sh <PR>`**가 이미 DIRTY 즉시 차단 + CI 폴링 + 하드 타임아웃을 제공한다. 굳이 워처가 필요하면 MERGED/CLOSED뿐 아니라 `mergeStateStatus==DIRTY` 분기를 반드시 넣어 재충돌을 일찍 알린다. auto-merge "등록=끝"이 아니다 — 멀티세션 활발기엔 분기 후 충돌이 사후 발생하니 머지까지 DIRTY를 감시하거나 pr-merge.sh로 동기 머지.

**관련**: T-083, T-096, T-098, T-094. **머지 자동화 hang·DIRTY-blind 군 5회차**.

---

## T-103. 스크립트로 파일 재생성 시 ReadAllText + UTF8Encoding(false)가 원본 BOM을 떨어뜨린다

**증상**: troubleshooting.md 목차를 자동 재생성하는 스크립트(`rebuild-troubleshooting-toc.ps1`)를 처음 돌렸더니, 의도한 목차 9줄 외에 **파일 첫 줄 전체가 diff에 떴다**(`-﻿# 트러블슈팅` → `+# 트러블슈팅`).

**원인**: `[System.IO.File]::ReadAllText`는 BOM을 감지해 **떼고** 문자열을 돌려준다. 그 문자열을 `New-Object System.Text.UTF8Encoding($false)`(BOM 없음)로 다시 쓰면 원본에 있던 BOM이 사라진다. troubleshooting.md는 UTF-8 BOM 포함이라 매 재생성마다 BOM이 빠져 **첫 줄 phantom diff + 매번 'changed'** 로, 정작 바뀐 목차가 노이즈에 묻힌다.

**해결 / 예방**:
- 재생성 전 **바이트로 BOM 유무를 감지**하고, 쓸 때 그 유무를 그대로 보존한다:
  ```powershell
  $head = [System.IO.File]::ReadAllBytes($Path)
  $hasBom = ($head.Length -ge 3 -and $head[0] -eq 0xEF -and $head[1] -eq 0xBB -and $head[2] -eq 0xBF)
  # ...
  $enc = New-Object System.Text.UTF8Encoding($hasBom)
  [System.IO.File]::WriteAllText($Path, $newText, $enc)
  ```
- EOL(`\r\n` vs `\n`)·끝 개행도 같은 원리로 원본 감지 후 보존(phantom CRLF 회피).
- 일반 원칙: **도구로 파일을 재생성할 땐 내용뿐 아니라 인코딩 메타(BOM·EOL)도 원본과 맞춘다** — 안 그러면 "한 줄 바꾸려다 파일 전체가 diff"가 된다.

**관련**: T-093(번들 빌드 phantom CRLF), T-057(`Set-Content -Encoding utf8`가 원치 않는 BOM 추가) — 같은 "재생성 시 인코딩 메타 미보존" 군. **2회차(T-093 재발)**.

---

## T-104. squash 머지가 브랜치 커밋 trailer를 메시지 중간으로 밀어 git %(trailers) 구조 조회를 깨뜨린다

**증상**: 세션 메타(`Session-Model`/`Session-Effort`)를 브랜치 커밋 trailer로 남기고 squash 머지했는데, `main`에서 `git log --format='%(trailers:key=Session-Model,valueonly)'`로 조회하면 **전부 빈 값**. 반면 `git log --grep='Session-Model'`로는 #543·#544·#545가 다 잡힌다 — 텍스트는 보존됐는데 구조 조회만 빈다.

**원인**: git의 trailer 파서(`%(trailers)`·`git interpret-trailers --parse` 공통)는 커밋 메시지의 **맨 끝 문단(블록) 하나만** trailer 후보로 본다. GitHub squash는 브랜치 커밋 메시지들을 이어붙인 뒤 **맨 끝에 자기 `Co-authored-by`를 `---------` 구분선과 함께 새 블록으로 덧붙인다**. 그래서 내 `Session-*` 줄은 메시지 *중간*으로 밀려 본문 텍스트로 취급되고, 마지막 블록(=`Co-authored-by`)만 trailer로 인식된다. 실증(scratch repo): `Session-*`가 마지막 블록인 대조군은 `%(trailers)`가 값을 정확히 반환, 중간이면 빈 값. 게다가 마지막 블록에 평문·구분선이 **한 줄만 섞여도** 25% 임계 휴리스틱이 깨져 그 블록의 진짜 trailer까지 동반 탈락한다.

**해결 / 예방**:
- 조회는 구조 포맷 대신 **grep으로** 한다(위치 비의존):
  ```bash
  git log --grep='Session-Effort'                                     # 존재 여부
  git log -1 --format=%B <commit> | grep -oP '^Session-Model:\s*\K.*' # 값 추출
  ```
  PowerShell이면 `Select-String '^Session-Model:\s*(.*)'`의 캡처 그룹.
- 근본책(원하면): squash 메시지 조립 시 `Session-*`를 **맨 마지막 trailer 블록**(=`Co-authored-by`와 같은 블록)에 합류시키면 `%(trailers:key=...)`가 정상 동작. 단 GitHub 기본 squash 조립은 제어가 어려워 grep 우회가 현실적.
- 일반 원칙: 커스텀 trailer는 "메시지 어디 있든 `Key: value`면 잡힌다"가 **아니다** — 마지막 블록 한정이라 squash·rebase가 위치를 흔들면 구조 조회가 깨진다. 위치 비의존이 필요하면 grep / `git notes`.

**관련**: 세션 메타 기록 규칙(#543), 개념은 [learning-notes.md](learning-notes.md) N-128. **1회차(신규)** — 트래커 표 미등재.

---

## T-105. 빈 워크트리 폴더가 `Device or resource busy`로 안 지워짐 — 죽은 세션 좀비 셸이 cwd 점유, cwd 검증 PID만 종료

**증상**: `git worktree` 정리 후 `.claude/worktrees/<name>` 또는 형제 `BookTimer-*` 빈 폴더가 남아 `rm -rf`가 "Device or resource busy". 폴더 안엔 작업물 0(`.`·`..`만).

**원인**: Claude Code의 Bash/PowerShell 도구가 그 워크트리에서 띄운 셸(`bash.exe`·`powershell.exe`)이 세션 종료 후에도 cwd를 그 폴더로 유지한 채 좀비로 남아 디렉토리를 점유 — **node/java가 아니라 셸 프로세스**라 `Get-Process node,java`로는 안 잡힘(실제로 한 폴더에 bash 10·powershell 1개가 남아 있었음).

**감별**: `handle.exe`(Sysinternals) 있으면 `handle <path>`. 없으면 `NtQueryInformationProcess`(PEB→ProcessParameters→CurrentDirectory) C# P/Invoke로 전체 프로세스 cwd를 읽어 그 폴더를 cwd로 가진 PID만 식별.

**해결 / 예방**: cwd 검증된 그 PID만 `Stop-Process -Force` 후 폴더 삭제 — 살아있는 세션 셸(cwd=메인/타 워크트리)·gradle 데몬(cwd=`~/.gradle`)·타 프로젝트는 cwd가 달라 자동 제외. PID 하드코딩 말고 삭제 직전 cwd 재검증(PID 재사용 방지). 세션 종료 시 도구 셸 정리, 워크트리 제거 전 그 폴더 기반 셸 종료.

**관련**: T-086(docker — 같은 "세션 종료 후 자원 미정리" 계열).

---

## T-106. auto-merge `--delete-branch`는 비동기 머지라 원격 브랜치가 안 지워진다 — 머지 확인 후 gh API로 삭제

> ✅ **근본 해결됨 (2026-06-27, gap#3)**: repo 설정 `deleteBranchOnMerge=true`를 켰다 → GitHub가 머지(auto-merge 포함) 직후 **서버사이드로 원격 브랜치를 자동 삭제**한다. 이 트랩(원격 잔존)도 아래 gh API 수동삭제도 더는 필요 없다. 아래 본문은 그 설정이 꺼진 환경을 위한 기록.

**증상**: `gh pr merge <PR> --auto --squash --delete-branch`로 머지했는데, 머지 완료(`state=MERGED`) 후에도 원격 브랜치가 남아 `git ls-remote --heads origin <branch>`에 잡힌다. 워크트리 세션이 아닌 **주 워크트리에서도** 발생(T-095의 워크트리 점유 충돌과는 다른 원인).

**원인**: `--auto`(auto-merge)는 CI 통과 후 **나중에 서버사이드로** 머지한다(비동기). 반면 `gh pr merge --delete-branch`의 브랜치 삭제는 gh CLI가 **머지 직후 로컬에서** 처리하는데, auto-merge 등록 시점엔 아직 머지 전이라 즉시 못 지우고, 실제 서버 머지가 일어날 땐 gh 프로세스가 이미 끝나 삭제가 누락된다. repo의 "Automatically delete head branches" 설정이 켜져 있으면 GitHub가 서버에서 지우지만, 이 repo는 그 설정에 의존하지 않아 잔존한다.

**해결 / 예방**:
- 머지 확인(`gh pr view <PR> --json state`=`MERGED`) 후 원격 브랜치를 **gh API로 명시 삭제**(T-094 — Windows `git push --delete` hang도 동시 회피):
  ```bash
  gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>
  ```
  로컬은 `git branch -D <branch>`. 잔존 여부는 `git ls-remote --heads origin <branch>`로 확인.
- ✅ 근본책(적용 완료 2026-06-27): repo 설정 **"Automatically delete head branches"**(`deleteBranchOnMerge=true`)를 켜면 서버가 머지 시 자동 삭제 → `--auto`와 무관하게 정리된다.
- 일반 원칙: `--auto`(비동기 머지)와 `--delete-branch`(즉시 로컬 처리)는 **시점이 어긋난다** — auto-merge를 쓰면 브랜치 삭제는 "MERGED 확인 후 별도 단계"로 다룬다.

**관련**: T-094(gh api로 원격 ref 삭제 + Windows push hang 회피), T-095(워크트리 `--delete-branch` 로컬 정리 실패 — 그쪽은 워크트리 main 점유 충돌, 본 건은 auto-merge 비동기), auto-merge 우선 경로(CLAUDE.md Git워크플로). **1회차(신규)** — T-095(`--delete-branch` 정리 실패) 계열과 묶일 소지, 재발 시 트래커 등재.

---

## T-107. `git add`와 `git commit`을 한 명령으로 묶으면 PreToolUse 자동수정 훅(목차·번들)이 skip된다 — add는 별도 호출로

**증상**: troubleshooting.md(또는 프론트 번들)를 고치고 `git add <file> && git commit -F msg`처럼 **한 Bash 명령으로 묶어** 커밋했더니, 목차 자동생성 훅(`require-troubleshooting-toc`)이 안 돌아 **목차가 갱신 안 된 채** 커밋됐다(본문 헤딩만 추가되고 목차에 그 줄이 빠짐). 같은 변경을 `git add` 따로, `git commit` 따로 하면 정상 작동했다(실제로 직전 T-106 커밋이 이 함정에 당해 목차 누락 → 스크립트 수동 실행 + amend로 보정).

**원인**: 이 자동수정 훅들은 **PreToolUse**(도구 실행 *전*)로 `git commit`을 가로채, 그 순간 스테이징(`git diff --cached`)을 보고 대상 파일이 있으면 산출물을 재생성해 다시 `git add` 한다. 그런데 `git add X && git commit`을 **한 호출**로 주면, 훅은 그 명령 문자열이 실행되기 **전에** 한 번 끼어드는데 — 그 시점엔 아직 `git add`가 안 돌아 스테이징이 비어 있다 → 훅이 "대상 변경 없음"으로 **skip**하고, 곧바로 명령이 add+commit을 한꺼번에 실행해 훅이 다시 끼어들 틈이 없다. `;`로 묶어도 동일(한 명령 문자열 = PreToolUse 1회, 실행 전 기준).

**해결 / 예방**:
- **`git add`를 별도 Bash 호출로 먼저** 실행하고, 그다음 `git commit`을 단독 호출한다 — commit 가로채기 시점에 이미 스테이징돼 있어 훅이 본다. (분리하면 `git add → (PreToolUse 통과) → git commit → (PreToolUse: 스테이징 봄 → 재생성·re-add)`.)
- 이미 묶어 커밋해 skip됐으면: 해당 스크립트(`rebuild-troubleshooting-toc.ps1` 등)를 수동 실행 → `git add` → `git commit --amend`로 보정.
- ⚠️ **번들 훅에서도 실증됨(2회차, #614 2026-06-30)**: 1회차 때 "미검증 소지"로 남겨둔 `require-bundle-build` 무력화가 실제로 발현 — 단 증상은 'skip'이 아니라 **반대로 오탐 BLOCK**이었다. 번들 훅은 빌드 후 `git diff --exit-code -- src/main/resources/static`(워킹트리 vs 인덱스)로 stale을 판정하는데, `git add -A && git commit` 묶음이면 훅 발동 시점에 add가 아직 안 돌아 **인덱스가 직전 시도의 구버전 산출물**이고, 훅이 새로 빌드한 워킹트리(`garden.js`)와 어긋나 `Bundle is stale`로 잘못 차단했다(3회 연속 BLOCK). `git add`를 별도 호출로 분리하니 인덱스=워킹트리가 돼 통과. 즉 skip(목차·테스트 무력화)이든 오탐(번들 BLOCK)이든 결론은 같다 — **PreToolUse 게이트가 걸린 커밋(`require-troubleshooting-toc`·`require-bundle-build`·`require-tests-before-commit`)은 add와 commit을 반드시 분리한다.** (참고: 이번엔 `npm ci`로 node_modules가 갈려 인덱스의 구버전 산출물과 새 빌드가 달라 BLOCK이 더 또렷이 드러났다. 산출물이 결정적이어도 묶음이면 같은 함정.)

**관련**: T-106(직전 커밋이 이 함정에 당함), 훅 `require-troubleshooting-toc`·`require-bundle-build`·`require-tests-before-commit`(PreToolUse 자동수정·게이트 군). **2회차(1회 T-107 목차훅 skip, 2회 #614 번들훅 오탐 BLOCK)** — 2회+이므로 하드픽스 승격 후보(훅이 명령 문자열에 `git add`+`git commit` 동시 포함을 감지하면 분리 안내).

---

## T-108. `gradlew.bat`이 phantom-modified로 rebase를 막는다 — `.gitattributes eol=crlf`와 커밋된 블롭 EOL 불일치, `--assume-unchanged`로 우회

**증상**: Dependabot PR 브랜치를 받아 `git rebase origin/main` 하려는데 매번 `error: cannot rebase: You have unstaged changes`로 막힘. `git status`엔 `gradlew.bat`만 `modified`로 뜨고, `git checkout -- gradlew.bat`·`git -c core.autocrlf=false checkout -- .`로도 안 지워짐(즉시 다시 modified). `git diff gradlew.bat`은 "82 insertions, 82 deletions"(전 줄 변경)으로 EOL 차이 신호. `git rebase --autostash`를 써도 autostash 직후 파일이 다시 더럽혀져 rebase가 또 막힘(`Applying autostash resulted in conflicts`).

**원인**: `.gitattributes`에 `*.bat text eol=crlf`(체크아웃 시 CRLF, repo엔 LF 정규화 저장)인데, **저장된 블롭이 그 규칙과 어긋나게 커밋돼 있다**(이번엔 #560 gradle-wrapper bump가 `gradlew.bat`을 비정규 EOL로 커밋). → git이 "working tree(정규화) ≠ 블롭"으로 보고 **영구 modified**. `git add --renormalize gradlew.bat`은 그 차이를 스테이징해버려(=실제 EOL 수정이 커밋에 끼어듦) 내 작업 PR과 무관한 변경이 섞인다.

**해결 / 우회**:
- rebase만 통과하면 될 땐 그 파일을 무시: `git update-index --assume-unchanged gradlew.bat` → `git -c core.autocrlf=false rebase origin/main` → `git update-index --no-assume-unchanged gradlew.bat`. (rebase가 replay하는 커밋들이 `gradlew.bat`을 안 건드릴 때 안전 — 안 건드리면 충돌 없음.)
- 근본 수정은 **별도 PR**로: `git add --renormalize gradlew.bat`를 정식 커밋해 블롭을 규칙대로(LF) 맞춘다(의존성/기능 PR과 섞지 말 것).

**관련**: T-093(CRLF)·T-103(BOM)·T-057 — "파일의 EOL·인코딩 메타가 repo 규칙과 어긋나 phantom diff" 군(이번은 *외부 bump가 커밋한 블롭* × `.gitattributes` 불일치판). `--assume-unchanged`, `git add --renormalize`. **1회차(신규)**.

---

## T-109. vitest include가 test/ 디렉토리만 잡아 src/ 곁 테스트가 조용히 미실행 — include에 src/** 추가

**증상**: `src/dashboard/timerProgress.test.ts`(79개) 등 소스 파일 곁에 둔 `*.test.ts`가 `npm run test`(vitest) 결과에 **아예 안 나타난다** — 실패도 통과도 아닌 *미실행*. 실행 파일 목록이 전부 `test/`로만 나오고, 소스 곁 테스트가 깨져 있어도 silent green처럼 보인다. `npx vitest run src/…/foo.test.ts`로 직접 경로를 줘도 "No test files found".

**원인**: `frontend/vite.config.ts`의 `test.include`가 `['test/**/*.{test,spec}.ts']`로 한정돼 있었다 — Playwright E2E(`e2e/**/*.spec.ts`)가 vitest 기본 include에 걸려 깨지는 걸 막으려 `test/`로 좁혔는데, 그 바람에 `src/` 곁 단위 테스트까지 배제됐다. CLI 경로 인자는 include와 *교집합*이라 include 밖 경로는 0개로 잡혀 "No test files found"가 된다.

**해결**: include에 `'src/**/*.{test,spec}.ts'`를 더한다 → `['test/**/*.{test,spec}.ts', 'src/**/*.{test,spec}.ts']`. E2E는 `e2e/` 디렉토리라 두 패턴 어디에도 안 걸려 안전. 이 한 줄로 죽어 있던 4개 파일(`timerProgress`·`profile/format`·`profile/icons`·`shared/navIcons`, 105개)이 부활하고 전부 green이었다.

**예방**: 테스트를 추가했으면 **실행 카운트가 늘었는지** 확인한다("작성=실행"이 아니다). include를 디렉토리로 좁히는 설정은 *새 위치의 테스트를 조용히 삼키는* 사각이 된다. 개념: [[N-131]](테스트 신호 사각과는 별개지만, "있는 줄 알았는데 안 돌던" 부류).

**관련**: 테스트 신호 희석/사각 군. **1회차(신규)**.

---

## T-110. 정션 둔 워크트리를 `git worktree remove --force`하면 정션 타깃(main node_modules)이 비워진다 — 정션 먼저 끊어라

**증상**: `link-node-modules.ps1`로 `frontend/node_modules` 정션을 건 워크트리를 작업 후 `git worktree remove --force`로 지웠더니, 나중에 보니 **main의 `frontend/node_modules`가 텅 비어 있다**(폴더는 존재하는데 패키지 0개). 워크트리 정리 뒤 main에서 빌드가 깨지거나, 다음 정션 연결이 `npm ci`부터 다시 돈다.

**원인**: 정션(junction)은 폴더를 가리키는 링크지만 파일시스템엔 일반 디렉토리처럼 보인다. `git worktree remove --force`가 워크트리 폴더를 **재귀 삭제**할 때 정션을 *따라 들어가* **타깃(main의 node_modules) 내용까지 지운다**. 정션 자체(빈 폴더)는 남고 타깃 내용만 증발 → "폴더는 있는데 0개". **격리 재현으로 확정**(임시 repo: `.gitignore`로 node_modules 미추적 → 워크트리 정션 → `worktree remove --force` → 더미 `keep.txt`가 폴더 존재·내용 삭제로 사라짐). #567 워크트리 정리 후 main node_modules가 `137→0`이던 정체.

**해결**: worktree remove **전에 정션을 먼저 끊는다** — 링크만 제거하고 타깃은 보존하는 `[System.IO.Directory]::Delete()`를 쓴다(`Remove-Item -Recurse`는 정션 타깃까지 지울 수 있어 **금물**).

```
powershell -c "[IO.Directory]::Delete('<wt>/frontend/node_modules')"
git worktree remove ../BookTimer-<task>
```

대조 입증: #569 정리에서 정션을 먼저 끊으니 main 137개가 그대로 보존됐다.

**예방**: 정션을 쓰는 워크트리는 정리 순서가 "**정션 끊기 → worktree remove**"로 고정이다(CLAUDE.md 다중 세션 절에 반영). 자동 정리 스크립트도 worktree remove 앞에 정션 제거를 넣는다. 일반화: **링크(정션·심볼릭)를 품은 폴더의 재귀 삭제는 타깃을 건드릴 수 있다**.

**관련**: 정션 워크플로([[N-132]] — node_modules 정션 공유)의 정리측 함정. **1회차(신규, 격리 재현으로 확정)**.

---

## T-111. "머지 전 브랜치 최신화 필수" 정책에서 BEHIND인 PR에 `--auto`만 걸면 영영 안 머지된다 — GitHub가 BEHIND 브랜치를 자동 갱신하지 않음

**증상**: `gh pr merge <PR> --auto --squash`를 걸었는데 CI(필수체크 `test`)는 "All checks have passed"인데도 머지가 안 되고 PR이 OPEN으로 무한 대기. PR 화면에 "This branch is out-of-date with the base branch / Update branch" 배너. `gh pr view`는 `mergeStateStatus=BEHIND`, `mergeable=MERGEABLE`(= 충돌 아님, 그냥 base에 뒤처짐).

**원인**: 레포 브랜치 보호가 **"머지 전 브랜치 최신화 필수"**(require branches to be up to date)인데 이 레포는 **auto-update가 꺼져 있어** GitHub가 BEHIND 브랜치를 스스로 갱신하지 않는다. `--auto`는 "머지 조건이 충족되면 서버가 머지"인데, BEHIND는 **시간이 지난다고 자기해결되지 않고 누가 `update-branch`를 해줘야만** 풀리는 조건이라 영원히 대기한다. DIRTY(충돌)와 달리 BEHIND는 충돌이 아니라서 기존 DIRTY 진단·rebase 경로에도 안 잡혔고, 폴백 `pr-merge.sh`도 BEHIND를 메인 루프 catch-all `*`로 흘려 12분 타임아웃까지 **대기만** 했다(해결 시도 없음).

**해결**: BEHIND는 **`gh pr update-branch <PR>`**(비파괴 서버사이드 base→head merge, force-push·로컬 체크아웃 불필요)로 갱신하면 CI가 재실행되고 `--auto`가 마저 머지한다. 수동이면 `git rebase origin/main && git push --force-with-lease`도 가능하나 파괴적(force-push)이라 비파괴 `update-branch`가 우선.

**예방(하드픽스)**: `pr-merge.sh`에 BEHIND를 명시 처리 — 폴링 루프·신규 `--arm` 모드 양쪽에서 `gh pr update-branch`로 자동 해소(`try_update_branch`). **표준 머지 경로를 `bash .claude/scripts/pr-merge.sh <PR> --arm`("걸고 떠나기": `--auto` 걸고 BEHIND/DIRTY만 1회 풀고 즉시 종료, 머지는 서버가 마저)로 승격**, bare `gh pr merge --auto` 단독 사용 금지(CLAUDE.md 🔀 Git 워크플로 5번 반영). 스모크 테스트 `.claude/scripts/tests/test-pr-merge-behind.sh`(arm/sync BEHIND→update-branch·DIRTY→exit3·CLEAN→arm 4케이스).

**관련**: PR 머지 자동화 hang 군 — T-083(no-checks→DIRTY 오인), T-091·T-094(push-delete hang→gh API + `--auto` 전환), T-102(하드픽스 안 쓰고 손수 워처→DIRTY 누락). 이번은 `--auto` 전환(T-094)이 만든 새 사각. 개념 [[N-070]]. **6회차(이 군 — BEHIND 무한 대기, 발견 즉시 하드픽스 `--arm`+`update-branch`로 승격)**.

---

## T-112. Chrome MCP `resize_window`가 렌더 뷰포트(`innerWidth`)를 못 바꿔 모바일 미디어쿼리 검증이 막힌다 — 폭 N px iframe에 페이지를 로드해 우회

**증상**: 모바일 전용 CSS(`@media (max-width: 599px)` — 예: 서술 내부 스크롤·반응형 레이아웃)를 실 브라우저로 검증하려고 Chrome MCP `resize_window`로 창을 430px로 줄여도, `window.innerWidth`가 **1920 고정**이라 모바일 브레이크포인트가 발동하지 않고 데스크톱 렌더만 나온다. 모바일 규칙의 적용·스크롤·오버플로를 확인할 길이 막힘.

**원인**: 이 환경의 `resize_window`는 **OS 창 크기만** 바꾸고 콘텐츠 **렌더(layout) 뷰포트**는 안 바꾼다(고DPI·창 최소폭·렌더 고정 추정). CSS 미디어쿼리는 layout viewport 폭을 보므로, 창만 줄여선 `max-width:599px`가 안 걸린다. (책BTI 작업에서 2회 봉착 — 상단 제목 작업 때 모바일 확인을 못 해 "불필요"로 우회했고, 모바일 서술 스크롤 작업에서 다시 막혀 이 우회법을 확립.)

**해결**: **폭 N px(예 390) iframe**을 만들어 같은 페이지(static-preview URL 등)를 `src`로 로드한다 — **미디어쿼리는 iframe 자체의 뷰포트 폭을 보므로** iframe 안에서 모바일 규칙이 진짜로 발동한다. 같은 오리진이면 `iframe.contentWindow`/`contentDocument`로 내부를 측정: `getComputedStyle(el).maxHeight`, `iwin.matchMedia('(max-width: 599px)').matches`, `scrollHeight > clientHeight`, `el.scrollTop = 9999`로 스크롤 가능 확인. **부모(1920)와 iframe(390)을 한 페이지에서 동시에 재면 데스크톱·모바일 대조가 한 번에** 된다(부모=캡 미적용 `max-height:none`, iframe=캡 적용). 스크린샷은 iframe을 `position:fixed`로 좌상단에 띄워 캡처.

**예방**: 모바일 한정 반응형 CSS는 이 iframe 기법을 검증 게이트로 삼는다. **static-preview(N-117)와 자연 결합** — 같은 오리진이라 `contentDocument` 접근이 열린다. `resize_window`의 뷰포트 무변경을 "환경 버그"로 의심해 시간 쓰지 말 것(이미 2회 확인).

**관련**: N-117(static-preview), N-118(CSS 침묵 드랍 시각검증), T-053(헤드리스 가짜 green), T-089(반응형 재현 mock worst-case). 2회차(직전 책BTI 모바일 검증 봉착 미기록 → 이번 iframe 우회 확립).

---

## T-113. 도메인 TLD 이전 후 `www.<신규>`를 ALB 301 규칙에서 빠뜨려 검색 유입자가 redirect_uri_mismatch + host-only 세션 분리

**증상**: 평소 `booktimer.app`(apex)에서 로그인을 유지하던 사용자가 **구글 검색창**에서 "booktimer"를 쳐 1위 결과를 클릭해 들어가니 ① 비로그인 소개 랜딩이 뜨고(로그인 유지 중이었는데) ② 거기서 구글 로그인을 누르니 `400 redirect_uri_mismatch`. **주소창에 직접 `booktimer.app`을 치면 멀쩡** — "검색 유입자만" 깨진다.

**원인**: 도메인 TLD 이전(`.click`→`.app`, PR #315)에서 ALB 443 우선순위1 리디렉트 규칙의 호스트 조건을 `booktimer.click`·`www.booktimer.click`으로만 등록하고 **신규 `www.booktimer.app`을 빠뜨렸다.** 그래서 `www.app`은 301 규칙에 안 잡히고 기본값(대상그룹 forward)으로 흘러 앱에 그대로 도달(200). 그런데 세 가지가 겹쳐 한 호스트 누락이 OAuth·세션을 동시에 깬다 — ⓐ canonical 신호 전무(`<link rel=canonical>`·sitemap·www→apex 301 모두 없음)라 **구글이 `www.booktimer.app`을 독립 색인·검색 1위로 노출**(실측: 검색 1위가 `https://www.booktimer.app`, signup은 apex로 호스트 혼재) → 검색 유입자는 `www`로 진입. ⓑ 앱은 `ForwardedHeaderFilter`가 `X-Forwarded-Host: www.booktimer.app`을 반영해 redirect_uri를 `https://www.booktimer.app/login/oauth2/code/google`로 **동적 생성**(구글 콘솔엔 apex만 등록 → mismatch). ⓒ 세션 쿠키는 `setCookieDomain` 미설정이라 **host-only** — apex에서 발급된 쿠키가 `www`엔 안 실려 비로그인 랜딩.

**해결**: ALB 콘솔에서 그 우선순위1 규칙의 **호스트 헤더 조건에 `www.booktimer.app` 한 값을 OR로 추가**(리디렉트 액션 `https://booktimer.app:443/#{path}?#{query}` 301은 그대로). ⚠️ 함정: "조건 추가"로 **새 HTTP-헤더 조건**을 만들면 안 된다 — 서로 다른 조건 타입은 **AND**로 묶여 기존 `.click` 301까지 깨진다(매칭 0). 반드시 **같은 호스트-헤더 조건 안에 OR 값**으로 넣는다(같은 타입 다중 값=OR). 검증: `curl -sD -`로 `www.app` 홈·`/oauth2/authorization/google`이 301→apex, `-L` 추적 시 최종 `redirect_uri=https://booktimer.app/…`·구글 로그인화면 200, `.click`·apex 무회귀 실측.

**예방**: 도메인/호스트를 추가·이전할 때 **모든 변형(apex·www·구/신 TLD)을 redirect 규칙·OAuth 콘솔·인증서 양쪽에 전수 등록**한다. canonical 미설정은 구글이 비정규 호스트를 1위로 띄우는 트리거이므로 `www→apex 301`+`<link rel=canonical>`로 정규화(후속). **"내 PC(주소창 apex)는 되는데 검색 유입만 깨짐"은 호스트 정규화 누락의 전형.**

**관련**: T-014(forward-headers 무동작으로 redirect_uri http — 이번은 헤더는 정상, 호스트 누락), T-027(`.click` 평판), N-021/N-022(TLS termination·X-Forwarded), N-138(canonical×OAuth×쿠키 이중타격 개념). 1회차 신규.

---

## T-114. preview_inspect가 border-radius·padding 등 shorthand CSS를 빈 객체로 반환 — longhand나 eval getComputedStyle로 읽어라

**증상**: `mcp__Claude_Preview__preview_inspect`에 `styles:["border-radius"]`·`["padding"]`을 주면 요소는 매칭되는데(text·className·boundingBox는 정상) `styles`가 `{}`로 빈다. 같은 호출에서 `display`·`grid-template-columns`·`background-color`·`width`·`height`는 정상 반환된다.

**원인**: inspect가 shorthand 속성(`border-radius`=4개 corner longhand 합성, `padding`=4방향 합성)을 computed style에서 못 끌어오는 듯 — longhand·단일값 속성만 신뢰 가능.

**해결**: ① longhand로 요청(`border-top-left-radius`·`padding-top` 등), 또는 ② `preview_eval`로 직접 `getComputedStyle(el).borderRadius`/`.padding`을 읽으면 shorthand도 정확히 나온다(여러 요소를 한 IIFE로 `pick`하면 1콜로 끝). 랜딩 디자인 토큰 통일(버튼 8px·카드 14px/24px) 검증을 eval로 갈음해 전부 실측했다.

**관련**: 「🖥️ 프론트 검증」 static-preview 게이트, T-112(Chrome MCP `resize_window`가 뷰포트 못 바꿈 — preview 도구 계열 검증 한계라는 같은 결). 1회차 신규.

---

## T-115. 워크트리에서 작업한 세션이 그 워크트리를 직접 정리하면 최상위 빈 폴더가 안 지워진다(세션 cwd 점유)

**증상**: 머지 후 `remove-worktree.ps1`(또는 `git worktree remove`)로 작업 워크트리를 정리하는데, 정션 끊기·내부 파일 삭제까지는 되지만 **최상위 폴더만** `Permission denied`/`Device or resource busy`로 안 지워진다. `worktree list`에선 빠졌는데(메타는 정리됨) 빈 폴더가 남는다.

**원인**: 그 워크트리에서 작업하던 **현재 세션 자신이 폴더를 작업 디렉토리(cwd)로 점유**한다. Claude Code harness가 매 도구 호출 후 cwd를 그 워크트리로 고정(reset)하므로, `remove-worktree.ps1`을 메인에서 `Set-Location`으로 실행해도 Bash/PowerShell 도구 셸의 cwd가 다시 워크트리가 돼 락이 안 풀린다. Windows에선 프로세스 cwd인 폴더의 **최상위는** 삭제 불가(하위 파일은 가능).

**해결**:
- 실질 정리는 끝낼 수 있다 — 정션 끊기(node_modules 보호) + `git -C <메인> worktree prune` + `git -C <메인> branch -D <branch>`까지 되면 사실상 완료. **빈 폴더만 그 세션 종료 후** 다른 셸에서 `rmdir "<경로>"` 한 줄(다음 SessionStart가 치우기도).
- 더 깔끔하게는 **워크트리 정리를 그 워크트리 세션이 아니라 메인/다른 세션에서** 한다 — 정리하는 세션의 cwd가 대상이 아니면 폴더째 삭제된다.

**관련**: T-105(빈 워크트리 폴더 cwd 점유 — 죽은 세션 좀비 셸 버전), T-110(정션 먼저 끊기), N-032(워크트리 격리). **T-105 재발(2회차, cwd-점유 군)**.

---

## T-116. 순수 마크업/CSS 변경이라 '단위 TDD 무의미'라 본 게 기존 통합 테스트를 놓쳐 CI에서 RED

**증상**: Thymeleaf 템플릿의 링크·문구만 바꾼 순수 마크업 변경이라 단위 TDD가 무의미하다 판단하고 preview 검증만 했는데, **CI의 통합 테스트(`@SpringBootTest` MockMvc)가 RED**. 예: 랜딩 `/village` 직접 링크를 `#village` 앵커로 바꾸자 `LandingPageTest.landing_hasVillageLink`(렌더 HTML에 `/village` 문자열 존재 기대)가 깨짐.

**원인**: 마크업 자체엔 단위테스트가 없어도, **기존 통합 테스트가 렌더된 HTML의 링크·키워드를 `content().string(containsString(...))`로 검증**하고 있었다. "단위 TDD 무의미"는 *새 테스트를 안 짠다*는 뜻일 뿐, *기존 테스트가 그 마크업을 검증하지 않는다*는 보장이 아니다.

**해결**: 마크업/카피/링크를 바꾸기 전에 **그 경로·문자열을 검증하는 기존 테스트를 grep**한다(예: `containsString("/village")`·뷰 이름·핵심 키워드). 변경이 의도된 설계 개선이면 테스트를 새 동작에 맞게 갱신(이번엔 동선 검증 `#village`·`#together` 앵커+섹션으로)하고, 가능하면 변경 전 로컬에서 그 테스트를 돌려 RED를 먼저 확인(TDD 가시성).

**관련**: 「🧪 TDD」, N-055(노출 기능 경계 테스트), T-114(같은 랜딩 작업의 preview 검증 한계). 1회차 신규.

---

## T-117. 공유 Vue 컴포넌트에 `<style scoped>`를 넣으면 페이지가 링크하지 않는 별도 번들 CSS가 생성된다

**증상**: 검색·내 책방이 공유하는 `UserSearchPanel.vue`에 추천 이유 칩용 `<style scoped>`를 추가했더니 vite 빌드가 새 산출물 `src/main/resources/static/search/search.css`를 생성. 그런데 `search.html`은 `app.css`와 `search.js`만 `<link>`하고 이 CSS는 안 받아, 실 페이지에서 칩이 무스타일(전역 fallback)로 뜬다. 게다가 컴포넌트가 둘 이상 페이지(검색·내 책방)에서 쓰여 페이지별 `<link>` 추가는 한쪽 누락이 쉽다.

**원인**: 이 프로젝트의 Vue 섬은 **컴포넌트에 `<style>`을 두지 않고 전역 `app.css` 클래스만 쓰는** 패턴이다(기존 `UserSearchPanel.vue`엔 `<style>`이 없었음 — `book-meta`·`book-author` 등 전역 클래스 사용). 컴포넌트에 `<style scoped>`를 넣으면 vite가 그 섬 번들 옆에 `<bundle>.css`를 따로 뽑는데, Thymeleaf 템플릿이 그 파일을 `<link>`하지 않는 한 JS 번들만 로드돼 스타일이 적용되지 않는다(헤드리스 vitest는 CSS 불요라 통과해 못 잡음 — 실 페이지에서만 드러남).

**해결**: 공유 컴포넌트의 스타일은 `<style scoped>`가 아니라 **전역 `app.css`에 클래스로** 넣는다(기존 `.book-status-badge` pill 레시피를 재사용하면 테마 일관성도 덤). 이미 생긴 orphan `<bundle>.css`는 삭제하고 재빌드해 산출물을 JS 번들만 남긴다. 페이지별 `<link>` 추가는 공유 컴포넌트엔 부적합(소비 페이지마다 누락 위험).

**관련**: 「🛠️ 빌드/실행 메모 — 프론트 번들」, T-063·T-082(번들 stale 군), N-083(헤드리스가 못 보는 클라 사각). 친구 추천 칩 작업(2026-06-29). 1회차 신규.

---

## T-119. PowerShell→`docker exec mysql -e`로 한글 INSERT 시 mojibake — `UNHEX`로 정확한 UTF-8 바이트 주입

**증상**: 실 브라우저 검증용 데이터를 만들려 `docker exec <mysql> mysql -e "INSERT INTO book (... author ...) VALUES ('한강')"`로 넣었더니 도감 보유 판정이 안 됨(설정 "프로필 사진" 카드에 "아직 모은 작가가 없어요"). `HEX(author)`로 보니 `C3ADE280A2…`(mojibake)라, `match_name`의 정상 `ED959CEAB095`("한강")와 안 맞아 contains 매칭 실패.

**원인**: Windows PowerShell 5.1이 native exe(`docker`) 인자를 UTF-8로 안 넘긴다(시스템 로캘 CP949). 한글이 docker→mysql 경로에서 깨져 컬럼에 잘못된 바이트로 저장. T-026(git commit `-m` 인라인 한글 깨짐)과 같은 뿌리(PowerShell→native exe 인자 인코딩).

**해결**: 한글 값은 `UNHEX('ED959CEAB095')`로 **정확한 UTF-8 바이트를 직접** 주입(`UPDATE book SET author=UNHEX('…')`). UNHEX는 바이트 리터럴이라 PowerShell·connection charset 인코딩을 안 탄다. 또는 SQL을 UTF-8 파일로 써서 `docker cp` 후 `source`, 혹은 앱 UI(브라우저=UTF-8)로 입력. 진단은 `HEX(컬럼)`을 기대 UTF-8 바이트와 대조한다(콘솔 출력 텍스트도 CP949라 같이 깨져 신뢰 불가).

**관련**: T-026(인라인 한글 커밋 → `.commit-msg-tmp`), T-044(PowerShell here-string JSON 인코딩 → 파일+`--input`). PowerShell→native exe 인자 인코딩 군. 검증 데이터 셋업 한정(운영 무관). 1회차 신규.

---

## T-121. WinRT 토스트가 미등록 AppUserModelID면 API 성공해도 화면에 안 뜬다(조용히 드랍)

**증상**: PowerShell에서 `[Windows.UI.Notifications.ToastNotificationManager]`로 토스트를 띄우는데 `.Show()`가 **예외 없이 성공**(반환 OK)하는데도 화면에 토스트가 **안 나타난다**. 에러 로그도 없어 "보냈다고는 하는데 안 보임".

**원인**: `CreateToastNotifier($appId)`의 `$appId`(AppUserModelID)가 **시스템에 등록돼 있어야** Windows가 토스트를 표시한다. 미등록 AppID(예: 임의의 PowerShell 경로 ID `{1AC14E77…}\WindowsPowerShell\v1.0\powershell.exe`)면 Windows가 **조용히 드랍**(API는 성공으로 보임). 추가로 **집중 지원(Focus Assist)·방해 금지**가 켜져 있어도 억제된다(`HKCU:\…\PushNotifications\ToastEnabled=0`이면 전역 off).

**해결**: **등록된 시스템 AppID를 쓴다** — `Microsoft.Windows.Explorer`가 어디서나 뜬다(실측 확인). 자체 AppID가 필요하면 Start Menu 바로가기에 `System.AppUserModel.ID`를 박아 등록하거나 `BurntToast` 모듈 사용. 진단: API가 성공인데 안 뜨면 ① AppID 등록 여부 ② 집중지원/`ToastEnabled` ③ 알림 센터(`Win+N`)에 배너 없이 쌓였는지 순으로 본다.

**관련**: B1 확인-대기 알림 훅 `notify-when-waiting.ps1`(#621)이 이 AppID로 발사. PowerShell↔Windows API 함정. 1회차 신규.

---

## T-122. 타임아웃/hang 수정의 RED 테스트는 하니스를 outer `timeout`으로 감싸지 않으면 테스트가 세션째 hang한다

**증상**: "무한 hang을 끊는 타임아웃"을 TDD로 짤 때, 구현 전(RED) 상태에서 hang을 재현하는 테스트를 돌리면 **테스트 자체가 안 끝나고 세션이 얼어붙는다**(고치려는 바로 그 증상을 테스트가 겪음).

**원인**: hang 케이스는 "안 끊기는 동작"(영영 안 돌아오는 자식)을 부른다. 타임아웃이 아직 없는 RED에선 그 호출이 무한정 블록 → 테스트 러너도 같이 블록 → 세션 hang.

**해결**: 테스트 하니스에서 그 호출을 **coreutils `timeout N …`(outer timeout)으로 감싸** RED에서도 유한 시간에 반환·실패 단언하게 한다. + 가짜 느린 자식(`ping -n`/`sleep`으로 지연 후 **exit 0**)을 써서, "미구현=오래 기다린 뒤 통과(=커밋 허용, 잘못)" vs "구현=타임아웃 차단(exit 2)"로 RED↔GREEN을 종료코드로 가른다(자식이 sleep 후 exit 1이면 RED도 차단처럼 보여 구분 실패 — **반드시 exit 0**). `--stop` 류 자가복구 호출은 가짜 자식이 빠르게 분기 처리하게 해 테스트가 또 안 늘어지게.

**관련**: T-078(무한 hang 본체), A1 게이트 타임아웃 테스트 `test-require-tests-timeout.sh`(#620), [learning-notes N-143](learning-notes.md)(외부 vs 내부 타임아웃). 1회차 신규.

## T-123. 커스텀 `display`(flex/grid)를 준 요소를 JS `[hidden]`으로 토글해도 author가 UA `[hidden]{display:none}`을 이겨 안 숨겨진다 (T-035 재발 3회차)

**증상**: `/settings` 재스킨 후, `notification-settings.js`가 `el.hidden = true`로 감춘 iOS 설치 안내(`#push-ios-install-hint`)·복귀 알림 힌트(`#push-marketing-hint`)가 화면에 그대로 보였다. 두 요소엔 `.set-notif-subhint{display:flex}`가 걸려 있었다(같은 트랩으로 `[data-push-row]`=`.set-notif-row{display:flex}`도 미지원 브라우저에서 잠재).

**원인**: T-035와 **완전히 같은 뿌리** — CSS cascade는 `!important` 제외 시 origin(author > UA)을 특정성보다 먼저 적용한다. author `.set-notif-subhint{display:flex}`가 UA `[hidden]{display:none}`을 늘 이겨, `hidden` 속성이 있어도 flex로 보인다. `el.hidden` 프로퍼티는 `[hidden]` 속성으로 반영되지만, 그 속성을 무력화하는 건 author의 `display` 선언이다. (#189 `li.hidden` vs `.book-row{display:flex}` → T-035 닫힌 `<details>` 자식 → 이 건, **3회차**.)

**감별**: 실 브라우저에서 JS로 `el.hidden`을 켠 요소가 `getComputedStyle(el).display !== 'none'`이면 이 트랩. MockMvc·헤드리스·preview는 못 잡는다(런타임 CSS 계산이라) → 실 브라우저 게이트에서만 노출(N-083/T-053 계열). 실제로 이 건도 bootRun+Chrome 실페이지에서 처음 드러났다.

**해결(이 PR)**: 페이지 스코프에 재숨김 리셋 `.settings-page [hidden]{display:none !important}` 추가 → author `display`를 `!important`로 눌러 UA 숨김을 복원.

**예방·승격**: JS `[hidden]` 토글에 기대는 요소엔 author `display`를 직접 주지 말거나, 전역 리셋에 맡긴다. **재발 3회차라 국소 리셋(prose·T-035 패턴)만으론 계속 새서 전역 하드픽스로 승격 완료**: `app.css` 베이스에 전역 `[hidden]{display:none !important}`(normalize류) 추가 → `[hidden]`-속성 변형을 앱 전역에서 제거하고, T-123이 넣었던 `.settings-page [hidden]` 스코프 리셋은 삭제(전역이 커버). ⚠️ 닫힌 `<details>` 자식(T-035)은 `[hidden]` 속성이 아니라 UA `details:not([open])` 메커니즘이라 이 리셋 밖 — 개별 `:not([open])` 재숨김 유지. T-035·#189, N-083.

---

## T-124. `npm install`(무인자)이 vite dist를 불완전하게 남겨 빌드가 `ERR_MODULE_NOT_FOUND`(cli.js 없음) — `npm ci`로 클린 복구

**증상**: 프론트 번들 재빌드(`npm --prefix frontend run build`)가 첫 앱부터 `Error [ERR_MODULE_NOT_FOUND]: Cannot find module '…/vite/dist/node/cli.js' imported from …/vite/bin/vite.js`로 실패. `vite/bin/vite.js`는 있는데 `dist/node/cli.js`만 없다(패키지가 반만 설치된 상태).

**원인**: 워크트리 세션에서 `frontend/node_modules`는 메인 워크트리로의 정션인데, 어느 시점 `cross-env` 같은 devDependency가 메인 설치에서 빠져 있었다(그래서 빌드가 `cross-env … 아닙니다`로 먼저 실패). 이를 채우려 `npm install`(무인자)을 돌렸더니 패키지들을 재정렬(dedupe·부분 갱신)하면서 **vite 패키지를 불완전 상태**로 남겼다(bin만 있고 dist 누락). `npm install`은 기존 트리 위에 얹는 증분 조작이라 이런 부분 손상이 날 수 있다.

**해결**: 메인 `frontend`에서 `npm ci` — 락파일(`package-lock.json`) 기준으로 node_modules를 **통째 지우고 정확히 재설치**해 일관성을 복구(`cli.js` 포함). 정션이라 모든 워크트리가 함께 고쳐진다. 이후 `npm run build`·`npm test` 정상.

**교훈**: node_modules가 이상하면(부분 누락·손상) `npm install`(증분)로 덧대지 말고 `npm ci`(클린)로 간다 — 특히 워크트리 정션으로 공유되는 트리에서. 빠진 dep 하나 채우려던 `npm install`이 멀쩡하던 vite까지 깨뜨릴 수 있다. Yes24 PR #625에서 번들 재빌드 중 발생. 1회차 신규.

---

## T-125. Thymeleaf `th:field` 체크박스가 삽입하는 hidden sibling이 CSS 인접 형제 선택자(`+`)를 깨뜨린다

**증상**: `/settings` "밀린 독서 시간을 타이머에 합쳐 표시" 커스텀 체크박스를 클릭해도 육안으로 아무 변화가 없다(실사용 피드백). `input.checked` 값 자체는 정상 토글되고 폼 제출·저장도 멀쩡하지만, 배경색·체크 아이콘(`.set-check-box`)이 영원히 초기 상태로 고정.

**원인**: Thymeleaf `th:field="*{debtCarryover}"`는 체크박스가 해제됐을 때도 폼이 그 필드를 인식하도록 실제 체크박스 **바로 뒤에** `<input type="hidden" name="_debtCarryover" value="on">`을 자동 삽입한다. 그런데 커스텀 체크박스 CSS가 인접 형제 선택자를 쓰고 있었다:
```css
.set-check input:checked + .set-check-box { background: var(--accent); }
```
`+`는 "바로 다음 형제"만 매칭하는데, 이제 `input` 바로 다음은 그 hidden input이라 `.set-check-box`(그 다음 형제)까지는 매칭이 안 닿는다 — 규칙이 조용히 죽어 있었다. 같은 페이지의 "복귀 안내 메일 받기" 체크박스는 `th:field`가 아니라 `th:checked` 수동 바인딩이라 hidden sibling이 없어 원래부터 정상 동작했다 — 그래서 "저 체크박스는 되는데 이건 안 된다"는 대조가 원인 추적의 단서가 될 수 있다.

**해결**: `app.css`에서 `.set-check-box`를 타깃하는 `input:checked`·`input:focus-visible` 규칙을 인접 형제(`+`) 대신 **일반 형제 선택자(`~`)**로 바꾼다 — 중간에 hidden input이 몇 개 끼어도 "그 이후에 나오는 형제"는 다 매칭되므로 안전하다. hidden sibling이 없는 체크박스에도 `~`는 `+`와 동일하게 동작해 회귀가 없다.

**교훈**: Spring/Thymeleaf `th:field`를 커스텀 스타일 체크박스·라디오에 쓸 때는 **DOM에 보이지 않는 hidden sibling이 끼어든다는 전제**로 CSS를 짠다 — `input:checked + .box` 같은 인접 형제 선택자는 th:field 체크박스에서 구조적으로 깨진다. 처음부터 `~`(일반 형제)를 기본으로 쓰거나, 값 바인딩은 되는데 시각만 안 바뀌는 체크박스를 보면 이 패턴부터 의심한다. `/settings` 재스킨(PR #623) 때부터 있던 결함으로 추정, 실사용 피드백으로 발견(PR #629). 1회차 신규.

---

## T-126. 검증 명령을 `| tail`/`| grep`으로 파이프하면 exit code가 가려져 실패가 GREEN으로 보임

**증상**: 백그라운드로 돌린 `./gradlew test 2>&1 | tail -4`가 exit 0으로 끝나 "전체 스위트 GREEN"으로 보고했는데, 실제로는 BUILD FAILED(테스트 3건 실패)였다 — 다중 에이전트 리뷰가 같은 스위트를 직접 돌려보고서야 발각(독서 스토리 PR #632 작업 중).

**원인**: 셸 파이프라인의 exit code는 **마지막 명령**(tail/grep) 것이다. gradle이 1로 죽어도 tail이 0이면 파이프라인 전체가 0. `| grep`은 반대로 "매치 없음"만으로 1을 만들기도 한다(성공을 실패로 보는 오탐). 출력을 요약하려고 붙인 파이프가 검증 명령의 성패 신호를 삼킨다.

**해결 / 예방**:
- 검증(테스트·빌드) 명령은 **exit code를 보존**해 판정: `set -o pipefail && ./gradlew test 2>&1 | tail -5; echo "EXIT=${PIPESTATUS[0]}"` — 또는 파이프 없이 돌리고 로그 파일을 따로 tail.
- 성패를 출력 문자열(BUILD SUCCESSFUL 검색)로 판정하지 않는다 — 출력은 잘리거나 버퍼링될 수 있다. 판정은 exit code, 요약 파이프는 사람 눈용으로 분리.
- 백그라운드 실행 완료 통지의 exit code는 "파이프 마지막 명령의 exit"일 수 있음을 의심한다 — 이번 건은 통지의 "exit 0"을 그대로 믿은 것이 뿌리.

**교훈**: 테스트를 돌리는 것과 결과를 **믿을 수 있게 읽는 것**은 별개다. 1회차 신규.

---

## T-127. 크롬 확장 네트워크 로그의 간헐 503 — 서비스워커 pass-through 내부 fallback 아티팩트(앱 결함 아님)

**증상**: 실 브라우저 게이트 중 크롬 확장의 네트워크 로그(`read_network_requests`)에 일부 POST(스토리 열람 `/api/stories/{id}/view`·DELETE)가 **503**으로 찍힘. 그런데 ① 서버 로그 에러 0 ② DB엔 해당 행이 정상 커밋 ③ 같은 요청을 페이지에서 fetch로 직접 쏘면 200 ④ `res.ok` 분기 클라 코드도 성공 경로를 탐(삭제 후 스트립 갱신 동작).

**원인**: PWA 서비스워커의 fetch 핸들러가 `/api/`를 `return`(no respondWith)으로 **브라우저 기본 처리에 넘기는데**, Chrome은 "SW를 거쳤으나 네트워크로 fallback"한 요청에 대해 확장(webRequest)/DevTools 계층에 **합성 503 엔트리**를 남길 수 있다 — 실제 네트워크 응답(200)과 별개의 표시 아티팩트. SW 콜드 부팅 타이밍 등에서 간헐적으로만 보인다.

**감별(3중 교차 검증)**: ① 서버 로그(예외 스택 유무) ② DB 상태(행 커밋 여부) ③ **페이지 레벨 실측** — `window.fetch` 몽키패치로 status 캡처(리로드 후 패치 → 트리거 순서 주의) 또는 코드의 `res.ok` 분기 동작 관찰. 셋 다 성공인데 확장 로그만 503이면 아티팩트.

**해결 / 예방**: 앱 수정 불필요. 실 브라우저 게이트에서 **확장 네트워크 로그의 상태코드를 단독 증거로 쓰지 않는다** — 특히 SW 스코프 페이지의 fire-and-forget POST(응답 본문을 안 읽는 요청)에 잘 낀다. 판정은 페이지 레벨 실측 + 서버 로그 + DB로.

**교훈**: 관측 도구도 계층이다 — 확장의 webRequest 관측엔 SW 계층의 내부 이벤트가 섞일 수 있다. "어느 계층의 관측인가"를 먼저 물어야 가짜 신호에 시간을 안 태운다(이번 진단 ~30분). 1회차 신규.

---

## T-128. Yes24 링크프라이스 딥링크, 모바일 UA면 Yes24 게이트가 목적지를 m.yes24 메인으로 치환 (tu에 모바일 URL을 넣어도 우회 불가)

**증상**: Yes24 "구매" 버튼이 데스크톱에서는 Yes24 검색 결과로 정상 이동하는데, 모바일(iPhone 사파리 실사용 보고·Android도 동일 실측)에서는 검색 결과 대신 **Yes24 모바일 메인(m.yes24.com)에 떨어진다**. 알라딘·쿠팡은 모바일에서도 정상.

**원인**: 현재 체인은 `/books/{id}/buy/yes24` → 링크프라이스 래퍼(`lpweb.kr/click.php?...&tu=<목적지 URL 인코딩>`) → `www.yes24.com/Cooperate/LinkPrice/lpfront.asp` → `lpfront.aspx` → `Yes24Gateway.aspx?pid=…&ReturnURL=<목적지>`(meta refresh로 최종 이동)다. **`www.yes24.com/Cooperate/LinkPrice/lpfront.aspx`가 UA 판별로 모바일(iPhone·Android 공통)이면 `ReturnURL`을 통째로 버리고 `http://m.yes24.com/`(모바일 메인)으로 치환**한다 — 목적지 URL 자체는 그 앞 단계까지 파라미터에 온전히 보존돼 있으므로 치환 주체는 링크프라이스가 아니라 Yes24 자신. `tu`에 모바일 검색 URL(`https://m.yes24.com/search?query=…`)을 넣어도 모바일 UA면 똑같이 메인으로 치환됨(판별 기준이 UA일 뿐 URL 형태와 무관) → **래퍼를 유지한 채로는 우회 불가**. `https://m.yes24.com/search?query=<ISBN13>`을 직접 열면 모바일에서 정상 동작(2026-07-02 운영 booktimer.app에서 curl UA별 리다이렉트 실측).

**해결**: 모바일 UA면 제휴 래퍼를 아예 타지 않고 `m.yes24.com/search`로 직행(모바일 클릭 커미션은 포기, 사용자 승인). `Yes24LinkBuilder.isMobileUserAgent`가 Yes24 자신의 기기 판별(`RedirectWebSiteList.min.js` `list_mobile_device`: `Android|BlackBerry|iPhone|iPad|iPod|Opera Mini|IEMobile`)을 미러링해 판별하고, `BookController`의 buy 2 엔드포인트가 `User-Agent` 헤더로 분기한다. 데스크톱 경로(래퍼)는 무변경.

**일반화**: **제휴 리다이렉트 래퍼는 최종 목적지 보존을 UA별로 실측하라** — 데스크톱만 확인하면 모바일에서 중간자(이번엔 제휴사 자신)가 목적지를 버리는 사각을 놓친다.

**관련**: 계획 md `claude-docs/plans/2026-07-02-yes24-mobile-ua-branch.md`(진단 체인 상세). 1회차 신규 — 재발·승격 트래커에는 올리지 않는다.

---

## T-129. 쿠팡 파트너스 "구매" 링크가 추적 0 — CoupangLinkBuilder 자작 lptag 검색 URL은 정식 추적링크가 아님(딥링크 API 필요)

**증상**: 쿠팡 파트너스 리포트에 BookTimer 경유 클릭이 **0**(구매·수익도 0). 실배포 운영자가 본인이 앱을 통해 쿠팡에서 2번 구매까지 했는데 **클릭조차 안 잡힘**(그 달 클릭 0). 알라딘·Yes24와 달리 쿠팡만.

**원인**: `CoupangLinkBuilder.buildSearchLink`가 `https://www.coupang.com/np/search?q={ISBN}&lptag={추적코드}`를 **문자열 치환**으로만 만들고(네트워크 호출 0), `/books/{id}/buy/coupang`(`BookController`)이 그 URL로 302 리다이렉트한다. 이건 파트너스가 **"생성"한 정식 추적링크가 아니다**. 쿠팡 추적은 파트너스 센터 "간편 링크 만들기"/딥링크 API로 생성한 링크(→ `link.coupang.com`·`coupa.ng` 리다이렉트 서버 경유 + `isshortened=Y`·`pageKey`)라야 클릭이 집계된다. 공식 이용 가이드 원문: *"쿠팡 페이지의 URL을 그대로 복사하거나, 쿠팡 내 공유 기능을 사용하면 수익금에 반영되지 않습니다."* → 자작 URL은 구조적으로 미집계.

**감별·배제**: ① 반영 지연 아님 — 지연은 최대 익일 정오±인데 한 달 내내 0. ② 추적코드 오타 아님 — 코드가 맞아도 `isshortened=Y`(정식 생성 흔적)가 없으면 0. ③ **본인 구매 0은 별개 원인** — 공식 가이드 STEP5 *"본인 링크를 통한 자가 구매는 실적이 집계되지 않아요"*라, 링크가 정상이었어도 운영자 자가 구매는 0(어뷰징 위험이라 본인 검증 금지). 즉 **클릭 0 = 링크 결함 / 수익 0 = 자가 구매 제외**로 나눠 본다.

**해결**: 302 목적지를 딥링크 API로 생성한 정식 추적링크(`shortenUrl`)로 교체한다. **⚠️ 환경변수 템플릿(`COUPANG_SEARCH_URL_TEMPLATE`) 교체로는 못 고친다** — 문자열 치환 방식 자체가 raw URL만 만드는 게 결함이다. 계획 md `claude-docs/plans/2026-07-03-coupang-deeplink-api.md`. 개념 = [learning-notes N-146](learning-notes.md).

**대조**: 같은 앱 Yes24는 linkprice 딥링크 래퍼(`lpfront.aspx`)를 **실제로 통과**해 추적됨(T-128) — 쿠팡만 자작이라 안 됐다. 1회차 신규 — 재발·승격 트래커에는 올리지 않는다.

---

## T-130. dark-launch 기능 secret을 task-def valueFrom으로 배선하면 SSM 파라미터 미생성 시 ECS 배포가 서킷브레이커 롤백

**증상**: #641(쿠팡 딥링크 dark-launch) 머지 후 자동 ECS 배포가 "Deploy to ECS" 단계에서 실패 — GitHub Actions 로그는 `Deployment ... not found after stabilization. The deployment was likely rolled back by the deployment circuit breaker.`만 남긴다. 앱은 구 리비전으로 계속 서비스(무중단)되나 새 코드가 안 나가고, 이후 main에 코드가 푸시될 때마다 같은 이유로 배포가 계속 막힌다.

**원인**: `deploy/task-definition.json`이 새로 추가한 기능 secret 3개(`COUPANG_ACCESS_KEY`/`SECRET_KEY`/`SUB_ID`)를 `secrets[].valueFrom`(SSM 파라미터 ARN)으로 참조했는데, 그 SSM 파라미터가 아직 미생성. Fargate는 태스크 기동 시 `valueFrom` secret을 SSM에서 pull해 env로 주입하는데, 파라미터가 없으면 컨테이너가 아예 못 뜬다 → 새 태스크 연속 실패 → 배포 서킷브레이커가 롤백. **핵심 함정**: `application.properties`에 안전 기본값(`${BOOKTIMER_COUPANG_ACCESS_KEY:not-configured}`)이 있어도 소용없다 — `valueFrom`은 "런타임에 이 env를 SSM에서 채운다"는 뜻이라, 파라미터 부재는 기본값 폴백이 아니라 **앱 코드 실행 전 태스크 기동 실패**다. "키 없이도 안전한 dark-launch"라는 의도가 코드 게이트(`isEnabled()`)엔 있어도 배포 배선(task-def)에서 깨진다.

**감별·배제**: ① Spring 기동 실패(새 빈 미주입 등) 아님 — 새 `CoupangDeeplinkClient`가 요구하는 `Clock` 빈은 `TimeConfig`에 이미 존재하고, 나머지 새 의존(@ConfigurationProperties·RestClient)은 전부 기본값 보유. ② 코드 버그 아님 — CI `test` green, 이미지 빌드·태스크 정의 등록까지 성공하고 오직 안정화(=태스크 기동) 단계만 실패. ③ **100% 확정처**: ECS 콘솔 → 서비스 → 중지된 태스크 → **Stopped reason**(SSM 부재면 `ResourceNotFoundException`·`unable to pull secrets ... parameter ... not found`). GitHub Actions 로그는 circuit-breaker 문구까지만 보여줘 원인 특정엔 부족하다.

**해결**: 미점등(dark-launch) 기능의 secret은 **① task-def에서 빼서 앱 기본값을 쓰거나, ② placeholder SSM 파라미터를 먼저 만든 뒤** 배선한다. 이번엔 ①로 secret 3줄 제거(PR #642) → 앱 기본값 `not-configured` 적용 → `CoupangDeeplinkProperties.isEnabled()` false로 기능 skip → 재배포 green으로 확정. 키 확보(점등) 시 **SSM 파라미터 생성 + secret 재배선 + 기능 플래그 on을 한 PR로** 묶는다(존재 보장과 배선을 분리하지 않기).

**예방**: 새 기능을 dark-launch로 심을 때 "코드 게이트가 꺼져 있으니 안전"과 "배포 인프라(task-def)도 안전"은 **별개**다 — `valueFrom` 한 줄이 SSM 파라미터 존재를 하드 의존으로 만든다. 커밋 전 자문: *"이 task-def가 참조하는 SSM 파라미터가 배포 시점에 전부 존재하는가?"* 다른 원인의 Fargate SSM pull 실패는 T-011(네트워크 도달 불가). 개념 = [learning-notes N-146](learning-notes.md). 1회차 신규 — 재발·승격 트래커에는 올리지 않는다.

---

## T-131. 알라딘 OpenAPI includeKey 미전송으로 응답 link에 TTBKey 없어 제휴 클릭 추적 0

**증상**: 알라딘 "구매" 버튼(`/books/{id}/buy`)이 정상 이동하고 클릭 집계(`clickCount`)도 오르는데, 알라딘 제휴(판매수익) 귀속이 잡히지 않는다(쿠팡 T-129와 같은 계열의 무성 실패). 운영 실측: `/books/60/buy`·`/books/59/buy`의 302 최종 목적지가 `www.aladin.co.kr/shop/wproduct.aspx?ItemId=…&partner=openAPI&start=api`로 **`ttbkey=`가 없다**(2건).

**원인**: 알라딘 제휴 귀속은 상품 link에 **TTBKey(제휴 식별자)**가 실려야 성립하는데([N-035](learning-notes.md)), 그 link에 TTBKey가 실리려면 알라딘 OpenAPI 요청 파라미터 **`includeKey=1`**이 필요하다(공식 매뉴얼, **기본값 0**). `AladinBookSearchClient.buildSearchUrl`·`buildLookupUrl`이 이 파라미터를 안 보내(기본 0), 알라딘이 돌려주는 `item.link`에 ttbkey가 빠진다. BookTimer는 그 link를 검증 없이 `book.purchaseLink`에 저장하고 `/buy`가 그대로 302 재생하므로 구매링크가 영영 무추적이다. 쿠팡(T-129)이 "플랫폼이 생성한 링크 미경유(자작 합성)"였다면, 알라딘은 "API가 생성한 link를 쓰되 제휴키 포함 옵션을 안 켬" — **합성 함정은 회피했으나 결과(추적 0)는 동일**한 한 겹 다른 함정.

**감별·배제**: ① 검색은 정상 — 요청의 `ttbkey`는 인증키로 실리고, 결함은 *응답 link*의 제휴키다(요청 ttbkey ≠ 응답 link ttbkey). ② 테스트가 못 잡음(가짜 GREEN): `AladinBookSearchClientTest.parse_mapsItems`가 픽스처 link에 `ttbkey=x`를 손으로 박고 그 존재를 단언 → 파서 verbatim 복사만 증명, 실 API가 includeKey 없이 ttbkey를 주는지는 미검증. ③ 자가 클릭·구매는 실적 제외(N-146·T-129)라 링크가 고쳐져도 본인 트래픽으론 검증 불가 — **링크 형식(ttbkey 유무)으로 판별**하고 실적은 제3자로.

**해결**: `buildSearchUrl`·`buildLookupUrl`에 `.queryParam("includeKey", 1)` 추가(새 검색·백필부터 ttbkey 실림). **이미 저장된 무추적 링크는 픽스만으론 안 고쳐지므로**(purchaseLink는 검색 시점에 박혀 저장됨) `BookCatalogBackfillService.backfillPurchaseLinks`(대상=`purchaseLink`에 `ttbkey=` 없고 isbn 있는 책, `lookupByIsbn` 재조회로 교체, 재조회에도 ttbkey 없으면 옛 링크 유지·`notFound` — 무추적 링크로 덮어쓰지 않음) + `POST /admin/books/backfill-purchase-links`(ADMIN·CSRF·limit) + admin 버튼. 배포 후 운영 ttbkey로 백필 실행 → `/buy` 링크에 ttbkey 재확인이 최종 검증(PR #644).

**일반화**: **"플랫폼이 생성한 링크"라도 제휴키가 실제로 실렸는지 실측하라** — 제휴사가 link를 발급해준다고 끝이 아니라, 그 발급에 제휴키 포함 옵션(알라딘 `includeKey=1`)이 켜졌는지까지 확인해야 한다. 저장형 링크(DB 적재)는 결함이 있으면 기존 데이터 전량이 영구 무추적이 되니 픽스와 **백필**이 짝이다.

**같은 계열 YES24(2026-07-04 실측·가드)**: 데스크톱 "구매" 링크가 링크프라이스 래퍼 없이 순수 `www.yes24.com/product/search?query=<ISBN>`(추적코드 부재)라 추적 0 — 운영 SSM `YES24_SEARCH_URL_TEMPLATE`가 래퍼가 아닌 순수 URL인데 `Yes24LinkBuilder.isEnabled()`가 `trackingCode`만 봐 버튼이 떠 있었다(무성실패). 가드 = `isEnabled()`에 `searchUrlTemplate.contains("{trackingCode}")` 추가(자리 없으면 비활성 → 버튼 숨김). 근본 복구는 SSM를 링크프라이스 래퍼(`lpweb.kr/click.php?a_id={trackingCode}&…&tu=…{query}`)로 교체. 알라딘(코드)과 달리 **원인이 운영 설정(SSM)**이라 코드 가드는 "무성실패를 티나게" 하는 역할.

**2회차(T-129 쿠팡 재발)** — "어필리에이트 클릭 추적 무성 실패" 트랩군으로 재발·승격 트래커에 등재. 개념 = [learning-notes N-146](learning-notes.md), [N-035](learning-notes.md). 자동 메모리 `aladin-affiliate-tracking-broken`.

---

## T-133. 스크립트가 읽는 비밀 파일 경로가 `.gitignore` 무시 경로·문서와 어긋나 조용히 실패 or 비밀 커밋

**증상**: LinkPrice 실적 스크립트 `affiliate-report.mjs`에 발급받은 `auth_key`를, 주석·에러메시지·자동 메모리·`.gitignore`가 모두 가리키는 `.claude/.secrets/linkprice-auth`에 저장했는데 스크립트가 "auth_key 없음"으로 `exit 2`. 반대로 스크립트가 실제 읽는 위치에 저장하면 그 경로가 gitignore 밖이라 비밀키가 `git status`에 떠서 커밋될 위험. 어느 쪽이든 에러 로그 없이 조용히 틀린다.

**원인**: `readAuthKey()`가 `resolve(here, '.secrets', 'linkprice-auth')`로 경로를 잡아 **스크립트 폴더 기준**(`.claude/scripts/.secrets/`)이 됐다. 스크립트가 `.claude/scripts/`에 있으니 `.claude/.secrets/`를 잡으려면 상위(`..`)로 올라가야 하는데 `..`이 빠졌다. 그런데 `.gitignore`는 `.claude/.secrets/`만 무시한다 → **코드 경로(`scripts/.secrets`) ≠ gitignore 경로(`.claude/.secrets`) ≠ 문서**. 문서대로 저장하면 스크립트가 못 읽고, 코드 경로에 저장하면 gitignore 밖이라 비밀이 커밋되는 **양방향 무성 함정**.

**해결**: 경로 해석을 순수 함수 `secretPathFor(scriptDir)`로 뽑고 `resolve(scriptDir, '..', '.secrets', 'linkprice-auth')`로 상위 `.claude/.secrets/`를 잡게 함. 단위테스트로 **경로가 `.claude/.secrets/linkprice-auth`로 끝나고 `scripts/` 밑이 아님**을 단언(gitignore 위치 일치 불변식) — 현 코드로 RED(`…/scripts/.secrets/…` 실측) → `..` 추가로 GREEN. 저장 후 `git check-ignore`로 실제 무시되는지·`git status`에 비밀 파일이 안 뜨는지 확인.

**일반화**: **비밀·설정 파일 경로를 코드가 `resolve()`로 잡을 땐, 그 경로가 `.gitignore` 무시 위치·문서와 정확히 일치하는지 테스트로 못박아라.** 셋(코드 경로·gitignore·문서)이 한 곳을 안 가리키면 에러 없이 "못 읽음" 또는 "비밀 커밋" 중 하나로 조용히 깨진다. 경로를 순수 함수로 분리하면 문자열 단언으로 싸게 고정된다.

**1회차 신규**. 자동 메모리 `affiliate-report-automation-pending`, PR #660.

---

## T-134. 외부 API의 "에러처럼 생긴" result 코드가 실은 정상 무데이터일 수 있다 — 비-성공을 뭉뚱그려 fatal 처리 금지

**증상**: LinkPrice 실적 조회(`translist.php`)에 유효한 auth_key로 이번 달을 조회했더니 `❌ API 오류 (result=101)`. `test=Y` 스모크는 `result=0`·더미 10건으로 멀쩡 → 인증·a_id·page 인덱싱은 정상인데 실 조회만 "오류"로 보였다.

**원인**: `result=101`은 가이드(`실적_조회_오픈_API_v1.6`)상 "정상 page 번호 아님"이지만, **실적이 0건이면 반환할 page가 없어** 이 코드가 온다(운영 실측: `202605`·`202606`·`202607` 전부 `result=101`·`list_count=0`, `page=0/1/2` 동일). 즉 101은 치명 오류가 아니라 **"해당 기간 데이터 없음"** 신호. 그런데 `fetchAllOrders`가 `result != '0'`을 전부 하드 에러(exit 1)로 처리해, 실적이 쌓이기 전(제휴 점등 직후·본인 클릭 제외)엔 매번 가짜 오류가 떴다.

**해결**: result 코드를 **3분류**하는 순수 함수 `classifyResult(result)`로 분리 — `'0'`=ok / `'101'`=no-data(빈 성공으로 종료 → "실적 없음") / 그 외(`100` a_id·`200`/`210` 날짜·`300` 인증키·`400` 통화)=error 유지. no-data면 지금까지 모은 것(0건이면 빈 배열)으로 정상 종료. **양방향 불변식을 테스트로 못박음**: 101을 error로 두면 무데이터마다 가짜 오류 / 실제 오류코드를 no-data로 삼키면 깨진 설정이 조용히 "실적 없음"으로 숨는다.

**감별**: `test=Y`(더미 데이터)가 `result=0`으로 오면 인증·요청 파라미터는 정상 → 실 조회만 비-0이면 "설정 오류"가 아니라 "데이터 상태" 코드를 의심하라. 벤더 코드 표를 실제로 열어 각 코드 의미를 확인(추측 금지).

**일반화**: **외부 API 응답 코드를 "0 아니면 전부 에러"로 뭉뚱그리지 말라.** 코드 표를 확인해 각 코드를 no-data / 재시도 가능 / 치명으로 구분하고, 특히 "빈 결과"를 에러로 오표기하지 않게 한다 — 정상 운영에서 늘 뜨는 가짜 알람이 되면 진짜 오류를 가린다. 코드 판정을 순수 함수로 뽑으면 경계를 싸게 테스트로 고정한다.

**1회차 신규**. `classifyResult`(`affiliate-report.mjs`), PR #661. 자매 T-133(같은 스크립트의 비밀키 경로 버그).

---

## T-135. `preview_screenshot`이 `readyState=complete`인데도 30초 타임아웃 — `preview_inspect`/`eval` + 비교 위젯으로 시각 검증 우회

**증상**: 정적 preview 서버(`static-preview`)로 페이지를 띄우고 `preview_screenshot`을 호출하면 30초 타임아웃("preview window may be stuck")이 반복된다. 반면 `preview_snapshot`·`preview_inspect`·`preview_eval`은 정상 동작하고, `document.readyState`는 `complete`, `getAnimations().length`는 0 — 페이지·애니메이션은 멈췄는데 캡처 단계만 무한 대기(PC 재부팅·서버 재시작 후에도 재현).

**부분 요인 하나(제거해도 안 풀림)**: `app.css` 첫 줄 Google Fonts `@import`가 preview 환경에서 외부 요청 pending → 페이지 `load` 이벤트가 안 끝나 스크린샷이 대기. serve.js에서 CSS 응답의 `@import ...fonts.googleapis...`를 스트립해 외부 요청을 없앴으나(폰트는 시스템 fallback), **그 뒤에도 스크린샷만 타임아웃** → 폰트가 유일 원인은 아니고 이 환경의 캡처 파이프라인 자체 한계로 판단.

**해결(우회 — 스크린샷 없이 검증)**:
- **값 실측**: `preview_inspect`(computed styles)와 `preview_eval`(`getComputedStyle(el)`·`getComputedStyle(el, '::before')`로 `::before`/`::after` 장식·`scrollHeight > clientHeight`로 2줄 clip 판정)로 폰트·색·정렬·구분선·클립을 픽셀보다 정확히 확인.
- **사용자 시각 비교**: `show_widget`(visualize MCP)으로 실제 폰트(위젯은 `fonts.googleapis` 허용)·앱 색을 로드한 비교 목업을 인라인 렌더 → 사용자가 직접 보고 고르게.

**교훈**: 스크린샷이 유일한 시각 검증 수단이 아니다 — 색·크기·간격은 `inspect`/`eval`이 오히려 정확(CLAUDE.md 프론트 검증 원칙과 동일). 자매: T-112(Chrome MCP `resize_window`가 `innerWidth` 못 바꿈→iframe 우회), T-114(`preview_inspect` shorthand 빈 객체→longhand/`eval`). **1회차 신규** — preview 도구 한계 계열이나 각기 다른 도구·증상이라 재발·승격 트래커에는 올리지 않는다.

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
| 2026-06-20 | T-070 (bootRun은 장기 실행 태스크라 Gradle 진행률이 80%대서 멈춤=정상, 100%는 앱 종료 시 도달 / ready 판정은 % 아닌 로그 `Started ...`·8080 LISTEN, Claude Code는 background 실행해야 foreground 무한대기 회피 / 검증 후 8080 반납 / T-063) |
| 2026-06-20 | T-071 (Service Worker + 해시 없는 번들(`garden.js` 파일명 고정) → cache-first만 쓰면 배포해도 사용자에게 안 묻힘 / Vite `entryFileNames: 'garden.js'`로 파일명이 고정이라 SW가 cache-first로 잡으면 캐시가 살아있는 한 구 버전이 계속 서빙됨 / 해결=`garden.js`는 SW에서 **network-first**(온라인이면 항상 네트워크 우선, 캐시는 오프라인 폴백), 나머지 정적 자산(CSS·아이콘·manifest)은 cache-first / 추가 안전장치=`CACHE` 버전 상수 올리면 activate에서 구 캐시 전량 삭제 / N-098(Vite 번들 static), N-101(PWA 레벨), PR L2) |
| 2026-06-20 | T-072 (Service Worker scope = sw.js 파일 위치 / SW의 scope는 sw.js가 있는 경로를 기준으로 결정됨 — `static/garden/sw.js`이면 scope=`/garden/`이라 `/dashboard`·`/` 등이 제어 안 됨 / 해결=`static/sw.js`(static 루트 직하)에 두면 scope=`/` 전역 → 모든 경로 fetch를 가로챌 수 있음 / Vite의 outDir=`static/garden`이라 빌드 산출물에 섞이지 않도록 손수 파일로 static 루트에 배치 / N-101, PR L2) |
| 2026-06-20 | T-073 (푸시 토글 함수에서 VAPID 체크를 최상단에 두면 OFF(철회) 경로도 막힌다 / VAPID 체크를 ON 분기 안으로 이동 / §50 수신거부는 즉시 처리 의무라 막히면 안 됨 / N-103·N-102, PWA L3b) |
| 2026-06-21 | T-075 (파일명 고정 자산 pwa-install.js·app.css를 SW cache-first로 두면 배포 후에도 stale 서빙 — T-071(garden.js)과 같은 뿌리, NETWORK_FIRST 배열 + CACHE 버전 올림으로 해결 / PR #430) |
| 2026-06-21 | T-076 (`inlineDynamicImports:true`를 멀티 input 객체와 함께 쓰면 Rollup 에러 / Vite 멀티빌드에서 `input: { pageA: '...', pageB: '...' }` 구조로 여러 엔트리를 하나의 빌드 명령에 넣으면 `inlineDynamicImports is not supported for multiple entry points` 에러 발생 — `inlineDynamicImports`는 단일 엔트리 전용 옵션 / 해결=페이지별 독립 빌드로 분리(`APP` env var 분기: `cross-env APP=search vite build`), 각 빌드는 단일 엔트리이므로 `inlineDynamicImports:true` 유지 가능·단일 파일 산출 그대로 / 배경: Vite는 `rollupOptions.input` 객체를 멀티 엔트리로 해석하고 이때 `inlineDynamicImports`를 허용 안 함 / PR #438) |
| 2026-06-21 | T-077 (jsdom에서 scroll-snap 컴포넌트 단위테스트 시 `scrollBy`·`clientWidth`·`scrollLeft`·`offsetWidth`가 모두 0 반환 — jsdom은 CSS 레이아웃 엔진이 없어 렌더링 연산을 수행 안 함 / 증상=step()·sync()가 의도한 스크롤을 일으켜도 값이 0이라 "동작 안 함"처럼 나타나 단언이 항상 통과하거나 항상 실패 / 해결(테스트 전략)=① 버튼 존재 + emit 호출·disabled 토글만 단언, ② 실제 스크롤 동작은 실 브라우저 게이트(Chrome 확장)로 위임 / 깊은 스크롤 동작이 필요하면 JSDOM 대신 Playwright·Cypress(headless Chromium=실 레이아웃) / N-083·T-053, PR personality-island) |
| 2026-06-22 | T-078 (구현 세션에서 git/commit이 무한 로딩·esc·머지로도 안 풀리고 clear로만 해소 — Claude Code 코어 버그가 아니라 **자식 프로세스(git/gradle) hang을 await**하는 구조 때문 / 이 프로젝트는 `git commit`을 require-tests-before-commit.ps1 훅이 가로채 `./gradlew test`를 돌리므로 "git 작업"으로 보여도 실제로 멈춘 건 gradle 테스트일 때가 많음 / hang 뿌리=멀티 세션(워크트리·동시 세션)이 gradle 데몬·빌드 락 동시 점유 / 감별=지금 `git status`가 빠르면 git/레포 정상 / 해결=`Get-Process java,git｜Stop-Process -Force`·`.git/index.lock` 삭제·`./gradlew --stop`, esc만으론 spawn된 자식·데몬이 안 죽어 부족 / 예방=멀티 세션 시 한 세션에서만 커밋·빌드 / N-032·T-070·T-051, 커밋 훅) |
| 2026-06-22 | T-079 (Vue 섬 번들 `/books/books-<hash>.js`가 book-detail `@GetMapping("/books/{id}")`에 가로채여 500/503·셸 무한로딩 — 컨트롤러 매핑이 기본 정적 핸들러(`/**`)보다 먼저 매칭, id=파일명→Long 변환 실패 / #425 book-detail+#447 섬 번들 둘 다 `/books/` prefix라 머지 후 잠복 / 감별=셸만 뜸+번들 503(404 아님)+배포 해시 일치 / 해결=`@GetMapping("/books/{id:\\d+}")` 숫자 제한→비숫자 정적 폴백 200 / 부작용=POST /books/add 405→404(SsrMutationRemovedTest 갱신) / 회귀=GET /books/books.js→200·/books/not-a-number→404, 헤드리스는 /api 목이라 실 번들 URL 사각 N-112 / PR #450) |
| 2026-06-22 | T-080 (SW가 에러 응답(500/503)을 `res.ok` 검사 없이 캐싱 → cache-first가 서버 fix 후에도 영구 서빙(self-heal 불가) — #450 라우트 충돌 시기 번들 500이 `shell-v5`에 캐싱된 사례 / 감별=fix 배포 후에도 fresh 503인데 캐시버스터 `?cb=1`는 200, `via`/`x-cache` 없으면 SW(`caches.match`의 `.status`로 확인) / 해결=① `put`에 `if(res.ok)` 가드 ② `CACHE` `shell-v5`→`v6`로 activate purge→피해자 다음 방문 자동복구, 개별 즉시복구=SW 해제+`caches.delete` / 예방=SW `put` 항상 `res.ok`, 검증은 실 브라우저 / T-071·T-075·N-101·N-112, PR #450 후속) |
| 2026-06-23 | T-082 (라디오 CSS탭→Vue v-if 탭 전환에서 책방 탭 3종 깨짐 — ①패널 미표시=`.vue`의 `tab-panel` 제거 후 `npm build` 누락으로 `profile.js` stale→`.tab-panel{display:none}` 적중(T-063 재발) ②`.link-row` 짓눌림=죽은 `#tab-bti:checked~.panel` 셀렉터 삭제 시 `clear:both` 동반 유실→`.record-tab{float:left}` 미clear→`.record-card` collapse→형제 block grid가 min-content로 붕괴(N-114) ③active 밑줄 누락=`:checked+.record-tab`만 있고 `.active`엔 미적용 / 감별=`git log` `.vue`vs빌드`.js` 커밋 어긋남, float 형제 제거 시 link-row 펴짐, active는 transition .15s라 300ms 대기 후 측정 / 해결=재빌드+`.panel-bti,.panel-shelf{clear:both}`+`.record-tab.active` 셀렉터 / 예방=라디오→JS 탭 옮길 때 display·clear·active 전수 이전, `.vue` 수정 후 npm build·산출물 커밋 / T-063·T-081·N-113·N-114) |
| 2026-06-23 | T-083 (gh pr checks --watch가 CI 등록 전 실행되면 "no checks reported"로 즉시 exit 1 — 뒤에 `&& gh pr merge` 체이닝 시 머지 무산 / 원인=push 후 체크 런 등록까지 지연, --watch는 "있는 체크 끝나길" 기다리지 "생기길" 안 기다림→비결정적 / 해결=watch 전 `gh pr checks`가 no checks 안 뱉을 때까지 sleep 폴링 후 --watch(run_in_background) / 워크트리 --delete-branch 로컬정리 실패는 별개 T-051 / T-048·N-070) |
| 2026-06-22 | T-081 (SSR→Vue 전환 후 리스트 행 버튼이 풀폭 세로로 깨짐 — 전역 `button{width:100%}`를 가두던 `<form>` 래퍼가 SPA 전환으로 사라져 버튼이 flex-row 컨테이너(`.book-actions`·`.profile-actions`)의 직계 자식이 됨 / 감별=같은 행 `<select>`는 멀쩡한데 `<button>`만 풀폭(select는 width:100% 안 받음), `getBoundingClientRect().width`=컨테이너 폭이면 확정 / 해결=`.book-actions > button`·`.profile-follow .profile-actions button`에 `width:auto`(248·761·1213행 상쇄 패턴), `.book-actions`는 UserRow 공용 셸로 books·follow·book-readers·block·search 전부 커버 / 예방=래퍼 제거 시 그게 전역 width를 가뒀는지 확인, block 컨테이너(`.timer-controls` 단일 CTA)는 무관, 실 렌더 폭 측정 검증 / N-113·N-112·T-079) |
| 2026-06-24 | T-085 (Docker exec mysql 명령으로 한글 INSERT 시 `?????` 깨짐 — PowerShell에서 `docker exec ... mysql -e "INSERT ... VALUES('한강'...)"`을 실행하면 CP949(ANSI)로 인코딩된 바이트가 MySQL로 전달돼 DB에 `?????`로 저장됨 / UTF-8 파일을 `docker cp` 후 stdin으로 넘겨도 PowerShell `Get-Content`의 CRLF 처리나 character set 협상 실패로 동일하게 깨질 수 있음 / 감별=INSERT 후 `SELECT HEX(col)` 결과가 올바른 UTF-8 HEX(한강=`ED959CEA B095`)가 아닌 다른 바이트열이면 깨진 것 / 해결=한글이 포함된 데이터는 **Spring Boot API(JSON POST)를 경유**해 삽입 — HTTP 요청은 UTF-8 Content-Type으로 전달되고 서버가 JPA로 올바르게 저장; 크롬 확장 `javascript_tool`로 `fetch('/api/...', {method:'POST', body: JSON.stringify({author:'한강'})})` 형태 / 영문 컬럼(code·status 등)만 포함된 INSERT는 docker exec mysql로 직접 가능 / 예방=로컬 DB 시드 시 한글 포함 여부 확인 후 경로 선택) |
| 2026-06-25 | T-087 (CSS 주석 속 `*/`가 주석을 조기 종료해 다음 규칙을 침묵 드랍 — `.dash-card` 앞 주석의 `기존 .grass-cell/.timer-*/.quick-*`에서 `.timer-*/`의 `*/`가 주석을 일찍 닫아, 뒤 `.quick-* … */ .dash-card { … }`가 invalid 셀렉터로 묶여 `.dash-card` 규칙(배경·테두리·그림자·padding·flex) 통째 드랍·콘솔 에러 0 / 증상=특정 클래스만 전부 미적용·바로 다음 규칙(`.dash-pill`)부터 정상, 파일·`fetch`엔 멀쩡 / 진단=`[...document.styleSheets[0].cssRules].some(r=>r.selectorText==='.dash-card')`가 false면 파싱 드랍, 캐시는 `?v=` cache-bust로 분리 / 해결=주석 안 `*/` 유발 토큰 회피(`.timer-*/.quick-*`→`.timer-*·.quick-*` 슬래시를 ·로) / 잠복성↑: PR-2부터 카드 스타일이 죽어 있었으나 배경색 유사로 안 들킴 / 개념 N-118, 시각검증 T-043. **2회차(이 PR — 책방 대시보드 이식): 새 `.shop-*` 블록 주석에 `공유 .book-*/.record-*`라 적어 `.book-*/`의 `*/`가 또 조기 종료 → 바로 뒤 `body.profile-page .container{max-width:920px}`가 침묵 드랍돼 와이드 2열 메인이 460px에 갇혀 오버플로. 같은 wildcard-slash 메커니즘 재발 → 재발 트래커 등재. 이번엔 §11 static-preview 게이트가 `body.profile-page .container` 규칙 부재(`cssRules.some(...)=null`)+컨테이너 폭 460 실측으로 포착(1차는 잠복). 해결=슬래시를 `과`/`·`로(`.book- 과 .record- 계열`). 예방 규칙은 있었으나 작성 시 미상기 → wildcard 클래스를 주석에 나열할 땐 슬래시 금지를 반사적으로.** **3회차(로그인·회원가입 이식 PR): 새 `.auth-*` 블록 주석에 `공유 …·.oauth-*/.entry-hero…`라 적어 `.oauth-*/`의 `*/`가 또 조기 종료 → 바로 뒤 `.auth-page .auth-shell{max-width:400px}`가 침묵 드랍돼 양 페이지 폭 미적용(로그인은 콘텐츠가 짧아 우연히 좁아 보여 더 잠복). 실 브라우저에서 `getComputedStyle(shell).maxWidth==='none'`+`cssRules`에 `.auth-page .auth-shell` 부재로 포착. 예방 규칙·게이트가 1·2차를 못 막아 3회차에 이르렀으므로 prose→하드픽스로 승격: 훅 `require-css-comment-safe.ps1`(스테이징 `.css` 스캔, 주석 닫는 `*/`가 양옆 모두 셀렉터문자 `[A-Za-z0-9._#*-]`에 붙으면 차단). 기존 보류 사유였던 FP(` */color`·standalone `/*x*/`)는 "양옆 glued" 조건으로 통과시켜 해소, 잔여 FP `/*c*/.sel`류는 우회 토큰 `SKIP_CSS_COMMENT_CHECK`. 테스트 `.claude/hooks/tests/test-require-css-comment-safe.sh`(차단 2종·정상/문자열/우회/비-css/비-commit/깨진JSON 9케이스).**) |
| 2026-06-24 | T-084 (Phaser `update()`가 덮어쓰는 속성에 tween 걸면 즉시 무효화 — 배회 캐릭터 먹이 반응 애니로 `this.tweens.add({targets: obj, y: ..., scaleY: ...})`를 걸었더니 `update()`가 매 프레임 `walkPose`로 `o.y = py+bobY`, `o.setScale(...)`, `o.setAngle(...)` 를 덮어써 tween이 보이지 않는 현상 / 감별=tween 콜백은 실행되는데 화면에 변화 없음, `update()`에 breakpoint 걸면 tween 값 덮임 확인 / 해결=캐릭터와 독립된 오브젝트(`this.add.text(...)`)를 `objs`에 넣지 않고 별도 tween으로 처리 → `update()`가 그 오브젝트를 건드리지 않아 충돌 없음 / 예방=Phaser에서 매 프레임 속성을 직접 세팅하는 update() 루프가 있으면, 그 오브젝트에 직접 tween 금지 — 독립 오브젝트로 효과 분리할 것 / PR #475) |
| 2026-06-24 | T-086 (Docker 컨테이너가 수십 개 누적 — `bootRun`이 범인, 테스트 아님 / 증상=`docker ps -a`에 `booktimer-*`·랜덤이름 mysql 컨테이너가 워크트리 수만큼 쌓이고 일부는 검증 후 안 꺼진 Up 좀비·일부는 워크트리 삭제 후 Exited 고아 / 원인=`./gradlew test`는 H2라 컨테이너를 안 만든다(`spring.docker.compose.enabled=false`) — `bootRun`만 `spring-boot-docker-compose`로 `compose.yaml` MySQL을 자동 기동하고, compose 프로젝트명이 **워크트리 폴더명별로 갈려** 컨테이너가 따로 생기며 Spring은 stop만 하고 rm은 안 해 사라진 워크트리의 것이 고아로 남음 / 해결=`bash .claude/scripts/docker-cleanup.sh`(기본 Exited만, `--all` Up 포함, `--dry-run` 미리보기) — `com.docker.compose.project.working_dir` 라벨이 BookTimer 경로 계열인 것만 지워 타 프로젝트(다른 repo의 mysql 등)는 보호 / 식별 키=`docker inspect <컨테이너> --format '{{.Config.Labels}}'`의 `com.docker.compose.project.working_dir` / 예방=검증용 bootRun 종료 시 8080 반납과 함께 컨테이너도 내린다, 멀티세션 땐 Up 보존 위해 기본 모드 사용. CLAUDE.md 「🪢 다중 세션」·「🛠️ 빌드/실행 메모」 반영) |
| 2026-06-25 | T-088 (백그라운드 PR 머지 태스크를 띄워 놓고 완료 후속을 안 챙겨 머지 ~40분 방치 — `pr-merge.sh`가 DIRTY로 exit 3 후 "수동 rebase→재실행"으로 흐름이 끊기는데, force push만 하고 재실행을 잊으면 머지가 영영 안 됨 / 함정=스크립트의 "12분 하드 타임아웃"은 **스크립트가 도는 동안만** 유효 — exit 후엔 아무도 안 돌아 타임아웃조차 안 걸려 무한 방치로 보임 / 감별=PR이 아직 OPEN인데 백그라운드 태스크는 이미 종료(`exit≠0`이면 사람이 손대야 할 신호) / 해결 ① 호출자 규칙=백그라운드 머지 태스크 **완료 알림이 오면 반드시 output 파일을 읽어 exit 코드 확인 후 후속 처리**(0 머지완료→로컬 main 갱신 / 3 DIRTY·rebase 충돌→수동 / 4 CI 실패→원인 / 5 타임아웃→상태 확인). ② 스크립트=`pr-merge.sh <PR> --rebase`로 DIRTY 자동 rebase+force push 후 머지 폴링을 **한 호출 안에서** 이어가 흐름 끊김 자체를 제거(안전장치=현재 브랜치==PR head·워킹트리 clean) / 예방=머지를 백그라운드로 띄웠으면 "완료=확인" 한 쌍을 끝까지, 자동화 가능한 흐름 끊김은 opt-in 플래그로 메움 / T-083(DIRTY 헛폴링)·T-051(워크트리 로컬정리), PR #489) |
| 2026-06-25 | T-089 (반응형 재현 하니스의 mock 값이 production worst-case를 안 담으면 RED가 안 떠 레이아웃 버그를 놓친다 — 대시보드 타이머 2단 겹침 디버깅에서 static-preview mock이 `remainingSeconds=1200`("20:00", 5글자)라 실제 `01:43:47`(8글자 HH:MM:SS) 오버플로가 재현 안 돼 첫 측정이 "겹침 없음(-38px)"으로 나옴 / 원인=숫자·문자열의 폭/줄수처럼 길이에 비례하는 레이아웃은 **가장 긴 케이스에서만** 깨지는데 mock이 짧은 값이라 그 경계를 안 건드림(헤드리스 green = 가짜 green, T-053 부류) / 감별=element box rect는 컬럼 폭에 맞아 멀쩡해 보이니 텍스트 실폭은 `range.getBoundingClientRect()`/`scrollWidth`로 따로 잰다(block div는 overflow돼도 box는 안 늘어남) / 해결=재현 mock을 production 최악 케이스로 맞춘다(최대 글자수·최장 문자열·최다 항목) 후 RED 확인 → 수정 → GREEN / 예방=레이아웃 회귀를 static-preview로 잡을 땐 "이 화면에서 가장 넓어질 수 있는 콘텐츠가 뭔가"를 먼저 mock에 박는다 — N-055(완성 픽스처만 만들면 못 잡는다, null-state)의 시각/레이아웃판 / 개념 N-119(축 전환 함정·같은 디버깅), N-117(static-preview), T-043, PR #491) |
| 2026-06-25 | T-090 (Windows에서 preview `launch.json`으로 `gradlew bootRun`을 못 띄움 — `runtimeExecutable: "gradlew.bat"`(상대경로)은 "내부/외부 명령이 아님" 에러로 즉시 실패(preview가 PATH에서 찾고 cwd가 워크트리 루트라는 보장 없음), `cmd /c gradlew.bat`로 감싸도 cwd가 안 맞아 또 실패 / 해결=`runtimeExecutable: "cmd"` + `runtimeArgs: ["/c", "<절대경로>\\gradlew.bat", "-p", "<워크트리 절대경로>", "bootRun"]` — `.bat`은 cmd 셸 경유 필수 + 절대경로로 cwd 의존 제거 + `-p`(gradle `--project-dir`)로 프로젝트 디렉토리 고정 / 단 `bootRun`은 무겁다(빌드+MySQL Docker) — SSR fragment 로드순서(`<use>`+`<symbol>` 같은 문서 의존)처럼 정적 mock으론 못 잡는 검증에만 쓰고, 순수 로직·반응성은 N-117 static-preview 우선 / 검증 후 8080 반납·`docker stop/rm`으로 컨테이너 정리(T-086) / `.claude/launch.json`은 gitignore라 커밋 안 됨, 절대경로는 그 워크트리 전용이라 stale 무해 / N-117·T-078(gradle 데몬 경합)·T-086(Docker 누적)) |
| 2026-06-25 | T-091 (`pr-merge.sh`가 머지 성공 후 `git push origin --delete`(원격 브랜치 삭제)에서 hang → 백그라운드 머지 태스크가 안 끝남 / 증상=PR은 이미 **MERGED**인데 백그라운드 Bash 작업이 계속 "실행 중", 완료 알림조차 안 옴(`do_merge`가 `exit 0`에 도달 못 해 명령 끝의 `\| tail`이 프로세스 종료를 영원히 대기) / 감별=`gh pr view <PR> --json state`가 MERGED + 원격 브랜치는 **미삭제**로 남아 있고 + 머지 직후 시각에 시작된 `git` 좀비 프로세스가 잔존(`Get-Process git \| Select StartTime`) — `git status`는 빠름(레포·코어 정상, 매달린 건 자식 프로세스, T-078 진단과 동형) / 원인=`gh pr merge`(자체 토큰)는 됐지만 뒤이은 `git push origin --delete`가 비대화형 백그라운드에서 credential/원격 단계에 멈춤 / 해결=`TaskStop`으로 작업 종료 → 좀비 `git` PID `Stop-Process -Force` + `.git/index.lock` 정리 → 수동 `git push origin --delete <branch>` → 로컬 main 갱신 / 예방=`do_merge`의 push를 `timeout 30`으로 감싸 hang을 30s로 제한(실패해도 머지는 끝났으니 진행) + 백그라운드 머지가 시간 내 완료 알림 없으면 PR 상태부터 직접 확인(MERGED면 스크립트 hang 확정) / T-088(exit 후 후속 누락)의 자매 — 이번은 **exit 자체를 못 함**, T-083·T-078) |
| 2026-06-25 | T-092 (minified Vue 프로덕션 번들에서 마운트된 컴포넌트에 테스트 데이터를 주입하려는데 `component.setupState` 키가 안 보임 — preview 검증용으로 `el.__vue_app__._instance`/`setupState.data`를 건드리려 했으나 `_instance`가 null처럼 보이고 `setupState`는 `Object.keys`가 `[]`·`setupState.data` 접근은 null 반환(빌드 minify로 `<script setup>` 바인딩 키가 숨겨짐) / 증상=실제 화면은 정상 렌더되는데 내부 상태에 손이 안 닿음, dev 빌드 가정한 `__vueParentComponent` 등도 stale / 감별=`el._vnode.component`는 살아 있음(루트 instance), 거기서 `subTree`로 자식 vnode 트리를 탈 수 있음 / 해결=루트(`el._vnode.component`)부터 `subTree`를 BFS로 훑어 **목표 자식 컴포넌트**(예: `props.garden`을 가진 GardenPanel)를 찾고, 그 `inst.props.<객체>`(reactive 프록시·부모 ref와 같은 참조)를 직접 변이 → Vue가 재렌더(`el.__vue_app__`는 존재하나 그 경유는 막힘) / 주의=`props`는 읽기전용이라 **재할당(`inst.props.x = ...`)이 아니라 객체 내부 속성 변이**(`g.ownedCharacters = [...]`)로, 부모 reactive 객체를 직접 건드려 reactivity 발동 / 용도=시드 계정이 빈 데이터(작가 0명)라 스크롤·목록 UI를 못 키울 때 실 컴포넌트 reactivity 위에서 검증(헤드리스 정적 mock 아님) / 예방=Vue 섬(대시보드·정원·검색·기록 등) 다수라 데이터 주입 검증을 또 만남 — setupState 대신 트리 BFS+props 변이가 prod 빌드 정공법 / N-117(static-preview), N-082(reactive Proxy), T-090(bootRun preview)) |
| 2026-06-25 | T-093 (워크트리 세션에서 `npm run build`(전체 10섬)을 돌리면 무관 9개 번들이 `git status`엔 ` M`으로 뜨지만 실 내용 변경은 0 — `core.autocrlf=true`라 working tree LF↔CRLF 차이뿐 / 증상=history만 고쳤는데 garden·dashboard·books 등 9개 `.js`가 전부 modified로 보여 "안 건드린 번들이 왜?" 혼란, `git add -A` 하면 무관 번들까지 stage / 감별=`git diff --numstat <bundle>`이 행을 안 뱉으면(경고만) 내용차 0=CRLF뿐, 실 변경은 numstat에 `+/-` 줄수가 찍힘(예: history.js `5 5`, app.css `68 18`) / 원인=커밋된 블롭은 LF인데 워크트리 새 `node_modules`의 vite가 재생성하며 mtime·줄바꿈 상태가 바뀌어 status는 dirty로 보이나 git 정규화 후 동일 / 해결=실 변경 파일만 명시 stage(history.js·app.css·소스·docs), 무관 9개는 add 안 함 — 번들 훅 `require-bundle-build.ps1`은 `git diff --exit-code -- src/main/resources/static`(내용 비교)라 CRLF-only는 통과(전체 빌드가 커밋 번들을 결정적으로 재현하면 exit 0) / 예방=워크트리에서 프론트 PR 커밋 시 `git add -A` 금지·변경 파일만 명시 / N-117·T-082·T-063) |
| 2026-06-25 | T-094 (Windows에서 `timeout 30 git push origin --delete`도 hang을 못 막는다 — git push가 띄운 **자식 프로세스**(git-remote-https·credential helper)가 SIGTERM을 안 받아 `timeout`이 죽이려 해도 살아남고, 그 자식이 stdout 파이프를 쥐고 있어 `timeout`/스크립트가 exit를 못 함 → 백그라운드 머지 태스크가 **40분+ "출력 없음" 좀비**(T-091의 `timeout 30` 처방이 실제론 무효였음이 재발로 드러남, 사용자 40분 대기) / 증상=PR은 MERGED인데 백그라운드 Bash가 계속 running·완료 알림 없음·출력 0(파이프 풀버퍼링이라 hang 중엔 그간 찍은 로그도 flush 안 됨), `Get-Process git`에 머지 시각 시작된 git이 수십 분째 잔존하고 `Stop-Process`/`taskkill /F`도 "Access denied"(하네스 태스크 트리 소속이라 외부서 못 죽임 → 앱 재시작 시 정리) / 원인=`timeout`은 직계 자식만 신호, git의 손주(remote-https·credential)는 생존+파이프 점유 / **해결(정의적)=git push를 아예 안 쓴다 — `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`로 원격 ref를 HTTP 삭제**(gh는 자체 토큰·자식 프로세스 없음이라 hang 불가). `pr-merge.sh do_merge`를 이 방식으로 교체, gh 호출도 `timeout 120/30`으로 감쌈 / 좀비 응급=PR `MERGED`면 머지는 끝난 것 → `TaskStop`으로 태스크 종료(좀비 git은 무해, 앱 재시작 시 소멸), 원격 브랜치가 남았으면 `gh api -X DELETE .../git/refs/heads/<branch>`로 정리 / 잔여 위험=`--rebase` 경로의 `git push --force-with-lease`도 같은 hang 소지(gh API로는 force-push 대체 불가라 git 유지, 드물어 보류) / T-091(이 트랩의 1차 — `timeout 30` 미봉책)·T-088·T-078, gh API 삭제) |
| 2026-06-25 | T-095 (워크트리 세션에서 `gh pr merge --auto --squash --delete-branch`가 `failed to run git: fatal: 'main' is already used by worktree at '<주 워크트리>'`로 비정상 종료 — 단 **머지·auto-merge 등록 자체는 성공**하고 로컬 `--delete-branch` 단계만 깨진다 / 증상=명령이 에러로 끝나 "머지 실패"처럼 보이지만 `gh pr view <PR> --json state`는 `MERGED`(머지 시점에 CI 통과면 즉시 머지, BLOCKED였으면 auto-merge가 등록돼 나중에 머지) / 원인=`gh pr merge --delete-branch`는 머지 후 로컬 브랜치를 지우려고 기본 브랜치(main)로 `git checkout`을 시도하는데, 워크트리 세션에선 main이 **주 워크트리에 이미 체크아웃**돼 있어(한 브랜치=한 워크트리 규칙, N-032) gh의 그 로컬 git 단계가 거부됨 — 서버사이드 머지·`--auto` 등록은 그 전에 끝나 영향 없음 / 감별=`gh pr view <PR> --json state,autoMergeRequest`(state=MERGED면 성공) + `git ls-remote --heads origin <branch>`로 원격 브랜치 잔존 확인(에러로 원격까지 못 지웠을 수 있음) / 해결=① 머지 확인(state=MERGED) ② 원격 정리 `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`(T-094 — Windows `git push --delete` hang도 동시 회피) ③ 로컬 정리는 main 말고 그 워크트리의 **베이스 브랜치로** `git checkout <base>` 후 `git branch -D <branch>` / 예방=워크트리에서 머지할 땐 `--delete-branch`를 빼고 `gh pr merge <PR> --auto --squash`만 등록 → 머지 확인 후 원격=gh api·로컬=수동 정리 / T-094(같은 worktree×Windows에서 머지 자동화의 로컬 git 단계가 깨지는 계열 — 그쪽은 git 손주 hang, 이쪽은 main 체크아웃 충돌)·#509(auto-merge 우선 경로 명문화). **2회차(#511 → #515, 2026-06-25 /books PR-3) — 트래커 등재 + CLAUDE.md Git워크플로 auto-merge 절 caveat 승격 완료.**) |
| 2026-06-25 | T-096 (연쇄 PR에서 auto-merge 머지 폴링이 **미머지 종료**(`TIMEOUT`/`OPEN`/`DIRTY`)인데 머지 완료로 오인해 다음 PR 브랜치를 `origin/main` 기준으로 따 직전 PR 변경이 빠진 채 시작 — /books 시리즈에서 PR-1 머지 폴링이 `RESULT: TIMEOUT last=OPEN`(CI는 SUCCESS였으나 다른 세션 머지로 **DIRTY**라 머지 안 됨)인데 "머지됨"으로 읽고 `git checkout -b <pr2> origin/main` → origin/main에 PR-1이 없어 `BooksApp.vue`가 PR-1 이전(`.greeting` 한 줄)으로 되돌아감 / 증상=새 브랜치인데 직전 PR 변경이 안 보임("File modified since read" 알림으로 인지), 빌드·테스트는 통과해 **조용한 회귀** / 원인=폴링 종료를 "머지 완료"와 동일시 — `TIMEOUT`/`OPEN`/`DIRTY`는 미머지 종료인데 다음 단계가 그걸 머지 전제로 진행 / 해결=다음 단계 브랜치를 따기 전 **반드시 `gh pr view <PR> --json state`가 `MERGED`인지 확인**(폴링 exit·메시지가 아니라 PR 상태가 단일 진실), 아니면 즉시 `git checkout <직전브랜치>`로 복귀(미커밋이라 무해) → 머지 완료까지 대기 / DIRTY면 `git rebase origin/main`+`push --force-with-lease`로 해결 후 재머지(CLAUDE.md Git워크플로 5번) / 예방=연쇄 PR에서 "폴링 끝 ≠ 머지 완료", 머지 감시 폴링에 DIRTY 감지를 넣어 미머지를 일찍 분기 / N-032(워크트리·브랜치 격리)·T-083(DIRTY 헛폴링)) |
| 2026-06-26 | T-097 (Git Bash에서 멀티바이트 문자열(이모지·한글)을 `grep`/`sed` 패턴으로 쓰면 조용히 0건 매칭 → 일괄 치환이 통째로 누락 — 헤더 로고 📚→책 SVG 일괄 교체 때 `grep -rl '<span class="emoji">📚</span>' ... | while read f; do sed -i ...; done`이 grep 0건이라 while 루프가 한 번도 안 돌아 **치환 0건인데 에러 없이 성공처럼 종료** / 증상=치환 스크립트가 정상 종료하는데 파일은 그대로, "치환된 파일 0"·grep 카운트 0 / 원인=이 환경 Git Bash가 C 로케일이라 4바이트 이모지 등 멀티바이트를 패턴으로 신뢰성 있게 못 잡음(반면 **Grep 도구=ripgrep은 같은 패턴을 정상 매칭**해 대비로 진단됨) / 해결=일괄 치환은 PowerShell 리터럴 `.Contains`/`.Replace`로 우회(정규식 아닌 리터럴이라 SVG의 `/`·`"`·`$` 이스케이프 불필요), 쓰기는 BOM 없는 UTF-8(`New-Object System.Text.UTF8Encoding $false` + `[IO.File]::WriteAllText`)로 저장(PS5.1 `Set-Content -Encoding utf8`은 BOM 부착) / 검증=치환 전후 카운트는 **bash grep 말고 Grep 도구(ripgrep)**로 — 여기선 brand 📚 0·brand-ico 25 확인 / 예방=멀티바이트(이모지·한글) 검색·치환은 Grep 도구나 PowerShell로, bash `grep`/`sed`에 멀티바이트 패턴을 직접 넣지 말 것 / T-026·T-003(PowerShell 5.1 인코딩·멀티바이트 처리 함정 군)) |
| 2026-06-26 | T-098 (멀티세션이 전부 `claude-docs/changelog.md` **맨 아래에 행을 추가**해 항상 같은 위치가 충돌 → PR마다 rebase·force-push 왕복 — 2026-06-26 하루에만 #516·#518·#520·#523에서 반복(한 PR이 두 번 DIRTY 나기도) / 원인=CLAUDE.md가 "changelog 맨 아래 한 줄"을 강제하는데 append-only 로그라 서로 다른 새 행이 파일 끝 같은 hunk에 떨어져 git이 자동 병합 불가 → 멀티세션 활발기엔 구조적으로 불가피 / **해결(하드픽스)=`.gitattributes`에 `claude-docs/changelog.md merge=union`** — git 내장 union merge 드라이버가 충돌 시 양쪽 hunk를 마커 없이 **둘 다** 보존(append-only에 정확히 맞음, 중복 행 없음). 적용 후 changelog는 merge/rebase에서 충돌 안 나고 양쪽 새 행이 자동으로 합쳐짐(순서는 날짜대로는 아니어도 둘 다 들어감) / 주의=union은 **양쪽이 같은 행을 수정**하면 둘 다 남겨 중복 가능 — changelog는 각자 새 행만 추가라 안전, 본문을 동시 편집하는 파일엔 부적합 / 적용 시점=이 PR이 main에 들어간 **다음** rebase부터 효과(merge 시 양쪽 브랜치에 `.gitattributes`가 있어야 드라이버 작동) / 수동 해결 잔존 절차는 CLAUDE.md Git워크플로 5번(DIRTY→rebase) 그대로 / T-083(DIRTY 진단)·T-096(미머지 오인)) |
| 2026-06-26 | T-099 (전역 `button{border-radius:10px}`가 컴포넌트 버튼에 누수 — 책장 필터 세그먼트를 각지게(컨테이너 `border-radius:8px`+`overflow:hidden`) 만든 뒤 선택된 active 셀의 초록 하이라이트만 더 둥글어(10px) 겉 박스(8px)와 어긋남 / 원인=세그먼트화 때 `.filter-chip`의 명시적 `border-radius`(원래 `999px` pill)를 **제거**하자 전역 `button, .btn{border-radius:10px}`(app.css §buttons)가 셀에 그대로 상속 → active 셀 배경이 컨테이너보다 둥근 10px라 첫/끝 셀 active 시 코너에 카드배경 틈 / 감별=static-preview에서 active 셀 `getComputedStyle.borderTopLeftRadius`가 10px(컨테이너 8px와 불일치)·좌상단 곡선 안쪽 hit-test(`elementFromPoint(left+2,top+2)`)가 셀 아닌 컨테이너로 잡힘=초록이 코너 못 채움 / 해결=`.filter-chip`에 `border-radius:0` 명시(전역 10px 상쇄) → 셀 직각 + 컨테이너 `overflow:hidden`이 첫/끝 active를 8px로 클립해 겉 박스와 동심 일치(수정 후 activeRadius 0px·코너가 셀로 채워짐 실측) / 예방=전역 `button`의 속성(width·border-radius 등)을 칩·탭·세그먼트로 쓸 때 **명시값을 제거하면 전역값이 샌다** — 기존 명시값을 지울 땐 그 속성을 `0`/`auto`로 끄거나 의도값을 다시 박을 것 / **전역 button 누수 계열: width판은 T-056(`width:auto` 상쇄)·T-081·#286·#368, 이번은 radius판 — 트래커 「전역 button 속성 누수」 군 등재**, 같은 뿌리(전역 요소 셀렉터 속성이 자식 컴포넌트에 상속) / #523, N-118(CSS 침묵 드랍 계열)) |
| 2026-06-25 | T-105 (빈 워크트리 폴더가 `Device or resource busy`로 안 지워짐 — 죽은 Claude 세션의 도구 셸 좀비가 cwd로 점유 / 증상=`git worktree` 정리 후 `.claude/worktrees/<name>` 또는 형제 `BookTimer-*` 빈 폴더가 남아 `rm -rf`가 "Device or resource busy", 폴더 안엔 작업물 0(`.`·`..`만) / 원인=Claude Code의 Bash/PowerShell 도구가 그 워크트리에서 띄운 셸(`bash.exe`·`powershell.exe`)이 세션 종료 후에도 cwd를 그 폴더로 유지한 채 좀비로 남아 디렉토리를 점유 — **node/java가 아니라 셸 프로세스**라 `Get-Process node,java`로는 안 잡힘(실제로 한 폴더에 bash 10·powershell 1개가 남아 있었음) / 감별=`handle.exe`(Sysinternals) 있으면 `handle <path>`; 없으면 `NtQueryInformationProcess`(PEB→ProcessParameters→CurrentDirectory) C# P/Invoke로 전체 프로세스 cwd를 읽어 그 폴더를 cwd로 가진 PID만 식별 / 해결=cwd 검증된 그 PID만 `Stop-Process -Force` 후 폴더 삭제 — 살아있는 세션 셸(cwd=메인/타 워크트리)·gradle 데몬(cwd=`~/.gradle`)·타 프로젝트는 cwd가 달라 자동 제외 / 안전=PID 하드코딩 말고 삭제 직전 cwd 재검증(PID 재사용 방지), 대량 종료는 자동 분류기가 막을 수 있어 사용자 승인 필요 / 예방=세션 종료 시 도구 셸 정리, 워크트리 제거 전 그 폴더 기반 셸 종료. T-086(docker)과 같은 "세션 종료 후 자원 미정리" 계열) |
| 2026-06-26 | T-100 (워크트리 세션에서 프론트 vitest/vite 빌드가 `Could not resolve 'vite'`/`Cannot find package 'vite'`로 즉시 실패 — 워크트리에 `frontend/node_modules`가 없어서 / 증상=`npx vitest run`·`npm run build`가 `vite.config.ts` 로드 단계에서 vite·@vitejs/plugin-vue 미해결로 startup error, 소스·테스트는 멀쩡 / 원인=`git worktree add`는 **git 추적 파일만** 복제하고 `node_modules`(gitignore)는 안 만든다 → 워크트리 frontend는 의존성 0 / 함정=메인 `frontend/node_modules`를 junction(`New-Item -ItemType Junction`)으로 재사용하려 해도, 메인 node_modules가 **빈 디렉토리거나 빌드도구(vite·vitest) 미설치**면 무용 — 이번 사례는 메인 node_modules가 빈 껍데기라 `test -d`엔 EXISTS로 잡혀 "있다"고 오인(디렉토리 존재 ≠ 패키지 설치), junction을 걸어도 vite 미해결 동일 / 감별=`ls node_modules | wc -l`(0이면 빈 껍데기)·`test -d node_modules/vite`로 **핵심 패키지** 존재를 확인(디렉토리 유무가 아니라) / 해결=워크트리 frontend에서 `npm ci`(package-lock 기준 완전 설치); 메인도 비었으면 메인에서도 `npm ci`로 채움. junction은 메인이 **완전 설치돼 있을 때만** 빠른 재사용 가치, 제거는 `cmd //c rmdir <link>`(reparse point만 제거, `/S` 금지 — 타겟 보존; PowerShell `Remove-Item -Recurse`는 타겟까지 지울 위험) / 예방=워크트리에서 프론트 테스트/빌드 전 `test -d frontend/node_modules` + 핵심 패키지 확인, 없거나 빈 껍데기면 `npm ci` 먼저 — node_modules는 "디렉토리 존재"가 아니라 "vite 등 핵심 패키지 존재"로 판정 / N-032(워크트리 격리)·T-063·T-093(번들 빌드)) |
| 2026-06-26 | T-101 (content-hash 정적자산 인증 누수 — `spring.web.resources.chain`이 `@{/pwa-install.js}`를 `/pwa-install-<md5>.js`로 렌더하는데 SecurityConfig permitAll이 정확 경로 `/pwa-install.js`만 둬서 해시 URL이 `anyRequest().authenticated()`로 떨어짐 → 미인증 페이지 로드 시 302 redirect + `RequestCache`에 SavedRequest 저장 → 로그인 성공(`SavedRequestAwareAuthenticationSuccessHandler`)이 그 .js로 리다이렉트 → 대시보드 대신 깨진 랜딩 / `manifest.json`도 동일(`@{}` 참조), `/sw.js`는 JS 문자열(`register('/sw.js')`)이라 해시 안 됨·`/css/**`·`/icons/**`는 와일드카드라 안전 — 루트 단일 파일을 `@{}`로 참조하는 것만 정확매칭에 갇힘 / 해시 자산 max-age 365일이라 **캐시 빈 신규 세션에서만** 재현(실사용자 첫 로그인·Playwright fresh context 100%·캐시되면 안 남) → 기존 `PwaStaticAccessTest`가 `get("/manifest.json")` 정확경로만 단언해 은폐(N-055 변형판) / **표적 E2E 도입 첫 실행이 발견**: 로그인 setup이 `#dashboard-app` 미도달, 디버그로 최종 URL=`/pwa-install-<hash>.js?continue`(SavedRequest) 실측(로그인 자체는 302 성공) / 해결=permitAll `/pwa-install*.js`·`/manifest*.json` 와일드카드(해시 변형 포함, ant `*`는 세그먼트 내) / 회귀가드=`PwaStaticAccessTest`에 가짜 해시 `get("/pwa-install-deadbeef.js")`·`/manifest-deadbeef.json` 미인증 `not(302)` 단언(파일부재 404 무방, content-hash 활성 무관하게 인가만 검증) RED(SecurityConfig 원복 시 1건 FAILED)→GREEN / E2E도 RED(버그)→수정→GREEN 4 passed / 개념 N-126·N-108(resource chain 해시)·N-055, 인가매처 누락 N-070, PR feat/playwright-e2e) |
| 2026-06-26 | T-103 (rebuild-troubleshooting-toc.ps1가 ReadAllText+UTF8Encoding($false)로 원본 BOM 떨굼 → 첫 줄 phantom diff·매번 changed / 바이트로 BOM 감지+보존으로 수정 / 도구 재생성 시 인코딩 메타(BOM·EOL) 미보존 군 — T-093(CRLF)·T-057(BOM 추가)와 2회차, 트래커 등재) |
| 2026-06-26 | T-102 (auto-merge 등록 후 **직접 짠** 백그라운드 머지 워처가 `MERGED`/`CLOSED`만 보고 `DIRTY`를 안 봐서, 멀티세션 중 분기 직후 타 PR 머지로 생긴 충돌을 못 알리고 auto-merge가 침묵 정지 — 탐색→책방 PR #536을 `origin/main`에서 따 auto-merge 등록한 직후 다른 세션 #535(E2E)가 머지되며 `plan.md`·`changelog.md`가 겹쳐 PR이 DIRTY/CONFLICTING로 전환, 워처는 MERGED만 기다려 헛폴링·사용자가 먼저 충돌 발견 / 증상=PR이 OPEN인데 한참 안 머지됨, 워처는 계속 running / 감별=`gh pr view <PR> --json state,mergeStateStatus,mergeable` — state=OPEN+mergeStateStatus=`DIRTY`+mergeable=`CONFLICTING`이면 충돌(auto-merge 못 돎), `BLOCKED`면 단순 CI 대기(이 둘 구분 필수 — T-083) / 원인=auto-merge는 DIRTY면 머지 못 함 → 분기 후 타 PR이 같은 파일을 머지하면 충돌이 **사후** 발생하는데, 머지 감시 폴링이 MERGED/CLOSED만 분기하면 DIRTY를 영영 안 잡아 hang처럼 보임(T-083 DIRTY 진단·T-096 폴링 DIRTY 감지의 재발) / 해결=`git rebase origin/main`(changelog는 `merge=union`이라 양쪽 append 자동 병합 → 로컬 충돌 0, GitHub만 union 미적용으로 DIRTY 표시였음, T-098) → 검증(내 커밋만·내 파일만·양쪽 docs 둘 다 보존) → `git push --force-with-lease` → PR MERGEABLE 복귀(auto-merge 등록은 force-push에도 유지돼 CI 통과 시 재개) / **정답=손수 워처를 짜지 말 것 — `.claude/scripts/pr-merge.sh <PR>`가 이미 DIRTY 즉시 차단 + CI 폴링 + 하드 타임아웃(동기 머지)을 제공한다. 굳이 워처가 필요하면 MERGED/CLOSED뿐 아니라 `mergeStateStatus==DIRTY` 분기를 반드시 넣어 재충돌을 일찍 알린다** / 예방=auto-merge "등록=끝"이 아니다 — 멀티세션 활발기엔 분기 후 충돌이 사후 발생하니 머지까지 DIRTY를 감시하거나 pr-merge.sh로 동기 머지 / 머지 자동화 hang·DIRTY-blind 군 5회차 — 이번 뿌리는 "하드픽스(pr-merge.sh)가 있는데 안 쓰고 워처를 손수 짬" / T-083·T-096·T-098·T-094) |
| 2026-06-26 | T-104 (squash 머지가 브랜치 커밋 trailer(`Session-Model`/`Effort`)를 메시지 중간으로 밀어 `git %(trailers)` 구조 조회가 빈 값 — git trailer 파서는 **맨 끝 문단 1개**만 인식, GitHub squash가 `Co-authored-by`를 `---------` 구분선과 함께 맨 끝 블록으로 붙여 `Session-*`가 중간으로 밀림 / 마지막 블록에 평문·구분선 한 줄만 섞여도 25% 임계로 그 블록 trailer 동반 탈락 / 값은 보존(`git log --grep`으로 잡힘)·조회만 깨짐 → 우회=grep(`%B|grep -oP`), 근본=마지막 trailer 블록에 합류 / 1회차 신규 / N-128·#543) |
| 2026-06-27 | T-106 (auto-merge `--delete-branch`는 비동기 머지라 원격 브랜치가 안 지워진다 — `gh pr merge <PR> --auto --squash --delete-branch`로 머지(MERGED) 후에도 원격 브랜치가 `git ls-remote`에 잔존, 주 워크트리에서도 발생 / 원인=`--auto`는 CI 통과 후 나중에 서버사이드로 머지(비동기)하는데 `--delete-branch`의 삭제는 gh CLI가 머지 직후 로컬에서 처리 → 등록 시점엔 머지 전이라 못 지우고, 서버 머지 땐 gh가 이미 끝나 누락 / 해결=MERGED 확인 후 `gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/<branch>`(T-094, Windows push hang 회피)·로컬 `git branch -D` / 근본=repo "Automatically delete head branches" 설정 / 1회차 신규, T-095(--delete-branch 정리 실패) 계열·T-094) |
| 2026-06-27 | T-108 (`gradlew.bat` phantom-modified로 `git rebase`가 `cannot rebase: You have unstaged changes`로 막힘 — `.gitattributes`의 `*.bat text eol=crlf`와 #560 gradle-wrapper bump가 비정규 EOL로 커밋한 블롭이 불일치해 영구 modified, `checkout`·`--autostash`로 안 풀림 / 우회=`git update-index --assume-unchanged gradlew.bat` 후 rebase(replay 커밋이 그 파일 미변경 시 안전), 근본=`git add --renormalize`를 별도 PR로 / EOL·인코딩 phantom diff 군 T-093·T-103·T-057, 1회차) |
| 2026-06-27 | T-109 (vitest `test.include`가 `test/**`만 잡아 `src/` 곁 단위 테스트(`timerProgress.test.ts` 등 4파일·105개)가 silent 미실행 — npm run test 목록에 안 뜨고 깨져도 green처럼, CLI 경로 인자는 include 교집합이라 "No test files found" / 해결=include에 `src/**/*.{test,spec}.ts` 추가(E2E는 `e2e/`라 무충돌), 부활 후 전부 green / 예방=테스트 추가 시 실행 카운트 증가 확인, 1회차) |
| 2026-06-27 | T-107 (`git add`와 `git commit`을 한 Bash 명령으로 묶으면 PreToolUse 자동수정 훅이 skip된다 — `git add <file> && git commit`처럼 묶으면 목차 자동생성 훅(require-troubleshooting-toc)이 안 돌아 목차 갱신 누락(본문 헤딩만 추가되고 목차 줄 빠짐), add/commit 분리하면 정상 / 원인=훅이 PreToolUse(명령 실행 *전*)로 commit을 가로채 `git diff --cached`를 보는데, 묶음 명령은 그 시점에 아직 add 전이라 스테이징이 비어 skip → 곧 add+commit이 한꺼번에 실행돼 끼어들 틈 없음(`;`로 묶어도 동일) / 해결=`git add`를 별도 호출로 먼저, 그다음 `git commit` 단독 호출 / 보정=스크립트 수동실행→add→`git commit --amend` / 주의=번들(require-bundle-build)·테스트게이트(require-tests-before-commit)도 같은 식 무력화 소지(테스트 skip되면 위험) / 1회차 신규, T-106에서 실제 당함) |
| 2026-06-27 | T-110 (정션 둔 워크트리를 `git worktree remove --force`하면 정션을 따라가 타깃(main node_modules) **내용**을 삭제 — 폴더는 남고 패키지 0개, #567에서 137→0이던 정체 / 격리 재현으로 확정(임시 repo·`.gitignore` node_modules 미추적·더미 keep.txt 소멸) / 해결=worktree remove **전에** `[IO.Directory]::Delete()`로 정션만 끊기(`Remove-Item -Recurse`는 타깃까지 지워 금물), #569에서 먼저 끊어 137 보존 대조입증 / 예방=정리순서 "정션 끊기→remove" 고정(CLAUDE.md 반영), 링크 품은 폴더 재귀삭제는 타깃 건드림 / N-132 정션 워크플로, 1회차 신규) |
| 2026-06-27 | T-111 ("머지 전 브랜치 최신화 필수" 정책 + BEHIND인 PR에 `--auto`만 걸면 무한 대기 — GitHub가 BEHIND 브랜치를 자동 갱신 안 함(auto-update off), `--auto`는 자기해결 안 되는 BEHIND 조건을 영영 기다림, DIRTY 아님이라 기존 rebase 경로에도 안 잡히고 `pr-merge.sh`는 catch-all로 타임아웃까지 대기만 / 증상=체크 통과인데 OPEN·"out-of-date with base" 배너·`mergeStateStatus=BEHIND` / 해결=`gh pr update-branch <PR>`(비파괴 서버사이드 갱신)→CI 재실행→`--auto` 머지 / 하드픽스=`pr-merge.sh`에 BEHIND `try_update_branch`(폴링·`--arm` 양쪽)+`--arm` 걸고떠나기 모드, 표준 경로 승격·bare `--auto` 금지(CLAUDE.md) / 스모크 `.claude/scripts/tests/test-pr-merge-behind.sh` / 머지 자동화 hang 군 T-083·T-091·T-094·T-102의 6회차) |
| 2026-06-28 | T-112 (Chrome MCP `resize_window`가 렌더(layout) 뷰포트를 못 바꿔 `window.innerWidth`가 1920 고정 → 모바일 미디어쿼리(`@media max-width:599px`) 검증 봉착 — 창을 430px로 줄여도 데스크톱 렌더만 나와 모바일 전용 CSS(서술 내부 스크롤 등) 적용·스크롤을 확인 못 함 / 원인=resize_window가 OS 창 크기만 바꾸고 콘텐츠 렌더 뷰포트는 고정, 미디어쿼리는 layout viewport를 봄 / 해결=폭 N px(예 390) iframe에 같은 페이지를 src로 로드 — 미디어쿼리는 iframe 자체 뷰포트를 보므로 모바일 규칙 실발동, 같은 오리진이면 `contentWindow`/`contentDocument`로 `getComputedStyle`·`matchMedia`·`scrollHeight>clientHeight`·`scrollTop` 측정, 부모(1920)+iframe(390) 동시 측정으로 데스크톱·모바일 대조, 스크린샷은 iframe `position:fixed`로 캡처 / 예방=모바일 한정 반응형 CSS는 이 iframe 기법을 게이트로, static-preview(N-117)와 같은 오리진이라 자연 결합 / N-117·N-118·T-053·T-089, 책BTI 모바일 검증 2회 봉착에 확립) |
| 2026-06-28 | T-113 (도메인 TLD 이전 후 `www.<신규>`를 ALB 301 규칙에서 누락 → 검색 유입자(구글이 canonical 미설정으로 www 색인·1위)가 `www.app`에 닿아 redirect_uri가 www로 동적 생성돼 `redirect_uri_mismatch` + host-only 세션 쿠키 분리로 비로그인 랜딩, 주소창 apex 직접진입은 정상 / 해결=ALB 우선순위1 규칙 호스트조건에 `www.booktimer.app`을 OR 값으로 추가(새 조건 추가는 AND라 .click 301 파손—금물) / 예방=호스트 변형 전수 등록 + canonical·www→apex 정규화 / N-138, 1회차 신규) |
| 2026-06-29 | T-114 (preview_inspect가 border-radius·padding 등 shorthand CSS를 빈 객체 `{}`로 반환 — 같은 호출의 display·background-color·width 등 longhand/단일값은 정상 / 해결=longhand로 요청(border-top-left-radius·padding-top)하거나 preview_eval로 getComputedStyle 직접 읽기(shorthand 정확), 여러 요소 IIFE pick으로 1콜 / 랜딩 디자인 토큰 통일(버튼 8px·카드 14px/24px) 검증 때 발생, T-112와 같은 preview 도구 검증 한계 결, 1회차 신규) |
| 2026-06-29 | T-115 (워크트리에서 작업한 세션이 그 워크트리를 직접 정리하면 최상위 빈 폴더가 안 지워짐 — 세션이 폴더를 cwd로 점유(harness가 매 호출 후 워크트리로 cwd reset)해 `git worktree remove`가 Permission denied/Device or resource busy, worktree list·메타·내부파일은 정리되나 빈 폴더 잔존 / 해결=정션 끊기+`worktree prune`+`branch -D`로 실질 정리하고 빈 폴더는 세션 종료 후 `rmdir`(다음 SessionStart가 치우기도), 또는 정리를 메인·다른 세션에서 / T-105 재발 2회차 cwd-점유 군) |
| 2026-06-29 | T-116 (순수 마크업/CSS 변경이라 '단위 TDD 무의미'라 보고 preview만 했는데 기존 통합 테스트(@SpringBootTest MockMvc)가 CI에서 RED — LandingPageTest가 렌더 HTML의 /village 링크를 containsString으로 검증 중이라 #village 앵커 전환에 깨짐 / 해결=마크업·링크·문구 변경 전 그 문자열·경로·뷰명을 검증하는 기존 테스트 grep, 의도된 변경이면 새 동작에 맞게 갱신하고 변경 전 로컬 RED 먼저 확인 / 1회차 신규) |
| 2026-06-29 | T-118 (CSS `rotate(90deg)` 으로 컨테이너 회전 시 Phaser `InputManager.transformPointer` 의 `_sx/_sy` 스케일 팩터가 잘못된 좌표를 계산 — 내부 `_sx = canvas.width / canvasBounds.width` 인데, 회전 후 `canvasBounds` 는 시각적(portrait) 치수를 반환하고 `canvas.width` 는 landscape 치수라 팩터가 틀린 배율(예: 812/375≈2.17)로 계산됨 / 증상=portrait 강제 가로 회전 후 터치 포인터가 실제 탭 위치와 크게 어긋남 / 해결=`transformPointer` 몽키패치 — `_sx/_sy` 를 우회하고 `scaleManager.canvasBounds`에서 직접 역좌표 수식 `canvas_x = bounds.height - relY, canvas_y = relX` 를 계산(N-140). 패치는 게임 인스턴스 생성 직후 호출, landscape나 데스크탑 진입엔 origFn으로 fall-through / 관련: N-140(역좌표 수식 도출), N-082(Phaser Game은 Vue ref 금지)) |
| 2026-06-29 | T-117 (공유 Vue 컴포넌트에 `<style scoped>` 추가 → vite가 페이지가 `<link>` 안 하는 별도 `<bundle>.css`(search.css) 생성, 칩이 무스타일로 뜸 — 헤드리스 vitest는 CSS 불요라 통과해 못 잡고 실 페이지에서만 드러남 / 원인=이 프로젝트 Vue 섬은 컴포넌트 `<style>` 없이 전역 app.css 클래스만 쓰는 패턴 / 해결=공유 컴포넌트 스타일은 전역 app.css 클래스로(`.book-status-badge` pill 레시피 재사용), orphan css 삭제+재빌드해 산출물 JS만 / 페이지별 `<link>`는 공유 컴포넌트엔 누락 위험 / T-063·T-082 번들 군·N-083, 친구 추천 칩 작업, 1회차 신규) |
| 2026-06-30 | T-119 (PowerShell→`docker exec mysql -e` 한글 INSERT가 mojibake로 저장돼 도감 매칭 실패 — `HEX`로 진단, `UNHEX('ED959C…')`로 정확한 UTF-8 바이트 직접 주입(또는 UTF-8 파일·UI), T-026·T-044 PowerShell→native exe 인자 인코딩 군, 검증 데이터 셋업 한정, 1회차) |
| 2026-06-30 | T-120 (rebase로 sibling PR의 새 `.java` 테스트가 들어온 뒤 `git commit --amend`가 비-`.java`(번들 `.js`)만 스테이징하면 `require-tests-before-commit` 게이트가 스킵돼 컴파일 에러가 CI까지 샌다 — 건물 은퇴 PR이 `GardenView`를 6→3-arg로 줄였는데 같은 시각 머지된 #610의 새 `ProfileCharacterServiceTest`가 옛 6-arg로 `GardenView` 생성, rebase가 그 테스트를 가져왔으나 amend 스테이징 델타엔 `dashboard.js`만 있어 훅이 `gradlew test`를 안 돎(훅은 staged `.java` 있을 때만 발동) → 로컬 GREEN인데 CI `compileTestJava` FAILED(1m6s 조기 실패) / 감별=CI 로그 `constructor X cannot be applied to given types`가 **내가 안 건드린 파일**에서 나면 rebase가 가져온 sibling 변경이 내 시그니처 변경과 충돌 / 해결=그 파일을 새 시그니처로 고치고 **`.java` 포함해** 다시 amend(이번엔 게이트 발동·GREEN)→force-push / 예방=**rebase가 sibling PR 변경을 끌어온 뒤엔 push 전 `./gradlew test`(최소 `compileTestJava`) 1회 수동** — 훅의 staged-delta 휴리스틱은 rebase가 가져온 파일을 못 봄(스테이징에 없음). 타입 시그니처(생성자·필드)를 바꾸는 PR일수록 sibling이 그 타입을 새로 쓰면 머지 시점에야 충돌 표출 / 1회차 신규, T-096(폴링≠머지)·T-107(묶음 명령이 훅 무력화) 같은 '게이트 우회' 계열) |
| 2026-06-30 | T-107 **2회차** (`require-bundle-build`에서 묶음 명령 함정 실증 — `git add -A && git commit`을 한 Bash 명령으로 묶으면 PreToolUse 번들 훅이 명령 실행 *전* 발동→그 시점 인덱스가 직전 시도의 구버전 산출물→훅이 새로 빌드한 워킹트리 `garden.js`와 어긋나 `Bundle is stale`로 오탐 BLOCK 3회 연속. `git add`를 별도 호출로 분리하니 인덱스=워킹트리로 통과. 1회차(T-107)는 목차훅 **skip**(무력화)이었고 이번은 번들훅 **오탐 BLOCK** — 증상은 반대지만 근본 동일(PreToolUse는 명령 문자열 실행 전 1회만 가로챔). 이번엔 `npm ci`로 node_modules가 갈려 구버전 산출물과 새 빌드 차이가 또렷이 드러남(결정적 빌드여도 묶음이면 동일 함정) / **2회+ → 하드픽스 승격 후보**: 훅이 명령 문자열에 `git add`+`git commit` 동시 포함을 감지하면 분리 안내. PreToolUse 게이트 걸린 커밋은 add·commit 분리가 정답) |
| 2026-07-01 | T-121 (WinRT 토스트가 미등록 AppUserModelID면 `.Show()`가 예외 없이 성공해도 화면에 안 뜸(조용히 드랍) — 등록 시스템 AppID `Microsoft.Windows.Explorer` 사용(실측 확인), 집중지원/`ToastEnabled`도 점검. B1 확인-대기 알림 훅 #621, PowerShell↔Windows API 함정, 1회차 신규) |
| 2026-07-01 | T-123 (커스텀 `display:flex`를 준 요소를 JS `el.hidden`으로 토글해도 author가 UA `[hidden]{display:none}`을 origin 우선으로 이겨 안 숨겨짐 — `/settings` 재스킨의 iOS/복귀 힌트가 화면에 샘 / 해결=`.settings-page [hidden]{display:none!important}` 재숨김 리셋 / 감별=실 브라우저 `getComputedStyle(el).display!=='none'`, MockMvc·헤드리스 못 잡음 / **T-035 재발 3회차**(#189→T-035→이 건), 전역 `[hidden]` 리셋 하드픽스가 다음 승격, N-083) |
| 2026-07-01 | T-122 (타임아웃/hang 수정의 RED 테스트는 하니스를 outer `timeout`으로 안 감싸면 테스트가 세션째 hang — 가짜 느린 자식을 **exit 0**으로 두어 RED=오래 기다린 뒤 통과(=허용, 잘못)·GREEN=타임아웃 차단(exit 2)로 종료코드로 가름. A1 게이트 타임아웃 테스트 #620·N-143, 1회차 신규) |
| 2026-07-02 | T-124 (`npm install`(무인자)이 vite dist(`cli.js`)를 불완전하게 남겨 빌드 `ERR_MODULE_NOT_FOUND` → `npm ci`로 클린 복구, 워크트리 정션 공유 트리, Yes24 PR #625, 1회차 신규) |
| 2026-07-02 | T-125 (Thymeleaf `th:field` 체크박스가 자동 삽입하는 hidden sibling(`_필드명`)이 CSS 인접 형제 선택자 `input:checked + .box`의 사슬을 끊어 값은 저장되는데 커스텀 체크 시각만 영원히 안 바뀜 → `+`를 `~`(일반 형제)로 교체. `/settings` debtCarryover 체크박스, 실사용 피드백, PR #629, 1회차 신규) |
| 2026-07-02 | T-126 (검증 명령 파이프가 exit code 가림 — pipefail·PIPESTATUS 판정, 가짜 GREEN) |
| 2026-07-02 | T-127 (크롬 확장 네트워크 로그 간헐 503 = SW pass-through 표시 아티팩트 — 페이지 실측·서버 로그·DB 3중 교차 검증) |
| 2026-07-02 | T-085 보강 (docker exec mysql 한글 군 3회차: T-085→T-119→이번 — UTF-8 파일 stdin 파이프 + --default-character-set + HEX 검증, 트래커 등재) |
| 2026-07-02 | T-128 (Yes24 링크프라이스 딥링크, 모바일 UA면 Yes24 자체 게이트 `lpfront.aspx`가 목적지를 m.yes24 메인으로 치환 — tu에 모바일 URL을 넣어도 우회 불가, 해결=모바일 UA면 래퍼 없이 m.yes24.com/search 직행, 운영 curl 실측, 1회차 신규) |
| 2026-07-03 | T-129 (쿠팡 파트너스 "구매" 링크 추적 0 — CoupangLinkBuilder가 정식 추적링크 아닌 자작 lptag 검색 URL을 302로 보냄, 쿠팡 추적은 딥링크 API/간편링크 생성·`isshortened=Y` 전제라 미집계, 본인구매 0은 별개(자가구매 제외), 해법=딥링크 API 연동 계획, 1회차 신규) |
| 2026-07-04 | T-130 (dark-launch 기능 secret을 task-def valueFrom으로 배선하면, 앱 기본값(application.properties)이 있어도 SSM 파라미터 미생성 시 ECS 배포가 서킷브레이커 롤백 — valueFrom이 파라미터 존재를 하드 강제해 태스크 기동 실패. 해결=미점등 기능 secret은 task-def에서 빼거나 placeholder 파라미터 먼저 생성, PR #642로 secret 3줄 제거해 언블록. 1회차 신규) |
| 2026-07-04 | T-131 (알라딘 제휴 클릭 추적 0 — OpenAPI 요청에 includeKey=1 미전송(기본 0)이라 응답 link에 TTBKey 안 실림, 저장·302 재생 구매링크가 무추적, 운영 실측 2건(partner=openAPI&start=api·ttbkey 부재)으로 확정, 해법=includeKey=1 추가+기존 저장분 백필, PR #644, **2회차=T-129 쿠팡 재발·"어필리에이트 추적 무성실패" 트래커 등재**) |
| 2026-07-05 | T-132 (dependabot 프론트 dep bump 중 **런타임 번들에 인라인되는 것**(vue 등)은 lockfile만 올려 CI `Verify bundle is not stale`가 RED — vue 런타임은 각 섬 번들 JS에 인라인되는데(`@vue/* 버전 마커`가 `static/**/*.js`에 박힘) dependabot은 `src/main/resources/static/**` 번들을 재빌드·커밋 안 해 lock↔아티팩트 드리프트로 stale 게이트가 실패 / 감별=CI에서 test·build는 GREEN인데 "Verify bundle is not stale"만 RED이고 PR diff가 `package-lock.json`뿐이면 코드 회귀가 아니라 번들 미재빌드 / 해결=PR 브랜치 체크아웃→`npm ci`→`npm run build`→재생성 번들 커밋·push→머지(번들이 새 버전 반영해 stale 해소) / **대조**: vitest(테스트러너)·@vitejs/plugin-vue(빌드타임 SFC 컴파일러)는 서빙 JS에 안 실려 재빌드 없이 통과 — "그 major dep이 런타임 번들에 들어가나"가 stale 실패 여부를 가름(vue=인라인→RED / vitest·plugin-vue=미포함→GREEN). phaser처럼 죽은 dep도 미포함이라 통과하되 bump 무의미→제거(#656) / 2026-07-05 dependabot 프론트 PR 정리 #649(vue), T-063·T-082 번들 군·N-083, 1회차 신규) |
| 2026-07-06 | T-133 (스크립트가 읽는 비밀 파일 경로가 `.gitignore` 무시 경로·문서와 어긋나 조용히 실패 or 비밀 커밋 — `affiliate-report.mjs` `readAuthKey`가 `resolve(here,'.secrets')`로 `scripts/.secrets`를 잡아 `.claude/.secrets`(gitignore·문서)와 불일치. 해결=`secretPathFor` 순수함수+`..`로 상위 `.claude/.secrets`, gitignore 위치 일치를 단위테스트로 못박음. PR #660, 1회차 신규) |
| 2026-07-06 | T-134 (외부 API의 "에러처럼 생긴" result 코드가 실은 정상 무데이터일 수 있다 — LinkPrice `result=101`("정상 page 번호 아님")이 실적 0건이면 반환할 page 없어서 온 코드였는데 `result!='0'`을 전부 fatal 처리해 무데이터마다 가짜 오류. 해결=`classifyResult` 3분류(0=ok·101=no-data·나머지=error), 양방향 불변식 테스트. `test=Y`가 정상이면 인증 OK→"데이터 상태 코드" 의심, 벤더 코드표 확인. PR #661, 1회차 신규, 자매 T-133) |
| 2026-07-10 | T-135 (`preview_screenshot`이 `readyState=complete`·`getAnimations()=0`인데도 30초 캡처 타임아웃 반복(`snapshot`·`inspect`·`eval`은 정상) — 이 환경 캡처 파이프라인 한계. `app.css` Google Fonts `@import`를 serve.js에서 스트립해도 안 풀림(폰트는 부분요인). 우회=`inspect`/`eval`로 값 실측(`getComputedStyle(el,'::before')`·`scrollHeight>clientHeight` clip 판정) + `show_widget` 비교 위젯으로 사용자 시각 선택. 홈 명언 하이브리드 PR #678, 자매 T-112·T-114, 1회차 신규) |
| 2026-07-28 | T-136 (**Windows에서 AWS CLI v2가 로케일을 cp949로 고정 — 출력·입력 양쪽이 깨진다** / 증상 ①: 명령은 서버에서 `Success`인데 결과를 읽을 때 `'cp949' codec can't encode character '\u2014'`로 CLI가 죽어 **출력을 못 본다**(em-dash·한글 섞인 SSM 로그 등). 증상 ②: `--cli-input-json file://...`의 파일에 한글이 있으면 `text contents could not be decoded`로 **입력 자체가 거부**된다. / 원인: AWS CLI v2 번들 Python이 시스템 로케일(한국어 Windows=cp949)로 stdout 인코딩·파일 디코딩을 결정한다. **`PYTHONIOENCODING`·`chcp 65001`·`[Console]::OutputEncoding` 전부 안 먹혔다**(3종 실측 실패) — PowerShell에선 우회가 없었다. / 해결: **Git Bash에서 `PYTHONUTF8=1 LC_ALL=C.UTF-8 LANG=C.UTF-8`** 로 실행하면 출력이 정상 디코딩된다. 입력 JSON은 **ASCII로만 작성**(comment 필드에 한글 금지). ⚠️ Git Bash라도 `file:///c/...`는 못 읽는다 — AWS CLI가 Windows 네이티브라 `file://C:/...` 형식이어야 한다. / 부수: PowerShell 5.1이 native exe 인자의 큰따옴표를 벗겨내 `--parameters '{"commands":[...]}'`도 깨진다 → **`--cli-input-json file://`(파일 경유)이 유일하게 안정적**. 글로벌 「Windows 셸 한글·인코딩 원칙」 2번의 AWS CLI판. ECS→EC2 이전(PR #689 후속), 1회차 신규) |
| 2026-07-28 | T-137 (**DB 이관 시 `mysqldump`는 계정을 안 옮긴다 — 앱이 DB 접속 실패로 기동 불가, 게다가 blue-green 안전망을 스스로 무력화해 수 분 503** / 증상: RDS→로컬 MySQL 덤프·임포트가 전부 성공하고 행 수도 정확히 일치(users 18·book 45·reading_session 361)하는데, 앱을 로컬 DB로 전환하니 헬스체크 실패로 기동 못 함. / 원인 ①: **`mysqldump`는 스키마·데이터만 옮기고 `mysql.user`(계정)은 옮기지 않는다.** RDS의 앱 계정 `admin`이 새 MySQL에 없어 접속 거부 → Hikari fail-fast → 컨테이너 기동 실패. 덤프·임포트가 "성공"이라 데이터만 보면 정상으로 보이는 게 함정. / 원인 ②(피해 확대): 환경변수 재적용을 확실히 하려고 **재배포 전에 기존 컨테이너를 `docker compose rm -f` 해버렸다.** blue-green의 "헬스 실패 시 옛 컨테이너 유지" 안전망은 옛 컨테이너가 있어야 작동하는데, 미리 지워 되돌아갈 대상이 없어져 **부분 실패가 전면 다운(503)으로 확대**됐다. / 해결: 임포트 후 `CREATE USER '<앱계정>'@'%' IDENTIFIED BY '<pw>'; GRANT ALL ON <db>.* TO ...; FLUSH PRIVILEGES;` 를 반드시 수행하고, **검증은 앱 자격증명으로 실제 조회**(`mysql -u<앱계정> -p<pw> -e "SELECT COUNT(*) ..."`)까지 해야 한다 — root로만 확인하면 이 결함을 못 잡는다. 재배포 전 컨테이너는 지우지 않는다(환경변수 변경은 compose가 알아서 재생성). / 부수: MySQL 8.4는 `caching_sha2_password`가 기본이라 JDBC URL에 `allowPublicKeyRetrieval=true&useSSL=false`가 필요할 수 있다(RDS URL엔 원래 있었는데 새로 쓰며 빠뜨릴 뻔함). ECS→EC2 DB 이관, 1회차 신규) |
| 2026-08-03 | T-138 (**일일 MySQL 백업 cron이 엿새 동안 0건 — 결함 3개가 겹쳐 잡이 실행조차 안 되고 실패 로그조차 안 남았다** / 증상: 구 리소스 정리 전 백업 확인차 `aws s3 ls s3://booktimer-ops-<acct>/mysql/` 했더니 **객체 0건**. cronie 설치됨·crond active·`/etc/cron.d/booktimer-backup` 등록 정상인데 산출물만 없고, 지정한 로그 `/var/log/booktimer-backup.log`조차 **파일 자체가 없어** 실패 흔적이 0이었다. / 원인(3중): ① cron 잡을 `ec2-user`로 돌렸는데 리다이렉트 대상 `/var/log/`가 root 전용 → **셸이 리다이렉트에서 죽어 스크립트가 실행조차 안 됨**(그래서 로그가 아예 안 생김 = 무성 실패의 정체). ② `.env`는 `render-env.sh`가 `install -m 600`으로 root:600 생성 → ec2-user가 `MYSQL_ROOT_PASSWORD`를 못 읽음. ③ `backup-mysql.sh`가 `BUCKET="${BACKUP_BUCKET:?...}"`로 환경변수를 필수 요구하는데 cron 환경엔 아무 변수도 안 실리고 `render-env.sh`도 `BACKUP_BUCKET`을 만들지 않음 → 그 줄에서 즉시 종료. **어느 하나만 고쳐도 나머지에서 또 죽는 구조**라 단일 원인 추적으론 못 끝낸다. / 해결: cron 실행 주체를 `root`로(①②를 한 단어로 동시 해소 — 배포 경로인 SSM Send-Command도 root라 `/opt/booktimer` 소유권이 이미 root에 맞춰져 있었다) + 버킷을 `booktimer-ops-$(aws sts get-caller-identity ...)`로 유도해 환경변수 의존 제거(명시 `BACKUP_BUCKET`은 오버라이드로 보존). ⚠️ 인스턴스의 `/etc/cron.d/booktimer-backup`은 `bootstrap-ec2.sh`가 최초 1회만 쓰므로 **repo 수정만으론 안 바뀐다** → SSM으로 별도 적용해야 한다. / 재발방지: `deploy/tests/test-backup-mysql.sh`(8단언 — cron이 root인지 정적 검사 + `BACKUP_DRYRUN=1`로 버킷 유도·오버라이드 검증) + 실인스턴스에서 백업 1건 실행해 S3 객체·gzip 무결성까지 확인. **교훈은 "고쳤다"가 아니라 "산출물을 봤다"** — 배선(cron 등록·스크립트 존재·데몬 active)이 전부 정상으로 보여도 산출물은 0건일 수 있다. `deploy.yml`의 `Verify live health`가 배포에 대해 이 클래스를 막으려 만든 스텝인데 백업엔 대응물이 없었다. 무성 실패 군(T-129·T-131) 3회차. 2026-08-03 구 리소스 정리 착수 중 발견) |
| 2026-08-06 | T-139 (**T-136 우회 셸(`MSYS_NO_PATHCONV=1`)이 같은 셸의 다른 native 도구까지 오염 — curl 쿠키 파일이 `/tmp` 경로에서 무성 실패해 운영 장애로 오진** / 증상: 운영 로그인 검증 curl이 CSRF 403 반복. GET은 200이고 `Set-Cookie`도 실제로 오는데 쿠키 jar 파일(`-c /tmp/x.txt`)이 **생성 자체가 안 됨** → `-b`가 빈 손으로 POST → 세션 없는 CSRF 토큰이라 403. "GET이 Set-Cookie를 안 준다"로 보여 **서버 결함(세션 쿠키 유실)으로 오진**하고 진단 3라운드를 태움 — 배포 직후였다면 롤백까지 갈 뻔한 가짜 신호. / 원인: AWS CLI T-136 우회로 셸에 export한 `MSYS_NO_PATHCONV=1`이 **curl 등 native 도구의 `/tmp/...` 경로 변환까지 꺼서**, native curl이 그 경로를 못 열어 jar 쓰기를 조용히 건너뜀(경고는 stderr로만 나가 파이프라인에서 안 보임). AWS CLI만 겨냥한 환경변수가 같은 셸 블록의 모든 도구에 적용되는 게 함정의 뿌리. / 해결: 그 셸에서 파일 경로 인자는 **전부 Windows 절대경로(`C:/...`)로** — jar를 스크래치패드 `C:/...` 경로로 바꾸니 즉시 정상(로그인 302). AWS CLI `file://C:/...` 규칙(T-136)과 정확히 같은 규칙이 curl에도 적용되는 것. / 재발방지: ① `MSYS_NO_PATHCONV=1`을 export한 셸 블록에서는 **어떤 도구든** 파일 경로를 `/tmp`·`/c/...` 형식으로 주지 않는다(스크래치패드 `C:/...` 고정) ② 원인 불명의 "쿠키가 안 온다"는 서버 탓 전에 **jar 파일이 실제로 생겼는지**부터 확인(`ls`) — 파일 부재면 서버가 아니라 로컬 쓰기 실패다 ③ 오진 비용이 큰 검증(운영 장애 판단)에서는 부정 결과("Set-Cookie 없음")를 도구 한 개로 확정하지 말 것. T-136의 자매 함정(같은 셸 설정의 다른 피해자), 1회차 신규. 2026-08-06 세션 이중 타임아웃(#699) 운영 검증 중) |
| 2026-08-06 | T-140 (**SSM `get-parameters-by-path --output text`는 여러 줄 값을 레코드로 쪼갠다 — 빈 줄이 빈 키가 돼 `bad array subscript`로 운영 배포가 죽었다** / 증상: #702 머지 직후 자동 배포 실패. EC2 SSM 커맨드 stderr에 `./render-env.sh: line 59: SECRET_MAP: bad array subscript` 한 줄뿐이고 `.env`가 아예 안 만들어진다. 운영 서비스 자체는 무사했다 — 헬스체크 게이트보다 앞 단계라 구 버전 컨테이너가 그대로 유지됐다. / 원인: #702가 토스 mTLS PEM 2개를 SSM SecureString으로 **`/booktimer` 아래에** 새로 등록했다. PEM 렌더는 `get-parameter`로 따로 받게 잘 짰지만, **기존 시크릿 루프의 `get-parameters-by-path`가 같은 경로를 통째로 훑어 그 PEM들까지 함께 읽어온다**는 걸 놓쳤다. `--output text`는 값의 줄바꿈마다 레코드를 쪼개므로 PEM 본문 줄이 탭 없이 흘러들고, PEM 안의 **빈 줄은 `name=""` 레코드**가 된다 → `key=""` → `${SECRET_MAP[$key]}` 빈 첨자 접근 → `set -euo pipefail`과 맞물려 즉사. PEM 본문 줄(`-----BEGIN...`·base64)은 SECRET_MAP에 없는 키라 기존 `continue`가 무해하게 걸러줬다 — **죽인 건 오직 빈 줄**이라 원인이 좁게 숨었다. / 해결: `env_name` 조회보다 **앞에** `[ -n "$key" ] || continue` 한 줄. 순서가 load-bearing이다(빈 첨자 접근 자체가 죽으므로 조회 뒤로 내리면 그대로 재발 — 돌연변이로 실측). SSM 파라미터는 이동 제약이 있어 그대로 두고 코드에서만 막았다. / 재발방지: `deploy/tests/test-render-env.sh`의 by-path 스텁이 이제 **여러 줄 PEM 레코드(빈 줄 포함)를 항상 함께 뱉는다** — 실제 SSM 응답과 같은 모양이라 13건 기존 단언 전부가 이 조건 위에서 돈다. 교훈: **`--output text`로 by-path를 줄 단위 파싱하는 루프가 있는 경로에 여러 줄 파라미터를 새로 등록하면, 그 파라미터를 안 쓰는 루프까지 깨진다** — 등록 위치가 곧 파싱 계약이다. 무성 실패가 아니라 명시적 크래시였던 게 그나마 다행. 1회차 신규, #702 후속 핫픽스) |
| 2026-08-09 | T-141 (**`pr-merge.sh --arm`이 gh 전멸 환경에서 "✅ auto-merge 걸림" 거짓 성공 — 파이프 `| sed`가 exit code를 가리고 빈 상태를 성공 취급** / 증상: PowerShell 경유 bash에서 `pr-merge.sh 705 --arm` 실행 → `timeout: failed to run command 'gh'`가 찍히는데도 마지막 줄은 ✅ 성공, `gh pr view 705 --json autoMergeRequest`는 null(전혀 안 걸림) — PR이 OPEN인 채 세션은 "처리 완료"로 보고. / 원인: ① arm 파이프 `gh pr merge --auto | sed`의 최종 exit가 sed(항상 0)라 gh 실패 불가시 ② `read_state` 빈 응답이 case `*)`로 흘러 무증거 성공 선언 — 환경 원인(gh 미해결 PATH)과 별개로 "실패해도 성공 보고"가 진짜 결함. / 해결: gh `command -v` 조기 가드(exit 2) + `PIPESTATUS[0]` 검사 + 빈 상태=에러 + 성공 선언 전 `autoMergeRequest` non-null 검증(CLEAN 즉시머지 레이스는 MERGED 재확인). gh 경로 하드코딩은 안 함. / 재발방지: 스모크 테스트에 스텁 gh 4케이스(미해결·명령실패·상태무응답·null 검증) 추가, PIPESTATUS 검사 제거 돌연변이가 실제 RED 되는 것 실측. ⚠️ 이때 **스텁이 모든 gh 호출을 실패시키면 빈-상태 가드가 대신 잡아 돌연변이가 살아남는다**(첫 시도에서 실측) — 명령실패 케이스의 스텁은 `gh pr merge`만 실패시키고 나머지 조회는 건강하게 답해야 PIPESTATUS만을 겨눈다. **PR머지자동화군 7회차** + **T-126(검증 파이프 exit 가림) 2회차** — 트래커 갱신) |
| 2026-08-10 | T-142 (**서블릿 필터에서 lazy 프록시를 건드리면 OSIV가 켜져 있어도 세션이 없다 — 미니앱 Bearer API 전량 500, 그리고 `@Transactional` 통합 테스트가 그 결함을 가렸다** / 증상: 토스 미니앱 로그인·계정연결 직후 홈이 부르는 `GET /api/dashboard`가 운영에서 500. 스택트레이스는 `org.hibernate.LazyInitializationException: Could not initialize proxy [com.booktimer.user.User#17] - no session` at `BearerTokenFilter.authenticate:61`(`user.getLoginId()`). 로컬 테스트는 전부 GREEN이었다. / 원인: `ApiTokenService.authenticate()`가 `token.getUser()`를 그대로 돌려주는데 `ApiToken.user`는 `FetchType.LAZY`라 **미초기화 프록시**다. 서비스의 `@Transactional`이 끝나면 세션이 닫히고, 이 User를 실제로 읽는 `BearerTokenFilter`는 **서블릿 필터 = `DispatcherServlet`보다 앞**이다. `spring.jpa.open-in-view`(기본 true)는 `OpenEntityManagerInViewInterceptor`, 즉 **DispatcherServlet 레벨**이라 시큐리티 필터 체인 단계는 그 범위 밖 — "OSIV 켜져 있으니 lazy는 안전하다"는 통념이 정확히 깨지는 지점이다. 결과적으로 미니앱의 **모든** Bearer 인증 API가 전량 500(단일 엔드포인트 문제가 아니다). / 해결: `ApiTokenRepository.findByTokenHash`를 `@Query("select t from ApiToken t join fetch t.user where t.tokenHash = :tokenHash")`로 교체 — **트랜잭션 밖으로 나갈 엔티티는 나가기 전에 초기화해서 내보낸다**. 한 줄이고 `revoke` 경로에도 무해. (대안인 필터에서 user를 재조회·DTO 변환은 더 큰 diff에 같은 결과.) / 재발방지: ① **비트랜잭션** 재현 테스트 `ApiTokenServiceDetachedUserTest` 신설 — 기존 `ApiTokenServiceTest`는 클래스 레벨 `@Transactional`이라 테스트 내내 영속성 컨텍스트가 살아 있어 프록시가 우연히 초기화된다(**통합 테스트의 트랜잭션이 프로덕션 세션 경계를 위조해 결함을 가리는 전형** — mock 단위테스트가 FK 제약을 못 보는 T-023·T-029와 같은 결). 프로덕션이 트랜잭션 밖에서 쓰는 값은 테스트도 트랜잭션 밖에서 읽어야 계측기가 된다. ② 규칙: **서블릿 필터·`@Async`·스케줄러 등 요청 트랜잭션 밖으로 엔티티를 내보내는 경로는 fetch join(또는 DTO)로 초기화해서 반환**한다. 돌연변이 실측: `@Query`를 지워 파생 쿼리로 되돌리면 새 테스트가 운영과 동일한 예외로 RED가 됨을 확인 후 복원. 1회차 신규, 토스 미니앱 PR-1~3 후속) |
| 2026-08-11 | T-143 (**다른 세션이 내 워크트리를 삭제하면 내 git 명령이 상위 메인 저장소로 폴스루한다 — `checkout -B`가 메인 폴더 브랜치를 갈아치움** / 증상: 활성 세션에서 `git checkout -B <새브랜치> origin/main`·`git log`가 정상 성공한 직후, `cd miniapp`이 "No such file or directory"·`ls`는 빈 디렉터리인데 **`git status`는 여전히 정상 응답**(브랜치도 방금 만든 것). 확인하니 메인 폴더(`ClodeProjects/BookTimer`)가 그 새 브랜치로 바뀌어 있었다. / 원인: ① 다른 세션이 워크트리 대청소를 하며 **이 세션의 활성 워크트리를 삭제**(6개→2개, 디렉터리는 빈 껍데기만 잔존·`git worktree list`에서 소멸) ② git은 cwd에 저장소 표식이 없으면 **상위 디렉터리로 올라가며 저장소를 찾으므로**, `.claude/worktrees/<세션>` 빈 폴더에서 친 명령이 조상인 메인 저장소에 그대로 적용됐다 — checkout이 메인 워킹트리의 브랜치·파일 전체를 교체(다른 세션이 메인 폴더 사용 중이었다면 그 세션 작업을 직격). 기존 규칙은 「자기 워크트리 삭제 금지」(연속 세션)뿐이라 **남의 활성 워크트리 삭제를 막는 가드가 없다**. / 해결: 메인 폴더가 클린임을 확인 후 `git checkout main && git pull --ff-only` + 사고 브랜치 `git branch -D`로 무손실 복원 → 세션 경로에 `git worktree add <같은경로> -b <브랜치> origin/main`으로 재생성해 작업 재개. / 재발방지: ① 워크트리 정리(remove-worktree 스킬·`git worktree remove/prune`·대청소)는 **자기 것과 확실히 종료된 세션 것만** — `.claude/worktrees/` 아래 하네스 생성 워크트리는 살아있는 다른 세션 소유일 수 있으니 소유 세션 종료를 확인하기 전엔 지우지 않는다 ② 워크트리에서 브랜치를 바꾸거나 파괴적 git 명령 전에 `git rev-parse --show-toplevel`이 예상 워크트리 경로인지 한 줄 확인(폴스루 감지) ③ "git 명령은 되는데 파일이 없다" 조합이 보이면 즉시 폴스루를 의심하고 메인 저장소 상태부터 점검. 자매: T-105(빈 워크트리 폴더를 좀비 셸이 점유)·T-110(정션 워크트리 삭제가 메인 node_modules를 비움)·T-115(자기 워크트리 정리 시 빈 폴더 잔존) — 다중 세션 워크트리 군. 1회차 신규, 리워드 광고 실ID 재빌드 중 발생) |
| 2026-08-11 | T-144 (**정적 마크업 `toContain` 검사가 TDS 주입 CSS와 우연히 겹쳐 「공허한 테스트」가 된다 — 통과했는데 아무것도 못 잡음** / 증상: 홈 잔디를 가로 채움(`aspect-ratio`)으로 바꾸고 `expect(homeMarkup).toMatch(/aspect-ratio:1 ?/ ?1/)`로 배선을 못 박았는데, **구현에서 `fill` prop을 통째로 떼어내도 그 테스트가 그대로 통과**했다(돌연변이 확인에서 발각 — 안 했으면 영영 몰랐다). / 원인: TDS Button이 누를 때 쓰는 물결 효과 div의 emotion 클래스(`.css-1ewwsqr`)에 이미 `aspect-ratio:1/1`이 들어 있다. `renderToStaticMarkup`은 emotion `<style>` 블록까지 문자열에 실어 주므로, **우리 컴포넌트가 안 그려도 TDS가 대신 그 문자열을 채워 준다**. 미니앱 테스트는 대부분 마크업 문자열 `toContain`이라(하니스가 정적 렌더) 이 겹침에 구조적으로 취약하다. / 해결: 검사 문자열을 **우리 컴포넌트만의 조합**으로 좁혔다 — `width:100%;aspect-ratio:1 / 1;border-radius:2px`(잔디 칸의 인라인 스타일 순서 그대로). `border-radius:2px`가 잔디 칸의 기존 서명이라(이미 다른 테스트가 그 값으로 칸 수를 센다) 겹침이 사라진다. / 재발방지: ① 마크업 문자열 검사를 새로 쓸 땐 **반드시 돌연변이 확인**(그 규칙을 지우고 RED가 되는지) — 통과는 증거가 아니다 ② 단일 CSS 속성 한 조각(`flex:1`·`aspect-ratio`·`width:100%`)은 TDS·emotion과 겹칠 확률이 높으니 **인접 속성까지 이어 붙인 조합**을 키로 쓴다 ③ 가능하면 그 컴포넌트만 단독 렌더해(TDS Provider 없이) 검사한다 — 잔디는 순수 div라 단독 렌더가 되고, 홈 전체 렌더는 배선 확인용 1건으로 족하다. 자매: 「TDS Button 재색칠」이 인라인 값(`--button-background-color:#3182f6`)을 선택자 키로 쓰는 것과 같은 뿌리 — **TDS는 우리 문자열 공간에 자기 문자열을 섞어 넣는다**. 1회차 신규, 미니앱 홈 UX 수정 중 발생) |
| 2026-08-11 | T-145 (**Mockito `never()` + `anyString()`은 null 인자를 매칭하지 않는다 — "보내면 안 되는데 보냄" 회귀를 통과시키는 공허한 테스트** / 증상: 완독 축하 푸시에서 "토스 미연동(`toss_user_key=null`) 사용자에겐 발송하지 않는다"를 `verify(client, never()).sendMessage(anyString(), anyString(), any())`로 못 박았는데, **구현의 null 가드를 통째로 무력화해도 그 테스트가 그대로 통과**했다(돌연변이 확인에서 발각). 나머지 4종 돌연변이는 전부 잡혔기에 이 한 건만 조용히 새고 있었다. / 원인: 가드를 없애면 코드가 `sendMessage(null, ...)`를 호출하는데, Mockito의 `anyString()`은 `ArgumentMatchers.any()`와 달리 **타입 매처라 null을 매칭하지 않는다**. 그래서 "호출이 0번이었다"가 아니라 **"매처에 맞는 호출이 0번이었다"**가 참이 돼 `never()`가 성립한다 — 검증하려던 실패 모드(null 키로 발송 시도)가 정확히 매처의 사각에 들어앉는다. 즉 **null을 가드하는 규칙을 `anyString()`으로 검증하면 구조적으로 공허**하다. / 해결: `verifyNoInteractions(client)`로 교체(매처를 아예 안 쓴다). 대안은 `never()).sendMessage(any(), any(), any())` — `any()`는 null도 매칭한다. 교체 후 같은 돌연변이가 실제로 RED가 되는 것을 실측하고 복원했다. / 재발방지: ① **"안 부른다"를 검증할 땐 `verifyNoInteractions`/`verifyNoMoreInteractions`를 먼저 고려**한다 — 인자 매처가 낄 자리가 없어 사각이 생기지 않는다 ② 굳이 `never()`를 쓸 땐 인자를 `any()`로 (`anyString()`·`anyLong()` 같은 타입 매처 금지 — null 가드 검증에서 특히) ③ **null 가드·"스킵한다" 계열 테스트는 돌연변이 확인이 필수**다. 자매: T-144(정적 마크업 `toContain`이 TDS 주입 CSS와 겹쳐 공허) — 도구는 다르지만 뿌리가 같다: **통과는 증거가 아니고, 계측기가 겨눈 실패를 실제로 만들어 봐야 증거가 된다**. 「공허한 테스트」군 2회차 — 트래커 갱신. 1회차 신규(이 트랩 자체는), 토스 완독 축하 푸시 구현 중 발생) |
| 2026-08-12 | T-146 (**돌연변이 확인용 백업을 Git Bash `/tmp`에 두고 Windows 파이썬으로 복원하면 조용히 실패한다 — 돌연변이가 "적용조차 안 된" 채 GREEN이 나와 「살아남았다」로 오독** / 증상: 미니앱 알림 동의 구현에서 `shouldShowNotificationCard`를 훼손하는 돌연변이를 걸었는데 36건 전부 통과 = "테스트가 공허하다"로 읽었다. 실제로는 **돌연변이 스크립트가 파일을 열지 못해 아무것도 안 바꿨다**(`FileNotFoundError`가 다른 출력에 묻힘). 더 나쁜 건 폴백으로 넣어 둔 `git checkout -- <file>`이 **미커밋 구현을 통째로 되돌려** 버린 것 — 다음 회차 돌연변이가 "구현이 아예 없는" 파일에 얹혀 무관한 실패 5건이 났다(복구는 bash로 만든 백업에서). / 원인: Git Bash의 `/tmp`는 MSYS 루트(`C:\Users\…\AppData\Local\Temp`)인데 **Windows 네이티브 파이썬은 같은 문자열을 `C:\tmp`로 해석**한다. 즉 `cp file /tmp/x`(bash)와 `open('/tmp/x')`(python)는 **다른 폴더**다. 글로벌 「Windows 셸 원칙」 5번(MSYS 경로 변환)의 사촌 — 그쪽은 native exe 인자, 이쪽은 셸↔파이썬 간 `/tmp` 해석 불일치다. / 해결: 임시 파일을 `/tmp`에 두지 말고 **작업 디렉터리 상대경로**(예: `./.home.bak`, 커밋 전 삭제)나 스크래치패드 절대경로를 쓴다. 복원 폴백으로 `git checkout --`를 쓰지 않는다 — 미커밋 작업물이 있는 상태에선 복원이 아니라 파괴다. / 재발방지: ① 돌연변이 확인은 **돌연변이가 실제로 적용됐는지 먼저 확인**한다(`grep`으로 바뀐 줄을 1회 확인 — "GREEN이면 공허"라는 판정은 돌연변이 적용이 전제다) ② 백업·복원은 **한 도구로 통일**(bash면 bash, python이면 python — 섞으면 경로 의미가 갈린다) ③ 미커밋 구현이 있는 동안 `git checkout -- <file>`은 금지. 1회차 신규, 미니앱 알림 동의 UI 구현 중 발생) |
| 2026-08-11 | T-147 (**@SpringBootTest에서 시계를 전진시키면 그 값이 다음 테스트로 샌다 — 컨텍스트 공유 빈이라 순서 의존 플레이키** / 증상: 목표 달성 푸시 테스트에서 "다음 날 재발송"을 보려고 전진 가능한 `MutableClock`을 `@Primary` 빈으로 주입했는데, 그 테스트가 시계를 +1일 해 두고 복원하지 않아 **같은 클래스의 다른 테스트가 실행 순서에 따라 다른 "오늘"을 보게** 됐다. 전부 GREEN이라 눈치채지 못했고, **돌연변이 확인(날짜 귀속 가드 제거)을 돌렸을 때 엉뚱한 테스트("목표 미달이면 미발송")까지 FAIL** 하면서 드러났다 — 그 테스트는 시계가 밀린 덕에 "어제 시작한 세션"으로 오분류돼 **우연히** 통과하고 있었다. / 원인: Spring 테스트 컨텍스트는 클래스 안 모든 테스트가 **같은 인스턴스의 빈을 공유**한다. `@Transactional`은 DB만 롤백하지 빈의 내부 상태는 되돌리지 않는다 — 가변 빈(시계·인메모리 캐시·플래그)을 테스트가 바꾸면 그 변경은 컨텍스트 수명 내내 남는다. `Clock.fixed`만 쓰던 관례에선 시계가 불변이라 이 함정이 없었다. / 해결: `@BeforeEach`로 매 테스트 시작 시 시계를 기준 시각으로 되돌린다(복원을 각 테스트의 선의에 맡기지 않는다). / 재발방지: ① **가변 빈을 테스트에 주입하면 상태 초기화를 `@BeforeEach`로 못 박는다** — `@AfterEach` 복원은 실패·예외 경로에서 새기 쉽고, 애초에 "바꾼 쪽이 되돌린다"는 규율에 의존한다 ② 시간을 움직여야 하면 그 클래스의 **모든** 테스트가 시각 가정을 갖는다고 보고 기준을 명시적으로 심는다 ③ **이 결함을 찾아낸 건 통과 로그가 아니라 돌연변이 확인이었다** — 돌연변이가 겨냥하지 않은 테스트까지 깨지면 그건 노이즈가 아니라 **테스트 간 결합의 신호**다(T-144·T-145와 같은 뿌리: 통과는 증거가 아니다). 1회차 신규, 토스 목표 달성 푸시 구현 중 발생) |
| 2026-08-12 | T-148 (**미니앱 운영 env를 커밋 안 해 워크트리 빌드가 localhost 번들로 배포됐다 — vite 기본값이 조용히 성공시켜 빌드·배포 어디서도 안 걸린다** / 증상: 리워드 광고 번들을 워크트리에서 `npm run build` → `npx ait deploy`로 올렸더니 실기기 진입 즉시 로그인 에러 페이지. 빌드는 경고 하나 없이 성공했고 배포 CLI도 아무 말이 없다. / 원인: `VITE_API_BASE_URL`·`VITE_REWARD_AD_GROUP_ID`는 **빌드 시점에 번들로 구워지는데**(`import.meta.env`), 실제 운영값을 담은 env 파일이 git에 없어(`.env.local`은 `.gitignore`) **새 워크트리·새 클론엔 아무것도 없다**. 게다가 둘 다 조용한 기본값이 있다 — API는 `http://localhost:8080`, 광고 그룹 ID는 빈 문자열(= 광고 기능 전체 OFF)이라 env 없이도 빌드가 성공한다. 즉 결함이 드러나는 곳은 실기기뿐이고, 워크트리는 정의상 "아무것도 없는 새 체크아웃"이라 세션이 바뀔 때마다 이 함정을 새로 밟는다. / 해결: 인라인 env(`VITE_API_BASE_URL=… VITE_REWARD_AD_GROUP_ID=… npm run build`)로 재빌드해 재배포. / 재발방지: 두 값은 비밀값이 아니므로 **`miniapp/.env.production`을 커밋**해 vite가 production 빌드에서 자동 로드하게 했다 — env 주입을 사람의 기억에 안 맡기고 `npm run build`만으로 운영 번들이 나온다. 파일이 지워지거나 값이 비면 깨지는 회귀 테스트 `miniapp/src/env-production.test.ts`(vite `?raw`로 파일 자체를 읽어 두 줄을 단언)를 함께 뒀다. 로컬 개발은 `.env.local`이 계속 우선한다. 1회차 신규 — "빌드 시점 상수가 조용한 기본값으로 폴백" 계열이라 T-130(미점등 secret)·T-132(번들 stale)의 사촌이나 메커니즘이 달라 재발·승격 트래커에는 올리지 않는다) |
| 2026-08-12 | T-149 (**정적 렌더(`renderToStaticMarkup`) 하니스는 effect·이벤트를 안 돌리므로 "호출하지 않는다"류 카운트 단언은 항상 통과 — 구조적으로 공허한 테스트** / 증상: 심사 반려 대응(인트로 페이지)에서 "진입 시 자동 로그인을 호출하지 않는다"를 login mock 호출 카운트 0으로 검증하려던 계획이, 자동 로그인이 살아 있어도 항상 통과한다 — `renderToStaticMarkup`은 `useEffect`를 실행하지 않아 어느 쪽이든 카운트가 0이다(이번엔 구현 세션이 작성 전에 알아채 사고 없이 회피). / 원인: 이 하니스의 알려진 한계(jsdom 미도입 — onClick·effect 미실행)가 **부정 단언("안 한다")**과 만나면, 검증하려는 실패 모드 자체가 계측기의 관측 불가 영역에 정확히 들어간다 — T-145(매처가 그 실패를 안 매칭)와 같은 구조. / 해결: 관측 가능한 대체 신호로 번역해 계측 — "첫 렌더가 인트로 화면인가"(자동 로그인이 살아 있으면 초기 상태가 checking이라 로딩 마크업이 나온다). 돌연변이(자동 로그인 복원 → 3건 RED)로 판별력을 실측했다. / 재발방지: ① 정적 렌더 하니스에서 effect·핸들러 내부 동작에 대한 **부정 단언 금지** — 상태·마크업 차이로 번역해 계측한다 ② "안 한다" 검증은 그 실패를 실제로 만들어(돌연변이) 테스트가 깨지는지 확인한 뒤에만 신뢰한다. 「공허한 테스트」군 **3회차**(T-144·T-145) — 트래커 갱신. 미니앱 심사 반려 대응 중) |
| 2026-08-12 | T-150 (**`npm run build`가 완료 로그 없이 조용히 끝났는데 exit 0이라 옛 dist가 그대로 배포됐다 — 배포 전 검증 마커가 직전 번들에도 있어 신·구를 구별 못 했다** / 증상: 심사 반려 대응(진입 인트로 + 플로팅 탭바)을 머지하고 미니앱을 배포(19ff1b4)했는데, **재심사가 동일 사유로 또 반려**됐다. 발견 계기는 CI도 배포 로그도 아닌 **사용자의 실기기 캡처** — 배포된 번들엔 인트로도 플로팅 탭바도 없었다. 빌드·`ait build`·`ait deploy` 어느 단계도 경고 한 줄 내지 않았다. / 원인: 두 결함이 겹쳤다. ① **빌드 무성 미완료** — `npm run build`가 vite의 완료 로그(`✓ built in …`) 없이 끝났는데 종료코드는 0이라, `&&`로 이어진 `ait build`→`ait deploy`가 그대로 진행됐다. `dist/`는 지워지지 않고 **직전 릴리스 산출물이 남아 있어** 패키징·업로드는 정상적으로 성공한다 — 즉 실패가 드러날 표면이 어디에도 없다. ② **비변별 검증 마커** — 배포 전에 번들을 확인하긴 했으나 본 것이 운영 env 값(`booktimer.app`·광고 그룹 ID)뿐이었다. **그 값들은 직전 번들에도 똑같이 들어 있어** 검증이 신·구 어느 쪽이든 통과한다. 검증을 했는데 아무것도 검증하지 못한 셈(T-144 「공허한 테스트」와 같은 형태, 대상이 테스트가 아니라 배포 전 확인일 뿐). / 해결: `rm -rf dist` 클린 재빌드 후 **이번 릴리스에서 새로 들어간 기능 마커**(`토스로 시작하기` · `borderRadius:28`)가 번들에 실제로 박혔는지 실측하고 재배포(019ff48e). / 재발방지: **하드픽스 — `miniapp/deploy.sh`가 표준 배포 경로**(README의 수동 3단계를 대체). 클린 빌드 강제 → dist 검증(index.html이 참조하는 js 실존 · `booktimer.app` 있음 · `localhost:8080` 없음 · 각 `--expect` 마커 포함) → `ait build` → **.ait 안의 js가 방금 빌드한 dist의 js와 바이트 동일**(패키징 단계 스테일 차단) → 배포. 어느 검증이든 실패하면 **배포 전에** exit 1. 게이트 자체는 `miniapp/tests/test-deploy-gate.sh`(npm/npx 스텁, 6케이스 19단언)가 지키고, 돌연변이 6종 전부 사살 실측 — 특히 **클린 빌드를 빼면 "빌드 무성 미완료 + 옛 dist 잔존" 케이스가 배포까지 가는 것**을 재현해 그 한 줄이 유일한 방어선임을 확인했다(마커가 이미 옛 번들에 있으면 `--expect`로도 못 잡는다). 한글 마커 비교는 grep(C 로케일 무성실패) 대신 파이썬으로 한다. ⚠️ **1회차 신규로 등재** — 번들 stale군(T-063·T-082 = Vue 섬 산출물 미커밋)·무성 실패군과 결이 닿지만 **메커니즘이 별개**다: 저쪽은 "빌드는 됐는데 산출물을 커밋 안 함"이고 이쪽은 "빌드가 안 됐는데 됐다고 판정됨 + 그걸 확인하는 계측기가 비변별". 재발·승격 트래커엔 올리지 않고, 같은 메커니즘이 2회째가 되면 그때 군을 만든다. 미니앱 심사 반려 대응 배포 중 발생) |
| 2026-08-12 | T-151 (**Android 에뮬레이터 Quick Boot 스냅샷 꼬임 — `adb devices`가 20분+ `offline`이고 qemu만 CPU를 태운다** / 증상: 미니앱 샌드박스 검증용으로 `Medium_Phone_API_36.1`을 띄웠는데 창은 떠 있고 `adb devices`는 계속 `emulator-5554 offline`. `adb shell getprop sys.boot_completed`는 응답이 없고 qemu 프로세스만 CPU를 태운다 — 20분 넘게 그 상태였다. 에러 메시지도 진행 로그도 없어 "느린 콜드 부트"와 구별되지 않는다. / 원인: 직전 종료에서 저장된 **Quick Boot 스냅샷이 손상**돼, 에뮬레이터가 그 스냅샷을 복원하려다 부팅을 끝내지 못한 채 매달린다(adb는 device까지 못 가고 offline에 머문다). / 해결: emulator·qemu 프로세스를 강제 종료한 뒤 **스냅샷을 무시하는 콜드 부트**로 재시작하면 즉시 정상 부팅한다 — `Get-Process qemu-system-x86_64, emulator -EA SilentlyContinue | Stop-Process -Force` 후 `emulator -avd <AVD> -no-snapshot-load`. / 재발방지: 표준 진입점 `.claude/scripts/miniapp-emulator.ps1`이 `sys.boot_completed=1`을 **5분 타임아웃**으로 폴링하고, 초과하면 "스냅샷 꼬임일 수 있음 — 프로세스 종료 후 `-ColdBoot`로 재시도"를 한국어로 출력하며 exit 1 한다(무한 대기 자체를 없애고 조치를 그 자리에서 안내). `-ColdBoot` 스위치가 `-no-snapshot-load`를 얹는다. 1회차 신규 — "무증상 무한 대기" 결이 gradle 커밋 게이트 hang(T-078)과 닿지만 원인·대상이 별개라 재발·승격 트래커에는 올리지 않는다. 미니앱 에뮬레이터 개발 루프 도구화 중 등재) |
| 2026-08-12 | T-152 (**웹 미니앱은 샌드박스 핫 리로드가 원리상 불가능하다 — 샌드박스 dev 연결은 granite(RN) 전용이고 `web-framework`엔 dev 서버 자체가 없다** / 증상: 미니앱 UI 작업의 실시간 확인 루프를 만들려고 에뮬레이터+샌드박스 앱을 띄웠는데, 샌드박스 앱이 우리 미니앱을 열어 주지 않고 **"메트로 서버에 연결해야만"** 쓸 수 있다는 게이트로 막는다. `adb reverse`로 포트를 열어 줘도 같다 — 반대로 브라우저로 열면 토스 SDK(`TossAuth.login`)가 없어 로그인에서 멈춘다. 즉 실기기·브라우저 양쪽이 다 막혀 화면 변경 하나를 보려면 매번 배포(`deploy.sh` 수 분)를 타야 했다. / 원인: 샌드박스의 dev 연결은 **메트로(metro) 번들러 프로토콜 = granite(React Native) 미니앱 전용**이다. 우리 미니앱은 `@apps-in-toss/web-framework`(웹) 스택이라 **패키지에 dev 서버도 vite 플러그인도 없다** — 샌드박스가 붙을 대상이 애초에 존재하지 않는다. 「에뮬레이터를 띄웠으니 HMR도 되겠지」가 스택을 넘어 유추된 잘못된 전제였고, 그 전제로 에뮬레이터 부팅·앱 설치·`adb reverse` 자동화까지 만든 뒤에야 실측으로 확정됐다. / 해결: **브라우저 dev 목 모드**(`npm --prefix miniapp run dev:mock`) — `.env.mock`의 `VITE_DEV_MOCK=1`이면 `api.ts`가 서버 대신 `src/dev-mock.ts` 픽스처를 돌려주고 토큰을 더미로 둬, 로그인·서버·에뮬레이터 없이 크롬에서 전 화면이 vite HMR로 돈다. 목 코드는 `import.meta.env.DEV` 게이트 + dynamic import로 프로드 번들에서 잘리고(dist 실측 0건), `deploy.sh`의 `__DEV_MOCK__` 음성 체크가 배포 전에 재확인한다. SDK 연동(실로그인·광고·알림 동의)만 `deploy.sh` + 실기기로 남는다. / 재발방지: ① CLAUDE.md 「미니앱 개발 루프」 절을 **기본=브라우저 목**으로 재작성하고 `miniapp-emulator.ps1`은 "향후 지원 대비·샌드박스 앱 설치용"으로 강등해 다음 세션이 같은 전제로 시간을 태우지 않게 한다 ② 교훈: **다른 스택(RN)의 개발 도구가 우리 스택(웹)에도 있을 것이라 가정하지 말고, 도구 자동화 전에 그 도구가 우리 스택을 지원하는지부터 1회 실측**한다(패키지에 dev 서버가 있는지 = 가장 싼 확인). 1회차 신규 — T-149·T-144 「계측기가 겨눈 것을 실제로 확인하라」와 결이 같으나 대상이 테스트가 아니라 개발 환경의 전제다. 미니앱 에뮬레이터 개발 루프 도구화 직후 실측으로 확정) |
