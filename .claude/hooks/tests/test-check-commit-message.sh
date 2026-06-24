#!/usr/bin/env bash
# TDD test for check-commit-message.ps1 (PreToolUse hook)
# Tests: (1) commit title ends with (#NNN) — blocked
#        (3) inline Korean in -m value — blocked
# See plan TDD케이스 매트릭스.

HOOK=".claude/hooks/check-commit-message.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# Run hook: feed JSON with given command string, return exit code
run() {
    local cmd="$1"
    # Escape cmd for JSON: backslash → \\, double-quote → \"
    local esc_cmd
    esc_cmd=$(printf '%s' "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$(json_cwd)" \
        | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

# Run hook with JSON that also includes a .commit-msg-tmp file in cwd
# $1 = command, $2 = file first-line content (leave empty to not create file)
run_with_file() {
    local cmd="$1" file_line="${2:-}"
    local tmpd; tmpd=$(mktemp -d); TMPS+=("$tmpd")
    if [ -n "$file_line" ]; then
        printf '%s\n' "$file_line" > "$tmpd/.commit-msg-tmp"
    fi
    local win_cwd; win_cwd=$(cygpath -w "$tmpd")
    local esc_cmd esc_cwd
    esc_cmd=$(printf '%s' "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_cwd=$(printf '%s' "$win_cwd" | sed 's/\\/\\\\/g')
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

# Shared cwd (no .commit-msg-tmp by default)
SHARED_TMP=$(mktemp -d); TMPS+=("$SHARED_TMP")
json_cwd() { cygpath -w "$SHARED_TMP" | sed 's/\\/\\\\/g'; }

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then
        echo "PASS: $label"
    else
        echo "FAIL: $label (expected exit $expected, got $got)"
        FAILED=1
    fi
}

# ── Case 1: normal file-via-F commit (clean title) → pass ──
got=$(run_with_file "git commit -F .commit-msg-tmp" "feat: something")
check "file-via-F, clean title → exit 0" 0 "$got"

# ── Case 2: file-via-F, title ends with (#351) → blocked ──
got=$(run_with_file "git commit -F .commit-msg-tmp" "feat: something (#351)")
check "file-via-F, title ends (#351) → exit 2" 2 "$got"

# ── Case 3: inline -m, title ends with (#351) → blocked ──
got=$(run 'git commit -m "feat: x (#351)"')
check "inline -m, title ends (#351) → exit 2" 2 "$got"

# ── Case 4: inline -m, Korean title → blocked ──
got=$(run 'git commit -m "feat: 한글 제목"')
check "inline -m, Korean title → exit 2" 2 "$got"

# ── Case 5: inline -m, plain English title → pass ──
got=$(run 'git commit -m "feat: plain title"')
check "inline -m, plain English title → exit 0" 0 "$got"

# ── Case 6: file-via-F, Korean title in file (file path only, no -m) → pass ──
got=$(run_with_file "git commit -F .commit-msg-tmp" "feat: 한글 제목")
check "file-via-F, Korean title in file → exit 0 (Korean ok in file)" 0 "$got"

# ── Case 7: chain command (printf + git commit -F) → no false positive ──
got=$(run 'printf '\''한글'\'' > .commit-msg-tmp; git commit -F .commit-msg-tmp')
check "chain cmd (printf+commit-F), no false positive → exit 0" 0 "$got"

# ── Case 8: -m with (#351) + ALLOW_PR_NUM_IN_TITLE token → pass ──
got=$(run 'git commit -m "feat: x (#351)" ALLOW_PR_NUM_IN_TITLE')
check "ALLOW_PR_NUM_IN_TITLE override → exit 0" 0 "$got"

# ── Case 9: inline Korean + ALLOW_INLINE_MSG token → pass ──
got=$(run 'git commit -m "feat: 한글" ALLOW_INLINE_MSG')
check "ALLOW_INLINE_MSG override → exit 0" 0 "$got"

# ── Case 10: non-commit command → pass ──
got=$(run 'git status')
check "git status (non-commit) → exit 0" 0 "$got"

# ── Case 11: broken JSON stdin → fail-open (exit 0) ──
got=$(echo "not-json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "broken JSON → fail-open exit 0" 0 "$got"

# ── Case 12: bare #NNN mid-title (no parens) → pass ──
got=$(run 'git commit -m "fix: relate to #449 issue"')
check "bare #449 mid-title (no parens) → exit 0" 0 "$got"

exit $FAILED
