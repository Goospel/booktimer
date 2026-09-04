#!/usr/bin/env bash
# TDD test for the SELF-STAGING BLIND SPOT in both commit gates (T-228).
#
# Both PreToolUse gates decide "is this commit interesting?" from the INDEX
# (`git diff --cached --name-only`). But when the command stages itself --
# `git add -A && git commit -F ...`, `git commit -am ...` -- the index is still
# EMPTY at PreToolUse time, so both gates exit 0 silently:
#   - require-bundle-build.ps1        -> stale bundles commit unchecked (PR #1035)
#   - require-tests-before-commit.ps1 -> `./gradlew test` never runs (worse)
#
# Fix: when the command itself stages, gate on the WORKING TREE
# (`git diff --name-only` + `git ls-files --others --exclude-standard`)
# in addition to the index. Fail-safe: the gate may run more often, never less.
#
# RED markers (fail before the fix): B1, B5, J1, J5.
# Positive controls (must stay green, so "block everything" cannot pass):
#   (a) already-staged normal path unchanged: B2, J2
#   (b) unrelated (docs-only) self-staging commit still skips: B3, J3
#   (c) SKIP_* bypass tokens still bypass: B4, J4

HOOK_BUNDLE=".claude/hooks/require-bundle-build.ps1"
HOOK_TESTS=".claude/hooks/require-tests-before-commit.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

to_win() { cygpath -w "$1"; }
json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

# Run a hook with a command string + Windows cwd. Outer `timeout` is a safety net.
run_hook() {
    local hook="$1" cmd="$2" win_cwd="$3"
    local esc_cmd esc_cwd
    esc_cmd=$(json_esc "$cmd")
    esc_cwd=$(json_esc "$win_cwd")
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | timeout 90 powershell.exe -NoProfile -File "$hook" >/dev/null 2>&1
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

make_repo() {
    local d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -b main >/dev/null 2>&1 \
        || { git -C "$d" init >/dev/null 2>&1; git -C "$d" checkout -b main >/dev/null 2>&1 || true; }
    git -C "$d" config user.email "test@test.com"
    git -C "$d" config user.name "Test"
    echo "$d"
}

# ── Bundle gate fixtures ──────────────────────────────────────────────────────
# A repo whose `npm run build` writes content DIFFERENT from the committed
# artifact -> any run of the gate detects a stale bundle and blocks (exit 2).
make_stale_bundle_repo() {
    local d; d=$(make_repo)
    mkdir -p "$d/src/main/resources/static/garden" "$d/frontend/src"
    printf 'old content\n' > "$d/src/main/resources/static/garden/garden.js"
    cat > "$d/frontend/build-fixture.js" << 'EOF'
const fs = require('fs'), path = require('path');
const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'garden', 'garden.js');
fs.mkdirSync(path.dirname(out), {recursive:true});
fs.writeFileSync(out, 'new content\n');
EOF
    printf '{"scripts":{"build":"node build-fixture.js"}}' > "$d/frontend/package.json"
    printf 'export const x = 1;\n' > "$d/frontend/src/index.ts"
    printf '# doc\n' > "$d/README.md"
    git -C "$d" add . >/dev/null 2>&1
    git -C "$d" commit -m "init" >/dev/null 2>&1
    echo "$d"
}

# ── Test gate fixture: fake gradlew that always FAILS ────────────────────────
# So exit 2 proves the gate actually ran; exit 0 proves it was skipped.
write_failing_gradlew() {
    printf '@echo off\r\nexit /b 1\r\n' > "$1/gradlew.bat"
}

make_java_repo() {
    local d; d=$(make_repo)
    echo "class Foo {}" > "$d/Foo.java"
    printf '# doc\n' > "$d/README.md"
    git -C "$d" add . >/dev/null 2>&1
    git -C "$d" commit -m "init" >/dev/null 2>&1
    write_failing_gradlew "$d"
    echo "$d"
}

if ! command -v node >/dev/null 2>&1; then
    echo "SKIP: node not in PATH -- bundle gate cases skipped"
    SKIP_BUNDLE=1
fi

# ══ Bundle gate (require-bundle-build.ps1) ═══════════════════════════════════
if [ -z "$SKIP_BUNDLE" ]; then

# B1 [RED]: `git add -A && git commit` -- frontend dirty in WORKTREE, index empty
R=$(make_stale_bundle_repo); W=$(to_win "$R")
printf 'export const x = 2;\n' > "$R/frontend/src/index.ts"
got=$(run_hook "$HOOK_BUNDLE" 'git add -A && git commit -F .commit-msg-tmp' "$W")
check "[B1 RED] self-staging (git add -A) + dirty frontend + stale bundle -> exit 2" 2 "$got"

