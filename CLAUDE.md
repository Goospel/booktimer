# BookTimer — 프로젝트 작업 규칙 (Claude Code)

> 이 파일은 글로벌 `~/.claude/CLAUDE.md` 와 **합쳐서** 적용된다 — 여기엔 BookTimer 고유 규칙만 둔다.
> 규칙의 배경·사례·경위는 [claude-docs/claude-md-reference.md](claude-docs/claude-md-reference.md)(절 제목이 이 파일과 대응) — 규칙을 의심하거나 고칠 때 연다.

프로젝트 개요·도메인 규칙은 [README.md](README.md), 로드맵·설계는 [plan.md](plan.md), 갱신 이력(변경 일지)은 [claude-docs/changelog.md](claude-docs/changelog.md), 트러블슈팅은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md), 경쟁사는 [claude-docs/competitors/](claude-docs/competitors/README.md) 참고.

⚠️ **이 EC2에는 BookTimer 말고 하나가 더 산다** — 다른 미니앱 **facefit**의 API 서버(`facefit-api` 컨테이너)가 같은 인스턴스·같은 MySQL에 얹혀 있다. **서버 자원(특히 메모리)·MySQL·Caddy 문제를 볼 때 먼저 읽는다**: [claude-docs/facefit-tenant.md](claude-docs/facefit-tenant.md) — 자원 몫 · 증상별 감별 · `docker stop facefit-api`로 facefit만 끄는 법.

---

## 🗺️ 계획 우선 — 의미 있는 코드 작업은 계획부터 (필수)

**의미 있는 코드 작업(기능 추가·버그 수정·리팩터·설계 변경 등)은 바로 구현하지 않는다.**
먼저 관련 코드를 파악해 **구현 계획을 제시하고 사용자 승인을 받은 뒤** 구현에 들어간다.

> **왜·분리 수단**: 계획은 깊게, 구현은 가볍게 — 두 단계를 다른 모델·effort가 맡는다(사용자 합의 2026-06-08). 계획 없이 바로 구현하면 이 분리가 깨진다.
> 설계=`designer` / 구현=`implementer`(**자기 diff의 커밋·PR·머지 절차까지**) / 리뷰=`reviewer`. 역할별 모델·effort 값은 `~/.claude/agents/*.md` frontmatter가 단일 출처다 — 이 파일에 사본을 두지 않는다(사본은 낡는다).
> **설계 판단이 들어가는 순간 designer 위임이 기본**(예외: 판단 없는 사소·자명 + 합의된 설계의 연속 작업).

### 계획에 담을 것

- **현황·원인** — 관련 코드를 실제로 읽고(추측 금지) 무엇이 어떻게 동작하는지 / 무엇이 문제인지.
- **해법 옵션과 추천** — 1개 이상 옵션 + 트레이드오프 + 추천안과 그 이유.
- **변경 범위** — 건드릴 파일·코드 수준(시그니처·핵심 스니펫).
- **엣지케이스·TDD 계획** — 경계 테스트로 무엇을 못 박을지. **계획의 TDD 절은 평소의 RED → GREEN 순서를 명시한다(필수)** —
  구현 단계(`implementer`)가 ① 실패 테스트 먼저 → ② Red 확인 → ③ 최소 구현 → ④ Green 확인을 밟도록 못 박는다. 테스트 가능한 로직이 있으면
  이 절차를 생략·약화하지 않는다 — 단위테스트가 무의미한 순수 시각 변경만 이유와 함께 preview/수동 검증을 게이트로 명시한다.
- **미검증 가정 — 산문 말고 체크리스트로 (필수)** — 설계가 「해봐야 안다」고 남긴 것은 항목마다
  `CHECK:`(실행할 명령·재현 절차) / `EXPECT:`(성공에서만 나오는 신호) / `EVIDENCE:`(측정 결과, 초기값 `pending`)를 달아
  **PR 직전 전부 채운다**. `EVIDENCE:`가 `pending`이면 체크박스가 켜져 있어도 **미충족**이다 — 체크는 주장이고 증거가 증명이다.
  못 채운 항목은 지우지 말고 `ABANDON: <id> <이유>`로 남겨 PR body에 드러낸다(조용한 범위 축소 금지 — 보이는 포기가 정직하다).
  ⚠️ `EXPECT:`는 **성공에서만 나오는 문자열**로 잡는다 — 「완료」처럼 실패해도 찍히는 말은 게이트가 아니다.
  이 원장은 읽고 끝낼 목록이 아니라 **머지 전 체크리스트**다 — T-176은 「이 애니메이션은 목 모드에서 판단」이 **설계에 적혀 있었는데도** 건너뛰고 머지됐다(산문은 게이트가 아니었다).
  ⚠️ `CHECK:`가 탈퇴·삭제·발송처럼 **되돌릴 수 없는 동작**에 닿으면 **버리는 계정으로만** 짠다 — 본 계정은 「화면만 보고 누르지 않기」로도
  그 단추 앞에 세우지 않는다. 운영은 binlog가 꺼져 있어 되살릴 길이 하루 1회 백업뿐이다(T-276 — 운영 본인 계정이 그렇게 지워졌다).
- **경쟁사 대조 — 사용자에게 보이는 변경이면 (필수, 2026-10-22~)** — 화면·기능·스토어 문구·온보딩·수익화를 추가·개선하는
  계획은 [claude-docs/competitors/](claude-docs/competitors/README.md)를 읽고 세 줄을 담는다: ① 경쟁사에 같은 게 있나
  (대조표의 행 id와 판정으로 — 같음·비슷·다른 방식·북트랙만·우리만, 경쟁사 쪽을 모르면 「모름」) ② 이 변경이 우리 차별점을
  키우나 흐리나 ③ 경쟁사를 따라가는 변경이면 그 근거(사용자 요구·실측 — 「경쟁사에 있으니까」는 근거가 아니다).
  인프라·훅·문서, 그리고 사용자에게 보이는 동작이 바뀌지 않는 리팩터·버그 수정이면 `경쟁사 대조: 해당 없음` 한 줄 —
  **빈칸으로 두지 않는다**(모호 기준은 0으로 수렴한다). 경쟁사 새 소식을 알게 되면 그 문서부터 고친다.
- **규모·리스크** — 작업량 가늠과 회귀 위험.

### 어떻게

- plan mode가 켜져 있으면 **ExitPlanMode**로 계획 승인을 요청한다.
- 아니면 **계획을 글로 제시하고 "이대로 진행할까요?"**로 확인한 뒤, 승인을 받고서 구현한다.
- 요구사항·접근이 애매하면 구현 전에 **AskUserQuestion**으로 먼저 묻는다.

