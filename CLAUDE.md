# BookTimer — 프로젝트 작업 규칙 (Claude Code)

> 이 파일은 글로벌 `~/.claude/CLAUDE.md` 와 **합쳐서** 적용된다.
> 글로벌은 사용자 메타 시스템(PKM 등), 이 파일은 BookTimer 고유 규칙.

프로젝트 개요·도메인 규칙은 [README.md](README.md), 로드맵·설계는 [plan.md](plan.md), 갱신 이력(변경 일지)은 [claude-docs/changelog.md](claude-docs/changelog.md), 학습 노트는 [claude-docs/learning-notes.md](claude-docs/learning-notes.md), 트러블슈팅은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md) 참고.

---

## 🗺️ 계획 우선 — 의미 있는 코드 작업은 계획부터 (필수)

**의미 있는 코드 작업(기능 추가·버그 수정·리팩터·설계 변경 등)은 바로 구현하지 않는다.**
먼저 관련 코드를 파악해 **구현 계획을 제시하고 사용자 승인을 받은 뒤** 구현에 들어간다.

### 왜 (배경)

사용자는 **계획 단계와 구현 단계에 서로 다른 모델·작업량(reasoning effort)을 배정**한다 —
계획은 깊게(강한 모델·높은 작업량으로 코드 파악·설계), 구현은 가볍게(낮은 작업량으로 승인된 계획대로 작업).
그래서 두 단계가 **분리**된다. 계획 없이 바로 구현하면 이 분리가 깨진다. (사용자 합의: 2026-06-08)

> **분리를 실현하는 수단이 바뀌었다 (2026-08-13)**: 예전엔 사용자가 세션을 갈아 끼워 분리했지만(계획 세션=Fable /
> 구현 세션=Opus), 지금은 **기본 세션이 Opus 하나이고 단계별로 서브에이전트에 위임**해 분리한다 —
> **설계=`designer`(fable·high) / 구현=`implementer`(opus·high) / 리뷰=`reviewer`(opus·xhigh) /
> 절차=`operator`(opus·low)**. 세션이 직접 설계하면 Opus가 설계하게 되므로, **설계 판단이 들어가는 순간
> designer 위임이 기본**이다(예외는 판단이 아예 없는 사소·자명한 것과 합의된 설계의 연속 작업뿐).
> 근거·경위는 글로벌 `~/.claude/CLAUDE.md` 「모델 분업」 절.

### 계획에 담을 것

- **현황·원인** — 관련 코드를 실제로 읽고(추측 금지) 무엇이 어떻게 동작하는지 / 무엇이 문제인지.
- **해법 옵션과 추천** — 1개 이상 옵션 + 트레이드오프 + 추천안과 그 이유.
- **변경 범위** — 건드릴 파일·코드 수준(시그니처·핵심 스니펫).
- **엣지케이스·TDD 계획** — 경계 테스트로 무엇을 못 박을지. **계획의 TDD 절은 평소의 RED → GREEN 순서를 명시한다(필수)** —
  구현 단계(`implementer`)가 ① 실패 테스트 먼저 → ② Red 확인 → ③ 최소 구현 → ④ Green 확인을 밟도록 못 박는다. 테스트 가능한 로직이 있으면
  이 절차를 생략·약화하지 않는다 — 단위테스트가 무의미한 순수 시각 변경만 이유와 함께 preview/수동 검증을 게이트로 명시한다
  (약화 재발 사례는 [참조](claude-docs/claude-md-reference.md)).
- **규모·리스크** — 작업량 가늠과 회귀 위험.

### 어떻게

- plan mode가 켜져 있으면 **ExitPlanMode**로 계획 승인을 요청한다.
- 아니면 **계획을 글로 제시하고 "이대로 진행할까요?"**로 확인한 뒤, 승인을 받고서 구현한다.
- 요구사항·접근이 애매하면 구현 전에 **AskUserQuestion**으로 먼저 묻는다.

> **⚠️ 계획 요청 시 구현 승인은 별개다 (재발 방지, 필수).** 사용자가 "계획 짜자 / 설계해"처럼 계획을 요청하면
> **그 세션의 산출물은 계획까지다** — 명시적 구현 지시("구현해 / 진행해") 전엔 코드(Edit/Write)에 손대지 않는다.
> 특히 **설계 방향을 좁히는 AskUserQuestion 옵션 선택은 계획의 일부일 뿐 구현 승인이 아니다.**
> 애매하면 구현 직전 **"구현 들어갈까요?"**로 한 번 더 확인한다(의심되면 계획 쪽으로). (재발 사례는 [참조](claude-docs/claude-md-reference.md).)

### 단계 분리 — 계획 md 핸드오프 (큰 작업 한정)

위 「왜」의 단계 분리를 **명시적·재현가능**하게 만드는 핸드오프 방식. **모든 작업에 강제하지 않는다** — 마찰이 이익을 잡아먹는다. (사용자 합의: 2026-06-09)

