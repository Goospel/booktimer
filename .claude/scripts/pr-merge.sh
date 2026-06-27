#!/usr/bin/env bash
# pr-merge.sh — PR 머지 보조기 (BookTimer)
#
# 목적: PR 머지 자동화에서 세 가지 함정을 구조적으로 막는다.
#   1) 머지 충돌(DIRTY)인데 "체크 등록 대기"로 오인해 헛폴링하는 것 → 폴링 전 진단으로 차단.
#   2) CI가 pending/충돌에 멈춰 대기가 영영 안 끝나는 것 → 모든 대기에 하드 타임아웃.
#   3) "머지 전 브랜치 최신화 필수" 정책에서 BEHIND(base에 뒤처짐)면 GitHub가 브랜치를 자동
#      갱신하지 않아 --auto 가 영영 못 머지하는 것 → BEHIND를 gh pr update-branch로 자동 해소
#      (폴링 루프·--arm 양쪽). (T-111 — T-083/T-091/T-094 머지-hang 계열)
#
# 사용:  bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초] [--rebase] [--arm]
#   - 최대대기초 기본 720(12분). 초과 시 현재 상태를 출력하고 비정상 종료(무한 대기 불가).
#   - 머지는 squash. 성공 시 원격 브랜치까지 삭제. 로컬 main 갱신은 호출자가 한다.
#   - --rebase: DIRTY(머지 충돌)를 만나면 origin/main에 자동 rebase + force-with-lease push 후
#               머지 폴링을 한 호출 안에서 이어간다(흐름 끊김=재실행 누락 방지). 텍스트 충돌이면
#               rebase --abort 후 exit 3(수동 필요). 안전장치: 현재 브랜치==PR head·워킹트리 clean.
#               플래그 없으면 기존대로 DIRTY 즉시 exit 3(파괴적 자동화는 opt-in).
#   - --arm:   무폴링 "걸고 떠나기". gh pr merge --auto --squash 를 걸고 상태를 1회 점검해 BEHIND면
#               gh pr update-branch(비파괴 서버사이드 갱신), DIRTY면 rebase(--rebase일 때)로 풀고
#               "즉시" 종료한다 — 머지는 서버(--auto)가 마저 한다(세션을 머지까지 안 묶음). 동기
#               머지(끝까지 보고)가 필요하면 --arm 없이(폴링 루프) 쓴다.
#
# 환경변수(테스트용):
#   PR_MERGE_DRYRUN=1     실제 머지/삭제/rebase를 하지 않고 "would: ..."만 출력.
#   PR_MERGE_FAKE_STATE   gh 조회를 건너뛰고 이 값(예: DIRTY)으로 한 번만 분기(스모크).
#
# 종료코드: 0 머지/이미머지, 3 충돌(DIRTY), 4 CI실패, 5 타임아웃, 2 사용법오류.
set -u

# 위치 인자 <PR번호> [최대대기초] + 위치 무관 플래그 --rebase / --arm
AUTO_REBASE=0
ARM=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --rebase) AUTO_REBASE=1 ;;
    --arm)    ARM=1 ;;
    *)        ARGS+=("$a") ;;
  esac
done
PR="${ARGS[0]:-}"
DEADLINE_SECS="${ARGS[1]:-720}"
POLL_SECS=15

if [ -z "$PR" ]; then
  echo "사용법: bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초] [--rebase] [--arm]" >&2
  exit 2
fi

note() { echo "[pr-merge] $*"; }

# elapsed 시작점 — SECONDS는 bash 내장(스크립트 시작 후 경과초). Date.now류 불필요.
SECONDS=0

# 한 번의 상태 조회. FAKE_STATE가 있으면 그걸 돌려준다(스모크 테스트용).
read_state() {
  if [ -n "${PR_MERGE_FAKE_STATE:-}" ]; then
    echo "OPEN ${PR_MERGE_FAKE_STATE}"
    return 0
  fi
  gh pr view "$PR" --json state,mergeStateStatus \
    -q '.state + " " + .mergeStateStatus' 2>/dev/null
}

# CI 체크 상태를 거칠게 분류: fail / pending / nochecks / pass
read_checks() {
  local out
  out="$(gh pr checks "$PR" 2>&1)"
  if echo "$out" | grep -qi 'no checks'; then echo nochecks; return; fi
  if echo "$out" | grep -qiE '\bfail|error|cancel'; then echo fail; return; fi
  if echo "$out" | grep -qiE '\bpending|in_progress|queued'; then echo pending; return; fi
  echo pass
}