> **⚠️ 계획 요청 시 구현 승인은 별개다 (재발 방지, 필수).** 사용자가 "계획 짜자 / 설계해"처럼 계획을 요청하면
> **그 세션의 산출물은 계획까지다** — 명시적 구현 지시("구현해 / 진행해") 전엔 코드(Edit/Write)에 손대지 않는다.
> 특히 **설계 방향을 좁히는 AskUserQuestion 옵션 선택은 계획의 일부일 뿐 구현 승인이 아니다.**
> 애매하면 구현 직전 **"구현 들어갈까요?"**로 한 번 더 확인한다(의심되면 계획 쪽으로).

### 단계 분리 — 계획 md 핸드오프 (큰 작업 한정)

위 「분리 수단」을 **명시적·재현가능**하게 만드는 핸드오프 방식. **모든 작업에 강제하지 않는다** — 마찰이 이익을 잡아먹는다. (사용자 합의: 2026-06-09)

- **언제 (트리거)**: 규모가 크거나·다단계거나·회귀 리스크가 큰 작업. 작은/자명한 작업은 위 「어떻게」(ExitPlanMode·글 승인)로 충분 — md를 만들지 않는다.
- **설계 = `designer`**: 산출물 = `claude-docs/plans/<YYYY-MM-DD>-<요약>.md` **1개**. 내용은 위 「계획에 담을 것」 템플릿 그대로. 이 md는 **작업용 임시 산출물**(`claude-docs/plans/`는 `.gitignore` 대상이라 커밋되지 않는다) — repo 로드맵 [plan.md](plan.md)와 **다르다(혼동 금지)**. 구현 완료 후 삭제하거나 PR 설명으로 흡수한다(stale 계획 방치 금지).
- **구현 = `implementer`**: 그 md를 읽고 **승인된 계획대로** TDD 구현한다. 다시 설계하지 않는다(설계 단계의 사고를 신뢰).
- **드리프트 규칙 (중요)**: 구현 중 계획이 틀렸거나 빠진 게 보이면 **임의로 벗어나지 말고 멈추고 사용자에게 보고**한다. 사소한 보정은 재량이되, **설계·범위가 흔들리면 `designer`를 다시 부른다**. 이게 없으면 "구현이 몰래 계획을 이탈" = 분리의 의미가 사라진다.
- **절차(커밋·PR·머지) = diff를 만든 쪽이 직접**: 방금 diff를 만든 세션(코디네이터든 `implementer`든)이 커밋·PR·머지까지 마무리한다 — 무엇을 왜 바꿨는지 아는 쪽이 PR body·changelog를 가장 정확·싸게 쓴다.
  `operator`는 **컨텍스트 없는 절차 전용**으로 좁힌다: 작업환경 준비(fetch·워크트리·정션) · 잔재 정리(머지된 브랜치·8080·docker·gradle) · 일괄 상태 확인 · 코디네이터가 다른 갈래로 바쁠 때의 병렬 절차.
- **리뷰 = `reviewer` — 밝은선 4종이면 머지 전 필수 (2026-08-22 재개정)**: 코드 diff가
  ① Flyway 마이그레이션 포함 ② 보안·인증 경로(Security·auth·비밀번호·CSRF) ③ 수익 경로(제휴·구매링크·광고)
  ④ 코드 ±150줄 이상 — 중 **하나라도** 해당하면 머지 전 `reviewer` 독립 리뷰를 필수로 돈다. ①~③은 줄 수 무관 —
  53줄짜리 알라딘 `&amp;`가 수수료 전체를 7주 무추적으로 만들었다. 실측 발동률 = 최근 40 PR 중 코드 PR의 61%.
  옛 문구 「회귀 리스크 큰 작업만 opt-in」은 벨트 로그 실측에서 리뷰를 94건→2건으로 무너뜨렸다 — **모호 기준은
  0으로 수렴한다**(designer 사문화와 같은 실패, 그래서 밝은선이다). consolidate 때 벨트 로그로 reviewer 비율을
  재확인한다(기대 ≈ 코드 PR의 60% — 다시 0에 수렴하면 문구가 아니라 운용을 의심).

### 세션 메타 기록 — 모델 (자동)

각 세션은 **자기 산출물에 자기 모델을 기록**한다 — 어느 모델이 낸 결과인지 사후 추적용. (사용자 합의: 2026-06-26)

- **모델**: 환경 주입값(시스템 프롬프트의 모델 ID)에서 자동 인식해 채운다. 세션 중 바뀌면 그 시점 값.
- **어디에**:
  - 계획 md(큰 작업만): 상단 blockquote에 `> 🧭 세션 메타: model=… · effort=…` 한 줄. **여기선 effort도 적는다** — 계획 md를 쓰는 서브에이전트는 자기 effort를 정의 파일 frontmatter(`effort:`)에서 안다.
  - 커밋(모든 세션): trailer에 `Session-Model: …`(`Co-authored-by` 옆). squash 머지 후에도 `main`에 텍스트로 보존된다 — 단 구조 조회(`%(trailers)`)로는 안 잡히니 `git log --grep='Session-Model'`으로 읽는다(T-104).
- ⚠️ **커밋 trailer에 `Session-Effort`는 적지 않는다 (2026-08-13 폐지)**: 분업 커밋엔 여러 모델·effort가 섞여 한 값을 적으면 틀린 기록이 되고, 서브에이전트 모델은 벨트 로그(`~/claude-logs/subagent-model.jsonl`)가 자동으로 남긴다. 역할 분담이 남길 가치가 있으면 **PR body에 자연어로**.
- soft 규칙이라 내가 누락할 수 있다 — 누락이 2회+ 재발하면 hard(훅)로 승격한다(해당 T 파일의 회차 줄).

### 예외 (계획 생략 가능)

- **사소·자명한 변경** — 오타·주석·한 줄 문구·명백한 설정값 등 설계 판단이 없는 것.
- 사용자가 **"바로 해" / "구현해"**라고 명시했거나, 직전에 이미 합의된 계획의 연속 작업일 때.
- 경계는 상황에 맞게 판단하되, 의심되면 **계획 쪽으로 기운다**(바로 구현보다 한 번 보여주는 게 안전).

---

## 🔀 Git 워크플로 — PR 우선 (필수)

**`main` 에 직접 push 하지 않는다.** 모든 변경은 브랜치 → PR → 머지 순서를 따른다. 예외는 사용자가 "main 에 바로" / "직접 push"라고 명시했을 때뿐이다(그 밖엔 훅 `block-main-push.ps1`이 차단한다).

### 절차

1. **브랜치 생성** — `git fetch origin` 후 `origin/main` 에서 분기
   - 네이밍: `feat/<요약>`, `fix/<요약>`, `docs/<요약>`, `chore/<요약>`
   - 예: `feat/reading-timer-entity`, `docs/t183-review-overlay`
