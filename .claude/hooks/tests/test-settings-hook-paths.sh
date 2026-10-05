#!/usr/bin/env bash
# TDD test for T-262 -- project hooks must not depend on the shell's current directory.
#
# settings.json registered every hook as `powershell.exe -NoProfile -File ".claude\hooks\x.ps1"`.
# Claude Code starts a command hook in the CURRENT directory (it follows the Bash tool's `cd`), so after
# `cd miniapp` all project hooks exited 127 (-File argument not found). 127 is a NON-blocking hook error:
# the tool call went ahead and the commit / push / test gates were silently off (2026-10-05, CLI 2.1.289
# and desktop 2.1.286). Fix: exec form, no shell --
#   "command": "powershell.exe", "args": ["-NoProfile", "-File", "${CLAUDE_PROJECT_DIR}/.claude/hooks/x.ps1"]
#
# Static check of settings.json (node parses the JSON; run from the repo root):
#   [REQ-01 RED]  every command hook is exec form with -File ${CLAUDE_PROJECT_DIR}/...  (13 violations before the fix)
#   [REQ-02]      every registered script exists in this checkout
#   [REQ-04 ctrl] the checker saw every command hook, and flags the fixtures it must flag (it can fail)
# NOT proven here: that Claude Code substitutes the placeholder at run time -- that is the real-runner
# check recorded in T-262 (claude -p, cd miniapp, a blocked commit title).

SETTINGS="${SETTINGS:-.claude/settings.json}"   # overridable: run against a mutated copy
FAILED=0
TMPS=()
cleanup() { for f in "${TMPS[@]}"; do rm -f "$f" 2>/dev/null; done; }
trap cleanup EXIT

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected $expected, got $got)"; FAILED=1; fi
}

[ -f "$SETTINGS" ] || { echo "FAIL: $SETTINGS not found -- run from the repo root"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "FAIL: node not in PATH -- cannot parse $SETTINGS (no SKIP: a skipped guard guards nothing)"; exit 1; }

CHECKER=$(mktemp --suffix=.js); TMPS+=("$CHECKER")
cat > "$CHECKER" <<'JS'
// node checker.js <settings.json>  (cwd = repo root). One line per violation, then COUNT/RAW.
const fs = require('fs'), path = require('path');
const text = fs.readFileSync(process.argv[2], 'utf8');
const PH = '${CLAUDE_PROJECT_DIR}/';
let n = 0;
for (const [ev, groups] of Object.entries(JSON.parse(text).hooks || {}))
  groups.forEach((g, gi) => (g.hooks || []).forEach((h, hi) => {
    if (h.type !== 'command') return;
    n++;
    const at = `${ev}[${gi}].hooks[${hi}]`;
    const i = Array.isArray(h.args) ? h.args.indexOf('-File') : -1;
    const p = i >= 0 ? h.args[i + 1] : undefined;
    if (h.command !== 'powershell.exe' || typeof p !== 'string' || !p.startsWith(PH))
      return console.log(`[REQ-01] ${at}: want "command": "powershell.exe", "args": [..., "-File", "${PH}.claude/hooks/<name>.ps1"] -- got ${JSON.stringify({ command: h.command, args: h.args })}`);
    if (!fs.existsSync(path.join(process.cwd(), p.slice(PH.length))))
      console.log(`[REQ-02] ${at}: script not found -- ${p}`);
  }));
console.log(`COUNT ${n}`);
console.log(`RAW ${(text.match(/"type"\s*:\s*"command"/g) || []).length}`);
JS
run_checker() { node "$(cygpath -w "$CHECKER")" "$1"; }
count_of() { printf '%s\n' "$1" | grep -c "^\[$2\]"; }
val_of()   { printf '%s\n' "$1" | sed -n "s/^$2 //p"; }

# ── the real settings ────────────────────────────────────────────────────────
out=$(run_checker "$SETTINGS")
printf '%s\n' "$out" | grep '^\[REQ-' | sed 's/^/    /'
check "[REQ-01 RED] every command hook is exec form: powershell.exe + -File \${CLAUDE_PROJECT_DIR}/.claude/hooks/*.ps1" 0 "$(count_of "$out" REQ-01)"
check "[REQ-02] every registered hook script exists in this checkout" 0 "$(count_of "$out" REQ-02)"
cnt=$(val_of "$out" COUNT); raw=$(val_of "$out" RAW)
if [ "${cnt:-0}" -gt 0 ] 2>/dev/null && [ "$cnt" = "$raw" ]; then seen=ok; else seen="parsed=$cnt text=$raw"; fi
check "[REQ-04 ctrl] checker parsed every command hook (parsed = text count, > 0)" ok "$seen"

# ── fixtures: the checker must be able to fail ───────────────────────────────
FX=$(mktemp --suffix=.json); TMPS+=("$FX")
cat > "$FX" <<'JSON'
{"hooks":{"PreToolUse":[{"matcher":"Bash|PowerShell","hooks":[
  {"type":"command","command":"powershell.exe -NoProfile -File \".claude\\hooks\\block-main-push.ps1\""},
  {"type":"command","command":"powershell.exe","args":["-NoProfile","-File",".claude/hooks/block-main-push.ps1"]},
  {"type":"command","command":"powershell.exe","args":["-NoProfile","-File","${CLAUDE_PROJECT_DIR}/.claude/hooks/no-such-hook.ps1"]}
]}]}}
JSON
fx=$(run_checker "$(cygpath -w "$FX")")
check "[REQ-04 ctrl] checker flags old shell form + exec-form relative path (REQ-01) and a missing script (REQ-02)" "2 1" "$(count_of "$fx" REQ-01) $(count_of "$fx" REQ-02)"

exit $FAILED
