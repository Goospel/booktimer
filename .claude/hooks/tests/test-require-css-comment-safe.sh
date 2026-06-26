#!/usr/bin/env bash
# TDD test for require-css-comment-safe.ps1 (PreToolUse hook)
#
# The hook blocks a commit when a staged .css file has a block comment that
# closes early on a glued '*/' (e.g. '.oauth-*/.entry-hero'), which silently
# drops the next CSS rule. Legitimate comment terminators ('... */' + newline)
# must pass. (BookTimer T-100 / N-118 recurrence hard gate.)

HOOK=".claude/hooks/require-css-comment-safe.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# Create a throwaway git repo with one staged CSS file holding $1 as content.
# Echoes the repo dir.
setup_repo() {
    local content="$1"
    local d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -q
    git -C "$d" config user.email t@t.t
    git -C "$d" config user.name tester
    mkdir -p "$d/css"
    printf '%s' "$content" > "$d/css/app.css"
    git -C "$d" add -A
    echo "$d"
}

# Run hook with cwd=repo and a given command; echo exit code.
run_in_repo() {
    local cmd="$1" d="$2"
    local win_cwd; win_cwd=$(cygpath -w "$d")
    local esc_cmd esc_cwd
    esc_cmd=$(printf '%s' "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_cwd=$(printf '%s' "$win_cwd" | sed 's/\\/\\\\/g')
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then
        echo "PASS: $label"
    else
        echo "FAIL: $label (expected exit $expected, got $got)"
        FAILED=1
    fi
}

# ── Case 1: glued '*/' in comment (.oauth-*/.entry-hero) → blocked ──
d=$(setup_repo $'/* shared: .card/.oauth-*/.entry-hero list */\n.auth-shell { max-width: 400px; }\n')
check "glued */ (.oauth-*/.entry-hero) → exit 2" 2 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 2: glued '*/' in comment (.book-*/.record-*) → blocked (the #526 form) ──
d=$(setup_repo $'/* shares .book-*/.record-* helpers */\nbody.profile-page .container { max-width: 1160px; }\n')
check "glued */ (.book-*/.record-*) → exit 2" 2 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 3: clean comment (commas) + normal terminator → pass ──
d=$(setup_repo $'/* shared: .card, .oauth-*, .entry-hero list */\n.auth-shell { max-width: 400px; }\n')
check "clean comment (commas) → exit 0" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 4: legit multi-line comment ending ' */' then a rule → pass ──
d=$(setup_repo $'/* note line one\n   note line two -- ends here */\n.x { color: red; }\n')
check "legit multiline comment terminator → exit 0" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 5: '*/' inside a CSS string (not a comment) → pass ──
d=$(setup_repo $'.x::before { content: "a*/b"; }\n')
check "*/ inside string literal → exit 0" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 6: bug present but SKIP_CSS_COMMENT_CHECK token → pass ──
d=$(setup_repo $'/* .oauth-*/.entry-hero */\n.auth-shell { max-width: 400px; }\n')
check "bypass token SKIP_CSS_COMMENT_CHECK → exit 0" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp SKIP_CSS_COMMENT_CHECK' "$d")"

# ── Case 7: bug only in a non-staged .css is irrelevant; here stage a .java with the pattern → pass ──
d=$(mktemp -d); TMPS+=("$d")
git -C "$d" init -q; git -C "$d" config user.email t@t.t; git -C "$d" config user.name tester
printf '%s' $'class A { String s = "x*/y"; }\n' > "$d/A.java"
git -C "$d" add -A
check "non-css staged (.java) → exit 0" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 8: non-commit command → pass ──
d=$(setup_repo $'/* .oauth-*/.entry-hero */\n.x{}\n')
check "git status (non-commit) → exit 0" 0 "$(run_in_repo 'git status' "$d")"

# ── Case 9: broken JSON stdin → fail-open ──
got=$(echo "not-json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "broken JSON → fail-open exit 0" 0 "$got"

exit $FAILED
