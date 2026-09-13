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

# ── Case 9-10: T-210 changelog gate in try_rebase ─────────────────────────────
# The PreToolUse hook (require-single-changelog-commit-before-rebase.ps1) reads the
# command string for `git rebase`; `bash pr-merge.sh <PR> --rebase` carries no such
# token, so the hard fix leaked through this path. try_rebase now runs the same count.
# Fixtures are throwaway repos; the script is invoked with an absolute path from inside
# them. ⚠️ every git here is `git -C "$d"` with a guarded $d -- an empty one would mean
# "the current directory", i.e. the live BookTimer worktree.
S_ABS="$(cd "$(dirname "$S")" && pwd)/$(basename "$S")"
TMPD=()
cleanup_t210() { for d in ${TMPD[@]+"${TMPD[@]}"}; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup_t210 EXIT

mk_gate_repo() {  # $1 = number of commits touching claude-docs/changelog.md
    local n="$1" d i
    d="$(mktemp -d)"; TMPD+=("$d")
    case "$d" in ""|/|.|..) echo "FATAL: refusing fixture dir '$d'" >&2; exit 1 ;; esac
    mkdir -p "$d/claude-docs"
    git -C "$d" init -q -b main 2>/dev/null || { git -C "$d" init -q; git -C "$d" checkout -q -b main; }
    git -C "$d" config user.email t@t.t; git -C "$d" config user.name tester
    printf '| date | what |\n| --- | --- |\n' > "$d/claude-docs/changelog.md"
    git -C "$d" add -A; git -C "$d" commit -q -m base
    git -C "$d" update-ref refs/remotes/origin/main HEAD
    for ((i = 1; i <= n; i++)); do
        printf '| 2026-09-13 | **row %d** |\n' "$i" >> "$d/claude-docs/changelog.md"
        git -C "$d" add -A; git -C "$d" commit -q -m "row $i"
    done
    printf '%s' "$d"
}

run_gate() {  # $1 = fixture dir ; echoes "<exit>\n<output>"
    local out rc
    out="$(cd "$1" && PR_MERGE_DRYRUN=1 PR_MERGE_FAKE_STATE=DIRTY "$BASH_BIN" "$S_ABS" 999 --rebase 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

D2="$(mk_gate_repo 2)"
r="$(run_gate "$D2")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
if [ "$rc" != "0" ]; then echo "PASS: t210 gate rejects 2 changelog commits (exit $rc)"; else echo "FAIL: t210 gate let 2 changelog commits through"; FAILED=1; fi
assert_has "t210 gate names T-210"            "$out" "T-210"
assert_has "t210 gate prescribes reset --soft" "$out" "reset --soft"
assert_has "t210 gate offers the override"     "$out" "ALLOW_MULTI_CHANGELOG_REBASE"

D1="$(mk_gate_repo 1)"
r="$(run_gate "$D1")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "t210 gate lets 1 changelog commit proceed" "$rc" "0"
assert_not  "t210 gate silent on a healthy branch"      "$out" "T-210"
assert_has  "t210 gate reaches the normal rebase path"  "$out" "would:"

# override must reopen the gate it closed
r_out="$(cd "$D2" && ALLOW_MULTI_CHANGELOG_REBASE=1 PR_MERGE_DRYRUN=1 PR_MERGE_FAKE_STATE=DIRTY "$BASH_BIN" "$S_ABS" 999 --rebase 2>&1)"; rc=$?
assert_exit "t210 gate override proceeds" "$rc" "0"
assert_not  "t210 gate override is silent" "$r_out" "T-210"

echo
if [ "$FAILED" = "0" ]; then echo "ALL PASS"; exit 0; else echo "SOME FAILED"; exit 1; fi