- **언제 (트리거)**: 규모가 크거나·다단계거나·회귀 리스크가 큰 작업. 작은/자명한 작업은 위 「어떻게」(ExitPlanMode·글 승인)로 충분 — md를 만들지 않는다.
- **설계 = `designer`(fable·high)**: 산출물 = `claude-docs/plans/<YYYY-MM-DD>-<요약>.md` **1개**. 내용은 위 「계획에 담을 것」 템플릿 그대로(현황·원인 / 해법 옵션·추천 / 변경 범위·시그니처 / 엣지케이스·TDD / 규모·리스크). 이 md는 **작업용 임시 산출물** — repo 로드맵 [plan.md](plan.md)와 **다르다(혼동 금지)**. 구현 완료 후 삭제하거나 PR 설명으로 흡수한다(stale 계획 방치 금지). `.gitignore`에 `claude-docs/plans/`를 둬 커밋 부담을 0으로 둘 수 있다.
- **구현 = `implementer`(opus·high)**: 그 md를 읽고 **승인된 계획대로** TDD 구현한다. 다시 설계하지 않는다(설계 단계의 사고를 신뢰).
- **드리프트 규칙 (중요)**: 구현 중 계획이 틀렸거나 빠진 게 보이면 **임의로 벗어나지 말고 멈추고 사용자에게 보고**한다. 사소한 보정은 재량이되, **설계·범위가 흔들리면 `designer`를 다시 부른다**. 이게 없으면 "구현이 몰래 계획을 이탈" = 분리의 의미가 사라진다.
- **PR·머지 절차 = `operator`(opus·low)**: 커밋·PR 생성·머지·브랜치 정리 같은 절차는 operator에 위임한다. 단 **PR body·changelog 문안은 컨텍스트를 쥔 코디네이터 세션이 준다**(방금 무엇을 왜 바꿨는지 아는 쪽이 가장 정확·싸게 쓴다) — operator는 판단이 필요해지면 멈추고 보고한다. **예외**: 회귀 리스크 큰 작업만 머지 전 `reviewer` **독립 리뷰 패스** opt-in. (근거·트레이드오프는 [참조](claude-docs/claude-md-reference.md).)

### 세션 메타 기록 — 모델·effort (반자동)

각 세션은 **자기 산출물에 자기 모델·effort를 기록**한다 — 어느 세션(모델·작업량)이 낸 결과인지 사후 추적용. 세션 분리상 다른 세션 값은 알 수 없으니 **전진 기록만** 가능(소급 불가). (사용자 합의: 2026-06-26)

- **모델**: 환경 주입값에서 자동 인식해 채운다(예: `claude-opus-4-8`). 세션 중 바뀌면 그 시점 값.
- **effort**: 사용자가 고지한 값을 그대로 쓴다 — effort는 내 컨텍스트에 안 들어오니 **추측하지 않는다. 미고지면 묻는다.** ⚠️ **`unknown` 자동 폴백 금지** — 기록 시점에 값이 미상이면 그 자리에서 다시 물어 실제 값을 받고서 기록한다. `unknown`은 사용자가 명시적으로 "모름"이라 답한 경우에만.
- **어디에**:
  - 계획 md(큰 작업만): 상단 blockquote에 `> 🧭 세션 메타: model=… · effort=…` 한 줄.
  - 커밋(모든 세션): trailer에 `Session-Model: …` / `Session-Effort: …`(`Co-authored-by` 옆). squash 머지 후에도 `main`에 보존된다.
- soft 규칙이라 내가 누락할 수 있다 — 누락이 2회+ 재발하면 hard(훅)로 승격한다(재발·승격 트래커).

### 예외 (계획 생략 가능)

- **사소·자명한 변경** — 오타·주석·한 줄 문구·명백한 설정값 등 설계 판단이 없는 것.
- 사용자가 **"바로 해" / "구현해"**라고 명시했거나, 직전에 이미 합의된 계획의 연속 작업일 때.

> 역할 분담: 이 가이드(soft)가 "계획부터" 판단을 담당한다. 사소함의 경계는 모델이 상황에 맞게 판단하되,
> 의심되면 **계획 쪽으로 기운다**(바로 구현보다 한 번 보여주는 게 안전).

---

## 🔀 Git 워크플로 — PR 우선 (필수)

**`main` 에 직접 push 하지 않는다.** 모든 변경은 브랜치 → PR → 머지 순서를 따른다.

### 절차

1. **브랜치 생성** — `main` 에서 분기
   - 네이밍: `feat/<요약>`, `fix/<요약>`, `docs/<요약>`, `chore/<요약>`
   - 예: `feat/reading-timer-entity`, `docs/learning-notes-n004`
2. **작업 + 커밋** — 의미 단위로 커밋
   - **커밋 제목에 PR 번호(`(#NNN)`)를 직접 넣지 않는다** — squash 머지 시 GitHub가 자동 부착해 중복된다.
     **(훅 `check-commit-message.ps1`이 하드 강제. 우회: `ALLOW_PR_NUM_IN_TITLE` 토큰.)**
   - **커밋 trailer에 세션 메타(`Session-Model` / `Session-Effort`)를 남긴다** — 모델은 자동, effort는
     사용자 고지값. 상세는 「🗺️ 계획 우선 → 세션 메타 기록」 절 참조.
