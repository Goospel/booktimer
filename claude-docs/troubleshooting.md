# 트러블슈팅 — 작업 중 만난 함정과 해결법

> "이렇게 하지 마라" 형 실전 트랩 기록. 같은 실수 두 번 반복 방지.
> 개념 이해는 [learning-notes.md](learning-notes.md), 프로젝트 규칙은 [../CLAUDE.md](../CLAUDE.md) 참고.

## 📑 목차

- [T-001. 확인 질문과 실행을 병렬로 보내 의도와 다르게 머지됨](#t-001-확인-질문과-실행을-병렬로-보내-의도와-다르게-머지됨)
- [T-002. 실수 머지된 PR — main force-push로 이력 되돌리기](#t-002-실수-머지된-pr--main-force-push로-이력-되돌리기)
- [T-003. `git show > 파일` 리다이렉트가 한글/UTF-8 파일을 깨뜨림](#t-003-git-show--파일-리다이렉트가-한글utf-8-파일을-깨뜨림)
- [T-004. gradlew stderr가 `$EAP=Stop` 훅을 죽임](#t-004-gradlew-stderr가-eapstop-훅을-죽임)

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

## 🔄 누적 갱신

| 일자 | 추가 항목 |
|---|---|
| 2026-05-31 | 초안 + T-001~T-004 |
