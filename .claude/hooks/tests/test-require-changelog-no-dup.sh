#!/usr/bin/env bash
# TDD test for require-changelog-no-dup.ps1 (PreToolUse hook)
#
# The hook blocks `git push` when claude-docs/changelog.md holds two rows with the
# same date + same bold title. That duplicate is produced structurally, not by
# carelessness: `.gitattributes` sets `claude-docs/changelog.md merge=union`
# (T-098), which keeps BOTH sides' lines. When one branch adds a row in commit 1
# and edits that same row in commit 2, replaying those commits over a base that
# also touched the file leaves the first draft AND the final version. No conflict,
# no failing test. (BookTimer T-210 recurrence hard gate.)

HOOK=".claude/hooks/require-changelog-no-dup.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# Create a throwaway dir with claude-docs/changelog.md holding $1. Echoes the dir.
setup_repo() {
    local content="$1"
    local d; d=$(mktemp -d); TMPS+=("$d")
    mkdir -p "$d/claude-docs"
    printf '%s' "$content" > "$d/claude-docs/changelog.md"
    echo "$d"
}

# Run hook with cwd=dir and a given command; echo exit code.
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

HEADER='| 날짜 | 한 일 |
| --- | --- |
'

# The real shape: same date + same bold title, bodies differ (the second carries
# the review round). This is exactly what a rebase leaves behind.
DUP="$HEADER"'| 2026-08-24 | **feat(미니앱): 서체 축을 뒤집는다** — 초판 본문. |
| 2026-08-24 | **feat(미니앱): 서체 축을 뒤집는다** — 초판 본문. reviewer 리뷰 반영까지 담긴 최종본. |
'

CLEAN="$HEADER"'| 2026-08-23 | **feat(미니앱): 시작 토스트** — 본문. |
| 2026-08-24 | **feat(미니앱): 서체 축을 뒤집는다** — 본문. |
'

# Two sessions each appending their own row is the case merge=union exists for.
# Same date, different titles -> must pass.
SAME_DAY="$HEADER"'| 2026-08-24 | **feat(미니앱): 서체 축을 뒤집는다** — 본문. |
| 2026-08-24 | **docs: T-209 등재** — 본문. |
'

d_dup=$(setup_repo "$DUP")
d_clean=$(setup_repo "$CLEAN")
d_same=$(setup_repo "$SAME_DAY")

# 1) blocks the duplicate on push
check "duplicate row blocks git push" 2 "$(run_in_repo 'git push -u origin feat/x' "$d_dup")"

# 2) clean changelog passes
check "clean changelog passes" 0 "$(run_in_repo 'git push' "$d_clean")"

# 3) same day + different titles passes (this is what merge=union is FOR)
check "same date, different titles passes" 0 "$(run_in_repo 'git push' "$d_same")"

# 4) only git push is gated -- commit/status must not be touched
check "git commit is not gated" 0 "$(run_in_repo 'git commit -F .commit-msg-tmp' "$d_dup")"
check "git status is not gated" 0 "$(run_in_repo 'git status --short' "$d_dup")"

# 5) override token
check "ALLOW_CHANGELOG_DUP overrides" 0 \
    "$(run_in_repo 'git push  # ALLOW_CHANGELOG_DUP' "$d_dup")"

# 6) fail-open: no changelog at all -> pass
d_none=$(mktemp -d); TMPS+=("$d_none")
check "missing changelog fails open" 0 "$(run_in_repo 'git push' "$d_none")"

# 7) mutation guard -- a hook that always exits 0 would pass every case above
#    except this one. Keep a case that only a working detector can satisfy:
#    three rows where the duplicate is NOT adjacent (proves it is not a
#    neighbour-comparison shortcut).
APART="$HEADER"'| 2026-08-24 | **feat: 같은 제목** — 초판. |
| 2026-08-24 | **docs: 사이에 낀 다른 행** — 본문. |
| 2026-08-24 | **feat: 같은 제목** — 최종본. |
'
d_apart=$(setup_repo "$APART")
check "non-adjacent duplicate is still caught" 2 "$(run_in_repo 'git push' "$d_apart")"

if [ "$FAILED" = "0" ]; then
    echo "ALL PASS"
else
    echo "SOME TESTS FAILED"
    exit 1
fi