2. **작업 + 커밋** — 의미 단위로 커밋
   - **커밋 제목에 PR 번호(`(#NNN)`)를 직접 넣지 않는다** — squash 머지 시 GitHub가 자동 부착해 중복된다(훅 `check-commit-message.ps1`이 차단).
   - **커밋 trailer에 세션 메타(`Session-Model`)를 남긴다** — 「🗺️ 계획 우선 → 세션 메타 기록」 절. 한글 메시지는 「🈲 한글 커밋 메시지」 절.
3. **push** — `git push -u origin <branch>`
4. **PR 작성** — `gh pr create` 로 작성
   - **보고 감사 — 숫자는 보고 시점에 다시 잰다 (필수)** — PR body·답변에 넣는 모든 수치(테스트 건수·검출 건수·측정값·건드린 파일 수)는
     **그 자리에서 재측정**하거나 「미검증」이라 표시한다. 기억으로 적은 숫자 · 내가 고른 깨끗한 값으로만 검증한 숫자가 실제와 어긋나는 것은 가장 조용한 보고 결함이다.
   - **미검증 가정 원장을 PR body에 옮긴다** — 계획에 그 절이 있었으면 `N/N 충족`과 **`ABANDON` 줄 전부**를 붙인다.
     항목이 남아 있으면 머지하지 않는다(못 채웠으면 `ABANDON`으로 이유를 드러내고 머지 여부를 사용자에게 묻는다).
   - **갱신 이력 항목을 PR 브랜치 안에 포함한다 (필수)** — 그 작업을
     [claude-docs/changelog.md](claude-docs/changelog.md) 표 **맨 아래에 한 줄**로 남긴다(일자 / 한 일).
     **PR 번호는 넣지 않는다**(번호 반영 추가 커밋 = push·CI 2회).
     plan.md에 원래 없던 즉흥 작업도 **무조건 남기고, 같은 PR 커밋에 함께** 넣는다 — 사후 보충 PR이 생기면 누락이다.
   - **plan.md 본문도 실제와 일치시킨다 (필수 sweep)** — 즉흥 기능이면 섹션 신설/"완료 ✅", 백로그 상태 변경이면 반영,
     설계가 바뀌었으면 옛 기록과 화해. "plan.md에 원래 그 항목이 있었는가"와 **무관하게** 점검하고(없던 작업일수록 더),
     마무리 직전 자가 점검: **"changelog 한 줄 + plan.md 본문이 실제와 일치하는가? 안 그러면 지금 고친다."**
5. **머지** — 사용자 확인 후. **표준 경로 = `bash .claude/scripts/pr-merge.sh <PR번호> --arm`** (레포 `allow_auto_merge=true`): `gh pr merge --auto --squash`를 걸고 → `mergeStateStatus`를 1회 점검해 **BEHIND면 `gh pr update-branch`**, DIRTY면 rebase(`--rebase` 동반 시)로 풀어준 뒤 **즉시 종료**한다 — 머지는 서버(`--auto`)가 `test` 통과 시 마저 하므로 세션을 묶지 않는다(머지 hang 클래스 제거, T-094).
   - **main 머지 = 운영 배포** — [deploy.yml](.github/workflows/deploy.yml)이 테스트 → ECR → EC2 blue-green 전환까지 한다. 바뀐 파일이 전부 `**.md`·`claude-docs/`·`.claude/`·`app/`(앱은 EAS로 따로 게시)이면 건너뛴다. 전역 규칙의 「머지가 곧 배포인 레포」다.
   - ⚠️ **bare `gh pr merge --auto --squash` 단독은 쓰지 않는다** — 이 레포는 「머지 전 브랜치 최신화 필수」 정책 + auto-update off라 BEHIND면 영영 대기한다(T-111).
   - 스크립트 밖에서 머지하면 **`mergeStateStatus` 진단이 먼저다** — `no checks reported`는 DIRTY(충돌)여서 CI가 안 붙은 것일 수 있다(T-083). **DIRTY** → `git rebase origin/main` → `git push --force-with-lease` / **BEHIND** → `gh pr update-branch <PR>` 후 CI 재실행 / **BLOCKED** → CI 통과 후 / **CLEAN** → 즉시 머지.
   - **동기 머지(이 세션에서 끝까지 보고)** = `bash .claude/scripts/pr-merge.sh <PR번호>` (`--arm` 없이): DIRTY 즉시 차단(또는 `--rebase` 자동 해결) + BEHIND 자동 해소 + CI 폴링 + 하드 타임아웃(12분) — 머지 완료까지 확인이 필요할 때.
   - **원격 브랜치 삭제는 `deleteBranchOnMerge=true`가 서버에서 처리**한다 — `--delete-branch`·수동 `gh api DELETE`를 쓰지 않는다(T-106 · 워크트리에서 `--delete-branch`는 `'main' is already used by worktree`로 깨진다, T-095). 머지 확인 후 **로컬만** 정리: 베이스 브랜치 checkout 후 `git branch -D <branch>`.
   - **연쇄 PR**(다음 분기가 이 머지에 의존)은 **`gh pr view <PR> --json state`=`MERGED` 확인 뒤** `origin/main`에서 다음 분기를 딴다 — 폴링이 `TIMEOUT`/`OPEN`/`DIRTY`로 끝난 건 미머지라, 머지 전제로 따면 직전 PR 변경이 빠진 채 시작된다(T-096).
   - ⚠️ **changelog 중복 행 (T-210)**: 한 브랜치가 자기 changelog 행을 두 커밋에서 건드리면(기능 커밋에서 추가 → 리뷰 커밋에서 보강) `.gitattributes`의 `merge=union`(T-098)이 rebase 때 초판과 최종본을 **둘 다** 남긴다 — 충돌도 테스트 실패도 없고, 초판을 지우는 정리 커밋은 다음 rebase에서 되살아난다. 그런 브랜치는 rebase 전에 `git reset --soft $(git merge-base HEAD origin/main)` 후 **한 커밋으로 합친다**. 훅 `require-changelog-no-dup.ps1`(push)·`require-single-changelog-commit-before-rebase.ps1`(rebase)과 `pr-merge.sh --rebase`가 막아 주지만 PowerShell 훅이라 원격(웹) 세션엔 안 붙는다 — 그땐 rebase 직후 `git diff origin/main -- claude-docs/changelog.md`로 자기 행이 1줄인지 확인한다.
   - 머지 후 로컬 `main` 갱신(`git checkout main && git pull`) 및 브랜치 정리

---

## 🪢 다중 세션 동시 작업 — 워크트리 분리 (필수)

