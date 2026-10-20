#!/usr/bin/env bash
# TDD test for block-npm-over-junction.ps1 (PreToolUse, T-271).
#
# A worktree's frontend/miniapp node_modules is a junction to the main checkout's (link-node-modules.ps1, N-132).
# `npm ci` deletes the entries INSIDE node_modules before installing (npm 11.9.0 lib/commands/ci.js), so over a
# junction it empties the target: 2026-10-20 the main checkout's frontend/node_modules went 128 -> 0 (and every
# other worktree linked to it broke). `npm install --force` moves the target's entries away too, and older npm
# rewrote the target without --force (T-124). The hook blocks node_modules-rewriting npm verbs when the folder
# npm runs in has a junction (reparse point) node_modules.
#
# Fixtures live in a temp dir -- fake main / fake worktree / fake junction. The real main checkout is never
# touched and npm is never run: the hook only receives JSON.
#   positive (expect 2): REQ-01 02 03 07 10 -- a hook that never blocks fails these
#   negative (expect 0): REQ-04 05 06 09    -- a hook that blocks everything fails these

HOOKS="${HOOKS:-.claude/hooks}"            # overridable: run against a mutated copy
SETTINGS="${SETTINGS:-.claude/settings.json}"
HOOK="$HOOKS/block-npm-over-junction.ps1"
FAILED=0

T=$(mktemp -d); W=$(cygpath -w "$T"); ERRF="$T/stderr.txt"
cleanup() {   # unlink the junction first, then delete (never `rm -rf <junction>/` -- the trailing slash empties the target)
    powershell -NoProfile -Command "if (Test-Path -LiteralPath '$W\\wt\\frontend\\node_modules') { [IO.Directory]::Delete('$W\\wt\\frontend\\node_modules', \$false) }"
    rm -rf "$T"
}
trap cleanup EXIT

json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }  # newlines -> \n
drive_posix() { local m; m=$(cygpath -m "$1"); printf '/%s%s' "$(printf '%s' "${m:0:1}" | tr 'A-Z' 'a-z')" "${m:2}"; }  # /c/Users/...

# Run the hook; prints exit code, keeps stderr in $ERRF.
run() {
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$(json_esc "$1")" "$(json_esc "$2")" \
        | timeout 90 powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>"$ERRF"
    echo $?
}
check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected $expected, got $got)"; FAILED=1; fi
}

# main/frontend (real + keep.txt) · wt/frontend (junction -> main) · wt/miniapp (real) · wt/plain (none) · other/frontend (real)
mkdir -p "$T/main/frontend/node_modules" "$T/wt/frontend/src" "$T/wt/miniapp/node_modules" "$T/wt/plain" "$T/other/frontend/node_modules"
for d in main/frontend wt/frontend wt/miniapp wt/plain other/frontend; do echo '{}' > "$T/$d/package.json"; done
echo keep > "$T/main/frontend/node_modules/keep.txt"
powershell -NoProfile -Command "New-Item -ItemType Junction -Path '$W\\wt\\frontend\\node_modules' -Target '$W\\main\\frontend\\node_modules' | Out-Null"
WT="$W\\wt"; MAIN="$W\\main"; OTHER="$W\\other"
lt() { powershell -NoProfile -Command "(Get-Item -LiteralPath '$1' -Force).LinkType" | tr -d '\r'; }
check "fixture · wt/frontend/node_modules is a junction" Junction "$(lt "$WT\\frontend\\node_modules")"
check "fixture · wt/miniapp/node_modules is a real folder" "" "$(lt "$WT\\miniapp\\node_modules")"

# --- REQ-01 · npm ci over the junction is blocked ---
check "REQ-01 · npm --prefix frontend ci over junction -> blocked" 2 "$(run 'npm --prefix frontend ci' "$WT")"
check "REQ-01 · the 2026-10-20 command verbatim (cd abs && ls | head; time npm ... | tail) -> blocked" 2 \
    "$(run "cd \"$(cygpath -m "$T/wt")\" && ls -la frontend/node_modules 2>&1 | head -2; time npm --prefix frontend ci --no-audit --no-fund 2>&1 | tail -3" "$W")"

# --- REQ-02 · verb set = npm 11.9.0 reify commands and aliases + audit fix ---
bad=""
for v in ci clean-install ic install-clean isntall-clean install-ci-test cit clean-install-test sit \
         install add i in ins inst insta instal isnt isnta isntal isntall install-test it \
         uninstall unlink remove rm r un update up upgrade udpate dedupe ddp prune link ln; do
    [ "$(run "npm --prefix frontend $v" "$WT")" = 2 ] || bad="$bad $v"
done
check "REQ-02 · every reify verb/alias is blocked (not blocked:${bad:- none})" "" "$bad"
check "REQ-02 · install --force (moves target entries away) -> blocked" 2 "$(run 'npm --prefix frontend install --force' "$WT")"
check "REQ-02 · audit fix -> blocked" 2 "$(run 'npm --prefix frontend audit fix' "$WT")"
check "REQ-02 · audit (report only) -> passes" 0 "$(run 'npm --prefix frontend audit' "$WT")"

