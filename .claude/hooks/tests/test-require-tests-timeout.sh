#!/usr/bin/env bash
# TDD test for require-tests-before-commit.ps1 -- TEST GATE TIMEOUT (A1, T-078 hard-fix)
#
# The gradle test gate must NOT hang forever on a build-lock/daemon deadlock.
# It runs gradlew under a timeout (BOOKTIMER_TEST_GATE_TIMEOUT_MS, default 20min,
# capped at 24min -- must stay below the settings.json hook timeout, T-235);
# on timeout it kills the gradle process tree, runs `gradlew --stop` to self-heal,
# and blocks the commit (fail-closed, exit 2) naming both possible causes.
# Case 8 pins the budget order, Case 9 the message markers.
#
# Cases 1-3 use a FAKE gradlew.bat so no real JDK/build is needed:
#   1. fast exit 0  -> hook exit 0   (passing tests allowed)         [red+green]
#   2. fast exit 1  -> hook exit 2   (failing tests blocked)         [red+green]
#   3. sleeps > timeout -> hook exit 2 within ~timeout (THE FIX)     [green only]
# Cases 4-6 are early-exit sanity (no gradlew needed).
#
# Case 3 is the RED->GREEN marker: before the timeout wrapper, a hanging gradlew
# that eventually exits 0 makes the hook return 0 (commit allowed despite a 45-min
# freeze); after the fix it returns 2 in seconds.

HOOK=".claude/hooks/require-tests-before-commit.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

to_win() { cygpath -w "$1"; }
json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

# Run hook with a command string + Windows cwd, optional inline env. Outer `timeout`
# is a safety net so a RED (no-timeout) hook can never hang this test session.
run_cmd() {
    local cmd="$1" win_cwd="$2" extra_env="$3"
    local esc_cmd esc_cwd
    esc_cmd=$(json_esc "$cmd")
    esc_cwd=$(json_esc "$win_cwd")
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | env $extra_env timeout 60 powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
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
    git -C "$d" commit --allow-empty -m "init" >/dev/null 2>&1
    echo "$d"
}

# Stage one .java file so the java gate path is taken.
stage_java() {
    local repo="$1"
    echo "class Foo {}" > "$repo/Foo.java"
    git -C "$repo" add Foo.java >/dev/null 2>&1
}

# Write a fake gradlew.bat. $2 = "pass" | "fail" | "hang".
#  - pass: exit 0 fast
#  - fail: exit 1 fast
#  - hang: sleep ~12s on `test`, but exit 0 fast on `--stop` (so the hook's
#          self-heal call doesn't also block the test)
write_fake_gradlew() {
    local repo="$1" mode="$2"
    case "$mode" in
        pass) printf '@echo off\r\nexit /b 0\r\n' > "$repo/gradlew.bat" ;;
        fail) printf '@echo off\r\nexit /b 1\r\n' > "$repo/gradlew.bat" ;;
        hang) printf '@echo off\r\necho %%* | findstr /C:"--stop" >nul && exit /b 0\r\nping -n 13 127.0.0.1 >nul\r\nexit /b 0\r\n' > "$repo/gradlew.bat" ;;
    esac
}

# ── Case 1: passing gradle test -> exit 0 ─────────────────────────────────────
R1=$(make_repo); W1=$(to_win "$R1"); stage_java "$R1"; write_fake_gradlew "$R1" pass
got=$(run_cmd "git commit -m \"feat: x\"" "$W1")
check "staged .java + gradle test passes -> exit 0" 0 "$got"

# ── Case 2: failing gradle test -> exit 2 ─────────────────────────────────────
R2=$(make_repo); W2=$(to_win "$R2"); stage_java "$R2"; write_fake_gradlew "$R2" fail
got=$(run_cmd "git commit -m \"feat: x\"" "$W2")
check "staged .java + gradle test fails -> exit 2" 2 "$got"

# ── Case 3: HANGING gradle test -> timeout -> exit 2 (THE FIX) ────────────────
R3=$(make_repo); W3=$(to_win "$R3"); stage_java "$R3"; write_fake_gradlew "$R3" hang
got=$(run_cmd "git commit -m \"feat: x\"" "$W3" "BOOKTIMER_TEST_GATE_TIMEOUT_MS=3000")
check "staged .java + gradle test HANGS -> timeout -> exit 2" 2 "$got"

# ── Case 4: doc-only commit (no staged .java) -> early exit 0 ─────────────────
R4=$(make_repo); W4=$(to_win "$R4")
echo "# doc" > "$R4/README.md"
git -C "$R4" add README.md >/dev/null 2>&1
got=$(run_cmd "git commit -m \"docs: x\"" "$W4")
check "doc-only commit (no .java) -> exit 0" 0 "$got"

# ── Case 5: SKIP_TESTS override -> exit 0 ─────────────────────────────────────
R5=$(make_repo); W5=$(to_win "$R5"); stage_java "$R5"; write_fake_gradlew "$R5" fail
got=$(run_cmd "git commit -m \"hotfix\" SKIP_TESTS" "$W5")
check "SKIP_TESTS token bypasses gate -> exit 0" 0 "$got"

