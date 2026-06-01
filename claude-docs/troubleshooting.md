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

## 🔄 누적 갱신

| 일자 | 추가 항목 |
|---|---|
| 2026-05-31 | 초안 + T-001~T-004 |
| 2026-05-31 | T-005 (머지 후 정리 순서) |
| 2026-05-31 | T-006 (Boot 4 @DataJpaTest import 경로) |
| 2026-06-01 | T-007 (@DataJpaTest 슬라이스 auditing 미로드 — createdAt null) |