여러 세션이 한 폴더(워킹 트리)를 공유하면 파일·브랜치가 충돌한다 — 같은 폴더의 `git checkout`은 폴더 전체 파일을 갈아끼워 다른 세션까지 흔든다.
**격리 단위는 브랜치가 아니라 워킹 트리(폴더)다.**

### 작업 시작 전 — 다른 세션 확인 (필수)

의미 있는 작업(편집/커밋)을 시작하기 전에 **git 상태부터 확인**한다:

```
git branch --show-current   # 예상 브랜치인가? (다른 세션이 바꿔놨을 수 있음)
git status --short          # 내가 안 만든 미커밋 변경이 있나?
git worktree list           # 워크트리가 이미 여럿인가?
```

브랜치가 예상과 다르거나 / 내가 안 만든 변경이 있거나 / 워크트리가 여럿이면 → **다른 세션이 이 폴더를 쓰는 중**일 수 있다. (SessionStart 훅 `warn-multi-session.ps1`이 세션 시작 때 이 정보를 띄우고 경고하지만, 판단·분리는 이 규칙이 한다.)

### 다른 세션이 떠 있으면 — worktree로 분리

이 폴더에서 `git checkout` 하지 말 것(다른 세션의 브랜치를 바꿔 망친다). 대신 **편집 전에 별도 워크트리로 분리**한다 — 엉뚱한 브랜치에 커밋·push·머지까지 한 뒤면 곤란해진다:

```
git fetch origin
git worktree add ../BookTimer-<task> -b <type>/<summary> origin/main
# → 그 폴더에서 작업(절대경로 편집/커밋). frontend·miniapp node_modules 정션은 SessionStart 훅이 새 세션마다
#   자동 연결한다(수동: powershell -File .claude/scripts/link-node-modules.ps1).
# 머지 후 정리 — remove-worktree(일회성 세션 전용): 정션 끊기 → worktree remove → 로컬 브랜치 정리를 한 번에.
#   대상 워크트리 바깥(메인)에서 실행(그 워크트리 안이면 거부), 먼저 -DryRun 으로 확인 가능.
powershell -File .claude/scripts/remove-worktree.ps1 ../BookTimer-<task>   # 또는 /remove-worktree 스킬 (-DryRun·-Force·-KeepBranch)
```

- ⚠️ 정션이 걸린 워크트리를 `git worktree remove`로 직접 지우지 않는다 — 정션을 따라가 메인의 node_modules까지 비운다(T-110). 정리는 위 스크립트로.
- 워크트리 = **새 브랜치 한 세트**(같은 브랜치를 두 워크트리에 동시 체크아웃 불가). PR 우선 워크플로와 그대로 맞물린다.

### 🔁 연속 구현 전용 세션 — 자기 워크트리 삭제 금지·재사용 (필수, 2026-06-30)

사용자가 한 세션을 여러 작업 순차 처리용으로 지정하면, 그 세션은 작업 사이에 자기 작업 환경을 해체하지 않는다:

- **자기 cwd 워크트리를 절대 삭제하지 않는다** — `remove-worktree`·`git worktree remove`를 자기 자신에게 쓰면 cwd가 사라져 세션이 마비된다. 위 「머지 후 정리 — remove-worktree」는 **일회성 세션 전용**이다 — 이 구분이 핵심.
- **워크트리는 재사용**: 머지 후 같은 폴더에서 `git fetch origin && git checkout -b <다음브랜치> origin/main`으로 다음 작업 시작. 정리는 옛 브랜치 로컬 삭제(`git branch -D`)까지만, 폴더·cwd는 보존.
- 작업이 끝나면 종료하지 말고 **"완료 — 다음 작업 대기 중"**으로 대기.

### worktree로도 남는 공유 자원 (따로 조율)

폴더를 나눠도 repo 전체가 공유하는 것은 여전히 충돌하니 조율한다:

