# BookTimer — 프로젝트 작업 규칙 (Claude Code)

> 이 파일은 글로벌 `~/.claude/CLAUDE.md` 와 **합쳐서** 적용된다.
> 글로벌은 사용자 메타 시스템(PKM 등), 이 파일은 BookTimer 고유 규칙.

프로젝트 개요·도메인 규칙은 [README.md](README.md), 학습 노트는 [claude-docs/learning-notes.md](claude-docs/learning-notes.md), 트러블슈팅은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md) 참고.

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
   - **plan.md 갱신 이력 항목을 PR 브랜치 안에 포함한다 (필수)** — 그 작업을 `plan.md`
     맨 아래 `## 🔄 갱신 이력` 표에 한 줄로 남긴다(일자 / 한 일·PR 번호). plan.md에
     원래 없던 작업(즉흥 기능·UX 수정 등)도 마찬가지로 **갱신 이력엔 무조건 남긴다**.
     갱신 단위는 PR이므로 이 줄도 그 PR 커밋에 함께 들어가야 한다 — 사후 보충 PR이
     생기면 누락이다. (배경: 2026-06-05 #134가 plan.md를 안 건드려 #136으로 사후 보충함.)
5. **머지** — `gh pr merge` (사용자 확인 후). 머지 후 로컬 `main` 갱신(`git checkout main && git pull`) 및 브랜치 정리

### 예외

- 사용자가 명시적으로 "main 에 바로", "직접 push" 라고 지시한 경우에만 직접 push 허용.

### 커밋/푸시 시점

- 커밋·push·PR·머지는 **사용자가 요청할 때만** 수행한다 (글로벌 규칙 동일).

---

## 🪢 다중 세션 동시 작업 — 워크트리 분리 (필수)

여러 Claude Code 세션을 동시에 돌릴 때, **한 폴더(워킹 트리)를 공유하면 파일·브랜치가 충돌**한다.
브랜치만 나누는 건 소용없다 — 같은 폴더에서 `git checkout` 은 폴더 전체 파일을 갈아끼워 다른 세션까지 흔든다.
**격리 단위는 브랜치가 아니라 워킹 트리(폴더)다.**

### 작업 시작 전 — 다른 세션 확인 (필수)

의미 있는 작업(편집/커밋)을 시작하기 전에 **git 상태부터 확인**한다:

```
git branch --show-current   # 예상 브랜치인가? (다른 세션이 바꿔놨을 수 있음)
git status --short          # 내가 안 만든 미커밋 변경이 있나?
git worktree list           # 워크트리가 이미 여럿인가?
```

> SessionStart 훅 `.claude/hooks/warn-multi-session.ps1` 이 세션 시작 시 이 정보를 자동으로 띄우고,
> 브랜치가 main이 아니거나 미커밋 변경이 있으면 경고한다(하드 보조). 판단·분리 실행은 이 규칙(soft)이 담당.

브랜치가 예상과 다르거나 / 내가 안 만든 변경이 있거나 / 워크트리가 여럿이면 → **다른 세션이 이 폴더를 쓰는 중**일 수 있다.

### 다른 세션이 떠 있으면 — worktree로 분리

이 폴더에서 `git checkout` 하지 말 것(다른 세션의 브랜치를 바꿔 망친다). 대신 **별도 워크트리**에서 작업한다:

```
git worktree add ../BookTimer-<task> -b <type>/<summary> main
# → 그 폴더에서 작업(절대경로 편집/커밋), 머지 후:
git worktree remove ../BookTimer-<task>
```

- 워크트리 = **새 브랜치 한 세트**(같은 브랜치를 두 워크트리에 동시 체크아웃 불가). PR 우선 워크플로와 그대로 맞물린다.
- 미커밋·단독 변경이면 사후 분리도 가능하지만(미커밋은 브랜치에 안 묶임), **이상적 순서는 편집 전에 분리**다.
  "늦어서 곤란"해지는 시점은 엉뚱한 브랜치에 **커밋·push·머지까지 한 뒤**다.

### worktree로도 남는 공유 자원 (따로 조율)

폴더를 나눠도 repo 전체가 공유하는 것은 여전히 충돌하니 조율한다:

- **Flyway 버전 번호**(`V5__`, `V6__` …) — 세션별 번호 구역 배정 또는 머지 후 부여
- **공유 문서**(plan.md / README / 이 파일 / learning-notes / troubleshooting) — 작게·원자적으로, **편집 직전 재읽기**
- **앱 포트 8080** — 두 세션이 `bootRun` 하면 충돌 → 트리별 `server.port` 분리(또는 한 곳에서만 실행)
- **"File modified since read" 가드는 버그가 아니라 덮어쓰기 직전 보호** — 재읽기 → 그쪽 변경 보존 → 내 것만 재적용이 정답

> 개념·배경: [claude-docs/learning-notes.md](claude-docs/learning-notes.md) **N-032**.

---

## 🧪 TDD — 테스트 먼저 (필수)

**기능을 구현할 때는 반드시 테스트를 먼저 작성**하고, 그 테스트로 기대 동작을 확인한 뒤 구현한다.

### 절차 (Red → Green → Refactor)

1. **Red** — 구현 전에, 기대 동작을 표현하는 **실패하는 테스트**를 먼저 작성한다.
   그리고 **실제로 실행해 실패를 눈으로 확인**한다 — 컴파일 에러/단언 실패가
   "의도한 이유로" 나는지 본다(아직 구현이 없어서 실패해야 정상).
2. **Green** — 그 테스트를 통과시키는 **최소 구현**을 한다. 구현 후 **다시 실행해 통과를 확인**한다.
3. **Refactor** — 테스트 통과를 유지하며 구조를 정리한다.

> 도메인 로직(예: Lazy 누적 계산, cap 적용, 일일 이월)은 특히 경계값 테스트를
> 먼저 짠다 — 0일 경과 / 여러 날 경과 / cap 초과 / 자정 경계 등.

> **진행 가시성 (필수)**: Claude 는 기능 구현 시 항상 이 순서를 **밖으로 드러낸다** —
> ① 실패 테스트를 먼저 보여주고 → ② 실행해 **Red(실패)** 임을 보고한 뒤 → ③ 구현하고 →
> ④ 다시 실행해 **Green(통과)** 임을 보고한다. "테스트 먼저 짰다"고 말만 하지 않고
> 실패→통과 전환을 실제 실행 결과로 확인시킨다. (사용자 합의: 2026-05-31)

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

## 🧯 트러블슈팅 활용 — `claude-docs/troubleshooting.md`

작업 중 만난 함정과 해결법은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md) 에 `T-###` 로 누적한다. **같은 실수 두 번 반복 방지**가 목적.

