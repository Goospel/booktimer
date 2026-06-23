#!/usr/bin/env bash
# pr-merge.sh — PR 머지 보조기 (BookTimer)
#
# 목적: PR 머지 자동화에서 두 가지 함정을 구조적으로 막는다.
#   1) 머지 충돌(DIRTY)인데 "체크 등록 대기"로 오인해 헛폴링하는 것 → 폴링 전 진단으로 차단.
#   2) CI가 pending/충돌에 멈춰 대기가 영영 안 끝나는 것 → 모든 대기에 하드 타임아웃.
#
# 사용:  bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초]
#   - 최대대기초 기본 720(12분). 초과 시 현재 상태를 출력하고 비정상 종료(무한 대기 불가).
#   - 머지는 squash. 성공 시 원격 브랜치까지 삭제. 로컬 main 갱신은 호출자가 한다.
#
# 환경변수(테스트용):
#   PR_MERGE_DRYRUN=1     실제 머지/삭제를 하지 않고 "would: ..."만 출력.
#   PR_MERGE_FAKE_STATE   gh 조회를 건너뛰고 이 값(예: DIRTY)으로 한 번만 분기(스모크).
#
# 종료코드: 0 머지/이미머지, 3 충돌(DIRTY), 4 CI실패, 5 타임아웃, 2 사용법오류.
set -u

PR="${1:-}"
DEADLINE_SECS="${2:-720}"
POLL_SECS=15

if [ -z "$PR" ]; then
  echo "사용법: bash .claude/scripts/pr-merge.sh <PR번호> [최대대기초]" >&2
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
    git push origin --delete "$head" 2>/dev/null || note "원격 브랜치 삭제 건너뜀(이미 없음?)"
  fi
  note "✅ 머지 완료. 로컬 main 갱신은 호출자가 마무리하세요."
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
      note "❌ 머지 충돌(DIRTY). CI는 충돌 상태에선 안 돈다 — 폴링하지 않는다." >&2
      note "   해결: origin/main에 rebase로 충돌을 풀고 force push 후 다시 실행." >&2
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