- **Flyway 버전 번호**(`V5__`, `V6__` …) — 세션별 번호 구역 배정 또는 머지 후 부여
- **공유 문서**(plan.md / changelog / README / 이 파일 / troubleshooting) — 작게·원자적으로, **편집 직전 재읽기**
- **앱 포트 8080** — 두 세션이 `bootRun` 하면 충돌 → 트리별 `server.port` 분리(또는 한 곳에서만 실행).
  **검증용 `bootRun`은 작업 종료 시 반드시 끈다(본인이 띄운 건 본인이 끈다).** 떠도는 잔재 강제 종료:
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen -EA SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
  ```
  ⚠️ **bootRun 정리는 8080 반납까지다 — `./gradlew --stop`을 붙이지 않는다.** `--stop`은 이 머신의 **모든** gradle 데몬을 멈춰 다른 세션이 돌리던 빌드까지 죽인다(T-235·T-078 4회차). 포트로 앱을 죽이면 데몬은 idle로 돌아가고, idle 데몬은 빌드 락을 쥐지 않으며 3시간 뒤 스스로 내려간다.
- **bootRun docker-compose 컨테이너** — 컨테이너를 만드는 건 `bootRun`이다(테스트는 H2). 워크트리마다 따로 쌓이니 **8080 반납 때 함께 내린다**: `bash .claude/scripts/docker-cleanup.sh`(기본 Exited만 — 멀티세션 안전, `--all`이면 Up 포함·주의). `SessionEnd` 훅이 기본 모드를 자동 호출하므로 일상 누적은 방치해도 청소된다.
- **gradle 데몬·빌드 락** — 두 세션이 동시에 `./gradlew`를 돌리면 **무한 hang** 가능 → 한 세션에서만 빌드/커밋. hang 대처는 「🧪 TDD → ⚠️ 커밋이 무한 hang 하면」 절(T-078).
- **"File modified since read" 가드는 버그가 아니라 덮어쓰기 직전 보호** — 재읽기 → 그쪽 변경 보존 → 내 것만 재적용이 정답

---

## 🧪 TDD — 테스트 먼저 (필수)

**기능을 구현할 때는 반드시 테스트를 먼저 작성**하고, 그 테스트로 기대 동작을 확인한 뒤 구현한다.
Red(구현 전 실패 테스트를 **실제로 실행해** 「의도한 이유로」 실패하는지 눈으로 확인 — 아직 구현이 없어서 실패해야 정상) → Green(**최소 구현** 후 다시 실행해 통과 확인) → Refactor(통과를 유지하며 구조 정리).
도메인 로직(예: Lazy 누적 계산, cap 적용, 일일 이월)은 경계값 테스트를 먼저 짠다 — 0일 경과 / 여러 날 경과 / cap 초과 / 자정 경계 등.

> **진행 가시성 (필수)**: Claude 는 기능 구현 시 항상 이 순서를 **밖으로 드러낸다** —
> ① 실패 테스트를 먼저 보여주고 → ② 실행해 **Red(실패)** 임을 보고한 뒤 → ③ 구현하고 →
> ④ 다시 실행해 **Green(통과)** 임을 보고한다. "테스트 먼저 짰다"고 말만 하지 않고
> 실패→통과 전환을 실제 실행 결과로 확인시킨다. (사용자 합의: 2026-05-31)

### 테스트 깊이 — 양이 아니라 distinct 실패 커버리지 (사용자 합의: 2026-06-06)

작성이 빨라도 유지보수·실행 시간·신호 희석·독립성 함정("구현과 테스트를 둘 다 Claude가 짜면 같은 가정을 공유")이라는
비용은 안 사라진다 — 예산은 변형 양산이 아니라 옳은 곳에 깊이로 쓴다.

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

- `.claude/hooks/require-tests-before-commit.ps1` 가 `git commit` 을 가로채, **스테이징에 `.java` 변경이 있으면 `./gradlew test` 를 실행**하고 실패 시 커밋을 차단한다(`.java` 변경 없는 문서/설정 커밋은 건너뜀).
- 역할 분담: **"테스트를 먼저"** 라는 판단은 이 가이드(소프트)가, **"테스트 통과 없이 커밋 불가"** 는 훅(하드)이 담당한다.

### ⚠️ 커밋이 무한 hang 하면 — esc 말고 강제 정리 (자주 재발)

이 게이트가 `./gradlew test` 를 돌리므로 **"git 이 멈춘" 것처럼 보여도 실제론 gradle 테스트·데몬이 훅의 출력 파이프를 쥐고 있는** 것일 때가 많다 — **코어 버그가 아니다**. 뿌리는 멀티 세션의 데몬·빌드 락 경합과, 게이트가 띄운 데몬이 파이프를 물고 사는 것이었다(T-078 4회차).
esc 는 이미 뜬 gradle 자식·데몬을 안 죽여 **다음 커밋도 또 hang**한다.

- **게이트는 자가차단한다(하드)**: `gradlew test`가 **20분 타임아웃**이면 **게이트 자신의 프로세스 트리만 `taskkill /T`로 끄고 커밋 차단(exit 2)** 한다(타임아웃 조정·상한은 훅 헤더와 T-235). **그래도 커밋이 20분+ 멈춰 있으면** 그건 게이트가 아닌 다른 빌드 hang일 수 있으니 아래 수동 정리로 간다.
- **게이트가 느리면 먼저 전원(AC·화면 켜짐)을 본다** — 배터리·대기면 같은 스위트가 3~4배 느리다(실측 T-235).
- **감별**: `git status` 가 빠르면(0.x초) git·레포 자체는 정상 → 코어·레포 문제 아님. 떠도는 `java`(gradle 데몬) 잔존이 단서.
- **강제 정리 — 자기 워크트리 것만**:
  ```powershell
  # 명령줄을 보고 이 워크트리의 java(gradle 클라이언트·bootRun 앱·테스트 워커)만 PID로 끈다.
  # 데몬(GradleDaemon)은 명령줄에 워크트리가 없다 — 클라이언트가 끊기면 그 빌드를 취소하고 idle로 돌아간다.
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select-Object ProcessId, CommandLine | Format-List
  Stop-Process -Id <PID> -Force
  Remove-Item (git rev-parse --git-path index.lock) -EA SilentlyContinue   # 워크트리에선 .git 이 파일이다
  ```
  ⚠️ 메인 체크아웃 경로는 모든 워크트리 경로의 앞부분이다 — `…\BookTimer\.claude\worktrees\<다른 이름>`으로 이어지면 남의 것이다. `Get-Process java | Stop-Process`·`./gradlew --stop`은 머신 전역이라 다른 세션의 빌드를 함께 죽인다(T-235 곁다리·T-078 4회차).

### 예외 (override)

- 커밋 명령에 `SKIP_TESTS` 토큰을 포함하면 게이트를 우회한다 — TDD red 단계의 **실패 테스트만 먼저 커밋**, 긴급 핫픽스 등 정당한 경우에만, **사용자가 명시적으로 허용한 경우에만** Claude 가 부착한다.

---

## 🖥️ 프론트 검증 — 로드순서·타이밍 버그는 실 브라우저로 (필수)

**Thymeleaf 템플릿 + Alpine/htmx/Phaser 같은 클라이언트 위젯**의 변경에서, 특히 **스크립트 로드 순서·타이밍·반응성(reactivity)**이 얽힌 부분은 **헤드리스 테스트·mock·preview만으로 검증을 끝내지 않는다.** 회귀 리스크가 있으면 **실 브라우저로 실배포/로컬 페이지에 직접 붙어 콘솔 에러까지** 확인한다.

**왜**: `defer`/`async`가 실행 시점을 바꿔, 헤드리스 재현이 동기 로드로 단순화되면 **버그가 사라진 가짜 green**이 나온다(T-053·T-054 — 정원 Phaser 위젯에서 반복 실측).

- 📌 **브라우저 표면 선택·CDP 함정은 글로벌 「🖥️ 브라우저 확인」 → `browser-verify` 스킬이 단일 출처다.** 아래는 **이 레포 고유분만**.
- **목 모드 dev 서버 (이 레포)**: Claude가 백그라운드로 띄울 땐 miniapp 폴더에서 `node node_modules/vite/bin/vite.js --mode mock --port 5300 --strictPort` — `npm run dev:mock`을 거치면 TaskStop이 손자 vite를 못 끄고 포트가 남는다(T-264, 훅 `warn-devmock-npm-background`가 경고). 사람이 터미널에서 돌릴 땐 `npm --prefix miniapp run dev:mock`. 포트 5300은 `miniapp/vite.config.ts`에 고정(T-197).
- **진단**: 막히면 추측 전에 **실 배포/로컬 페이지에 붙어 콘솔·네트워크를 직접 읽는다.** "UI는 멀쩡한데 한 기능만 안 됨"일수록 콘솔에 답이 있다.
- **미니앱 실계정·실데이터 화면은 사용자 폰이 유일 경로다** — API 토큰이 토스 SDK의 `authorizationCode`로만 발급돼(`TossAuthApiController`) 브라우저·curl로 만들 수 없고, 운영 CORS에도 localhost가 없다. 목 모드로 찍고, 실데이터가 필요하면 **사용자에게 촬영을 요청**한다(찍은 척 목 화면을 올리지 않는다).
- **재현 하니스**: 만든다면 production의 `defer`/로드 순서·반응성 래핑까지 충실히 복제한다 — 동기 로드로 단순화하면 그 버그를 못 잡는다(T-053).
- **게이트**: 로드순서·타이밍·반응성이 걸린 변경은 **머지 전 실 브라우저 1회 확인**. 순수 로직은 단위테스트(TDD), 클라이언트 통합은 실 브라우저 — 역할 분담.
- ⚠️ **미니앱의 성능·합성(CSS 애니메이션·큰 리스트·이미지 다량)이 걸린 변경은 실기기가 유일한 게이트다 (T-176).**
  데스크톱 크롬·목 모드는 여유가 커서 **이 클래스를 원리상 못 잡는다** — **검증 환경이 실환경보다 관대하면 그 클래스는 가짜 green으로 통과한다**(위 「로드순서」와 같은 뿌리).
  **애니메이션할 속성은 `transform`·`opacity`로 제한**하고(그 외는 페인트·레이아웃 유발), 특히 **이미지·텍스트를
  감싸는 요소에 걸지 않는다**(콘텐츠 재래스터화까지 끌고 온다). 실기기 확인 전이라면 애니메이션 없는 정적 표현을 기본값으로.
- **E2E(Playwright)는 로컬 수동 — 커밋 훅 금지, 승격은 CI 잡(머지 게이트)으로** (2026-06-26 결정): `frontend/e2e/` 표적 2개(로그인·정원 저장). 승격 트리거 = E2E가 잡았어야 할 회귀가 또 새거나, 스펙이 2~3개 흐름 이상으로 커질 때. 실행법은 N-127.

---

## 🔒 CSRF 폼 세션 선확정 — 큰 SSR·익명 폼 GET 핸들러 (T-033·T-049)

**`th:action` 폼이 있는 SSR 페이지를 렌더하는 GET 핸들러는, 폼을 렌더하기 전에 CSRF 토큰을 한 번 당겨 세션을 선확정한다.**

**왜**: Thymeleaf의 CSRF 숨김필드가 세션을 lazy 생성하는데, 큰 페이지는 그 시점에 응답 버퍼가 이미 커밋돼 `IllegalStateException`으로 **그 페이지만 500** — 익명 폼·폼 여럿인 큰 페이지가 위험, 4회+ 재발.

- GET 핸들러에서 폼 렌더 전에 `CsrfTokenUtil.precommit(request)`(`src/main/java/com/booktimer/web/CsrfTokenUtil.java`) 호출 — CsrfToken이 있으면 세션 즉시 생성, 없으면 no-op. 예: `web/PasswordResetController`.
- 훅 강제 불가(정적 감지 오탐 30~50%) — 이 prose 규칙 + 코드 리뷰가 담당.

---

## 🧯 트러블슈팅 활용 — `claude-docs/troubleshooting/T-###.md`