# B2 [control a]: already-staged normal path unchanged
R=$(make_stale_bundle_repo); W=$(to_win "$R")
printf 'export const x = 2;\n' > "$R/frontend/src/index.ts"
git -C "$R" add frontend/src/index.ts >/dev/null 2>&1
got=$(run_hook "$HOOK_BUNDLE" 'git commit -F .commit-msg-tmp' "$W")
check "[B2 ctrl-a] already-staged frontend + stale bundle -> exit 2 (unchanged)" 2 "$got"

# B3 [control b]: self-staging but only docs changed -> still skips
R=$(make_stale_bundle_repo); W=$(to_win "$R")
printf '# doc changed\n' > "$R/README.md"
got=$(run_hook "$HOOK_BUNDLE" 'git add -A && git commit -F .commit-msg-tmp' "$W")
check "[B3 ctrl-b] self-staging + docs-only worktree -> exit 0 (no false block)" 0 "$got"

# B4 [control c]: SKIP_BUNDLE_CHECK still bypasses on the self-staging path
R=$(make_stale_bundle_repo); W=$(to_win "$R")
printf 'export const x = 2;\n' > "$R/frontend/src/index.ts"
got=$(run_hook "$HOOK_BUNDLE" 'git add -A && git commit -F .commit-msg-tmp SKIP_BUNDLE_CHECK' "$W")
check "[B4 ctrl-c] self-staging + SKIP_BUNDLE_CHECK -> exit 0 (bypass honored)" 0 "$got"

# B5 [RED]: `git commit -am` self-stages too
R=$(make_stale_bundle_repo); W=$(to_win "$R")
printf 'export const x = 2;\n' > "$R/frontend/src/index.ts"
got=$(run_hook "$HOOK_BUNDLE" 'git commit -am "feat: x"' "$W")
check "[B5 RED] git commit -am + dirty frontend + stale bundle -> exit 2" 2 "$got"

fi

# ══ Test gate (require-tests-before-commit.ps1) ══════════════════════════════

# J1 [RED]: `git add -A && git commit` -- .java dirty in WORKTREE, index empty
R=$(make_java_repo); W=$(to_win "$R")
echo "class Foo { int x; }" > "$R/Foo.java"
got=$(run_hook "$HOOK_TESTS" 'git add -A && git commit -F .commit-msg-tmp' "$W")
check "[J1 RED] self-staging (git add -A) + dirty .java -> gate runs -> exit 2" 2 "$got"

# J2 [control a]: already-staged normal path unchanged
R=$(make_java_repo); W=$(to_win "$R")
echo "class Foo { int x; }" > "$R/Foo.java"
git -C "$R" add Foo.java >/dev/null 2>&1
got=$(run_hook "$HOOK_TESTS" 'git commit -F .commit-msg-tmp' "$W")
check "[J2 ctrl-a] already-staged .java -> exit 2 (unchanged)" 2 "$got"

# J3 [control b]: self-staging but only docs changed -> gate must NOT run
R=$(make_java_repo); W=$(to_win "$R")
printf '# doc changed\n' > "$R/README.md"
got=$(run_hook "$HOOK_TESTS" 'git add -A && git commit -F .commit-msg-tmp' "$W")
check "[J3 ctrl-b] self-staging + docs-only worktree -> exit 0 (no false gate)" 0 "$got"

# J4 [control c]: SKIP_TESTS still bypasses on the self-staging path
R=$(make_java_repo); W=$(to_win "$R")
echo "class Foo { int x; }" > "$R/Foo.java"
got=$(run_hook "$HOOK_TESTS" 'git add -A && git commit -F .commit-msg-tmp SKIP_TESTS' "$W")
check "[J4 ctrl-c] self-staging + SKIP_TESTS -> exit 0 (bypass honored)" 0 "$got"

# J5 [RED]: brand-new UNTRACKED .java file picked up by `git add -A`
R=$(make_java_repo); W=$(to_win "$R")
echo "class Bar {}" > "$R/Bar.java"
got=$(run_hook "$HOOK_TESTS" 'git add -A && git commit -F .commit-msg-tmp' "$W")
check "[J5 RED] self-staging + untracked new .java -> gate runs -> exit 2" 2 "$got"

exit $FAILED