# --- REQ-03 · the folder npm actually runs in ---
check "REQ-03 · cd frontend && npm ci -> blocked" 2 "$(run 'cd frontend && npm ci' "$WT")"
check "REQ-03 · npm -C frontend ci -> blocked" 2 "$(run 'npm -C frontend ci' "$WT")"
check "REQ-03 · npm ci --prefix=frontend (after the verb) -> blocked" 2 "$(run 'npm ci --prefix=frontend' "$WT")"
check "REQ-03 · npm ci from a subfolder walks up to package.json -> blocked" 2 "$(run 'npm ci' "$WT\\frontend\\src")"
check "REQ-03 · PowerShell: Set-Location frontend; npm.cmd ci -> blocked" 2 "$(run 'Set-Location frontend; npm.cmd ci' "$WT")"
check "REQ-03 · /c/... absolute --prefix from another cwd -> blocked" 2 "$(run "npm --prefix \"$(drive_posix "$T/wt")/frontend\" ci" "$OTHER")"
check "REQ-03 · first npm is run, second npm is ci -> blocked" 2 "$(run 'npm --prefix frontend run build && npm --prefix frontend ci' "$WT")"
check "REQ-03 · cd inside an open subshell -> blocked" 2 "$(run '(cd frontend && npm ci)' "$WT")"

# --- REQ-04 · target node_modules is not a junction -> passes ---
check "REQ-04 · real node_modules (miniapp) -> passes" 0 "$(run 'npm --prefix miniapp ci' "$WT")"
check "REQ-04 · main checkout (real folder) -> passes" 0 "$(run 'npm --prefix frontend ci' "$MAIN")"
check "REQ-04 · no node_modules -> passes" 0 "$(run 'npm --prefix plain ci' "$WT")"
check "REQ-04 · repo root without package.json -> passes" 0 "$(run 'npm ci' "$WT")"
check "REQ-04 · another repo's real folder by absolute path -> passes" 0 "$(run "npm --prefix \"$(cygpath -m "$T/other")/frontend\" ci" "$WT")"
check "REQ-04 · cd inside a closed subshell does not leak -> passes" 0 "$(run '(cd frontend && npm run build) && npm --prefix miniapp ci' "$WT")"

# --- REQ-05 · non-reify npm commands pass ---
check "REQ-05 · run build -> passes" 0 "$(run 'npm --prefix frontend run build' "$WT")"
check "REQ-05 · test -> passes" 0 "$(run 'npm --prefix frontend test' "$WT")"
check "REQ-05 · exec -- playwright install (verb = first token) -> passes" 0 "$(run 'npm --prefix frontend exec -- playwright install chromium' "$WT")"
check "REQ-05 · help ci -> passes" 0 "$(run 'npm help ci' "$WT\\frontend")"

# --- REQ-06 · npm in prose is not a command ---
check "REQ-06 · heredoc body -> passes" 0 \
    "$(run "cat > .commit-msg-tmp <<'EOF'
fix: block npm ci

npm --prefix frontend ci empties main
EOF" "$WT")"
check "REQ-06 · PowerShell here-string body -> passes" 0 \
    "$(run "\$m = @'
npm --prefix frontend ci
'@; Set-Content x.txt \$m" "$WT")"
check "REQ-06 · sentence inside quotes -> passes" 0 "$(run 'echo "run npm ci later" && git status' "$WT\\frontend")"

# --- REQ-07 · unknown target folder -> blocked with its own message ---
got=$(run 'M=/c/x/frontend; npm --prefix "$M" ci' "$WT"); grep -qF 'target folder is unknown (T-271)' "$ERRF" && got="$got+msg"
check "REQ-07 · variable --prefix -> blocked + 'target folder is unknown (T-271)'" "2+msg" "$got"

# --- REQ-08 · block message carries the safe procedure (UTF-8, Korean intact) ---
run 'npm --prefix frontend ci' "$WT" >/dev/null
miss=""
for lit in 'over a node_modules junction (T-271)' "$WT\\frontend\\node_modules" 'cmd //c rmdir' 'IO.Directory]::Delete(' \
           'deps-match-lock.js' '정션만 먼저 끊고' 'ALLOW_NPM_OVER_JUNCTION'; do
    grep -qF -- "$lit" "$ERRF" || miss="$miss [$lit]"
done
check "REQ-08 · block message literals (missing:${miss:- none})" "" "$miss"

# --- REQ-09 · bypass token ---
check "REQ-09 · ALLOW_NPM_OVER_JUNCTION -> passes" 0 "$(run 'npm --prefix frontend ci # ALLOW_NPM_OVER_JUNCTION' "$WT")"

# --- REQ-10 · stdin read as UTF-8 (a Korean char right before a quote must not break JSON parsing) ---
check "REQ-10 · Korean before a quote + npm ci -> blocked" 2 "$(run 'echo "문" && npm --prefix frontend ci' "$WT")"

# --- REQ-11 · registered where the runner calls it ---
reg=$(node -e 'const s=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
console.log((s.hooks.PreToolUse||[]).some(g=>g.matcher==="Bash|PowerShell"&&(g.hooks||[]).some(h=>h.command==="powershell.exe"
&&Array.isArray(h.args)&&h.args.includes("${CLAUDE_PROJECT_DIR}/.claude/hooks/block-npm-over-junction.ps1")))?"registered":"missing")' "$SETTINGS")
check "REQ-11 · settings.json PreToolUse(Bash|PowerShell) exec form" registered "$reg"

check "fixture · fake target keep.txt intact (the hook never runs npm)" yes "$(test -f "$T/main/frontend/node_modules/keep.txt" && echo yes)"
exit $FAILED