함정과 해결법은 항목 1건 = 파일 1개로 누적한다(허브 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md)는 자동 목차 — 손대지 않는다). 쓰기 규칙은 글로벌 CLAUDE.md 「작업 추적 3종」이 정한다: 먼저 가드로 막고 `summary:`+`guard:` 두 줄, 문서로만 막을 수 있을 때만 4필드, 2회차는 새 번호 대신 그 파일 끝 `- **N회차**` 줄 + `/hookify`.

### 작업 시작 / 디버깅 전 — 먼저 검색

- 빌드·git·PowerShell·테스트 등에서 막히면 **추측하기 전에** `summary:`를 검색한다 — `Select-String -Path claude-docs/troubleshooting/T-*.md -Pattern '^summary:' | Select-String '<증상 키워드>'`. 허브는 Read하지 않는다(목차뿐, 길다).
- 이미 기록된 트랩이면 그 해법을 그대로 적용한다.
- 옛 개념 노트 `claude-docs/learning-notes.md`는 **아카이브**다(2026-08-18 폐기) — 검색해 읽는 건 유용하니 막지 않되 새로 쓰지 않는다. 그 안의 `troubleshooting.md#t-###` 앵커 링크는 이관(2026-09-25)으로 깨져 있다 — 번호로 `troubleshooting/T-###.md`를 연다.

### 디버깅 후

- 해결 직후 답변 끝에 **"🧯 troubleshooting 추가 후보 — `<한 줄 요약>`. 박을까?"**를 짧게 제안한다. OK면 `claude-docs/troubleshooting/T-###.md`를 만들고 `powershell -ExecutionPolicy Bypass -File scripts/rebuild-troubleshooting-index.ps1 -HubPath claude-docs/troubleshooting.md`로 목차를 재생성한다(커밋 훅 `require-troubleshooting-toc.ps1`이 자동으로 해 주지만, 형식이 틀리면 그 훅이 커밋을 막는다). 훅의 검사기는 스테이징이 아니라 워킹 트리를 읽으니 미완성 T 파일은 커밋 전에 치우거나 함께 커밋한다. 번호는 `git ls-tree --name-only origin/main claude-docs/troubleshooting/ | tail -1` 다음 번호.
- **2회 이상 재발한 트랩은 승격**한다 — prose 한 줄보다 하드픽스(훅·스크립트) 우선(사용자 합의 2026-06-22). 하드픽스가 안 되는 이 레포 고유의 예방 규칙·행동 트리거는 이 `CLAUDE.md`(항상 로드)의 해당 섹션에 둔다(T-276·T-277의 `guard:`가 가리키는 줄이 그런 예다). 배경·수치·경위는 참조 문서로, 그 밖의 배치는 `/hookify`의 배치 사다리로 정한다. 승격했으면 그 파일 frontmatter에 `promoted:`(또는 `guard:`)를 단다.
- 옛 「🔁 재발·승격 트래커」 표는 [claude-docs/troubleshooting-tracker.md](claude-docs/troubleshooting-tracker.md)에 동결(참고용). 재발 카운팅은 표가 아니라 **회차 줄**이다.
- 이관 항목(`legacy:`)은 원문 한 줄 그대로다 — 그 파일을 만질 일이 있으면 그때 4필드로 다듬는다(강제 아님).

---

## 🈲 한글 커밋 메시지 — `.commit-msg-tmp` 사용 (T-026)

PowerShell 5.1 에서 한글 커밋 메시지를 인라인으로 넘기면 깨진다 → 메시지를 **UTF-8 파일** `.commit-msg-tmp`(`.gitignore` 등록)로 쓰고 `git commit -F .commit-msg-tmp` 로 커밋한다(인라인 `-m` 한글은 훅 `check-commit-message.ps1`이 차단).

- **쓰기 직전 `rm -f .commit-msg-tmp`, 커밋 직후 삭제, 그리고 쓰기와 커밋은 별도 명령으로** — 이전 세션 잔재를 `-F`가 조용히 집어 옛 메시지로 커밋된다(T-221). 쓰기 명령이 훅에 막혀도 뒤에 이은 커밋만 살아남는 구조를 없앤다.