3. **push** — `git push -u origin <branch>`
4. **PR 작성** — `gh pr create` 로 작성
   - body 끝에 다음을 붙인다:
     ```
     🤖 Generated with [Claude Code](https://claude.com/claude-code)
     ```
   - PR body 작성 시 글로벌 규칙대로 **troubleshooting / learning-notes sweep** 수행
   - **갱신 이력 항목을 PR 브랜치 안에 포함한다 (필수)** — 그 작업을
     [claude-docs/changelog.md](claude-docs/changelog.md) 표 **맨 아래에 한 줄**로 남긴다(일자 / 한 일).
     **PR 번호는 넣지 않는다**(번호 반영 추가 커밋 = push·CI 2회. 추적은 PR body·git log로 충분).
     plan.md에 원래 없던 즉흥 작업도 **무조건 남기고, 같은 PR 커밋에 함께** 넣는다 — 사후 보충 PR이 생기면 누락이다.
   - **plan.md 본문도 실제와 일치시킨다 (필수 sweep)** — 즉흥 기능이면 섹션 신설/"완료 ✅", 백로그 상태 변경이면 반영,
     설계가 바뀌었으면 옛 기록과 화해. "plan.md에 원래 그 항목이 있었는가"와 **무관하게** 점검하고(없던 작업일수록 더),
     마무리 직전 자가 점검: **"changelog 한 줄 + plan.md 본문이 실제와 일치하는가? 안 그러면 지금 고친다."**
     (연혁·배경은 [참조](claude-docs/claude-md-reference.md).)
