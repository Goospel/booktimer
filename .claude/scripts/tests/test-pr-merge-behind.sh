#!/usr/bin/env bash
# Smoke test for pr-merge.sh BEHIND handling + --arm mode (BookTimer, T-111).
# Invariants this guards (each a distinct real failure):
#   1) --arm + BEHIND  -> arms --auto, then resolves BEHIND via `gh pr update-branch`
#      (the exact gap that made bare --auto hang forever under "up-to-date required").
#   2) sync(no --arm) + BEHIND -> the poll loop also resolves BEHIND (update-branch),
#      NOT falling into the catch-all '*' wait (regression guard: BEHIND must be an
#      explicit case, else it silently polls to the 12-min timeout with no fix).
#   3) --arm + DIRTY (no --rebase) -> exit 3 (manual), never a silent merge.
#   4) --arm + CLEAN -> just arms auto-merge and exits 0 (nothing to unstick).
#   5) --arm with gh unreachable (empty PATH) -> early guard exits 2, never "✅ armed"
#      (T-141: every gh call failed silently yet the script reported success).
#   6) --arm where `gh pr merge` exits nonzero -> nonzero exit, never "✅ armed"
#      (the `| sed` pipe used to mask gh's exit code — PIPESTATUS[0] now checked).
#   7) --arm armed but state query returns nothing -> nonzero; empty state is not success.
#   8) --arm "succeeded" but autoMergeRequest is null -> nonzero; success needs evidence.
#
# Cases 1-4 are pure dry-run (PR_MERGE_DRYRUN=1 + PR_MERGE_FAKE_STATE bypass real gh/git).
# Cases 5-8 drive the real code path with a stub `gh` on PATH.

S=".claude/scripts/pr-merge.sh"
FAILED=0

run() {  # $1=FAKE_STATE  $2.. = extra args ; echoes "<exit>\n<output>"
    local fake="$1"; shift
    local out rc
    out="$(PR_MERGE_DRYRUN=1 PR_MERGE_FAKE_STATE="$fake" bash "$S" 999 "$@" 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}
assert_not() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "FAIL: $1 — unexpected '$3'"; FAILED=1; else echo "PASS: $1"; fi
}

# ── Case 1: --arm + BEHIND ──
r="$(run BEHIND --arm)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "arm+BEHIND" "$rc" "0"
assert_has  "arm+BEHIND arms auto-merge" "$out" "gh pr merge 999 --auto --squash"
assert_has  "arm+BEHIND resolves via update-branch" "$out" "gh pr update-branch 999"

# ── Case 2: sync(no --arm) + BEHIND — explicit case, not catch-all ──
r="$(run BEHIND)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "sync+BEHIND" "$rc" "0"
assert_has  "sync+BEHIND resolves via update-branch" "$out" "gh pr update-branch 999"
assert_not  "sync+BEHIND not catch-all wait" "$out" "예상 못한 mergeStateStatus"

# ── Case 3: --arm + DIRTY (no --rebase) -> manual (exit 3) ──
r="$(run DIRTY --arm)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "arm+DIRTY no-rebase" "$rc" "3"
assert_not  "arm+DIRTY no-rebase did not update-branch" "$out" "gh pr update-branch"

# ── Case 4: --arm + CLEAN -> just armed (exit 0) ──
r="$(run CLEAN --arm)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "arm+CLEAN" "$rc" "0"
assert_has  "arm+CLEAN armed and left to server" "$out" "auto-merge 걸림"

# ── Case 5: gh 미해결 → 조기 가드 exit 2, 거짓 성공 금지 (T-141 관측 결함) ──
BASH_BIN="$(command -v bash)"
r_out="$(PATH=/nonexistent "$BASH_BIN" "$S" 999 --arm 2>&1)"; rc=$?
assert_exit "arm+no-gh guard" "$rc" "2"
assert_not  "arm+no-gh no false success" "$r_out" "auto-merge 걸림"

# ── Case 6: gh 존재하나 arm 명령 실패 → 비0 종료, 거짓 성공 금지 (파이프 exit 가림 수정) ──
# 스텁은 `gh pr merge`만 실패시키고 나머지 조회는 전부 건강하게 답한다 — 그래야 이 케이스가
# PIPESTATUS 검사(결함 ①)만을 겨눈다. 전부 실패시키면 빈 상태 가드(결함 ②)가 대신 잡아
# PIPESTATUS를 제거해도 통과하는 공허한 테스트가 된다(돌연변이 실측으로 확인).
STUB="$(mktemp -d)"
cat > "$STUB/gh" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *"pr merge"*)       exit 1 ;;
  *autoMergeRequest*) echo '{"enabledAt":"2026-08-09T00:00:00Z"}'; exit 0 ;;
  *)                  echo "OPEN CLEAN"; exit 0 ;;
esac
EOF
chmod +x "$STUB/gh"
r_out="$(PATH="$STUB:$PATH" "$BASH_BIN" "$S" 999 --arm 2>&1)"; rc=$?
assert_exit "arm gh-fail nonzero" "$rc" "1"
assert_not  "arm gh-fail no false success" "$r_out" "auto-merge 걸림"

# ── Case 7: arm 성공 + 상태 조회 무응답 → 빈 상태를 성공 취급 금지 ──
STUB2="$(mktemp -d)"
cat > "$STUB2/gh" <<'EOF'
#!/usr/bin/env bash
[ "$1" = "pr" ] && [ "$2" = "merge" ] && exit 0
exit 1
EOF
chmod +x "$STUB2/gh"
r_out="$(PATH="$STUB2:$PATH" "$BASH_BIN" "$S" 999 --arm 2>&1)"; rc=$?
assert_exit "arm empty-state nonzero" "$rc" "1"
assert_not  "arm empty-state no false success" "$r_out" "auto-merge 걸림"

# ── Case 8: arm '성공'인데 autoMergeRequest=null(실제 미장착) → 증거 없는 성공 금지 ──
STUB3="$(mktemp -d)"
cat > "$STUB3/gh" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *"pr merge"*)         exit 0 ;;
  *autoMergeRequest*)   exit 0 ;;   # 출력 없음 = null(미장착)
  *mergeStateStatus*)   echo "OPEN CLEAN"; exit 0 ;;
  *)                    exit 0 ;;
esac
EOF
chmod +x "$STUB3/gh"
r_out="$(PATH="$STUB3:$PATH" "$BASH_BIN" "$S" 999 --arm 2>&1)"; rc=$?
assert_exit "arm null-autoMergeRequest nonzero" "$rc" "1"
assert_not  "arm null-autoMergeRequest no false success" "$r_out" "auto-merge 걸림"

echo
if [ "$FAILED" = "0" ]; then echo "ALL PASS"; exit 0; else echo "SOME FAILED"; exit 1; fi