---

## 🛠️ 빌드 / 실행 메모

- 빌드: `./gradlew build` (Windows: `gradlew.bat`)
- 실행: `./gradlew bootRun` — **`local` 프로파일** 자동 활성(build.gradle `bootRun` 태스크). Security 가 있어 전 엔드포인트 기본 잠김(로컬은 아래 시드 계정으로 로그인).
- **로컬 테스트 계정(시드)** — **로컬 전용이다. 운영엔 테스트 계정이 없다.**
  - `bootRun` 시 `LocalTestAccountSeeder(@Profile("local"))`가 멱등하게 시드. login_id: `testid`
    (소문자 — loadUserByUsername이 입력을 소문자화하지 않음), 비번: `1234qwer!!`, 이메일: `testid@local.test`.
  - admin 뷰 필요 시 코드 추가 없이 `BOOKTIMER_ADMIN_LOGIN_IDS=testid` 환경변수로 `AdminAccountSeeder`가 승격.
  - ⚠️ **운영(`booktimer.app`)에 로그인하지 않는다.** 동명 운영 테스트 계정은 공개 레포 자격증명 노출이라
    2026-08-15 삭제했다. 운영 확인이 꼭 필요하면 새 계정을 만들되 **비밀번호는 이 레포에 적지 않는다**.
  - 위 로컬 비밀번호가 여기 있는 건 안전하다 — `LocalTestAccountSeeder` 하드코딩 **로컬 픽스처**이고
    `@local.test`는 라우팅 불가 예약 TLD다.
- DB: `compose.yaml` 의 MySQL 이 `bootRun` 때 DevTools docker-compose 연동으로 자동 기동 (Docker 필요) — 정리는 「🪢 다중 세션 → bootRun docker-compose 컨테이너」 줄.
- 테스트 DB: **테스트는 H2 인메모리**(`src/test/resources/application.properties`) — Docker 없이 독립 실행(docker-compose 자동 기동 꺼짐).
- toolchain: Java 21 (로컬에 없어도 foojay-resolver 가 자동 다운로드)
- **프론트 번들 (Vue 섬)**: `npm --prefix frontend run build` — `src/main/resources/static/<앱>/<앱>.js` 재생성. `frontend/` TS 수정 후 `bootRun` 전에 반드시 재실행 (T-063). 산출물은 git add·commit까지 해야 반영.
  **(훅 `require-bundle-build.ps1`이 `frontend/**` 스테이징 커밋 전 재빌드·diff를 검사한다.)**
- **책생글 앱 화면 확인 = 개발 앱 「책생글 개발」 + Metro**: PR 워크트리의 `app/`에서(`npm --prefix app ci` 먼저 — 정션 금지) 사용자 터미널 탭에 Git Bash `APP_VARIANT=development npx expo start --dev-client --port 8082 --scheme booktimer-dev`(PowerShell은 `$env:APP_VARIANT='development'; npx expo start --dev-client --port 8082 --scheme booktimer-dev` — 그 탭은 Metro 전용, 게시 · 지문 검사는 다른 탭). npm 스크립트로 만들지 않는다 — `package.json` scripts는 production 지문에 해시된다.
  `--scheme booktimer-dev`는 빼지 않는다 — 없으면 QR 스킴이 `exp+chaeksaenggeul`이 되는데 TestFlight 앱도 그 스킴을 가져 카메라 QR이 어느 앱을 열지 정해지지 않는다.
  개발 앱이 깔린 기기에서는 내 Metro QR 말고는 앱을 여는 링크·QR을 (스킴과 무관하게 — 「책생글 개발」 · `exp+chaeksaenggeul` · `booktimer-dev` · 구글 역순 스킴 · 카카오 스킴 모두) 열지 않는다 — 개발 클라이언트는 등록된 어느 스킴으로든 `__expo_*` 매개변수가 붙은 링크 하나로 남의 JS를 확인 없이 싣고(expo-dev-menu `ExpoLauncherURL.swift` — 스킴 검사 없음), 그 JS는 기기에 저장된 운영 로그인 토큰을 읽을 수 있다(문서 규칙 — 자동 검사 없음 · PR-L 설계 D2).
  예외: 앱이 스스로 그린 위젯 단추(`app/src/lib/widgetIntent.ts`의 `WIDGET_URL` — 위젯 파일엔 주소 글 0을 `widgetIntent.test.ts` 가드가 잠근다) · Live Activity를 눌렀을 때 여는 주소(`app/src/lib/surfaces.ts`) — 둘 다 소스 상수 · `__expo` 없음 — 는 눌러도 된다(위젯 확장 설계 D8).
  개발 앱은 모든 PR 세션이 함께 쓰는 한 대다 — 하룻밤 기기 확인(위젯 자정 등)은 「쓰기 ~ 확인」 동안 다른 세션이 개발 앱을 열거나 8082에 Metro를 띄우지 않게 원장 CHECK에 적고, 쓰기 직전 8082 주인 프로세스의 명령줄이 내 워크트리인지 본다. 개발 클라이언트는 다시 켤 때 마지막 주소(PC:8082)를 저절로 열고, 위젯 쓰기는 저장값을 통째로 덮어 그 세션의 옛 JS가 내 쓰기를 지운다(T-277).

### 미니앱 개발 루프 — 기본은 브라우저 목 모드 (2026-08-12 실측 갱신)

미니앱 화면 작업 시 **Claude가 직접 목 모드 vite를 백그라운드로 띄워 브라우저(vite HMR, 포트 5300)로 확인한다** — 명령은 위 「🖥️ 프론트 검증 → 목 모드 dev 서버」 줄(npm 경유 금지 — T-264) — 사용자에게 명령어를 요구하지 않는다(사용자 지정). 목 모드는 `api.ts`가 서버 대신 `src/dev-mock.ts`의 픽스처를 돌려주고 토큰을 더미로 두므로, 서버·토스 SDK·에뮬레이터 없이 홈·서재·소셜·스토리·기록·목표가 전부 뜬다. 목에 없는 경로는 404로 던져 조용히 빈 화면이 되지 않는다.

