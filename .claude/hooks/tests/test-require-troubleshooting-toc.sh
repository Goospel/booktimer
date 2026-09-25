#!/usr/bin/env bash
# TDD test for the troubleshooting commit gate after the split (2026-09-25).
#  .claude/hooks/require-troubleshooting-toc.ps1 (PreToolUse, `git commit`):
#   - hub claude-docs/troubleshooting.md or any claude-docs/troubleshooting/ file staged
#     -> runs scripts/rebuild-troubleshooting-index.ps1 -HubPath <hub> (rebuild mode)
#        exit 0 -> re-adds the regenerated hub (auto-fix, REQ-13)
#        exit 1 (INVALID) -> BLOCK exit 2 with the checker output (REQ-14)
#        no `INDEX-CHECK:` marker -> BLOCK exit 2 (the check did not run, REQ-14)
#   - old single-file rows added to the hub (`| date | T-###` / `## T-###.`) -> BLOCK exit 2 (REQ-15)
#   - broken JSON / not a commit / nothing staged / checker missing -> exit 0 (fail-open, REQ-16)
# Fixtures use the real checker copied from scripts/ (the one the hook will run).

CHECKER="scripts/rebuild-troubleshooting-index.ps1"
HOOK=".claude/hooks/require-troubleshooting-toc.ps1"
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT
ERRF=$(mktemp); TMPS+=("$ERRF")

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected [$expected], got [$got])"; FAILED=1; fi
}

HUB=$'# 트러블슈팅\n\n## 항목 목차 (자동 생성 — 직접 편집 금지)\n\n<!-- INDEX:START -->\n<!-- INDEX:END -->\n'
item() {  # id summary
    printf -- '---\nsummary: %s\n---\n\n# %s · %s\n\n- **증상**: a\n- **원인**: b\n- **해결**: c\n- **재발방지**: d\n' "$2" "$1" "$2"
}

