#!/usr/bin/env bash
# TDD test for notify-when-waiting.ps1 (Notification hook -- B1)
#
# Fires a Windows toast when Claude Code is waiting for the user (e.g. a merge
# confirmation while the user is on another tab). It is a SIDE-EFFECT-ONLY hook:
# it must ALWAYS exit 0 (never block the session) and must NOT hang.
#
# Tests run in DRY-RUN (BOOKTIMER_NOTIFY_DRYRUN=1): the script prints
# "WOULD_TOAST: <body>" instead of actually popping a toast, so CI/headless runs
# don't spam the desktop. An outer `timeout` guards against any hang.

HOOK=".claude/hooks/notify-when-waiting.ps1"
FAILED=0
LOG="$(mktemp)"; trap 'rm -f "$LOG"' EXIT

# run <json> -> echoes "<exitcode>|<stdout-first-line>"
run() {
    local json="$1" out code
    out=$(printf '%s' "$json" \
        | env BOOKTIMER_NOTIFY_DRYRUN=1 BOOKTIMER_NOTIFY_LOG="$LOG" \
              timeout 15 powershell.exe -NoProfile -File "$HOOK" 2>/dev/null)
    code=$?
    # collapse to one line for easy grepping
    printf '%s|%s' "$code" "$(printf '%s' "$out" | tr '\r\n' '  ')"
}

check_exit() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else
        echo "FAIL: $label (expected exit $expected, got $got)"; FAILED=1; fi
}
check_contains() {
    local label="$1" needle="$2" hay="$3"
    case "$hay" in
        *"$needle"*) echo "PASS: $label" ;;
        *) echo "FAIL: $label (output did not contain '$needle': $hay)"; FAILED=1 ;;
    esac
}

# ── Case 1: Notification (idle_prompt) -> exit 0 + would toast ────────────────
R=$(run '{"hook_event_name":"Notification","notificationType":"idle_prompt","message":"Claude is waiting for your input"}')
check_exit     "Notification idle_prompt -> exit 0" 0 "${R%%|*}"
check_contains "Notification idle_prompt -> WOULD_TOAST" "WOULD_TOAST" "${R#*|}"

# ── Case 2: Notification (permission_prompt) -> exit 0 ────────────────────────
R=$(run '{"hook_event_name":"Notification","notificationType":"permission_prompt","message":"Permission required"}')
check_exit "Notification permission_prompt -> exit 0" 0 "${R%%|*}"

# ── Case 3: broken JSON -> fail-open exit 0 ──────────────────────────────────
R=$(run 'not-json')
check_exit "broken JSON -> fail-open exit 0" 0 "${R%%|*}"

# ── Case 4: empty stdin -> exit 0 (never blocks) ─────────────────────────────
R=$(run '')
check_exit "empty stdin -> exit 0" 0 "${R%%|*}"

exit $FAILED
