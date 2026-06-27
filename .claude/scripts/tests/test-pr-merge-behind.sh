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
#
# Pure dry-run: PR_MERGE_DRYRUN=1 + PR_MERGE_FAKE_STATE bypass all real gh/git calls.

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

echo
if [ "$FAILED" = "0" ]; then echo "ALL PASS"; exit 0; else echo "SOME FAILED"; exit 1; fi