5. **머지** — 사용자 확인 후, **머지 전 `mergeStateStatus` 진단이 필수**다 — `no checks reported` 는 DIRTY(충돌)여서 CI가 안 붙은 것일 수 있어, 진단 없이 "CI 등록 대기" 루프로 들어가면 영영 안 끝난다(T-083).
   - **DIRTY** → `gh pr merge` 전에 `git rebase origin/main` → `git push --force-with-lease` 로 충돌 해결. 그 후 재진행.
   - **CLEAN** → 즉시 머지.
   - **BLOCKED** (CI 대기) → CI 통과 후 머지.
   - **BEHIND** (base에 뒤처짐, 충돌 아님 — 레포가 "머지 전 브랜치 최신화 필수" 정책) → `gh pr update-branch <PR>`(비파괴 서버사이드 base→head merge) 후 CI 재실행→머지. **GitHub는 BEHIND 브랜치를 자동 갱신하지 않으므로(이 레포 auto-update off) bare `--auto`만 걸면 영영 대기**한다(T-111).
   - **표준 머지 경로 = `bash .claude/scripts/pr-merge.sh <PR번호> --arm`** (2026-06-27~, 레포 `allow_auto_merge=true`): `gh pr merge --auto --squash`를 걸고 → `mergeStateStatus`를 1회 점검해 **BEHIND면 `gh pr update-branch`**, DIRTY면 rebase(`--rebase` 동반 시)로 풀어준 뒤 **즉시 종료**한다. 머지는 서버(`--auto`)가 `test` 통과 시 마저 하므로 세션을 묶지 않으면서(머지 hang 클래스 제거, T-094) **"up-to-date 필수 + BEHIND = 무한 대기"(T-111)** 사각도 닫는 "걸고 떠나기". ⚠️ **bare `gh pr merge --auto --squash` 단독은 쓰지 않는다** — BEHIND면 무한 대기(이 정책에선 흔함). **원격 브랜치 삭제는 `deleteBranchOnMerge=true`(2026-06-27~)가 서버사이드 자동 처리**하므로 `--delete-branch`·수동 `gh api DELETE` 불필요(T-106). 연쇄 PR(다음 분기가 이 머지에 의존)은 `--arm` 후 `gh pr view <PR> --json state`=`MERGED` 확인 뒤 다음 분기를 딴다.
     - **⚠️ 워크트리 세션 caveat (T-095·T-096, 2회+ 승격)**: ① 위처럼 `--delete-branch`를 애초에 안 쓰므로(원격은 `deleteBranchOnMerge`가 서버사이드 자동 삭제) T-095의 `fatal: 'main' is already used by worktree` 깨짐은 발생하지 않는다 — 워크트리에서도 `pr-merge.sh <PR> --arm` 그대로. 머지 확인 후 **로컬만** 정리: 베이스 브랜치 checkout 후 `git branch -D <branch>`(원격은 손대지 않음). ② 연쇄 PR에서 **다음 브랜치를 `origin/main` 기준으로 따기 전 반드시 `gh pr view <PR> --json state`=`MERGED` 확인** — 폴링이 `TIMEOUT`/`OPEN`/`DIRTY`로 끝난 건 미머지라, 머지 전제로 브랜치를 따면 직전 PR 변경이 빠진 채 시작된다(T-096). 미머지면 DIRTY→rebase·force-push로 해결 후 재머지.
   - **동기 머지(이 세션에서 끝까지 보고)** = `bash .claude/scripts/pr-merge.sh <PR번호>` (`--arm` 없이): DIRTY 즉시 차단(또는 `--rebase` 자동 해결) + **BEHIND `gh pr update-branch` 자동 해소** + CI 폴링 + 하드 타임아웃(12분) + 원격 브랜치 삭제(gh API)를 한 호출로 처리 — auto-merge 미허용 환경이나 머지 완료까지 확인이 필요할 때. (`deleteBranchOnMerge`가 켜진 지금 스크립트의 원격 삭제는 서버 자동삭제와 중복이나 무해 — 이미 지워졌으면 조용히 넘어간다.) 스모크 테스트: `.claude/scripts/tests/test-pr-merge-behind.sh`.
   - 머지 후 로컬 `main` 갱신(`git checkout main && git pull`) 및 브랜치 정리

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
# → 그 폴더에서 작업(절대경로 편집/커밋). frontend·miniapp 의존성(node_modules)은 SessionStart 훅
#   (link-node-modules-on-session-start.ps1)이 새 세션마다 자동 정션 연결한다 — 수동 입력 0(N-132).
#   수동 실행도 가능: powershell -File .claude/scripts/link-node-modules.ps1
# 머지 후 정리 — remove-worktree 스킬/스크립트가 [정션 끊기 → worktree remove → 로컬 브랜치 정리]를
#   안전하게 한 번에 한다(수동으로 정션 먼저 끊던 절차를 도구화 — T-110 하드픽스). 대상 워크트리
#   바깥(메인)에서 실행하고, 먼저 -DryRun 으로 확인 가능. ⚠️ 현재 셸이 그 워크트리 안이면 거부되니 메인에서.
powershell -File .claude/scripts/remove-worktree.ps1 ../BookTimer-<task>   # 또는 /remove-worktree 스킬 (-DryRun·-Force·-KeepBranch)
```

- 워크트리 = **새 브랜치 한 세트**(같은 브랜치를 두 워크트리에 동시 체크아웃 불가). PR 우선 워크플로와 그대로 맞물린다.
- 미커밋·단독 변경이면 사후 분리도 가능하지만(미커밋은 브랜치에 안 묶임), **이상적 순서는 편집 전에 분리**다.
  "늦어서 곤란"해지는 시점은 엉뚱한 브랜치에 **커밋·push·머지까지 한 뒤**다.

### 🔁 연속 구현 전용 세션 — 자기 워크트리 삭제 금지·재사용 (필수, 2026-06-30)

한 세션을 **여러 작업을 순차로 처리하는 "연속 구현 전용 세션"**으로 쓸 때(사용자가 그렇게 지정), 그 세션은 **작업 사이에 자기 작업 환경을 해체하지 않는다.**

- **자기가 서 있는 워크트리(cwd)를 절대 삭제하지 않는다.** 한 작업(PR 머지)이 끝나도 `remove-worktree` 스킬·스크립트·`git worktree remove`를 **현재 작업 중인** 워크트리에 쓰지 않는다 — cwd가 사라지면 이후 모든 셸 명령이 실패하고 세션이 마비된다(실제 재발 2026-06-30 — [참조](claude-docs/claude-md-reference.md)).
- **워크트리는 한 번 만들고 계속 재사용한다.** 머지 후 그 워크트리를 그대로 두고, 다음 작업은 같은 워크트리에서 `git fetch origin && git checkout -b <다음브랜치> origin/main`으로 시작한다(메인 폴더에서 작업 중이면 거기서 브랜치만 갈아끼운다 — 새 워크트리 생성 불필요).
- **머지 후 정리는 "옛 작업 브랜치 로컬 삭제"까지만**(`git branch -D <머지된옛브랜치>`; 원격은 `deleteBranchOnMerge`가 서버 자동 삭제). **워크트리 폴더·cwd는 보존**한다.
- 작업이 끝나면 세션을 정리·종료하지 말고 **"완료 — 다음 작업 대기 중"**만 보고하고 대기한다(다음 지시가 같은 세션·같은 폴더로 온다).

> 위 「머지 후 정리 — remove-worktree」는 **일회성 작업 세션**(끝나면 폴더를 치우는)용이다. **연속 전용 세션은 그 정리를 자기 자신에게 적용하지 않는다** — 이 구분이 핵심. (soft 규칙. **2회+ 재발하면 훅으로 승격** — 예: 연속 세션 표식이 있을 때 `git worktree remove`/`remove-worktree`를 cwd 대상에 쓰면 차단하는 가드. 재발·승격 트래커는 troubleshooting 상단 표.)

### worktree로도 남는 공유 자원 (따로 조율)

폴더를 나눠도 repo 전체가 공유하는 것은 여전히 충돌하니 조율한다:

- **Flyway 버전 번호**(`V5__`, `V6__` …) — 세션별 번호 구역 배정 또는 머지 후 부여
- **공유 문서**(plan.md / changelog / README / 이 파일 / learning-notes / troubleshooting) — 작게·원자적으로, **편집 직전 재읽기**
- **앱 포트 8080** — 두 세션이 `bootRun` 하면 충돌 → 트리별 `server.port` 분리(또는 한 곳에서만 실행).
  **검증용 `bootRun`은 작업 종료 시 반드시 끈다(본인이 띄운 건 본인이 끈다).** 떠도는 잔재 강제 종료:
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen -EA SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
  ```
  ⚠️ **포트로 죽여도 gradle 데몬은 살아남아** 다음 커밋의 테스트 게이트와 락을 경합한다(T-078) — **bootRun 정리 = 8080 반납 + `./gradlew --stop`** 을 한 쌍으로.