do_merge() {
  local head
  if [ "${PR_MERGE_DRYRUN:-0}" = "1" ]; then
    note "would: gh pr merge $PR --squash + 원격 브랜치 삭제(gh API)"
    return 0
  fi
  # head는 머지 전에 확보(머지 후에도 남지만 안전하게 먼저).
  head="$(timeout 30 gh pr view "$PR" --json headRefName -q .headRefName 2>/dev/null)"
  note "squash 머지 실행…"
  timeout 120 gh pr merge "$PR" --squash || return 1
  if [ -n "$head" ]; then
    note "원격 브랜치 삭제(gh API): $head"
    # ⚠️ git push origin --delete 금지(T-091/T-094): Windows Git Bash에선 git이 띄운 자식
    # (git-remote-https·credential helper)이 SIGTERM을 안 받아 `timeout`으로도 안 죽고 매달려,
    # 머지는 됐는데 스크립트가 exit 못 해 백그라운드 태스크가 40분+ "출력 없음"으로 좀비가 된다.
    # → gh API(자체 토큰 HTTP, 자식 프로세스 없음)로 ref를 직접 삭제하면 hang이 원천 차단된다.
    timeout 30 gh api -X DELETE "repos/{owner}/{repo}/git/refs/heads/$head" >/dev/null 2>&1 \
      || note "원격 브랜치 삭제 실패 — 수동: gh api -X DELETE repos/{owner}/{repo}/git/refs/heads/$head"
  fi
  note "✅ 머지 완료. 로컬 main 갱신은 호출자가 마무리하세요."
}

# DIRTY(머지 충돌)를 origin/main 자동 rebase로 풀어 본다. --rebase 일 때만 호출.
# 성공(충돌 없음) → force-with-lease push 후 return 0(루프가 머지로 진행). 충돌/실패 → return 1(수동 필요).
try_rebase() {
  if [ "${PR_MERGE_DRYRUN:-0}" = "1" ]; then
    note "would: 안전검증(현재 브랜치==PR head·워킹트리 clean) 후 git fetch && git rebase origin/main && git push --force-with-lease"
    return 0
  fi
  local head cur
  head="$(gh pr view "$PR" --json headRefName -q .headRefName 2>/dev/null)"
  cur="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
  # 안전장치 ①: 현재 체크아웃 브랜치가 PR head와 같아야 한다(엉뚱한 브랜치 rebase·force push 방지).
  if [ -z "$head" ] || [ "$cur" != "$head" ]; then
    note "❌ --rebase 거부: 현재 브랜치($cur) ≠ PR head($head). PR 브랜치를 체크아웃하고 재실행." >&2
    return 1
  fi
  # 안전장치 ②: 미커밋 변경이 있으면 rebase가 위험 → 거부.
  if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    note "❌ --rebase 거부: 워킹 트리에 미커밋 변경이 있음. 정리(커밋/stash) 후 재실행." >&2
    return 1
  fi
  note "🔄 DIRTY → origin/main에 자동 rebase 시도(--rebase)…"
  git fetch origin --quiet || { note "❌ fetch 실패 — 중단." >&2; return 1; }
  if git rebase origin/main; then
    note "rebase 성공 → force-with-lease push…"
    git push --force-with-lease origin "$head" || { note "❌ force push 실패 — 중단." >&2; return 1; }
    note "✅ rebase+push 완료 — 머지 폴링 재개."
    return 0
  fi
  note "❌ rebase 충돌 — 자동 해결 불가(텍스트 충돌). abort하고 수동 해결 필요." >&2
  git rebase --abort 2>/dev/null
  return 1
}

# BEHIND(base에 뒤처짐, 충돌은 아님)를 비파괴로 푼다. "머지 전 브랜치 최신화 필수" 정책에서
# --auto가 영영 못 머지하는 주된 원인 — GitHub는 BEHIND 브랜치를 자동 갱신하지 않는다(이 레포
# auto-update off). gh pr update-branch(서버사이드 base→head merge, force-push·로컬 체크아웃
# 불필요)로 갱신하면 CI가 재실행되고 머지가 진행된다. 성공 0, 수동 필요 3.
try_update_branch() {
  note "🔃 BEHIND — gh pr update-branch 로 비파괴 갱신(서버사이드 base→head merge)…"
  if [ "${PR_MERGE_DRYRUN:-0}" = "1" ]; then
    note "would: gh pr update-branch $PR"
    return 0
  fi
  if timeout 60 gh pr update-branch "$PR" >/dev/null 2>&1; then
    note "✅ 브랜치 갱신 요청됨 — CI 재실행 후 머지 진행."
    return 0
  fi
  note "⚠️ update-branch 실패 — 갱신 중 충돌로 DIRTY 전환됐을 수 있음." >&2
  if [ "$AUTO_REBASE" = "1" ] && try_rebase; then return 0; fi
  note "   --rebase 옵션으로 재시도하거나 수동 rebase·force push 필요." >&2
  return 3
}

