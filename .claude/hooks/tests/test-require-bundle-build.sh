#!/usr/bin/env bash
# TDD test for require-bundle-build.ps1 (PreToolUse hook)
# Tests: stale frontend bundle detection before commit
#
# Cases 1-4: early-exit via stdin JSON only (no real npm needed)
# Cases 5-6: fixture git repo with a minimal npm build script

HOOK=".claude/hooks/require-bundle-build.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

to_win() { cygpath -w "$1"; }
json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

# Run hook with arbitrary JSON payload, return exit code
run_json() {
    local json="$1"
    echo "$json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

# Run hook with given command string and Windows cwd
run_cmd() {
    local cmd="$1" win_cwd="$2"
    local esc_cmd esc_cwd
    esc_cmd=$(json_esc "$cmd")
    esc_cwd=$(json_esc "$win_cwd")
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

# Helper: create minimal git repo and return Unix path
make_repo() {
    local d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -b main >/dev/null 2>&1 \
        || { git -C "$d" init >/dev/null 2>&1; git -C "$d" checkout -b main >/dev/null 2>&1 || true; }
    git -C "$d" config user.email "test@test.com"
    git -C "$d" config user.name "Test"
    echo "$d"
}

# ── Early-exit cases (stdin-only, no real npm) ────────────────────────────────

# Case 1: commit cmd, staged .java only (no frontend/) → early exit 0
# We can't easily fake git diff --cached here, so we test via a real temp repo
# where no frontend files are staged.
REPO_JAVA=$(make_repo); W_JAVA=$(to_win "$REPO_JAVA")
echo "class Foo {}" > "$REPO_JAVA/Foo.java"
git -C "$REPO_JAVA" add Foo.java >/dev/null 2>&1
git -C "$REPO_JAVA" commit --allow-empty -m "init" >/dev/null 2>&1
echo "class Bar {}" > "$REPO_JAVA/Bar.java"
git -C "$REPO_JAVA" add Bar.java >/dev/null 2>&1
got=$(run_cmd "git commit -m \"feat: java only\"" "$W_JAVA")
check "staged .java only (no frontend/) → exit 0" 0 "$got"

# Case 2: git status (non-commit) → exit 0
SHARED_TMP=$(mktemp -d); TMPS+=("$SHARED_TMP"); W_SHARED=$(to_win "$SHARED_TMP")
got=$(run_cmd "git status" "$W_SHARED")
check "git status (non-commit) → exit 0" 0 "$got"

# Case 3: SKIP_BUNDLE_CHECK bypass token → exit 0
got=$(run_cmd "git commit -m \"test\" SKIP_BUNDLE_CHECK" "$W_SHARED")
check "SKIP_BUNDLE_CHECK token → exit 0" 0 "$got"

# Case 4: SKIP_TESTS bypass token (also bypasses bundle check) → exit 0
got=$(run_cmd "git commit -m \"test\" SKIP_TESTS" "$W_SHARED")
check "SKIP_TESTS token → exit 0" 0 "$got"

# Case 4b: broken JSON → fail-open exit 0
got=$(echo "not-json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "broken JSON → fail-open exit 0" 0 "$got"

# ── Fixture cases (real npm build script) ─────────────────────────────────────
# These require node in PATH. Skip gracefully if node is absent.
if ! command -v node >/dev/null 2>&1; then
    echo "SKIP: node not in PATH -- fixture cases skipped"
    exit $FAILED
fi

# Case 5: frontend staged + bundle ALREADY up-to-date after build → exit 0
REPO_CLEAN=$(make_repo); W_CLEAN=$(to_win "$REPO_CLEAN")
mkdir -p "$REPO_CLEAN/src/main/resources/static/garden"
printf 'built\n' > "$REPO_CLEAN/src/main/resources/static/garden/garden.js"
git -C "$REPO_CLEAN" add . >/dev/null 2>&1
git -C "$REPO_CLEAN" commit -m "init" >/dev/null 2>&1

mkdir -p "$REPO_CLEAN/frontend/src"
# Build script rewrites garden.js with same content ("built") → no diff
cat > "$REPO_CLEAN/frontend/build-fixture.js" << 'EOF'
const fs = require('fs'), path = require('path');
const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'garden', 'garden.js');
fs.mkdirSync(path.dirname(out), {recursive:true});
fs.writeFileSync(out, 'built\n');
EOF
printf '{"scripts":{"build":"node build-fixture.js"}}' > "$REPO_CLEAN/frontend/package.json"
printf 'export const x = 1;\n' > "$REPO_CLEAN/frontend/src/index.ts"
git -C "$REPO_CLEAN" add frontend/ >/dev/null 2>&1

got=$(run_cmd "git commit -m \"feat: bundle up-to-date\"" "$W_CLEAN")
check "(fixture) frontend staged + bundle up-to-date → exit 0" 0 "$got"

# Case 6: frontend staged + bundle STALE (build modifies static) → exit 2
REPO_STALE=$(make_repo); W_STALE=$(to_win "$REPO_STALE")
mkdir -p "$REPO_STALE/src/main/resources/static/garden"
printf 'old content\n' > "$REPO_STALE/src/main/resources/static/garden/garden.js"
git -C "$REPO_STALE" add . >/dev/null 2>&1
git -C "$REPO_STALE" commit -m "init" >/dev/null 2>&1

mkdir -p "$REPO_STALE/frontend/src"
# Build script writes DIFFERENT content → git diff detects stale
cat > "$REPO_STALE/frontend/build-fixture.js" << 'EOF'
const fs = require('fs'), path = require('path');
const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'garden', 'garden.js');
fs.mkdirSync(path.dirname(out), {recursive:true});
fs.writeFileSync(out, 'new content\n');
EOF
printf '{"scripts":{"build":"node build-fixture.js"}}' > "$REPO_STALE/frontend/package.json"
printf 'export const x = 1;\n' > "$REPO_STALE/frontend/src/index.ts"
git -C "$REPO_STALE" add frontend/ >/dev/null 2>&1

got=$(run_cmd "git commit -m \"feat: bundle stale\"" "$W_STALE")
check "(fixture) frontend staged + bundle stale → exit 2" 2 "$got"

exit $FAILED