- **bootRun docker-compose 컨테이너** — 컨테이너를 만드는 건 `bootRun`이다(테스트는 H2). 워크트리마다 따로 쌓이니 **8080 반납 때 함께 내린다**: `bash .claude/scripts/docker-cleanup.sh`(기본 Exited만 — 멀티세션 안전, `--all`이면 Up 포함·주의). `SessionEnd` 훅이 기본 모드를 자동 호출하므로 일상 누적은 방치해도 청소된다. (라벨 보호·훅 상세는 [참조](claude-docs/claude-md-reference.md).)
- **gradle 데몬·빌드 락** — 두 세션이 동시에 `./gradlew`를 돌리면 **무한 hang** 가능 → 한 세션에서만 빌드/커밋. hang 대처는 「🧪 TDD → ⚠️ 커밋이 무한 hang 하면」 절(T-078).
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

### 테스트 깊이 — 양이 아니라 distinct 실패 커버리지 (사용자 합의: 2026-06-06)

작성이 빨라도 유지보수·실행 시간·신호 희석·독립성 함정("구현과 테스트를 둘 다 Claude가 짜면 같은 가정을 공유")이라는
비용은 안 사라진다 — 예산은 변형 양산이 아니라 옳은 곳에 깊이로 쓴다(비용 전문은 [참조](claude-docs/claude-md-reference.md)).

> **원칙: 테스트마다 "이게 단독으로 잡는 진짜 실패가 무엇인가"에 답할 수 있으면 추가, 못 하면 보류.**
> 의도를 박는 행동 테스트 > 구현을 베끼는 테스트.

| 깊게 간다 (싸고 고가치) | 누른다 (브리틀·노이즈) |
|---|---|
| 도메인 로직 경계값 전수 (0일 / 여러 날 / cap 초과 / 자정 경계 / null / 빈 값) | 프레임워크·라이브러리 동작 (예: Spring 파라미터 바인딩) |
| 실패·권한 경로 (IDOR·남의 리소스 조작, 잘못된 입력, not-found, 미인증) | 정확한 UI 문자열·플래시 텍스트 |
| 불변식 (예: visibility 기본 PRIVATE opt-in, loginId 정규화·once-set) | getter/setter·단순 위임 |
| 사용자가 명세한 의도를 못 박는 행동 | 자유롭게 리팩터해야 할 구현 디테일 |

> **구체 예 2건(필수)** — ① 발견/노출/목록 기능엔 **null-state 엔티티(예: 온보딩 전 `login_id=null`)를 일부러 만들어
> 결과에서 빠지는지** 경계 테스트로 단언한다(완성된 픽스처만으론 영영 못 잡음 — 전문은 [N-055](claude-docs/learning-notes.md)).
> ② 부모 엔티티 삭제 경로엔 **자식 픽스처를 가진 부모를 만들어 실 H2 통합 테스트**로 FK 제약 위반 없이 삭제되는지
> 단언한다(mock은 FK를 검증 못 함 — 전문은 [T-023·T-029](claude-docs/troubleshooting.md)).

### 강제 (커밋 시 테스트 게이트)

- `.claude/hooks/require-tests-before-commit.ps1` 가 `git commit` 을 가로채,
  **스테이징에 `.java` 변경이 있으면 `./gradlew test` 를 실행**하고 실패 시 커밋을 차단한다.
- 역할 분담: **"테스트를 먼저"** 라는 판단은 이 가이드(소프트)가, **"테스트 통과 없이 커밋 불가"** 는 훅(하드)이 담당한다.
- 문서/설정 전용 커밋(`.java` 변경 없음)은 게이트가 자동으로 건너뛴다.

### ⚠️ 커밋이 무한 hang 하면 — esc 말고 강제 정리 (자주 재발)

이 게이트가 `./gradlew test` 를 돌리므로 **"git 이 멈춘" 것처럼 보여도 실제론 gradle 테스트가 hang** 한 것일 때가 많다 —
Claude Code 는 그 자식 프로세스 종료를 기다릴 뿐이라 **코어 버그가 아니다**(그래서 esc·머지로 안 풀리고 clear 로만 풀렸던 것).
흔한 뿌리 = **멀티 세션이 gradle 데몬·빌드 락을 동시 점유**. esc 는 이미 뜬 gradle 자식·데몬을 안 죽여 **다음 커밋도 또 hang**한다.