# ── Case 6: broken JSON -> fail-open exit 0 ───────────────────────────────────
got=$(echo "not-json" | timeout 30 powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "broken JSON -> fail-open exit 0" 0 "$got"

# ── Case 7: Korean in the command must not bypass the gate (stdin UTF-8) ──────
# Read via [Console]::In (CP949), the trailing lead byte of "테스트" swallows the
# next quote -> JSON parse fails -> catch { exit 0 } -> gate silently skipped.
# "가"/"문서"/"한글" decode harmlessly and would NOT catch that -- keep "테스트".
R7=$(make_repo); W7=$(to_win "$R7"); stage_java "$R7"; write_fake_gradlew "$R7" fail
got=$(run_cmd "git add \"docs/테스트\" && git commit -F .commit-msg-tmp" "$W7")
check "Korean before quote in command + gradle fails -> exit 2" 2 "$got"

# ── Case 8: budget order invariant  default <= max < settings.json timeout ────
# Claude Code hook timeouts are FAIL-OPEN (docs: "A timed-out command ... hook doesn't
# block the tool call"). If the gate's own budget (or the env override cap) reaches
# the settings.json timeout, a slow run is cancelled and the commit goes through
# WITHOUT tests. A parse failure must FAIL here, never pass on empty values.
# The cap also needs a 5-min reserve (RESERVE_SEC) under the settings timeout: the
# hook runs `npm --prefix frontend test` (no timeout, ~47s on AC, 3-4x on battery)
# BEFORE the gradle budget starts, then taskkill + `gradlew --stop` after it.
RESERVE_SEC=300
SETTINGS=".claude/settings.json"
def_min=$(grep -E '^\$timeoutMs *= *[0-9]+ *\* *60 *\* *1000' "$HOOK" | head -1 | sed -E 's/^[^=]*= *([0-9]+).*/\1/')
max_min=$(grep -E '^\$maxTimeoutMs *= *[0-9]+ *\* *60 *\* *1000' "$HOOK" | head -1 | sed -E 's/^[^=]*= *([0-9]+).*/\1/')
set_sec=$(grep -A3 'require-tests-before-commit' "$SETTINGS" | grep -E '"timeout" *: *[0-9]+' | head -1 | sed -E 's/.*"timeout" *: *([0-9]+).*/\1/')
case "$def_min$max_min$set_sec" in
    ''|*[!0-9]*) parsed_ok=0 ;;
    *) parsed_ok=1 ;;
esac
if [ "$parsed_ok" = 1 ] && [ -n "$def_min" ] && [ -n "$max_min" ] && [ -n "$set_sec" ] \
   && [ $((def_min * 60000)) -le $((max_min * 60000)) ] \
   && [ $((max_min * 60 + RESERVE_SEC)) -le "$set_sec" ]; then
    echo "PASS: budget order default(${def_min}m) <= max(${max_min}m) + ${RESERVE_SEC}s reserve <= settings.json(${set_sec}s)"
else
    echo "FAIL: budget order (default='$def_min'm max='$max_min'm settings='$set_sec's) -- parse failure or default<=max, max+${RESERVE_SEC}s<=settings violated"
    FAILED=1
fi

# ── Case 8b: the env override is clamped to $maxTimeoutMs ────────────────────
# Structural check (not behavioral): a behavioral one would have to wait out the
# 24-min cap. Without the clamp, BOOKTIMER_TEST_GATE_TIMEOUT_MS >= 1800s silently
# turns the gate fail-open -- the exact hole Case 8 exists to close.
if grep -qF '$timeoutMs = [math]::Min($parsed, $maxTimeoutMs)' "$HOOK"; then
    echo "PASS: env override clamped by [math]::Min(\$parsed, \$maxTimeoutMs)"
else
    echo "FAIL: env override not clamped to \$maxTimeoutMs"
    FAILED=1
fi

# ── Case 9: timeout message names both causes + current power state ───────────
# The old message asserted "likely gradle daemon/lock contention" and sent two
# diagnoses the wrong way (T-235). Reuse the hang fixture and read stderr.
R9=$(make_repo); W9=$(to_win "$R9"); stage_java "$R9"; write_fake_gradlew "$R9" hang
esc_cmd9=$(json_esc "git commit -m \"feat: x\""); esc_cwd9=$(json_esc "$W9")
err9=$(printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd9" "$esc_cwd9" \
    | env BOOKTIMER_TEST_GATE_TIMEOUT_MS=3000 timeout 60 powershell.exe -NoProfile -File "$HOOK" 2>&1 >/dev/null)
if printf '%s' "$err9" | grep -qF 'Two possible causes' && printf '%s' "$err9" | grep -qF 'Power now:'; then
    echo "PASS: timeout message has 'Two possible causes' and 'Power now:'"
else
    echo "FAIL: timeout message missing 'Two possible causes' and/or 'Power now:'"
    FAILED=1
fi

exit $FAILED
