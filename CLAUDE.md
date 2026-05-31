# BookTimer — 프로젝트 작업 규칙 (Claude Code)

> 이 파일은 글로벌 `~/.claude/CLAUDE.md` 와 **합쳐서** 적용된다.
> 글로벌은 사용자 메타 시스템(PKM 등), 이 파일은 BookTimer 고유 규칙.

프로젝트 개요·도메인 규칙은 [README.md](README.md), 학습 노트는 [claude-docs/learning-notes.md](claude-docs/learning-notes.md) 참고.

---

## 🔀 Git 워크플로 — PR 우선 (필수)

**`main` 에 직접 push 하지 않는다.** 모든 변경은 브랜치 → PR → 머지 순서를 따른다.

### 절차

1. **브랜치 생성** — `main` 에서 분기
   - 네이밍: `feat/<요약>`, `fix/<요약>`, `docs/<요약>`, `chore/<요약>`
   - 예: `feat/reading-timer-entity`, `docs/learning-notes-n004`
2. **작업 + 커밋** — 의미 단위로 커밋
3. **push** — `git push -u origin <branch>`
4. **PR 작성** — `gh pr create` 로 작성
   - body 끝에 다음을 붙인다:
     ```
     🤖 Generated with [Claude Code](https://claude.com/claude-code)
     ```
   - PR body 작성 시 글로벌 규칙대로 **troubleshooting / learning-notes sweep** 수행
5. **머지** — `gh pr merge` (사용자 확인 후). 머지 후 로컬 `main` 갱신(`git checkout main && git pull`) 및 브랜치 정리

### 예외

- 사용자가 명시적으로 "main 에 바로", "직접 push" 라고 지시한 경우에만 직접 push 허용.

### 커밋/푸시 시점

- 커밋·push·PR·머지는 **사용자가 요청할 때만** 수행한다 (글로벌 규칙 동일).

---

## 🧪 TDD — 테스트 먼저 (필수)

**기능을 구현할 때는 반드시 테스트를 먼저 작성**하고, 그 테스트로 기대 동작을 확인한 뒤 구현한다.

### 절차 (Red → Green → Refactor)

1. **Red** — 구현 전에, 기대 동작을 표현하는 **실패하는 테스트**를 먼저 작성한다.
2. **Green** — 그 테스트를 통과시키는 **최소 구현**을 한다.
3. **Refactor** — 테스트 통과를 유지하며 구조를 정리한다.

> 도메인 로직(예: Lazy 누적 계산, cap 적용, 일일 이월)은 특히 경계값 테스트를
> 먼저 짠다 — 0일 경과 / 여러 날 경과 / cap 초과 / 자정 경계 등.

### 강제 (커밋 시 테스트 게이트)

- `.claude/hooks/require-tests-before-commit.ps1` 가 `git commit` 을 가로채,
  **스테이징에 `.java` 변경이 있으면 `./gradlew test` 를 실행**하고 실패 시 커밋을 차단한다.
- 역할 분담: **"테스트를 먼저"** 라는 판단은 이 가이드(소프트)가, **"테스트 통과 없이 커밋 불가"** 는 훅(하드)이 담당한다.
- 문서/설정 전용 커밋(`.java` 변경 없음)은 게이트가 자동으로 건너뛴다.

### 예외 (override)

- 커밋 명령에 `SKIP_TESTS` 토큰을 포함하면 게이트를 우회한다.
  - 정당한 경우에만: TDD red 단계의 **실패 테스트만 먼저 커밋**, 긴급 핫픽스 등.
  - 사용자가 명시적으로 허용한 경우에만 Claude 가 부착한다.

---

## 🈲 한글 커밋 메시지 — `.commit-msg-tmp` 사용 (T-026)

PowerShell 5.1 에서 한글 커밋 메시지를 인라인으로 넘기면 깨진다.
→ 메시지를 **UTF-8 파일** `.commit-msg-tmp` 로 쓰고 `git commit -F .commit-msg-tmp` 로 커밋.

- `.commit-msg-tmp` 는 `.gitignore` 에 등록되어 있어 추적되지 않는다 (잔재 add 방지).

---

## 🛠️ 빌드 / 실행 메모

- 빌드: `./gradlew build` (Windows: `gradlew.bat`)
- 컴파일만: `./gradlew compileJava`
- 실행: `./gradlew bootRun` — Security 가 있어 전 엔드포인트 기본 잠김(콘솔에 임시 비번 출력)
- DB: `compose.yaml` 의 MySQL 이 DevTools docker-compose 연동으로 자동 기동 (Docker 필요)
- 테스트 DB: 운영은 MySQL, **테스트는 H2 인메모리**(`src/test/resources/application.properties`) — Docker 없이 테스트 독립 실행. 테스트 시 docker-compose 자동 기동은 꺼짐(`spring.docker.compose.enabled=false`)
- toolchain: Java 21 (로컬에 없어도 foojay-resolver 가 자동 다운로드 — 노트 N-002)
