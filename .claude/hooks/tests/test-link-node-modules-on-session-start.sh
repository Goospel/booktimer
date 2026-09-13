#!/usr/bin/env bash
# TDD test for link-node-modules-on-session-start.ps1 (SessionStart hook)
# Invariants this guards (each a distinct real failure):
#   1) git-repo cwd -> the link script IS invoked (the auto-link actually fires)
#   2) fail-open — SessionStart must NEVER block startup, so the hook exits 0 when:
#        a) the link script fails (exit 1)
#        b) the link script is missing
#        c) cwd is NOT a git repo  -> AND the link script is NOT invoked
#          (junction is meaningless outside a worktree; don't run npm/junction there)
#
# The hook resolves the link script from $PSScriptRoot by default; tests inject a
# stub .ps1 via env BOOKTIMER_LINK_SCRIPT. The stub writes a marker file so we can
# assert whether the hook actually ran it in the session cwd.

HOOK=".claude/hooks/link-node-modules-on-session-start.ps1"
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

run_hook() {  # $1=BOOKTIMER_LINK_SCRIPT (win path or junk)  $2=cwd (fwd-slash win path)
  printf '{"hook_event_name":"SessionStart","cwd":"%s"}' "$2" \
    | BOOKTIMER_LINK_SCRIPT="$1" powershell.exe -NoProfile -File "$HOOK" 2>&1
}

mkgitdir() {  # echoes a git-initialized dir as a forward-slash Windows path
  local d; d=$(mktemp -d); TMPS+=("$d")
  git -C "$d" init -q >/dev/null 2>&1
  cygpath -m "$d"
}

mkstub() {  # $1=exitcode  $2=marker(win path)  -> echoes stub .ps1 as win path
  local s; s=$(mktemp --suffix=.ps1); TMPS+=("$s")
  printf 'Set-Content -LiteralPath "%s" -Value "called"\r\nexit %s\r\n' "$2" "$1" > "$s"
  cygpath -m "$s"
}

# ── Case 1: git repo + working stub -> hook exits 0 AND stub invoked ──
GD=$(mkgitdir)
MARK=$(mktemp -u); TMPS+=("$MARK"); MARKW=$(cygpath -m "$MARK")
STUB=$(mkstub 0 "$MARKW")
run_hook "$STUB" "$GD" >/dev/null 2>&1; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: git-repo cwd -> hook exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi
if [ -f "$MARK" ]; then echo "PASS: link script invoked in git repo"; else echo "FAIL: link script NOT invoked in git repo"; FAILED=1; fi

# ── Case 2: stub fails (exit 1) -> hook still 0 (fail-open) ──
GD2=$(mkgitdir)
STUBF=$(mkstub 1 "$(cygpath -m "$(mktemp -u)")")
run_hook "$STUBF" "$GD2" >/dev/null 2>&1; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: failing link script -> hook still exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi

# ── Case 3: missing link script -> hook tolerates, exits 0 ──
GD3=$(mkgitdir)
run_hook "$(cygpath -m "$(mktemp -u)")-nope.ps1" "$GD3" >/dev/null 2>&1; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: missing link script -> hook exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi

# ── Case 4: cwd NOT a git repo -> hook 0 AND link NOT invoked ──
ND=$(mktemp -d); TMPS+=("$ND"); NDW=$(cygpath -m "$ND")   # deliberately no git init
MARK4=$(mktemp -u); TMPS+=("$MARK4"); MARK4W=$(cygpath -m "$MARK4")
STUB4=$(mkstub 0 "$MARK4W")
run_hook "$STUB4" "$NDW" >/dev/null 2>&1; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: non-git cwd -> hook exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi
if [ -f "$MARK4" ]; then echo "FAIL: link invoked on non-git cwd (should skip)"; FAILED=1; else echo "PASS: link skipped on non-git cwd"; fi

# ── Case 5: git repo under a Korean-named dir -> link invoked (stdin UTF-8) ──
# Read as CP949, "테스트" before the closing quote breaks JSON -> cwd falls back to the
# process cwd. Run the hook FROM a non-git dir so that fallback cannot pass by accident.
KD=$(mktemp -d); TMPS+=("$KD"); mkdir -p "$KD/테스트"; git -C "$KD/테스트" init -q >/dev/null 2>&1
MARK5=$(mktemp -u); TMPS+=("$MARK5")
STUB5=$(mkstub 0 "$(cygpath -m "$MARK5")")
HOOK_ABS=$(cygpath -m "$PWD/$HOOK")
(cd "$ND" && printf '{"hook_event_name":"SessionStart","cwd":"%s"}' "$(cygpath -m "$KD/테스트")" \
    | BOOKTIMER_LINK_SCRIPT="$STUB5" powershell.exe -NoProfile -File "$HOOK_ABS" >/dev/null 2>&1)
if [ -f "$MARK5" ]; then echo "PASS: Korean-named git cwd -> link invoked"; else echo "FAIL: Korean-named git cwd -> link NOT invoked"; FAILED=1; fi

exit $FAILED
