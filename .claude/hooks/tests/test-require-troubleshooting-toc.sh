#!/usr/bin/env bash
# TDD test for troubleshooting TOC auto-generation.
#  - .claude/scripts/rebuild-troubleshooting-toc.ps1: regenerates the
#    "## 📑 목차" section from "## T-###" body headings (GitHub anchor rules).
#    Echoes 'changed' | 'unchanged'. Non-fatal on missing TOC section.
#  - .claude/hooks/require-troubleshooting-toc.ps1: PreToolUse gate that, on
#    `git commit` with claude-docs/troubleshooting.md staged, runs the rebuild
#    and re-adds the file if the TOC changed (auto-fix, mirrors rebuild-memory-index).

SCRIPT=".claude/scripts/rebuild-troubleshooting-toc.ps1"
HOOK=".claude/hooks/require-troubleshooting-toc.ps1"
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# Fixture: TOC lists T-001/T-002 only; body has T-001/002/003 (003 has special chars).
FIXTURE_STALE=$'# 트러블슈팅\n\n## 📑 목차\n\n- [T-001. 첫째](#t-001-첫째)\n- [T-002. 둘째](#t-002-둘째)\n\n---\n\n## T-001. 첫째\n\n본문1\n\n## T-002. 둘째\n\n본문2\n\n## T-003. `git`/특수 > 문자\n\n본문3\n'
# Fully-synced fixture (TOC already has all 3 with correct anchors).
FIXTURE_SYNCED=$'# 트러블슈팅\n\n## 📑 목차\n\n- [T-001. 첫째](#t-001-첫째)\n- [T-002. 둘째](#t-002-둘째)\n- [T-003. `git`/특수 > 문자](#t-003-git특수--문자)\n\n---\n\n## T-001. 첫째\n\n본문1\n\n## T-002. 둘째\n\n본문2\n\n## T-003. `git`/특수 > 문자\n\n본문3\n'

winpath() { cygpath -w "$1"; }
check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected [$expected], got [$got])"; FAILED=1; fi
}

# ── Case 1: stale TOC → regenerate adds T-003 with correct special-char anchor ──
f=$(mktemp -d); TMPS+=("$f")
printf '%s' "$FIXTURE_STALE" > "$f/ts.md"
out=$(powershell.exe -NoProfile -File "$SCRIPT" -Path "$(winpath "$f/ts.md")" 2>/dev/null | tr -d '\r' | tr -d '\n')
check "stale → stdout 'changed'" "changed" "$out"
grep -qF '[T-003. `git`/특수 > 문자](#t-003-git특수--문자)' "$f/ts.md" && r=ok || r=missing
check "stale → T-003 line with correct GitHub anchor" "ok" "$r"
n=$(grep -c '^## T-' "$f/ts.md")
check "stale → body headings intact (3)" "3" "$n"

# ── Case 2: synced TOC → no-op, file byte-identical ──
f=$(mktemp -d); TMPS+=("$f")
printf '%s' "$FIXTURE_SYNCED" > "$f/ts.md"
before=$(md5sum "$f/ts.md" | cut -d' ' -f1)
out=$(powershell.exe -NoProfile -File "$SCRIPT" -Path "$(winpath "$f/ts.md")" 2>/dev/null | tr -d '\r' | tr -d '\n')
after=$(md5sum "$f/ts.md" | cut -d' ' -f1)
check "synced → stdout 'unchanged'" "unchanged" "$out"
check "synced → file byte-identical" "$before" "$after"

# ── Case 3: no '## 📑 목차' section → fail-soft, body untouched ──
f=$(mktemp -d); TMPS+=("$f")
printf '%s' $'# 트러블슈팅\n\n## T-001. 첫째\n\n본문\n' > "$f/ts.md"
before=$(md5sum "$f/ts.md" | cut -d' ' -f1)
powershell.exe -NoProfile -File "$SCRIPT" -Path "$(winpath "$f/ts.md")" >/dev/null 2>&1
after=$(md5sum "$f/ts.md" | cut -d' ' -f1)
check "no TOC section → file untouched" "$before" "$after"

# ── Hook setup: throwaway git repo with troubleshooting.md + the script copied in ──
setup_repo_with_script() {
    local content="$1" d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -q; git -C "$d" config user.email t@t.t; git -C "$d" config user.name tester
    mkdir -p "$d/claude-docs" "$d/.claude/scripts"
    printf '%s' "$content" > "$d/claude-docs/troubleshooting.md"
    cp "$SCRIPT" "$d/.claude/scripts/"
    echo "$d"
}
run_hook() {
    local cmd="$1" d="$2" win_cwd esc_cmd esc_cwd
    win_cwd=$(cygpath -w "$d")
    esc_cmd=$(printf '%s' "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_cwd=$(printf '%s' "$win_cwd" | sed 's/\\/\\\\/g')
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

# ── Case 4: staged stale troubleshooting.md + git commit → hook re-adds fixed TOC ──
d=$(setup_repo_with_script "$FIXTURE_STALE"); git -C "$d" add -A
check "hook: staged stale ts.md → exit 0" "0" "$(run_hook 'git commit -F .commit-msg-tmp' "$d")"
git -C "$d" show :claude-docs/troubleshooting.md | grep -qF '#t-003-git특수--문자' && r=ok || r=missing
check "hook: regenerated TOC is staged" "ok" "$r"

# ── Case 5: troubleshooting.md NOT staged → pass (no-op) ──
d=$(setup_repo_with_script "$FIXTURE_STALE")
printf 'x\n' > "$d/other.txt"; git -C "$d" add other.txt
check "hook: ts.md not staged → exit 0" "0" "$(run_hook 'git commit -F .commit-msg-tmp' "$d")"

# ── Case 6: non-commit command → pass ──
d=$(setup_repo_with_script "$FIXTURE_STALE"); git -C "$d" add -A
check "hook: git status (non-commit) → exit 0" "0" "$(run_hook 'git status' "$d")"

# ── Case 7: broken JSON → fail-open ──
got=$(echo "not-json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "hook: broken JSON → fail-open exit 0" "0" "$got"

# ── Case 8: BOM-prefixed stale → changed, original BOM preserved ──
f=$(mktemp -d); TMPS+=("$f")
printf '\xef\xbb\xbf%s' "$FIXTURE_STALE" > "$f/ts.md"
out=$(powershell.exe -NoProfile -File "$SCRIPT" -Path "$(winpath "$f/ts.md")" 2>/dev/null | tr -d '\r' | tr -d '\n')
check "BOM stale → 'changed'" "changed" "$out"
b3=$(head -c3 "$f/ts.md" | od -An -tx1 | tr -d ' \n')
check "BOM stale → BOM preserved (efbbbf)" "efbbbf" "$b3"

# ── Case 9: BOM-prefixed synced → unchanged, byte-identical ──
f=$(mktemp -d); TMPS+=("$f")
printf '\xef\xbb\xbf%s' "$FIXTURE_SYNCED" > "$f/ts.md"
before=$(md5sum "$f/ts.md" | cut -d' ' -f1)
out=$(powershell.exe -NoProfile -File "$SCRIPT" -Path "$(winpath "$f/ts.md")" 2>/dev/null | tr -d '\r' | tr -d '\n')
after=$(md5sum "$f/ts.md" | cut -d' ' -f1)
check "BOM synced → 'unchanged'" "unchanged" "$out"
check "BOM synced → byte-identical" "$before" "$after"

exit $FAILED