- **이젠 게이트가 자가차단(하드, 2026-07-01)**: 커밋 훅 `require-tests-before-commit.ps1` 의 `gradlew test` 가 **8분 타임아웃**(`BOOKTIMER_TEST_GATE_TIMEOUT_MS` 로 조정)으로 감싸여, 초과 시 **프로세스 트리 `taskkill /T` + `gradlew --stop` 자가복구 후 커밋 차단(exit 2)** 한다 → 45분 무한 freeze는 더 안 난다. **그래도 커밋이 8분+ 멈춰 있으면** 그건 게이트가 아닌 다른 빌드 hang일 수 있으니 아래 수동 정리로 간다.
- **감별**: `git status` 가 빠르면(0.x초) git·레포 자체는 정상 → 코어·레포 문제 아님. 떠도는 `java`(gradle 데몬) 잔존이 단서.
- **강제 정리**:
  ```powershell
  Get-Process java, git -EA SilentlyContinue | Stop-Process -Force
  Remove-Item .git\index.lock -EA SilentlyContinue
  ./gradlew --stop
  ```
- **예방**: 멀티 세션일 땐 **한 세션에서만 커밋/빌드**(gradle 은 워크트리를 나눠도 데몬·캐시를 공유해 경합). **bootRun 정리는 8080 반납에 더해 `./gradlew --stop` 까지**(포트만 죽이면 데몬이 남아 다음 게이트와 경합 — 위 「🪢 8080」 절). 배경: [troubleshooting T-078](claude-docs/troubleshooting.md), 다중 세션 N-032.

### 예외 (override)

- 커밋 명령에 `SKIP_TESTS` 토큰을 포함하면 게이트를 우회한다.
  - 정당한 경우에만: TDD red 단계의 **실패 테스트만 먼저 커밋**, 긴급 핫픽스 등.
  - 사용자가 명시적으로 허용한 경우에만 Claude 가 부착한다.

---

## 🖥️ 프론트 검증 — 로드순서·타이밍 버그는 실 브라우저로 (필수)

**Thymeleaf 템플릿 + Alpine/htmx/Phaser 같은 클라이언트 위젯**의 변경에서, 특히 **스크립트 로드 순서·타이밍·반응성(reactivity)**이 얽힌 부분은 **헤드리스 테스트·mock·preview만으로 검증을 끝내지 않는다.** 회귀 리스크가 있으면 **실 브라우저(Chrome 확장)로 실배포/로컬 페이지에 직접 붙어 콘솔 에러까지** 확인한다.