### 작업 시작 / 디버깅 전 — 먼저 참고

- 빌드·git·PowerShell·테스트 등에서 막히면, **추측하기 전에 먼저** `troubleshooting.md` 와 `learning-notes.md` 를 확인한다.
- 이미 기록된 트랩이면 그 해법을 그대로 적용한다 (두 번 헤매지 않기).

### 디버깅 후 — 자동 sweep (learning-notes 파이프라인과 동일)

1분 이상 헤맨 문제를 해결했으면 **두 종류의 후보를 점검**한다:

| 종류 | 위치 | 성격 |
|---|---|---|
| **Trap (해결법)** — "이렇게 하지 마라" | `troubleshooting.md` (`T-###`) | 재발 방지 절차 |
| **개념 (이해)** — "왜 이렇게 동작하는가" | `learning-notes.md` (`N-###`) | 면접 설명 가능 수준 |

- 해결 직후, 답변 끝에 **"🧯 troubleshooting 추가 후보 — `<한 줄 요약>`. 박을까?"** (또는 learning-notes 후보)를 짧게 제안한다.
- 사용자가 OK 하면 즉시 해당 파일에 `T-###` / `N-###` 로 추가한다.
- PR 머지 직전에도 sweep 을 함께 수행한다 (Git 워크플로 4번).

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
