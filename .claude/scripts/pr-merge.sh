#!/usr/bin/env bash
# pr-merge.sh — PR 머지 보조기 (BookTimer)
#
# 목적: PR 머지 자동화에서 두 가지 함정을 구조적으로 막는다.
#   1) 머지 충돌(DIRTY)인데 "체크 등록 대기"로 오인해 헛폴링하는 것 → 폴링 전 진단으로 차단.
#   2) CI가 pending/충돌에 멈춰 대기가 영영 안 끝나는 것 → 모든 대기에 하드 타임아웃.
#
# 사용:  bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초] [--rebase]
#   - 최대대기초 기본 720(12분). 초과 시 현재 상태를 출력하고 비정상 종료(무한 대기 불가).
#   - 머지는 squash. 성공 시 원격 브랜치까지 삭제. 로컬 main 갱신은 호출자가 한다.
#   - --rebase: DIRTY(머지 충돌)를 만나면 origin/main에 자동 rebase + force-with-lease push 후
#               머지 폴링을 한 호출 안에서 이어간다(흐름 끊김=재실행 누락 방지). 텍스트 충돌이면
#               rebase --abort 후 exit 3(수동 필요). 안전장치: 현재 브랜치==PR head·워킹트리 clean.
#               플래그 없으면 기존대로 DIRTY 즉시 exit 3(파괴적 자동화는 opt-in).
#
# 환경변수(테스트용):
#   PR_MERGE_DRYRUN=1     실제 머지/삭제/rebase를 하지 않고 "would: ..."만 출력.
#   PR_MERGE_FAKE_STATE   gh 조회를 건너뛰고 이 값(예: DIRTY)으로 한 번만 분기(스모크).
#
# 종료코드: 0 머지/이미머지, 3 충돌(DIRTY), 4 CI실패, 5 타임아웃, 2 사용법오류.
set -u

# 위치 인자 <PR번호> [최대대기초] + 위치 무관 플래그 --rebase
AUTO_REBASE=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --rebase) AUTO_REBASE=1 ;;
    *)        ARGS+=("$a") ;;
  esac
done
PR="${ARGS[0]:-}"
DEADLINE_SECS="${ARGS[1]:-720}"
POLL_SECS=15

if [ -z "$PR" ]; then
  echo "사용법: bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초] [--rebase]" >&2
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
    note "would: gh pr merge $PR --squash + 원격 브랜치 삭제"
    return 0
  fi
  note "squash 머지 실행…"
  gh pr merge "$PR" --squash || return 1
  head="$(gh pr view "$PR" --json headRefName -q .headRefName 2>/dev/null)"
  if [ -n "$head" ]; then
    note "원격 브랜치 삭제: $head"
    # 백그라운드(비대화형) git push가 credential/원격 단계에서 멈추면 머지는 됐는데 스크립트가
    # exit 못 하고 영원히 매달린다(T-091) → 30s 하드 타임아웃. 실패해도 머지는 끝났으니 진행.
    timeout 30 git push origin --delete "$head" 2>/dev/null \
      || note "원격 브랜치 삭제 실패/타임아웃 — 수동 정리: git push origin --delete $head"
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
    UNKNOWN|"")
      note "머지 가능성 계산 중 — 잠시 대기(5s)"; sleep 5 ;;
    *)
      note "예상 못한 mergeStateStatus=$merge — 대기(${POLL_SECS}s)"; sleep "$POLL_SECS" ;;
  esac
done