# --arm: auto-merge를 걸고 상태를 1회 점검해 BEHIND/DIRTY만 풀어준 뒤 "즉시" 종료한다(무폴링).
# 머지 자체는 서버(--auto)가 조건 충족 시 마저 한다 → 세션을 머지까지 묶지 않으면서도
# "BEHIND라 영영 안 머지"(T-111) 사각을 닫는 "걸고 떠나기"의 안전판. 종료코드는 호출부로 전파.
arm_and_exit() {
  if [ "${PR_MERGE_DRYRUN:-0}" = "1" ]; then
    note "would: gh pr merge $PR --auto --squash"
  else
    note "auto-merge(squash) 설정…"
    timeout 60 gh pr merge "$PR" --auto --squash 2>&1 | sed 's/^/[pr-merge] /'
  fi
  local st state merge
  st="$(read_state)"; state="${st%% *}"; merge="${st##* }"
  note "arm 후 상태: state=$state mergeStateStatus=$merge"
  case "$state" in
    MERGED) note "이미 머지됨."; return 0 ;;
    CLOSED) note "PR이 닫힘(머지 아님)." >&2; return 2 ;;
  esac
  case "$merge" in
    BEHIND)
      try_update_branch; return $? ;;
    DIRTY)
      if [ "$AUTO_REBASE" = "1" ]; then
        try_rebase && { note "✅ rebase 완료 — CI 재실행 후 --auto가 마저 머지."; return 0; } || return 3
      fi
      note "❌ DIRTY(머지 충돌) — --rebase로 풀거나 수동 처리 필요." >&2
      return 3 ;;
    *)
      note "✅ auto-merge 걸림 — 필수체크 통과 시 서버가 머지(이 세션 대기 불필요)." ;;
  esac
  return 0
}

# --arm 무폴링 모드: 걸고 BEHIND/DIRTY만 풀고 종료(머지는 서버가 마저).
if [ "$ARM" = "1" ]; then
  arm_and_exit
  exit $?
fi

while :; do
  if [ "$SECONDS" -ge "$DEADLINE_SECS" ]; then
    note "⏱ 타임아웃(${DEADLINE_SECS}s 초과). 마지막 상태: $(read_state). 자동 머지 중단 — 사람이 확인 필요." >&2
    exit 5
  fi

  st="$(read_state)"
  state="${st%% *}"; merge="${st##* }"
  note "상태: state=$state mergeStateStatus=$merge (경과 ${SECONDS}s)"

  case "$state" in
    MERGED) note "이미 머지됨."; exit 0 ;;
    CLOSED) note "PR이 닫힘(머지 아님). 중단." >&2; exit 2 ;;
  esac

  case "$merge" in
    DIRTY)
      if [ "$AUTO_REBASE" = "1" ]; then
        if try_rebase; then
          [ "${PR_MERGE_DRYRUN:-0}" = "1" ] && exit 0   # dry-run 스모크는 rebase 경로 확인 후 종료(무한루프 방지)
          continue                                       # 실제: rebase+push 됐으니 다음 루프에서 CLEAN 재평가
        fi
        exit 3                                           # rebase 충돌/거부 → 수동 필요
      fi
      note "❌ 머지 충돌(DIRTY). CI는 충돌 상태에선 안 돈다 — 폴링하지 않는다." >&2
      note "   해결: --rebase 옵션으로 자동 rebase하거나, 수동으로 origin/main에 rebase·force push 후 다시 실행." >&2
      exit 3 ;;
    CLEAN|HAS_HOOKS)
      do_merge && exit 0 || { note "머지 호출 실패." >&2; exit 1; } ;;
    BLOCKED|UNSTABLE)
      c="$(read_checks)"
      case "$c" in
        fail)     note "❌ CI 실패. 자동 머지 중단." >&2; exit 4 ;;
        pass)     note "CI 통과 — 다음 루프에서 머지 가능 상태 확인."; ;;
        nochecks) note "체크 미등록 — 등록 대기(${POLL_SECS}s)"; ;;
        pending)  note "CI 진행 중 — 대기(${POLL_SECS}s)"; ;;
      esac
      sleep "$POLL_SECS" ;;
    BEHIND)
      if try_update_branch; then
        [ "${PR_MERGE_DRYRUN:-0}" = "1" ] && exit 0   # dry-run 스모크: 경로 확인 후 종료(무한루프 방지)
        sleep "$POLL_SECS"                            # 실제: 갱신됨 → CI 재실행, 다음 루프에서 재평가
      else
        exit 3                                         # 갱신 실패(충돌 등) → 수동 필요
      fi ;;
    UNKNOWN|"")
      note "머지 가능성 계산 중 — 잠시 대기(5s)"; sleep 5 ;;
    *)
      note "예상 못한 mergeStateStatus=$merge — 대기(${POLL_SECS}s)"; sleep "$POLL_SECS" ;;
  esac
done