# Throwaway repo: current hub + T-001, committed. Echoes the path.
setup_repo() {
    local d; d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -q; git -C "$d" config user.email t@t.t; git -C "$d" config user.name tester
    git -C "$d" config core.autocrlf false
    mkdir -p "$d/claude-docs/troubleshooting" "$d/scripts"
    cp "$CHECKER" "$d/scripts/"
    printf '%s' "$HUB" > "$d/claude-docs/troubleshooting.md"
    item T-001 '첫째' > "$d/claude-docs/troubleshooting/T-001.md"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$d/$CHECKER")" \
        -HubPath "$(cygpath -w "$d/claude-docs/troubleshooting.md")" >/dev/null 2>&1
    git -C "$d" add -A; git -C "$d" commit -q -m init
    echo "$d"
}
run_hook() {
    local cmd="$1" d="$2" win_cwd esc_cmd esc_cwd
    win_cwd=$(cygpath -w "$d")
    esc_cmd=$(printf '%s' "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_cwd=$(printf '%s' "$win_cwd" | sed 's/\\/\\\\/g')
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | timeout 90 powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>"$ERRF"
    echo $?
}
staged_hub_has() { git -C "$1" show :claude-docs/troubleshooting.md | grep -qF "$2" && echo yes || echo no; }
C='git commit -F .commit-msg-tmp'

# ── REQ-13: new T file staged -> hub index regenerated and staged ──
d=$(setup_repo)
item T-002 '둘째' > "$d/claude-docs/troubleshooting/T-002.md"; git -C "$d" add claude-docs/troubleshooting/T-002.md
check "[REQ-13] T file staged -> exit 0" "0" "$(run_hook "$C" "$d")"
check "[REQ-13] T file staged -> staged hub lists T-002" "yes" "$(staged_hub_has "$d" '[T-002](troubleshooting/T-002.md)')"

# ── REQ-13: same with Korean right before the closing quote (UTF-8 stdin, no fail-open) ──
d=$(setup_repo)
item T-002 '둘째' > "$d/claude-docs/troubleshooting/T-002.md"; git -C "$d" add claude-docs/troubleshooting/T-002.md
run_hook "$C # 한글" "$d" >/dev/null
check "[REQ-13] Korean in command -> staged hub still lists T-002" "yes" "$(staged_hub_has "$d" '[T-002](troubleshooting/T-002.md)')"

# ── REQ-13 control: nothing troubleshooting-related staged -> exit 0, hub untouched ──
d=$(setup_repo)
item T-002 '둘째' > "$d/claude-docs/troubleshooting/T-002.md"   # present but NOT staged
printf 'x\n' > "$d/other.txt"; git -C "$d" add other.txt
check "[REQ-13 ctrl] not staged -> exit 0" "0" "$(run_hook "$C" "$d")"
check "[REQ-13 ctrl] not staged -> staged hub unchanged" "no" "$(staged_hub_has "$d" 'T-002')"

# ── REQ-14: T file without summary -> exit 2, checker output passed on ──
d=$(setup_repo)
printf -- '---\npromoted: x\n---\n\n# T-002 · 둘째\n' > "$d/claude-docs/troubleshooting/T-002.md"
git -C "$d" add claude-docs/troubleshooting/T-002.md
check "[REQ-14] summary-less T file -> exit 2" "2" "$(run_hook "$C" "$d")"
grep -qF 'INDEX-CHECK: INVALID' "$ERRF" && r=yes || r=no
check "[REQ-14] block message carries checker output (INDEX-CHECK: INVALID)" "yes" "$r"

# ── REQ-14: checker that prints no marker -> exit 2 (the check did not run) ──
d=$(setup_repo)
printf 'param([string]$HubPath)\nexit 0\n' > "$d/$CHECKER"
item T-002 '둘째' > "$d/claude-docs/troubleshooting/T-002.md"; git -C "$d" add claude-docs/troubleshooting/T-002.md
check "[REQ-14] checker without INDEX-CHECK marker -> exit 2" "2" "$(run_hook "$C" "$d")"

# ── REQ-15: old table row added to the hub -> exit 2 with the new-path message ──
d=$(setup_repo)
printf '| 2026-09-25 | T-002 (**옛 형식 행** / 증상: x) |\n' >> "$d/claude-docs/troubleshooting.md"
git -C "$d" add claude-docs/troubleshooting.md
check "[REQ-15] hub + old table row -> exit 2" "2" "$(run_hook "$C" "$d")"
grep -qF 'claude-docs/troubleshooting/T-###.md' "$ERRF" && r=yes || r=no
check "[REQ-15] block message names claude-docs/troubleshooting/T-###.md" "yes" "$r"

# ── REQ-15: old heading added to the hub -> exit 2 ──
d=$(setup_repo)
printf '\n## T-002. 옛 헤딩\n\n본문\n' >> "$d/claude-docs/troubleshooting.md"
git -C "$d" add claude-docs/troubleshooting.md
check "[REQ-15] hub + old ## T-### heading -> exit 2" "2" "$(run_hook "$C" "$d")"

# ── REQ-15 control: plain sentence added to the hub -> passes (exit 0) ──
d=$(setup_repo)
printf '\n일반 문장 한 줄.\n' >> "$d/claude-docs/troubleshooting.md"
git -C "$d" add claude-docs/troubleshooting.md
check "[REQ-15 ctrl] hub + plain sentence -> exit 0" "0" "$(run_hook "$C" "$d")"

# ── REQ-16: fail-open paths ──
d=$(setup_repo)
item T-002 '둘째' > "$d/claude-docs/troubleshooting/T-002.md"; git -C "$d" add -A
check "[REQ-16] git status (non-commit) -> exit 0" "0" "$(run_hook 'git status' "$d")"
got=$(echo "not-json" | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1; echo $?)
check "[REQ-16] broken JSON -> exit 0" "0" "$got"
rm "$d/$CHECKER"
check "[REQ-16] checker missing -> exit 0" "0" "$(run_hook "$C" "$d")"

exit $FAILED
