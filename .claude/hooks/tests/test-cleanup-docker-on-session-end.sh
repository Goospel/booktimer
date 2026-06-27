#!/usr/bin/env bash
# TDD test for cleanup-docker-on-session-end.ps1 (SessionEnd hook)
# Invariants this guards (each a distinct real failure):
#   1) DEFAULT mode — the cleanup script is invoked with NO --all, so Up
#      containers of other live sessions are never killed (multi-session safety).
#   2) fail-open — a failing cleanup script must NOT make the hook non-zero
#      (SessionEnd must never disrupt teardown).
#   3) tolerant — a missing cleanup script must not crash the hook.
#
# The hook resolves the cleanup script from $PSScriptRoot by default; tests
# inject a stub via env BOOKTIMER_CLEANUP_SCRIPT (Unix path; bash runs it).

HOOK=".claude/hooks/cleanup-docker-on-session-end.ps1"
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

run_hook() {  # $1 = BOOKTIMER_CLEANUP_SCRIPT value
    printf '{"hook_event_name":"SessionEnd","reason":"clear"}' \
      | BOOKTIMER_CLEANUP_SCRIPT="$1" powershell.exe -NoProfile -File "$HOOK" 2>&1
}

# ── Case 1: default mode — stub records its args; assert exit 0 + no --all ──
D=$(mktemp -d); TMPS+=("$D")
ARGFILE="$D/args.txt"
STUB="$D/stub.sh"
cat > "$STUB" <<EOF
#!/usr/bin/env bash
printf '%s' "\$*" > "$ARGFILE"
exit 0
EOF
chmod +x "$STUB"
run_hook "$STUB" >/dev/null; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: default-mode hook exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi
if [ -f "$ARGFILE" ]; then
    args=$(cat "$ARGFILE")
    if echo "$args" | grep -q -- '--all'; then
        echo "FAIL: cleanup invoked with --all (unsafe): '$args'"; FAILED=1
    else
        echo "PASS: cleanup invoked in default mode (no --all): '$args'"
    fi
else
    echo "FAIL: cleanup stub was not invoked"; FAILED=1
fi

# ── Case 2: cleanup script fails (exit 1) — hook must still exit 0 (fail-open) ──
D2=$(mktemp -d); TMPS+=("$D2")
STUB2="$D2/fail.sh"
printf '#!/usr/bin/env bash\nexit 1\n' > "$STUB2"; chmod +x "$STUB2"
run_hook "$STUB2" >/dev/null; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: failing cleanup -> hook still exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi

# ── Case 3: missing cleanup script — hook tolerates, exits 0 ──
run_hook "$D2/nonexistent-$$.sh" >/dev/null; rc=$?
if [ "$rc" = "0" ]; then echo "PASS: missing cleanup script -> hook exits 0"; else echo "FAIL: exit $rc (want 0)"; FAILED=1; fi

exit $FAILED