**왜**: `defer`/`async`가 실행 시점을 바꿔, 헤드리스 재현이 동기 로드로 단순화되면 **버그가 사라진 가짜 green**이 나온다 — 정원 Phaser 위젯에서 반복 실측(#356~364). 전문은 [참조](claude-docs/claude-md-reference.md), 개념은 N-082·N-083·T-053·T-054.

- **브라우저는 데스크톱 크롬이 기본** (사용자 지정 2026-08-13): 화면 확인·스크린샷은 `mcp__claude-in-chrome__*`(사용자 실 크롬)로 한다 — 클로드 내부 브라우저 패널은 사용자가 패널을 열어놔야만 스크린샷·클릭이 되고, 닫혀 있으면 "not compositing frames" 타임아웃이 난다. **단 dev 서버 기동(`preview_start`)은 내부 도구가 유일 경로**라 그것만 쓰고, 확인은 크롬 탭에서 한다 — 미니앱 목 모드는 `.claude/launch.json` 의 `miniapp-mock` 항목(`npm --prefix miniapp run dev:mock`, 포트 5174). ⚠️ **`launch.json` 은 gitignore 대상(머신 로컬)이라 새 워크트리·새 PC엔 없다** — 없으면 그 항목을 다시 넣고 시작한다. 미니앱은 폰 화면 기준이라 크롬 `Ctrl+Shift+M`(기기 모드)로 본다. ⚠️ vite는 `::1`에만 바인딩 — `127.0.0.1:5174`는 안 열리니 `localhost:5174`로.
- **진단**: 막히면 추측 전에 **실 배포/로컬 페이지에 Chrome 확장으로 붙어 콘솔·네트워크를 직접 읽는다.** "UI는 멀쩡한데 한 기능만 안 됨"일수록 콘솔에 답이 있다.
- **재현 하니스**: 만든다면 production의 `defer`/로드 순서·반응성 래핑까지 충실히 복제한다 — 동기 로드로 단순화하면 그 버그를 못 잡는다(T-053).
- **게이트**: 로드순서·타이밍·반응성이 걸린 변경은 **머지 전 실 브라우저 1회 확인**. 순수 로직은 단위테스트(TDD), 클라이언트 통합은 실 브라우저 — 역할 분담.
- **E2E(Playwright)는 로컬 수동 — 커밋 훅 금지, 승격은 CI 잡(머지 게이트)으로** (2026-06-26 결정): `frontend/e2e/` 표적 2개(로그인·정원 저장). 승격 트리거 = E2E가 잡았어야 할 회귀가 또 새거나, 스펙이 2~3개 흐름 이상으로 커질 때. (근거 전문은 [참조](claude-docs/claude-md-reference.md), 실행법은 N-127.)

---

## 🔒 CSRF 폼 세션 선확정 — 큰 SSR·익명 폼 GET 핸들러 (T-033·T-049)

**`th:action` 폼이 있는 SSR 페이지를 렌더하는 GET 핸들러는, 폼을 렌더하기 전에 CSRF 토큰을 한 번 당겨 세션을 선확정한다.**

**왜**: Thymeleaf의 CSRF 숨김필드가 세션을 lazy 생성하는데, 큰 페이지는 그 시점에 응답 버퍼가 이미 커밋돼 `IllegalStateException`으로 **그 페이지만 500** — 익명 폼·폼 여럿인 큰 페이지가 위험, 4회+ 재발(전문은 [참조](claude-docs/claude-md-reference.md), 개념은 N-077).

- GET 핸들러에서 폼 렌더 전에 `CsrfTokenUtil.precommit(request)`(`src/main/java/com/booktimer/web/CsrfTokenUtil.java`) 호출 — CsrfToken이 있으면 세션 즉시 생성, 없으면 no-op. 예: `web/PasswordResetController`.
- 훅 강제 불가(정적 감지 오탐 30~50%) — 이 prose 규칙 + 코드 리뷰가 담당.

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
- **자주 재발(2회 이상)하는 트랩은 `troubleshooting.md`(참조용)에 더해 이 `CLAUDE.md`(항상 로드)의 해당 섹션에도 승격**한다 — 매번 troubleshooting 을 안 펼쳐도 바로 대처하게. (예: git/gradle 무한 hang → 「🧪 TDD → ⚠️ 커밋이 무한 hang 하면」, T-078. 사용자 합의: 2026-06-22.)
- **재발 카운팅 = `troubleshooting.md` 상단 「🔁 재발·승격 트래커」 표로 한다 (필수).** `T-###` 를 새로 쓸 때 같은 트랩의 재발이면 ① 항목 끝에 `N회차(이전 T-### 재발)` 명시 ② 트래커 표의 회차·승격상태 갱신(신규 1회는 표에 안 올리고 2회째에 군으로). 표에서 **2회+인데 미승격**이 보이면 승격 — **prose 한 줄보다 하드픽스(훅·스크립트) 우선**. 답변에서도 재발이면 "이건 N회차"를 짚는다. (배경은 [참조](claude-docs/claude-md-reference.md).)

---

## 🈲 한글 커밋 메시지 — `.commit-msg-tmp` 사용 (T-026)

PowerShell 5.1 에서 한글 커밋 메시지를 인라인으로 넘기면 깨진다.
→ 메시지를 **UTF-8 파일** `.commit-msg-tmp` 로 쓰고 `git commit -F .commit-msg-tmp` 로 커밋.

- `.commit-msg-tmp` 는 `.gitignore` 에 등록되어 있어 추적되지 않는다 (잔재 add 방지).
- **(훅 `check-commit-message.ps1`이 하드 강제 — `-m` 인라인 한글 감지 시 차단. 우회: `ALLOW_INLINE_MSG` 토큰. `-F` 파일경유는 검사 대상 아님.)**

---

## 🛠️ 빌드 / 실행 메모

- 빌드: `./gradlew build` (Windows: `gradlew.bat`)
- 컴파일만: `./gradlew compileJava`
- 실행: `./gradlew bootRun` — **`local` 프로파일** 자동 활성(build.gradle `bootRun` 태스크). Security 가 있어 전 엔드포인트 기본 잠김(로컬은 아래 시드 계정으로 로그인).
- **로컬 테스트 계정(시드)**: `bootRun` 시 `LocalTestAccountSeeder(@Profile("local"))`가 멱등하게 시드.
  - login_id: `testid`(소문자 — loadUserByUsername이 입력을 소문자화하지 않음), 비번: `1234qwer!!`
  - 이미 존재하면 "시드 생략" 로그만 출력(멱등). 재기동에도 중복 생성 없음.
  - admin 뷰 필요 시 코드 추가 없이 `BOOKTIMER_ADMIN_LOGIN_IDS=testid` 환경변수로 `AdminAccountSeeder`가 승격.
  - **운영(`booktimer.app`)에도 동일 계정(`testid` / `1234qwer!!`)이 존재** — 로컬·운영 양쪽에서 Chrome MCP 폼에 직접 입력해도 된다. 로그인 화면이 뜨면 이 계정으로 바로 로그인하고, 새 회원가입은 금지.
- DB: `compose.yaml` 의 MySQL 이 DevTools docker-compose 연동으로 자동 기동 (Docker 필요).
  - **이 컨테이너를 만드는 건 `bootRun`이지 `./gradlew test`가 아니다**(테스트는 H2 — 아래). bootRun이 워크트리별로 MySQL 컨테이너를 띄워 누적되니, **검증을 마치거나 주기적으로** `bash .claude/scripts/docker-cleanup.sh`(기본 Exited만, `--all`이면 Up 포함)로 정리한다 — `working_dir` 라벨로 BookTimer 소속만 지우고 타 프로젝트는 보호. 세션 종료 시엔 `SessionEnd` 훅이 기본 모드로 자동 정리한다(gap#3 자동배선). 멀티세션 동시 작업 시 정리 주의는 「🪢 다중 세션 → bootRun docker-compose 컨테이너」 절 참고.
- 테스트 DB: 운영은 MySQL, **테스트는 H2 인메모리**(`src/test/resources/application.properties`) — Docker 없이 테스트 독립 실행. 테스트 시 docker-compose 자동 기동은 꺼짐(`spring.docker.compose.enabled=false`)
- toolchain: Java 21 (로컬에 없어도 foojay-resolver 가 자동 다운로드 — 노트 N-002)
- **프론트 번들 (정원 편집)**: `npm --prefix frontend run build` — `src/main/resources/static/garden/garden.js` 재생성. 정원 관련 TS 수정 후 `bootRun` 전에 반드시 재실행 (T-063). 산출물은 git add·commit까지 해야 반영.
  **(훅 `require-bundle-build.ps1`이 하드 강제 — `frontend/**` 스테이징 커밋 전 재빌드·diff 검사. 10섬 전수 커버(CI의 garden-only 사각 보완). 우회: `SKIP_BUNDLE_CHECK` 토큰.)**

### 미니앱 개발 루프 — 기본은 브라우저 목 모드 (2026-08-12 실측 갱신)

미니앱 화면 작업 시 **Claude가 직접 `npm --prefix miniapp run dev:mock`을 백그라운드로 띄워 브라우저(vite HMR, 포트 5174)로 확인한다** — 사용자에게 명령어를 요구하지 않는다(사용자 지정). 목 모드는 `api.ts`가 서버 대신 `src/dev-mock.ts`의 픽스처를 돌려주고 토큰을 더미로 두므로, 서버·토스 SDK·에뮬레이터 없이 홈·서재·소셜·스토리·기록·목표가 전부 뜬다. 목에 없는 경로는 404로 던져 조용히 빈 화면이 되지 않는다.

- 픽스처를 고치려면 `miniapp/src/dev-mock.ts` 한 곳(모듈 메모리 상태라 새로고침이 초기화다).
- 목 코드는 프로드 번들에서 통째로 잘린다(`import.meta.env.DEV` 게이트 + dynamic import). `deploy.sh`가 `__DEV_MOCK__` 부재를 배포 전에 재확인한다.
- **실기기·SDK 연동(실로그인·광고·알림 동의)만** `bash miniapp/deploy.sh --expect "<이번 변경 문구>"` + 실기기다 — 목 모드로는 원리상 확인되지 않는다. ⚠️ **`--expect` 마커는 압축에 살아남는 UI 한글 문자열로 고른다** — `slice(0,15)` 같은 코드 조각은 minify가 `slice(0,EGe)`로 변형해 검증이 헛돈다(T-153).
- **배포 권한 경계**: Claude는 `deploy.sh` 업로드(deploymentId 확보)까지다 — **앱인토스 콘솔(심사 제출·[출시하기])은 Claude 접근이 차단**돼 있어 사용자 몫. 배포 보고 시 "업로드 완료, 심사 제출은 콘솔에서"로 경계를 명시하고 출시 완료로 오보고하지 않는다.
- ⚠️ **잔디 방향 규약**: 서버 `ContributionGraphBuilder`가 weeks를 뒤집어 보낸다 — **`weeks[0]` = 최신 주 = 왼쪽**, `monthLabels`도 그 순서 기준. 최근 N주는 `slice(0, N)`. oldest-first로 가정하면 안 된다 — 두 화면이 같은 오가정으로 깨졌고 조사 서브에이전트도 오독했다(2026-08-12 핫픽스). 규약 테스트는 `api.ts` 주석 + 최신 주만 초록인 픽스처 단언.
- **테스트 하니스**(`npm --prefix miniapp test` = vitest): **jsdom 없음** — `renderToStaticMarkup` 정적 렌더라 effect·클릭이 안 돈다. 로직은 순수 함수로 꺼내 계측하고, effect·핸들러에 대한 부정 단언("호출 안 한다")은 항상 통과라 금지(T-149). TDS `BottomSheet` 등 **포털 컴포넌트는 정적 렌더에서 마크업이 통째로 빈다**(그래서 시트는 자체 구현). `miniapp/.env.test`가 `.env.local` 누수를 차단한다 — 머신 로컬 env로 테스트가 깨지면 이걸 의심.

⚠️ **샌드박스 앱의 dev(핫 리로드) 연결은 웹 미니앱에서 불가능하다** — 샌드박스의 dev 프로토콜은 granite(RN) 전용이고 `@apps-in-toss/web-framework`엔 dev 서버·vite 플러그인 자체가 없다(2026-08-12 실측, T-152). 그래서 `.claude/scripts/miniapp-emulator.ps1`은 **개발 루프의 기본 경로가 아니다** — 향후 지원 대비 + 샌드박스 앱 설치·`adb reverse` 자동화용으로 보존한다(사용법은 스크립트 헤더 주석). 부팅이 5분+ 걸리면 Quick Boot 스냅샷 꼬임(T-151) — `-ColdBoot`로 재시도.