- 픽스처를 고치려면 `miniapp/src/dev-mock.ts` 한 곳(모듈 메모리 상태라 새로고침이 초기화다).
- **실기기·SDK 연동(실로그인·광고·알림 동의)만** `bash miniapp/deploy.sh --expect "<이번 변경 문구>"` + 실기기다 — 목 모드로는 원리상 확인되지 않는다. ⚠️ **`--expect` 마커는 「소스에 따옴표째 적혀 있어 번들에 그대로 실리는」 문구여야 한다**(T-153) — ① `slice(0,15)` 같은 **코드 조각**은 minify가 `slice(0,EGe)`로 변형하고 ② `11시간 5분`처럼 **런타임에 조립되는 값**(`formatDuration(...)` 결과·템플릿 보간)은 애초에 번들에 리터럴로 없다. 판별법: 그 문구를 소스에서 따옴표째 찾을 수 있는가. **새 UI 문구가 0인 변경**(순수 동작·레이아웃 수정)은 마커를 억지로 만들지 말고 `--expect` 없이 클린 빌드 + `.ait` 해시 검증에 맡긴다.
- ⚠️ **진입 직후 화면을 덮는 것을 만들지 않는다 — 심사가 두 번 반려한 자리다 (T-183).**
  시트·딤·모달·**툴팁까지 전부**다. 심사가 막는 것은 「바텀시트」라는 모양이 아니라 **사용자 동의 없이 진입
  직후 화면을 덮는 행위**다. 안내가 필요하면 **화면 안 요소**(배너·링크)로
  두고 사용자가 눌러 시작하게 한다.
  - **판정은 첫 진입을 실제로 재현해서** 한다: 목 모드에서 `localStorage.clear()` → 진입 → **`position:fixed` ·
    `z-index ≥ 99` 요소가 0개**임을 세고, **거절·완주·재방문**까지 밟는다. 「자동 노출을 껐다」는 코드 판독으로
    끝내지 않는다 — 실제로 **거절 경로에서 재발**했다(길 안내 키가 남아 인라인 안내의 게이트가 열렸다).
  - 훅 강제는 불가하다(「진입 직후 뜨는가」는 정적 분석으로 판정할 수 없다) — 이 prose와 위 재현 절차가 게이트다.
- **배포 권한 경계**: Claude는 `deploy.sh` 업로드(deploymentId 확보)에 더해 **앱인토스 콘솔 MCP**(`mcp__apps-in-toss-console__*`, 워크스페이스 69821 · miniAppId 61047)로 **라이브 번들 조회(`bundle_get_live_version`)·테스트 푸시(`bundle_test_push`)·검수 요청(`bundle_submit_review`, 출시노트 포함)까지 직접** 할 수 있다. **[출시하기](라이브 전환)만 콘솔 웹 전용**(MCP에 해당 tool 없음)이라 사용자 몫이다. 운용 원칙: **검수 제출은 사용자 지시가 있을 때만** 한다 — 선제 제출 금지. 배포 보고 시 "검수 요청까지 완료, [출시하기]는 콘솔에서"처럼 경계를 명시하고 출시 완료로 오보고하지 않는다.
- **심사·제출이 언급되면 스토어 스크린샷을 점검한다 (필수)**: 사용자가 심사·[출시하기]를 입에 올리거나 **Claude가 검수 요청(`bundle_submit_review`)을 실행하기 직전**이면 그 자리에서
  `miniapp/screenshots/`의 그림들이 **지금 배포될 UI와 일치하는지** 확인하고, 어긋나면 갱신한다.
  화면이 바뀐 채 옛 그림이 콘솔에 남으면 "실제 앱과 다르다"는 반려 사유가 된다.
  - **Claude가 찍을 수 있는 건 목 모드뿐이다** — 목 모드 vite(위 「목 모드 dev 서버」 줄의 직접 실행) + 크롬 기기 뷰포트(390×844@3x), 촬영 전 포커스 링 blur.
    실계정 화면이 필요하면 위 「🖥️ 프론트 검증」대로 **사용자에게 촬영을 요청**한다(찍은 척 목 화면을 올리지 않는다).
  - 규격(해상도·장수)은 콘솔 확인값을 `miniapp/screenshots/README.md`에 적어 단일 출처로 둔다.
  - 보조 훅: `.claude/hooks/remind-screenshot-on-review.ps1`(UserPromptSubmit)이 프롬프트에서 「심사」·「출시하기」를 만나면 리마인더를 넣는다.
    **훅은 리마인드만 한다** — 점검·촬영 판단은 이 규칙(soft)이 맡는다.
- **검수 요청 직전 [비게임 출시 체크리스트](https://developers-apps-in-toss.toss.im/checklist/app-nongame)의 필수 항목을 대조한다 (필수, T-220)** — 연속 통과는 「허용」의 증거가 아니다(자체 「돌아가기」와 토스 네이티브 뒤로가기의 동시 노출이 열 번쯤 통과한 뒤 반려됐는데, 그 규칙은 처음부터 공개돼 있었다). 그래서 **T-185의 「반려가 직전 통과분 diff에 안 닿으면 무변경 재제출」에는 선행 조건이 있다**: 그 사유가 공개 필수 항목에 있는지부터 본다(있으면 편차가 아니라 규칙 위반이라 재제출해도 같은 자리에서 막힌다).
- ⚠️ **잔디 방향 규약**: 서버 `ContributionGraphBuilder`가 weeks를 뒤집어 보낸다 — **`weeks[0]` = 최신 주 = 왼쪽**, `monthLabels`도 그 순서 기준. 최근 N주는 `slice(0, N)`. oldest-first로 가정하면 안 된다 — 두 화면이 같은 오가정으로 깨졌고 조사 서브에이전트도 오독했다. 규약 테스트는 `api.ts` 주석 + 최신 주만 초록인 픽스처 단언.
- **테스트 하니스**(`npm --prefix miniapp test` = vitest): **jsdom 없음** — `renderToStaticMarkup` 정적 렌더라 effect·클릭이 안 돈다. 로직은 순수 함수로 꺼내 계측하고, effect·핸들러에 대한 부정 단언("호출 안 한다")은 항상 통과라 금지(T-149). TDS `BottomSheet` 등 **포털 컴포넌트는 정적 렌더에서 마크업이 통째로 빈다**(그래서 시트는 자체 구현). `miniapp/.env.test`가 `.env.local` 누수를 차단한다 — 머신 로컬 env로 테스트가 깨지면 이걸 의심.

⚠️ **샌드박스 앱의 dev(핫 리로드) 연결은 웹 미니앱에서 불가능하다** — 샌드박스의 dev 프로토콜은 granite(RN) 전용이고 `@apps-in-toss/web-framework`엔 dev 서버·vite 플러그인 자체가 없다(2026-08-12 실측, T-152). 그래서 `.claude/scripts/miniapp-emulator.ps1`은 **개발 루프의 기본 경로가 아니다** — 향후 지원 대비 + 샌드박스 앱 설치·`adb reverse` 자동화용으로 보존한다(사용법은 스크립트 헤더 주석). 부팅이 5분+ 걸리면 Quick Boot 스냅샷 꼬임(T-151) — `-ColdBoot`로 재시도.
