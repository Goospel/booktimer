#!/usr/bin/env bash
# TDD test for warn-multi-session.ps1 (SessionStart hook)
# Tests: merged claude/* branch counter + orphaned worktree folder detection

HOOK=".claude/hooks/warn-multi-session.ps1"
FAILED=0
TMPS=()

# Create a bare-minimum git repo and return its Unix path
make_repo() {
    local d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -b main >/dev/null 2>&1 \
        || { git -C "$d" init >/dev/null 2>&1; git -C "$d" checkout -b main >/dev/null 2>&1 || true; }
    git -C "$d" config user.email "test@test.com"
    git -C "$d" config user.name "Test"
    git -C "$d" commit --allow-empty -m "init" >/dev/null 2>&1
    echo "$d"
}

# Convert Unix path to Windows path for PowerShell (cwd in JSON)
to_win() { cygpath -w "$1"; }

# Escape backslashes for JSON string embedding
json_esc() { echo "$1" | sed 's/\\/\\\\/g'; }

check() {
    local label="$1" win="$2" want="${3:-}" no_want="${4:-}"
    local out rc ok=1
    out=$(printf '{"cwd":"%s"}' "$(json_esc "$win")" \
          | powershell.exe -NoProfile -File "$HOOK" 2>&1)
    rc=$?
    if [ "$rc" != "0" ]; then
        echo "FAIL: $label (exit $rc, expected 0)"; FAILED=1; return
    fi
    if [ -n "$want" ] && ! echo "$out" | grep -qi "$want"; then
        echo "FAIL: $label (expected pattern '$want' not found in output)"
        echo "  output: $(echo "$out" | head -3)"; ok=0
    fi
    if [ -n "$no_want" ] && echo "$out" | grep -qi "$no_want"; then
        echo "FAIL: $label (unexpected pattern '$no_want' found in output)"; ok=0
    fi
    [ "$ok" = "1" ] && echo "PASS: $label" || FAILED=1
}

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# ── Fixture 1: 12 merged claude/* branches (above threshold=10) ──
R1=$(make_repo); W1=$(to_win "$R1")
for i in $(seq 1 12); do git -C "$R1" branch "claude/test-$i" >/dev/null 2>&1; done

# ── Fixture 2: 3 merged claude/* branches (below threshold=10) ──
R2=$(make_repo); W2=$(to_win "$R2")
for i in $(seq 1 3); do git -C "$R2" branch "claude/test-$i" >/dev/null 2>&1; done

# ── Fixture 3: 2 orphaned worktree folders (not registered in git worktree list) ──
R3=$(make_repo); W3=$(to_win "$R3")
mkdir -p "$R3/.claude/worktrees/orphan-a" "$R3/.claude/worktrees/orphan-b"

# ── Fixture 4: Plain directory (not a git repo) ──
R4=$(mktemp -d); TMPS+=("$R4"); W4=$(to_win "$R4")

# ── Fixture 5: Normal repo on main, clean, no stale branches or orphans ──
R5=$(make_repo); W5=$(to_win "$R5")

# ── Test cases ──
check "12 merged claude/* branches → warning shown"          "$W1" "merged"   ""
check "3 merged claude/* branches (below threshold) → quiet" "$W2" ""         "merged"
check "2 orphaned worktree folders → warning shown"          "$W3" "orphaned" ""
check "non-git dir → exit 0, no crash"                       "$W4" ""         ""
check "normal repo (main/clean) → no stale warnings"         "$W5" ""         "merged"

exit $FAILED
